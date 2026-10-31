"""Run with python -m unittest discover -s scripts -p 'test_*.py'. Nothing here builds jars or calls an API."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import publish_curseforge
import release_manifest

SCRIPTS = Path(__file__).resolve().parent
HEAD = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=SCRIPTS, text=True).strip()
OTHER = '976d03a95d665cabebd165a9a88e3477738a2c91' if HEAD != '976d03a95d665cabebd165a9a88e3477738a2c91' else '0' * 40
# Any request that slips past a guard fails at this closed port instead of reaching CurseForge.
OFFLINE = dict(os.environ, https_proxy='http://127.0.0.1:9', http_proxy='http://127.0.0.1:9')


def release_folder(root, commit):
    folder = Path(root) / 'release'
    folder.mkdir()
    manifest = {'mod_version': '1.3.0', 'commit': commit, 'license': 'MIT', 'files': [
        {'filename': 'familytree-1.3.0+26.3.jar', 'version': '1.3.0+26.3', 'minecraft': '26.3', 'loader': 'fabric',
         'release_type': 'release', 'sha512': '0' * 128}]}
    (folder / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (folder / 'familytree-1.3.0+26.3.jar').write_bytes(b'published jar')
    return folder


def snapshot(folder):
    return {path.name: path.read_bytes() for path in sorted(folder.iterdir())}


class ReleaseManifestGuard(unittest.TestCase):
    def test_a_new_folder_is_allowed(self):
        with tempfile.TemporaryDirectory() as root:
            release_manifest.refuse_other_release(Path(root) / 'missing', HEAD)

    def test_the_same_commit_may_regenerate_its_folder(self):
        with tempfile.TemporaryDirectory() as root:
            release_manifest.refuse_other_release(release_folder(root, HEAD), HEAD)

    def test_another_commit_is_refused(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(SystemExit) as refused:
                release_manifest.refuse_other_release(release_folder(root, OTHER), HEAD)
            self.assertIn(OTHER, str(refused.exception))
            self.assertIn(HEAD, str(refused.exception))

    def test_the_script_leaves_the_folder_unchanged_when_it_refuses(self):
        with tempfile.TemporaryDirectory() as root:
            folder = release_folder(root, OTHER)
            before = snapshot(folder)
            run = subprocess.run([sys.executable, str(SCRIPTS / 'release_manifest.py'), '--output', str(folder)],
                                 capture_output=True, text=True, env=OFFLINE)
            self.assertNotEqual(run.returncode, 0)
            self.assertIn('already holds the 1.3.0 release', run.stderr)
            self.assertEqual(snapshot(folder), before)


class CurseForgeFirstUploadGuard(unittest.TestCase):
    def test_a_missing_record_needs_the_flag(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(SystemExit) as refused:
                publish_curseforge.published_files(Path(root) / 'curseforge-published.json', False)
            self.assertIn('--first-upload', str(refused.exception))

    def test_the_flag_starts_an_empty_record(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertEqual(publish_curseforge.published_files(Path(root) / 'curseforge-published.json', True), [])

    def test_an_existing_record_is_read(self):
        with tempfile.TemporaryDirectory() as root:
            record = Path(root) / 'curseforge-published.json'
            record.write_text(json.dumps([{'version': '1.3.0+26.3', 'id': 1, 'sha512': '0' * 128}]))
            self.assertEqual([item['version'] for item in publish_curseforge.published_files(record, False)],
                             ['1.3.0+26.3'])

    def test_the_flag_is_refused_when_a_record_exists(self):
        with tempfile.TemporaryDirectory() as root:
            record = Path(root) / 'curseforge-published.json'
            record.write_text('[]')
            with self.assertRaises(SystemExit):
                publish_curseforge.published_files(record, True)

    def test_the_script_stops_before_reading_the_token_or_uploading(self):
        with tempfile.TemporaryDirectory() as root:
            folder = release_folder(root, OTHER)
            before = snapshot(folder)
            run = subprocess.run([sys.executable, str(SCRIPTS / 'publish_curseforge.py'),
                                  '--manifest', str(folder / 'manifest.json')],
                                 capture_output=True, text=True, env=OFFLINE)
            self.assertNotEqual(run.returncode, 0)
            self.assertIn('does not exist', run.stderr)
            self.assertNotIn('Traceback', run.stderr)
            self.assertEqual(snapshot(folder), before)


if __name__ == '__main__':
    unittest.main()
