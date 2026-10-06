"""Which way up the panel draws.

The NSPanel Pro's display is mounted upright: plain portrait is the right way
up. The first validation recorded reversePortrait as "required", which was a
misreading, and every launch of the app since then has turned the screen upside
down until it was corrected by hand in Android's display settings — until an
update restarted the app and undid it again.
"""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

MANIFEST = Path(__file__).parents[1] / "android/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class OrientationTest(unittest.TestCase):
    def test_every_screen_is_drawn_upright(self):
        activities = ET.parse(MANIFEST).getroot().iter("activity")
        orientations = {
            a.get(f"{ANDROID}name"): a.get(f"{ANDROID}screenOrientation")
            for a in activities
        }
        self.assertTrue(orientations)
        for name, orientation in orientations.items():
            with self.subTest(activity=name):
                self.assertEqual("portrait", orientation)


if __name__ == "__main__":
    unittest.main()
