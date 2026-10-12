"""Run the shipped tool from a temporary directory without repository imports."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class ToolTestCase(unittest.TestCase):
    def setUp(self):
        archive = os.environ.get("HQA_TOOLS_ARCHIVE")
        if not archive:
            self.fail("Run ./gradlew hqaToolsTest to exercise the built archive")
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.archive = self.directory / "hqa.pyz"
        shutil.copyfile(archive, self.archive)

    def call(self, *arguments):
        return subprocess.run([sys.executable, str(self.archive), *map(str, arguments)],
            cwd=self.directory, env=dict(os.environ, PYTHONPATH=""),
            capture_output=True, text=True, timeout=20)
