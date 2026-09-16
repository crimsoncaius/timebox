import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('emulator_pool', Path(__file__).parents[1] / 'android-emulator.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class AdditionalDeviceTest(unittest.TestCase):
    def test_additional_device_preserves_reservations(self):
        with tempfile.TemporaryDirectory() as directory:
            pool = module.Pool(directory)
            try:
                existing = [pool.claim(f'owner-{i}') for i in range(pool.config['capacity'])]
                grown = pool.claim('ordinary')
                self.assertEqual(grown['id'], pool.config['capacity'] + 1)
                extra = pool.claim('additional review', extra=True)
                self.assertEqual(extra['id'], grown['id'] + 1)
                self.assertEqual([pool.get(row['token']) for row in existing], existing)
                second = module.Pool(directory)
                try:
                    self.assertEqual(second.get(extra['token']), extra)
                    another = second.claim('another extra request', extra=True)
                    self.assertNotEqual(another['id'], extra['id'])
                finally:
                    second.db.close()
            finally:
                pool.db.close()


if __name__ == '__main__':
    unittest.main()
