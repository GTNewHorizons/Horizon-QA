"""Verify the external report contract through the standalone command"""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
VERIFY = ROOT / "tools/verify-client-reports.py"


class ReportVerificationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.result = {
            "schemaVersion": 5, "status": "passed", "exitCode": 0,
            "configuration": {"mode": "ci", "tests": "mod:Holder.test", "allowNoTests": False},
            "reports": {"junit": str(self.directory / "TEST-horizonqa.xml"),
                        "status": str(self.directory / "horizonqa-result.json")},
            "issues": [],
            "tests": [{"id": "mod:Holder.test", "classname": "mod:Holder", "name": "test",
                       "status": "passed", "required": True, "ticks": 1, "timeSeconds": 0.05,
                       "output": ["holderClass=com.expected.Holder"]}],
            "counts": {"selectedTests": 1, "passed": 1, "failed": 0, "timedOut": 0,
                       "skipped": 0, "incomplete": 0, "requiredFailures": 0, "optionalFailures": 0,
                       "issues": 0, "diagnosticErrors": 0, "junitFailures": 0,
                       "junitErrors": 0, "junitSkipped": 0}}

    def write_report(self, result=None, outcome=None):
        result = self.result if result is None else result
        (self.directory / "horizonqa-result.json").write_text(json.dumps(result), encoding="utf-8")
        counts = result["counts"]
        root = ET.Element("testsuite", tests=str(len(result["tests"]) + len(result["issues"])),
                          failures=str(counts["junitFailures"]), errors=str(counts["junitErrors"]),
                          skipped=str(counts["junitSkipped"]))
        for test in result["tests"]:
            case = ET.SubElement(root, "testcase", {key: test[key] for key in ("classname", "name") if key in test})
            if outcome:
                ET.SubElement(case, outcome)
        for issue in result["issues"]:
            case = ET.SubElement(root, "testcase", classname=issue["source"], name=issue["name"])
            ET.SubElement(case, "error")
        ET.ElementTree(root).write(self.directory / "TEST-horizonqa.xml")

    def verify(self, selectors="mod:Holder.test", earliest=0):
        return subprocess.run([sys.executable, str(VERIFY), str(self.directory), "--tests", selectors,
                               "--earliest", str(earliest)], capture_output=True, text=True, timeout=10)

    def assert_rejected(self, message, **arguments):
        completed = self.verify(**arguments)
        self.assertEqual(2, completed.returncode, completed.stderr)
        self.assertIn(message, completed.stderr)

    def test_current_schema_five_report_is_accepted(self):
        self.write_report()
        completed = self.verify()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("passed exit=0", completed.stdout)

    def test_empty_success_requires_explicit_allow_no_tests(self):
        self.result["tests"] = []
        self.result["counts"].update(passed=0, selectedTests=0)
        self.result["configuration"].update(tests=None, selectsAllTests=True)
        self.write_report()
        self.assert_rejected("empty", selectors="")
        self.result["configuration"]["allowNoTests"] = True
        self.write_report()
        completed = self.verify(selectors="")
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_outcomes_preserve_exit_policy(self):
        outcomes = (
            ("passed", True, 0, "passed", None, {"passed": 1}),
            ("failed", False, 0, "passed", "skipped", {"failed": 1, "optionalFailures": 1, "junitSkipped": 1}),
            ("failed", True, 1, "failed", "failure", {"failed": 1, "requiredFailures": 1, "junitFailures": 1}),
            ("timedOut", True, 1, "failed", "failure", {"timedOut": 1, "requiredFailures": 1, "junitFailures": 1}),
            ("error", False, 2, "error", "error", {"junitErrors": 1}),
            ("running", True, 2, "error", "error", {"incomplete": 1, "junitErrors": 1}),
            ("notStarted", True, 2, "error", "skipped", {"incomplete": 1, "junitSkipped": 1}),
        )
        for status, required, code, run_status, junit, counts in outcomes:
            with self.subTest(status=status, required=required):
                result = copy.deepcopy(self.result)
                result.update(status=run_status, exitCode=code)
                result["tests"][0].update(status=status, required=required)
                result["counts"].update({"passed": 0, **counts})
                self.write_report(result, junit)
                completed = self.verify()
                self.assertEqual(code, completed.returncode, completed.stderr)
                self.assertEqual("", completed.stderr)

    def test_missing_stale_malformed_and_incomplete_evidence_is_rejected(self):
        for filename in ("horizonqa-result.json", "TEST-horizonqa.xml"):
            with self.subTest(filename=filename):
                self.write_report()
                (self.directory / filename).unlink()
                self.assert_rejected("missing/unsafe report")
                self.write_report()
                os.utime(self.directory / filename, (1, 1))
                self.assert_rejected("stale", earliest=2)
                self.write_report()
                (self.directory / filename).write_text("{")
                self.assert_rejected("evidence rejected")
        self.result["tests"][0]["status"] = "running"
        self.write_report()
        self.assert_rejected("inconsistent status counts")

    def test_counts_identity_paths_and_junit_outcomes_must_agree(self):
        mutations = (
            lambda result: result["counts"].update(passed=0),
            lambda result: result["tests"].append(copy.deepcopy(result["tests"][0])),
            lambda result: result["reports"].update(junit=str(self.directory / "other.xml")),
            lambda result: result.update(exitCode=1, status="failed"),
            lambda result: result.update(schemaVersion=3),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                result = copy.deepcopy(self.result)
                mutation(result)
                self.write_report(result)
                self.assert_rejected("evidence rejected")
        self.write_report(outcome="skipped")
        self.assert_rejected("differ")

    def test_missing_or_invalid_metadata_is_infrastructure_failure(self):
        for field in ("classname", "name", "ticks", "timeSeconds"):
            with self.subTest(field=field):
                result = copy.deepcopy(self.result)
                del result["tests"][0][field]
                self.write_report(result)
                self.assert_rejected("missing/invalid case metadata")
        for field in ("configuration", "reports", "timeSeconds"):
            with self.subTest(field=field):
                result = copy.deepcopy(self.result)
                if field == "timeSeconds":
                    result["tests"][0][field] = 10 ** 309
                else:
                    result[field] = None
                self.write_report(result)
                self.assert_rejected("evidence rejected")

    def test_selector_prefix_namespace_and_holder_identity(self):
        for selector in ("mod", "mod:Holder", "mod:Holder.te", "Holder", "com.expected.Holder",
                         "mod:Holder.test, Holder"):
            with self.subTest(selector=selector):
                self.result["configuration"]["tests"] = selector
                self.write_report()
                completed = self.verify(selector)
                self.assertEqual(0, completed.returncode, completed.stderr)
        self.result["configuration"]["tests"] = "com.other.Holder"
        self.write_report()
        self.assert_rejected("unexecuted selector", selectors="com.other.Holder")

    def test_missing_mod_family_skip_can_satisfy_selected_parameter_row(self):
        selector = "mod:Holder.test[fast]"
        self.result["configuration"]["tests"] = selector
        self.result["tests"][0].update(status="skipped", skipType="MISSING_REQUIRED_MOD")
        self.result["tests"][0]["output"].append("unresolvedCaseFamily=true")
        self.result["counts"].update(passed=0, skipped=1, junitSkipped=1)
        self.write_report(outcome="skipped")
        self.assertEqual(0, self.verify(selector).returncode)
        for changes in ({"output": ["holderClass=com.expected.Holder"]}, {"skipType": "ASSUMPTION_FAILED"},
                        {"id": "mod:Holder.test[slow]", "name": "test[slow]"}):
            with self.subTest(changes=changes):
                result = copy.deepcopy(self.result)
                result["tests"][0].update(changes)
                self.write_report(result, "skipped")
                self.assert_rejected("unexecuted selector", selectors=selector)

    def test_prerun_infrastructure_report_need_not_have_executed_requested_selector(self):
        self.result.update(exitCode=2, status="error", tests=[])
        self.result["issues"] = [{"id": "client:startup", "kind": "CLIENT_STARTUP", "source": "horizonqa.client",
                                  "name": "startup", "message": "World did not start", "fatalInCi": True}]
        self.result["counts"].update(selectedTests=0, passed=0, issues=1, diagnosticErrors=1, junitErrors=1)
        self.write_report()
        completed = self.verify()
        self.assertEqual(2, completed.returncode, completed.stderr)
        self.assertEqual("", completed.stderr)
        self.assertIn("error exit=2", completed.stdout)


if __name__ == "__main__":
    unittest.main()
