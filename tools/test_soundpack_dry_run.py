"""CLI preflight must report mappings without creating a pack."""

import argparse
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import soundpack_maker as maker


class DryRunTest(unittest.TestCase):
    def test_preview_reports_keys_and_weights_without_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source.mkdir()
            (source / "pass_1.ogg").write_bytes(b"one")
            (source / "pass_2_w3.ogg").write_bytes(b"two")
            (source / "bgm_playing.ogg").write_bytes(b"music")
            (source / "unknown.ogg").write_bytes(b"unknown")
            output = root / "output"
            args = argparse.Namespace(source=str(source), pack_id=None, name="Preview",
                                      author="Tester", desc="", out=str(output), dry_run=True)
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                code = maker.run_cli(args)
            report = buffer.getvalue()
            self.assertEqual(0, code)
            self.assertIn("pass_1.ogg", report)
            self.assertIn("pass_2_w3.ogg", report)
            self.assertIn("权重 3", report)
            self.assertIn("bgm_playing", report)
            self.assertIn("unknown.ogg", report)
            self.assertFalse(output.exists())

    def test_same_cli_without_preview_creates_pack(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source.mkdir()
            (source / "pass_1.ogg").write_bytes(b"one")
            (source / "pass_2_w3.ogg").write_bytes(b"two")
            output = root / "output"
            args = argparse.Namespace(source=str(source), pack_id=None, name="Playable",
                                      author="Tester", desc="", out=str(output), dry_run=False)
            with contextlib.redirect_stdout(io.StringIO()):
                code = maker.run_cli(args)
            self.assertEqual(0, code)
            packs = list(output.iterdir())
            self.assertEqual(1, len(packs))
            manifest = json.loads((packs[0] / "pack.json").read_text(encoding="utf-8"))
            self.assertEqual("Playable", manifest["name"])
            self.assertEqual(["pass_1.ogg", {"file": "pass_2_w3.ogg", "weight": 3}],
                             manifest["sounds"]["pass"])


if __name__ == "__main__":
    unittest.main()
