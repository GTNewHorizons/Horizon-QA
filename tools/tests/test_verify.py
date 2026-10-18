"""Protocol rejection tests, included in ./gradlew check via hqaToolsTest."""
import copy
import json
import os
import unittest
import xml.etree.ElementTree as ET

from tool_test_case import ToolTestCase


class ReportVerificationTest(ToolTestCase):
    def setUp(self):
        super().setUp()
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

    def write_report(self, result=None, outcome=None):
        result = self.result if result is None else result
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

    def verify(self, selectors="mod:Holder.test", earliest=0):
        return self.call("verify", self.directory, "--tests", selectors, "--earliest", earliest)

    def assert_rejected(self, message, **arguments):
        completed = self.verify(**arguments)
        self.assertEqual(2, completed.returncode, completed.stderr)
        self.assertIn(message, completed.stderr)

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
        for status, required, exit_code, run_status, junit, counts in outcomes:
            with self.subTest(status=status, required=required):
                result = copy.deepcopy(self.result)
                result.update(status=run_status, exitCode=exit_code)
                result["tests"][0].update(status=status, required=required)
                result["counts"].update({"passed": 0, **counts})
                self.write_report(result, junit)
                completed = self.verify()
                self.assertEqual(exit_code, completed.returncode, completed.stderr)
                self.assertEqual("", completed.stderr)
                self.assertIn(f"{run_status} exit={exit_code}", completed.stdout)

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

    def test_empty_pass_and_missing_case_metadata_are_rejected(self):
        empty = copy.deepcopy(self.result)
        empty["tests"] = []
        empty["counts"].update(passed=0, selectedTests=0)
        self.write_report(empty)
        self.assert_rejected("empty")
        for field in ("classname", "name", "ticks", "timeSeconds"):
            with self.subTest(field=field):
                malformed = copy.deepcopy(self.result)
                del malformed["tests"][0][field]
                self.write_report(malformed)
                self.assert_rejected("missing/invalid case metadata")

    def test_null_objects_and_huge_durations_are_cli_infrastructure_failures(self):
        for field in ("configuration", "reports", "timeSeconds"):
            with self.subTest(field=field):
                malformed = copy.deepcopy(self.result)
                if field == "timeSeconds":
                    malformed["tests"][0][field] = 10 ** 309
                else:
                    malformed[field] = None
                self.write_report(malformed)
                self.assert_rejected("evidence rejected")

    def test_selector_identity_and_trimmed_multiple_selectors(self):
        selectors = (
            ("com.expected.Holder", "com.expected.Holder", None),
            ("com.expected.Holder", "com.other.Holder", "unexecuted selector"),
            ("mod:Holder.test, Holder", "com.expected.Holder", None),
        )
        for selector, holder, rejection in selectors:
            with self.subTest(selector=selector, holder=holder):
                result = copy.deepcopy(self.result)
                result["configuration"]["tests"] = selector
                result["tests"][0]["output"] = ["holderClass=" + holder]
                self.write_report(result)
                if rejection:
                    self.assert_rejected(rejection, selectors=selector)
                else:
                    completed = self.verify(selector)
                    self.assertEqual(0, completed.returncode, completed.stderr)

    def test_junit_must_match_case_outcome(self):
        self.write_report(outcome="skipped")
        self.assert_rejected("differ")

    def test_missing_mod_family_skip_matches_selected_row_without_accepting_other_cases(self):
        selector = "mod:Holder.test[fast]"
        self.result["configuration"]["tests"] = selector
        self.result["tests"][0].update(status="skipped", skipType="MISSING_REQUIRED_MOD")
        self.result["tests"][0]["output"].append("unresolvedCaseFamily=true")
        self.result["counts"].update(passed=0, skipped=1, junitSkipped=1)
        self.write_report(outcome="skipped")
        completed = self.verify(selector)
        self.assertEqual(0, completed.returncode, completed.stderr)

        for changes in (
            {"output": ["holderClass=com.expected.Holder"]},
            {"skipType": "ASSUMPTION_FAILED"},
            {"id": "mod:Holder.test[slow]", "name": "test[slow]"},
            {"id": "mod:Holder.testing", "name": "testing"},
        ):
            with self.subTest(changes=changes):
                result = copy.deepcopy(self.result)
                result["tests"][0].update(changes)
                self.write_report(result, "skipped")
                self.assert_rejected("unexecuted selector", selectors=selector)


if __name__ == "__main__":
    unittest.main()
