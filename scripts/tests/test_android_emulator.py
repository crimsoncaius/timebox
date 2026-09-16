import concurrent.futures
import importlib.util
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
        self.pool.stop = lambda row: self.assertEqual(self.pool.get(row["token"])["state"], "active")
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


if __name__ == "__main__":
    unittest.main()
