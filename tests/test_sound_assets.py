"""The sounds the panel plays and the sounds the editor previews.

The same three files live in two repositories: in the app, because the panel
can only play what was built into it, and in the integration, because the
editor's preview button is played by a browser talking to Home Assistant.
Nothing can force them to hold the same bytes, so each side pins the digests
and asserts them — a preview that no longer matches what the panel plays is
worse than no preview, because it is confidently wrong.

Update both repositories together when a sound changes.
"""

import hashlib
import unittest
from pathlib import Path

RAW = Path(__file__).parents[1] / "android/src/main/res/raw"

SOUNDS = {
    "chime_1.mp3": "bc2d599a1cab6611d9284ee4bc5a7181897aea3e25b6d2e60758c7a82b76d14e",
    "chime_2.mp3": "44a15db0bfe89657fc9a77b0e581befe2b8db973390c5220a347e362e69cc4cb",
    "chime_3.mp3": "58636839c8198c51ca89e8c3f8f205dc7f53c6eabee7a5b6aaee33fa3683b02c",
}


class SoundAssetTest(unittest.TestCase):
    def test_the_panel_carries_exactly_the_sounds_the_editor_offers(self):
        self.assertEqual(sorted(SOUNDS), sorted(p.name for p in RAW.glob("*.mp3")))

    def test_each_sound_is_the_file_the_editor_previews(self):
        for name, digest in SOUNDS.items():
            with self.subTest(sound=name):
                self.assertEqual(digest, hashlib.sha256((RAW / name).read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
