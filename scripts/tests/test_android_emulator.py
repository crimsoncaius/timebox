import concurrent.futures
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("emulator_pool", Path(__file__).parents[1] / "android-emulator.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PoolTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.pool = module.Pool(self.temp.name)
        self.pool.stop = lambda row: None

    def tearDown(self):
        self.pool.db.close()
        self.temp.cleanup()

    def test_concurrent_claims_are_exclusive(self):
        def claim(number):
            pool = module.Pool(self.temp.name)
            try:
                return pool.claim(str(number))["id"]
            except RuntimeError:
                return None
            finally:
                pool.db.close()
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            result = list(executor.map(claim, range(8)))
        self.assertEqual(sorted(result), list(range(1, 9)))

    def test_ordinary_claims_grow_beyond_configured_capacity(self):
        claimed = [self.pool.claim(f"owner-{i}") for i in range(self.pool.config["capacity"] + 3)]
        self.assertEqual([row["id"] for row in claimed], list(range(1, self.pool.config["capacity"] + 4)))

    def test_extra_claim_preserves_review_and_reuses_released_slot(self):
        pool = self.pool
        existing = [pool.claim(f"owner-{n}") for n in range(pool.config["capacity"])]
        pool.touch(existing[0]["token"], "review")
        before = pool.rows()
        extra = pool.claim("additional emulator", extra=True)
        self.assertEqual(pool.config["capacity"] + 1, extra["id"])
        self.assertEqual(before, pool.rows()[:len(before)])
        ordinary = pool.claim("ordinary request")
        self.assertEqual(extra["id"] + 1, ordinary["id"])
        pool.touch(extra["token"], "free")
        replacement = pool.claim("next extra", extra=True)
        self.assertEqual(extra["id"], replacement["id"])
        self.assertNotEqual(extra["token"], replacement["token"])
        with self.assertRaisesRegex(RuntimeError, "Invalid or released"):
            pool.get(extra["token"])
        peer = module.Pool(self.temp.name)
        try:
            self.assertEqual("review", peer.get(existing[0]["token"])["state"])
        finally:
            peer.db.close()

    def test_extra_claim_after_growth_is_visible_to_other_pool_instances(self):
        pool = self.pool
        existing = [pool.claim(f"owner-{i}") for i in range(pool.config["capacity"])]
        grown = pool.claim("ordinary")
        self.assertEqual(grown["id"], pool.config["capacity"] + 1)
        extra = pool.claim("additional review", extra=True)
        self.assertEqual(extra["id"], grown["id"] + 1)
        self.assertEqual([pool.get(row["token"]) for row in existing], existing)
        peer = module.Pool(self.temp.name)
        try:
            self.assertEqual(peer.get(extra["token"]), extra)
            another = peer.claim("another extra request", extra=True)
            self.assertNotEqual(another["id"], extra["id"])
        finally:
            peer.db.close()

    def test_old_token_cannot_touch_reassigned_slot(self):
        old = self.pool.claim("first")
        self.pool.release(old["token"])
        new = self.pool.claim("second")
        self.assertEqual(old["id"], new["id"])
        with self.assertRaises(RuntimeError):
            self.pool.touch(old["token"])

    def test_review_survives_expiry_and_requires_explicit_completion(self):
        row = self.pool.claim("review")
        self.pool.touch(row["token"], "review")
        with patch.object(module.time, "time", return_value=time.time() + 10000):
            with self.assertRaises(RuntimeError):
                self.pool.release(row["token"], recover=True)
        with self.assertRaises(RuntimeError):
            self.pool.release(row["token"])
        self.pool.release(row["token"], review_done=True)

    def test_active_operation_blocks_release_and_recovery(self):
        row = self.pool.claim("busy")
        with self.pool.lock(row):
            with self.assertRaises(RuntimeError):
                self.pool.release(row["token"])

    def test_recovery_requires_expiry_and_stops_before_release(self):
        row = self.pool.claim("abandoned")
        with self.assertRaises(RuntimeError):
            self.pool.release(row["token"], recover=True)
        self.pool.stop = lambda row: self.assertEqual(self.pool.get(row["token"])["state"], "cleanup_pending")
        with patch.object(module.time, "time", return_value=time.time() + 10000):
            self.pool.release(row["token"], recover=True)

    def test_failed_stop_preserves_ownership(self):
        row = self.pool.claim("stop failure")
        with patch.object(self.pool, "stop", side_effect=RuntimeError("offline uncertainty")):
            with self.assertRaises(RuntimeError):
                self.pool.release(row["token"])
        self.assertEqual(self.pool.get(row["token"])["owner"], "stop failure")

    def test_test_command_releases_after_failure(self):
        row = self.pool.claim("failed test")
        with patch.object(module, "Pool", return_value=self.pool), \
             patch.object(self.pool, "acquire", return_value=row), \
             patch.object(self.pool, "operation", return_value=1), \
             patch.object(module.sys, "argv", ["helper", "test", "--owner", "failed test"]):
            self.assertEqual(module.main(), 1)
        with self.assertRaises(RuntimeError):
            self.pool.get(row["token"])

    def test_interrupted_gradle_blocks_recovery(self):
        row = self.pool.claim("orphan")
        marker = self.pool.home / f"operation-{row['id']}.json"
        marker.write_text('{"pid": 99999999, "kind": "gradle"}')
        with patch.object(module.time, "time", return_value=time.time() + 10000):
            with self.assertRaisesRegex(RuntimeError, "descendants"):
                self.pool.release(row["token"], recover=True)

    def test_different_config_cannot_change_active_device_mapping(self):
        self.pool.claim("active")
        configuration = dict(self.pool.config, first_port=5600)
        with patch.object(module.json, "loads", return_value=configuration):
            with self.assertRaisesRegex(RuntimeError, "configuration differs"):
                module.Pool(self.temp.name)

    def test_unknown_device_identity_is_not_stopped(self):
        row = self.pool.claim("identity")
        with patch.object(self.pool, "adb", return_value=module.subprocess.CompletedProcess([], 0, "Personal_Phone\nOK\n")):
            with self.assertRaisesRegex(RuntimeError, "unmanaged"):
                self.pool.identity(row)

    def test_empty_adb_response_during_shutdown_is_offline(self):
        row = self.pool.claim("stopping")
        with patch.object(self.pool, "adb", return_value=module.subprocess.CompletedProcess([], 0, "")):
            self.assertFalse(self.pool.identity(row))

    def test_gradle_receives_only_reserved_serial_and_failure_is_returned(self):
        row = self.pool.claim("target")
        with patch.object(self.pool, "identity", return_value=True), \
             patch.object(module.subprocess, "Popen") as launch:
            launch.return_value.wait.return_value = 7
            launch.return_value.poll.return_value = 7
            launch.return_value.pid = 1234
            self.assertEqual(self.pool.operation(row["token"], "gradle", ["connectedDebugAndroidTest"]), 7)
            self.assertEqual(launch.call_args.kwargs["env"]["ANDROID_SERIAL"], self.pool.serial(row))
        self.assertFalse((self.pool.home / "operation-1.json").exists())

    def test_review_device_rejects_operations(self):
        row = self.pool.claim("review")
        self.pool.touch(row["token"], "review")
        with self.assertRaisesRegex(RuntimeError, "Resume"):
            self.pool.operation(row["token"], "adb", ["shell", "input", "tap", "1", "1"])

    def populate(self, row):
        avd, ini, log = self.pool.device_paths(row)
        avd.mkdir(parents=True)
        (avd / "userdata.img").write_bytes(b"device data")
        ini.write_text("registration")
        log.write_text("log")
        return avd, ini, log

    def test_release_deletes_only_owned_files(self):
        row = self.pool.claim("temporary")
        paths = self.populate(row)
        other = self.pool.claim("other review")
        other_paths = self.populate(other)
        self.pool.touch(other["token"], "review")
        self.pool.release(row["token"])
        self.assertTrue(all(not p.exists() for p in paths))
        self.assertTrue(all(p.exists() for p in other_paths))
        self.assertTrue((self.pool.home / "registry.sqlite").exists())
        self.assertTrue((self.pool.home / "slot-1.lock").exists())
        self.assertEqual("review", self.pool.get(other["token"])["state"])

    def test_failed_delete_keeps_slot_and_recovery_retries_immediately(self):
        row = self.pool.claim("locked files")
        paths = self.populate(row)
        with patch.object(module.shutil, "rmtree", side_effect=PermissionError("locked")):
            with self.assertRaises(PermissionError):
                self.pool.release(row["token"])
        current = self.pool.get(row["token"])
        self.assertEqual("cleanup_pending", current["state"])
        self.assertIn("locked", current["cleanup_error"])
        self.assertNotEqual(row["id"], self.pool.claim("next")["id"])
        self.pool.release(row["token"], recover=True)
        self.assertTrue(all(not p.exists() for p in paths))

    def test_transient_windows_shutdown_handle_is_retried(self):
        row = self.pool.claim("shutdown helper")
        paths = self.populate(row)
        actual_delete = module.shutil.rmtree
        sharing = OSError("SDK shutdown helper still holds log")
        sharing.winerror = 32
        attempts = []
        def delete(path):
            attempts.append(path)
            if len(attempts) == 1:
                raise sharing
            actual_delete(path)
        with patch.object(module.shutil, "rmtree", side_effect=delete), patch.object(module.time, "sleep"):
            self.pool.release(row["token"])
        self.assertEqual(2, len(attempts))
        self.assertTrue(all(not p.exists() for p in paths))

    def test_windows_sharing_retry_is_bounded(self):
        row = self.pool.claim("persistent lock")
        self.populate(row)
        sharing = OSError("still locked")
        sharing.winerror = 32
        with patch.object(module.shutil, "rmtree", side_effect=sharing), \
             patch.object(module.time, "monotonic", side_effect=[0, 31]):
            with self.assertRaises(OSError):
                self.pool.release(row["token"])
        self.assertEqual("cleanup_pending", self.pool.get(row["token"])["state"])

    def test_test_failure_and_cleanup_failure_are_both_reported(self):
        row = self.pool.claim("test failure")
        with patch.object(module, "Pool", return_value=self.pool), \
             patch.object(self.pool, "acquire", return_value=row), \
             patch.object(self.pool, "operation", return_value=7), \
             patch.object(self.pool, "delete_device", side_effect=PermissionError("locked")), \
             patch.object(module.sys, "argv", ["helper", "test", "--owner", "test failure"]):
            with self.assertRaisesRegex(RuntimeError, "Tests exited with code 7; cleanup also failed.*locked"):
                module.main()

    def test_cleanup_interruption_can_be_retried_after_reopening_pool(self):
        row = self.pool.claim("interrupted")
        paths = self.populate(row)
        original_delete = self.pool.delete_device
        def interrupted(current):
            module.shutil.rmtree(paths[0])
            raise KeyboardInterrupt()
        with patch.object(self.pool, "delete_device", side_effect=interrupted):
            with self.assertRaises(KeyboardInterrupt):
                self.pool.release(row["token"])
        with module.contextlib.closing(module.Pool(self.temp.name).db) as db:
            self.assertEqual("cleanup_pending", db.execute("SELECT state FROM slots WHERE id=1").fetchone()[0])
        self.pool.delete_device = original_delete
        self.pool.release(row["token"])
        self.assertTrue(all(not p.exists() for p in paths))

    def test_stale_release_cannot_delete_reassigned_device(self):
        old = self.pool.claim("old")
        self.pool.release(old["token"])
        new = self.pool.claim("new")
        paths = self.populate(new)
        with self.assertRaisesRegex(RuntimeError, "Invalid or released"):
            self.pool.release(old["token"])
        self.assertTrue(all(p.exists() for p in paths))

    def test_cleanup_pending_rejects_device_operations_and_state_changes(self):
        row = self.pool.claim("pending")
        self.pool.touch(row["token"], "cleanup_pending")
        for kind in ("adb", "gradle"):
            with self.assertRaisesRegex(RuntimeError, "Cleanup pending"):
                self.pool.operation(row["token"], kind, [])
        for state in ("active", "review"):
            with self.assertRaisesRegex(RuntimeError, "Cleanup pending"):
                self.pool.touch(row["token"], state)

    def test_failed_startup_releases_device_files(self):
        def fail(row):
            self.populate(row)
            raise RuntimeError("boot failed")
        with patch.object(self.pool, "boot", side_effect=fail):
            with self.assertRaisesRegex(RuntimeError, "boot failed"):
                self.pool.acquire("failed boot")
        self.assertEqual("free", self.pool.rows()[0]["state"])
        self.assertFalse(self.pool.device_paths(self.pool.rows()[0])[0].exists())

    def test_boot_and_cleanup_errors_are_both_reported(self):
        with patch.object(self.pool, "boot", side_effect=RuntimeError("boot failed")), \
             patch.object(self.pool, "delete_device", side_effect=PermissionError("locked")):
            with self.assertRaisesRegex(RuntimeError, "boot failed; cleanup also failed.*locked"):
                self.pool.acquire("failed boot")
        self.assertEqual("cleanup_pending", self.pool.rows()[0]["state"])

    def test_low_disk_space_does_not_allocate(self):
        with patch.object(module, "free_space", return_value=19 * 1024**3):
            with self.assertRaisesRegex(RuntimeError, "Low disk space"):
                self.pool.claim("too full")
        self.assertEqual([], self.pool.rows())

    def test_disk_check_occurs_inside_allocation_transaction(self):
        def space(path):
            self.assertTrue(self.pool.db.in_transaction)
            return 20 * 1024**3
        with patch.object(module, "free_space", side_effect=space):
            self.pool.claim("enough")

    def test_absent_status_does_not_create_storage(self):
        root = Path(self.temp.name) / "absent"
        pool = module.Pool(root, readonly=True)
        self.assertEqual([], pool.rows())
        summary = pool.summary()
        self.assertEqual(str(root.resolve()), summary["root"])
        self.assertEqual(0, summary["allocated_bytes"])
        self.assertFalse(root.exists())

    def test_status_does_not_modify_existing_registry(self):
        row = self.pool.claim("status")
        before = (self.pool.home / "registry.sqlite").read_bytes()
        peer = module.Pool(self.pool.home, readonly=True)
        try:
            self.assertEqual(row, peer.rows()[0])
            self.assertGreaterEqual(peer.summary()["allocated_bytes"], 0)
        finally:
            peer.db.close()
        self.assertEqual(before, (self.pool.home / "registry.sqlite").read_bytes())

    def test_delete_refuses_paths_outside_pool(self):
        row = self.pool.claim("unsafe")
        with tempfile.TemporaryDirectory() as outside:
            sentinel = Path(outside) / "keep.txt"
            sentinel.write_text("keep")
            with patch.object(self.pool, "device_paths", return_value=[sentinel]):
                with self.assertRaisesRegex(RuntimeError, "outside managed"):
                    self.pool.release(row["token"])
            self.assertEqual("keep", sentinel.read_text())

    def test_delete_refuses_linked_children_before_deleting_other_files(self):
        row = self.pool.claim("linked")
        avd, ini, _ = self.populate(row)
        with tempfile.TemporaryDirectory() as outside:
            sentinel = Path(outside) / "keep.txt"
            sentinel.write_text("keep")
            link = avd / "external"
            if os.name == "nt":
                # Junction creation needs no administrator privileges.
                module.subprocess.run(["powershell.exe", "-NoProfile", "-Command",
                                       "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $env:TIMEBOX_TEST_LINK -Target $env:TIMEBOX_TEST_TARGET | Out-Null"],
                                      env=dict(os.environ, TIMEBOX_TEST_LINK=str(link), TIMEBOX_TEST_TARGET=outside),
                                      check=True, capture_output=True)
            else:
                link.symlink_to(outside, target_is_directory=True)
            try:
                with self.assertRaisesRegex(RuntimeError, "outside managed|Unexpected link"):
                    self.pool.release(row["token"])
                self.assertTrue(ini.exists())
                self.assertEqual("keep", sentinel.read_text())
            finally:
                if os.name == "nt":
                    link.rmdir()
                else:
                    link.unlink()

    def test_offline_but_live_emulator_blocks_deletion(self):
        row = self.pool.claim("offline")
        paths = self.populate(row)
        with patch.object(self.pool, "owned_processes", return_value=[{"pid": 123}]), \
             patch.object(self.pool, "identity", return_value=False):
            with self.assertRaisesRegex(RuntimeError, "still running"):
                module.Pool.stop(self.pool, row)
        self.assertTrue(all(p.exists() for p in paths))

    def test_uncertain_launch_and_reused_pid_are_not_adopted(self):
        row = self.pool.claim("identity")
        self.pool.update(row["token"], launch=json.dumps({"phase": "starting", "processes": []}))
        with self.assertRaisesRegex(RuntimeError, "Uncertain emulator startup"):
            self.pool.owned_processes(self.pool.get(row["token"]))
        self.pool.update(row["token"], launch=json.dumps({"phase": "running", "processes": [{"pid": 42, "started": "old"}]}))
        process = {"pid": 42, "parent": 1, "started": "new", "command": "emulator -avd timebox-agent-01 -port 5580"}
        with patch.object(module, "emulator_processes", return_value=[process]):
            with self.assertRaisesRegex(RuntimeError, "Unrecorded"):
                self.pool.owned_processes(self.pool.get(row["token"]))

    def test_emulator_child_identity_survives_parent_exit(self):
        row = self.pool.claim("child")
        parent = {"pid": 42, "started": "parent", "parent": 1, "command": "emulator -avd timebox-agent-01 -port 5580"}
        child = {"pid": 43, "started": "child", "parent": 42, "command": "qemu -avd timebox-agent-01 -port 5580"}
        self.pool.update(row["token"], launch=json.dumps({"phase": "running", "processes": [{"pid": 42, "started": "parent"}]}))
        with patch.object(module, "emulator_processes", return_value=[parent, child]):
            self.assertEqual(2, len(self.pool.owned_processes(self.pool.get(row["token"]))))
        with patch.object(module, "emulator_processes", return_value=[child]):
            self.assertEqual([child], self.pool.owned_processes(self.pool.get(row["token"])))


if __name__ == "__main__":
    unittest.main()
