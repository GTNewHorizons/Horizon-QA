"""Protocol rejection tests, included in ./gradlew check via hqaToolsTest."""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

from hqa_tools import verify as verifier


class ReportFixture(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.result = {
            "schemaVersion": 3, "status": "passed", "exitCode": 0,
            "configuration": {"mode": "ci", "tests": "mod:Holder.test"},
            "reports": {"junit": str(self.directory / "TEST-horizonqa.xml"),
                "status": str(self.directory / "horizonqa-result.json")},
            "issues": [],
            "tests": [{"id": "mod:Holder.test", "classname": "mod:Holder", "name": "test",
                "status": "passed", "required": True, "ticks": 1, "timeSeconds": 0.05,
                "output": ["holderClass=com.expected.Holder"]}],
            "counts": {"selectedTests": 1, "passed": 1, "failed": 0, "timedOut": 0,
                "skipped": 0, "incomplete": 0, "requiredFailures": 0, "optionalFailures": 0,
                "issues": 0, "diagnosticErrors": 0, "junitFailures": 0, "junitErrors": 0, "junitSkipped": 0}}

    def write(self, result=None, outcome=None):
        result = result or self.result
        (self.directory / "horizonqa-result.json").write_text(json.dumps(result))
        counts = result["counts"]
        root = ET.Element("testsuite", tests=str(len(result["tests"])),
            failures=str(counts["junitFailures"]), errors=str(counts["junitErrors"]), skipped=str(counts["junitSkipped"]))
        for test in result["tests"]:
            attributes = {key: test[key] for key in ("classname", "name") if key in test}
            case = ET.SubElement(root, "testcase", attributes)
            if outcome:
                ET.SubElement(case, outcome)
        ET.ElementTree(root).write(self.directory / "TEST-horizonqa.xml")


class ReportVerificationTest(ReportFixture):
    def test_success_and_optional_failure_keep_exit_zero(self):
        self.write()
        self.assertEqual(0, verifier.verify(self.directory, "mod:Holder.test")["exitCode"])
        self.result["tests"][0].update(status="failed", required=False)
        self.result["counts"].update(passed=0, failed=1, optionalFailures=1, junitSkipped=1)
        self.write(outcome="skipped")
        self.assertEqual(0, verifier.verify(self.directory, "mod:Holder.test")["exitCode"])

    def test_required_timeout_is_exit_one(self):
        self.result.update(status="failed", exitCode=1)
        self.result["tests"][0]["status"] = "timedOut"
        self.result["counts"].update(passed=0, timedOut=1, requiredFailures=1, junitFailures=1)
        self.write(outcome="failure")
        self.assertEqual(1, verifier.verify(self.directory, "mod:Holder.test")["exitCode"])

    def test_missing_stale_malformed_and_incomplete_evidence_is_rejected(self):
        with self.assertRaises(ValueError):
            verifier.verify(self.directory, "mod:Holder.test")
        self.write()
        os.utime(self.directory / "TEST-horizonqa.xml", (1, 1))
        with self.assertRaisesRegex(ValueError, "stale"):
            verifier.verify(self.directory, "mod:Holder.test", 2)
        self.write()
        (self.directory / "horizonqa-result.json").write_text("{")
        with self.assertRaises(ValueError):
            verifier.verify(self.directory, "mod:Holder.test")
        self.result["tests"][0]["status"] = "running"
        self.write()
        with self.assertRaises(ValueError):
            verifier.verify(self.directory, "mod:Holder.test")

    def test_empty_pass_and_missing_case_metadata_are_rejected(self):
        empty = copy.deepcopy(self.result)
        empty["tests"] = []
        empty["counts"].update(passed=0, selectedTests=0)
        self.write(empty)
        with self.assertRaisesRegex(ValueError, "empty"):
            verifier.verify(self.directory, "mod:Holder.test")
        for field in ("classname", "name", "ticks", "timeSeconds"):
            with self.subTest(field=field):
                malformed = copy.deepcopy(self.result)
                del malformed["tests"][0][field]
                self.write(malformed)
                with self.assertRaises(ValueError):
                    verifier.verify(self.directory, "mod:Holder.test")

    def test_null_objects_and_huge_durations_are_cli_infrastructure_failures(self):
        for field in ("configuration", "reports", "timeSeconds"):
            with self.subTest(field=field):
                malformed = copy.deepcopy(self.result)
                if field == "timeSeconds":
                    malformed["tests"][0][field] = 10 ** 309
                else:
                    malformed[field] = None
                self.write(malformed)
                completed = subprocess.run([sys.executable, "-m", "hqa_tools", "verify", str(self.directory)], capture_output=True)
                self.assertEqual(2, completed.returncode, completed.stderr)

    def test_selector_identity_and_trimmed_multiple_selectors(self):
        self.result["configuration"]["tests"] = "com.expected.Holder"
        self.write()
        self.assertEqual(0, verifier.verify(self.directory, "com.expected.Holder")["exitCode"])
        self.result["tests"][0]["output"] = ["holderClass=com.other.Holder"]
        self.write()
        with self.assertRaisesRegex(ValueError, "unexecuted selector"):
            verifier.verify(self.directory, "com.expected.Holder")
        self.result["configuration"]["tests"] = "mod:Holder.test, Holder"
        self.write()
        self.assertEqual(0, verifier.verify(self.directory, "mod:Holder.test, Holder")["exitCode"])

    def test_junit_must_match_case_outcome(self):
        self.write(outcome="skipped")
        with self.assertRaisesRegex(ValueError, "differ"):
            verifier.verify(self.directory, "mod:Holder.test")


if __name__ == "__main__":
    unittest.main()
