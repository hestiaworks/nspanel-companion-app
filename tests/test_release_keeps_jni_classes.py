"""The classes WebRTC's native library looks up by name must survive R8.

libjingle_peerconnection_so.so registers its JNI entry points by asking the
JVM for classes by their original names, through the jni_zero bridge. R8
cannot see those lookups. When it renamed them, the panel died on the first
outgoing call with a SIGTRAP inside the native library and no Java stack —
in release builds only, so no unit test and no debug run could show it.

This reads the built APK rather than the mapping file: what matters is what
shipped.
"""

import subprocess
import unittest
import zipfile
from pathlib import Path

APK = Path(__file__).parents[1] / "android/build/outputs/apk/release/android-release.apk"

# The one the native library names explicitly at load time, and the factory
# it calls straight afterwards.
REQUIRED = ["org/jni_zero/JniInit", "org/webrtc/PeerConnectionFactory"]


class ReleaseApkTest(unittest.TestCase):
    def setUp(self):
        if not APK.exists():
            self.skipTest("no release APK built; run :android:assembleRelease")

    def test_the_jni_bridge_classes_are_not_renamed(self):
        with zipfile.ZipFile(APK) as apk:
            dex = [n for n in apk.namelist() if n.endswith(".dex")]
            self.assertTrue(dex, "release APK has no dex")
            blob = b"".join(apk.read(name) for name in dex)
        for cls in REQUIRED:
            with self.subTest(cls=cls):
                self.assertIn(cls.encode(), blob, f"{cls} was stripped or renamed by R8")


if __name__ == "__main__":
    unittest.main()
