"""Exercise the distributed verifier outside the source checkout"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


class ToolsArchiveTest(unittest.TestCase):
    def test_archive_runs_without_adjacent_source_and_contains_only_runtime_resources(self):
        archive = os.environ.get("HQA_TOOLS_ARCHIVE")
        self.assertTrue(archive, "Run hqaToolsTest to provide the built tools archive")
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with zipfile.ZipFile(archive) as package:
                files = {item.filename for item in package.infolist() if not item.is_dir()}
                self.assertEqual({"LICENSE", "tools/README.md", "tools/verify-client-reports.py",
                                  "scripts/client-tests/build.gradle", "scripts/client-tests/settings.gradle",
                                  "scripts/client-test-isolation.init.gradle"}, files)
                package.extractall(directory)
            report = directory / "report"
            report.mkdir()
            counts = dict.fromkeys(("selectedTests", "passed", "failed", "timedOut", "skipped", "incomplete",
                                    "requiredFailures", "optionalFailures", "issues", "diagnosticErrors",
                                    "junitFailures", "junitErrors", "junitSkipped"), 0)
            result = {"schemaVersion": 5, "status": "passed", "exitCode": 0, "tests": [], "issues": [],
                      "counts": counts,
                      "configuration": {"mode": "ci", "tests": None, "allowNoTests": True, "selectsAllTests": True},
                      "reports": {"junit": str(report / "TEST-horizonqa.xml"),
                                  "status": str(report / "horizonqa-result.json")}}
            (report / "horizonqa-result.json").write_text(json.dumps(result), encoding="utf-8")
            (report / "TEST-horizonqa.xml").write_text(
                '<testsuite tests="0" failures="0" errors="0" skipped="0"/>', encoding="utf-8")
            completed = subprocess.run([sys.executable, str(directory / "tools/verify-client-reports.py"), str(report)],
                                       cwd=directory, env=dict(os.environ, PYTHONPATH=""),
                                       text=True, capture_output=True, timeout=15)
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertIn("passed exit=0", completed.stdout)
