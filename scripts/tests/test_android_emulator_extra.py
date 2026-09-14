import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("android_emulator", Path(__file__).parents[1] / "android-emulator.py")
emulator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(emulator)


class ExtraReservationTest(unittest.TestCase):
    def test_explicit_extra_preserves_existing_reservations_and_default_limit(self):
        with tempfile.TemporaryDirectory() as home:
            pool = emulator.Pool(home)
            try:
                existing = [pool.claim(f"owner-{n}") for n in range(pool.config["capacity"])]
                pool.touch(existing[0]["token"], "review")
                before = pool.rows()
                extra = pool.claim("user-requested extra", extra=True)
                self.assertEqual(pool.config["capacity"] + 1, extra["id"])
                self.assertEqual(before, pool.rows()[:len(before)])
                with self.assertRaisesRegex(RuntimeError, "Pool full"):
                    pool.claim("ordinary request")
                pool.touch(extra["token"], "free")
                replacement = pool.claim("next explicit extra", extra=True)
                self.assertEqual(extra["id"], replacement["id"])
                self.assertNotEqual(extra["token"], replacement["token"])
                with self.assertRaisesRegex(RuntimeError, "Invalid or released"):
                    pool.get(extra["token"])
                # Other worktrees can continue using the unchanged configuration.
                peer = emulator.Pool(home)
                try:
                    self.assertEqual("review", peer.get(existing[0]["token"])["state"])
                finally:
                    peer.db.close()
            finally:
                pool.db.close()


if __name__ == "__main__":
    unittest.main()
