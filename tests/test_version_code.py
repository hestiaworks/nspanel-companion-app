"""Tests for deterministic SemVer to Android version-code conversion."""

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import sys
import unittest

PATH = Path(__file__).parents[1] / "tools/version-code.py"
SPEC = spec_from_file_location("nspanel_version_code", PATH)
module = module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class VersionCodeTest(unittest.TestCase):
    def test_orders_prereleases_before_stable_and_next_patch(self):
        versions = ["1.0.0-alpha.1", "1.0.0-beta.1", "1.0.0-rc.1", "1.0.0", "1.0.1-alpha.1"]
        codes = [module.version_code(version) for version in versions]
        self.assertEqual(codes, sorted(codes))
        self.assertEqual(len(codes), len(set(codes)))

    def test_accepts_v_prefix_and_rejects_invalid_versions(self):
        self.assertEqual(module.version_code("1.2.3"), module.version_code("v1.2.3"))
        with self.assertRaises(ValueError):
            module.version_code("1.2")
        with self.assertRaises(ValueError):
            module.version_code("1.2.3-beta.30")


if __name__ == "__main__":
    unittest.main()
