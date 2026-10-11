"""Exercise the shipped command outside a source checkout, including its bundled Gradle adapter."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from test_verify import ReportFixture


class PackagedToolTest(ReportFixture):
    def setUp(self):
        super().setUp()
        archive = os.environ.get("HQA_TOOLS_ARCHIVE")
        if not archive:
            self.skipTest("Run ./gradlew hqaToolsTest to exercise the built archive")
        self.archive = self.directory / "hqa.pyz"
        shutil.copyfile(archive, self.archive)

    def call(self, *arguments):
        environment = dict(os.environ, PYTHONPATH="")
        return subprocess.run([sys.executable, str(self.archive), *map(str, arguments)],
            cwd=self.directory, env=environment, capture_output=True, text=True, timeout=20)

    def test_help_and_version_work_without_adjacent_source(self):
        result = self.call("--version")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(result.stdout.startswith("Horizon-QA tools "))
        self.assertNotIn("development", result.stdout)
        for command in ((), ("run-client",), ("verify",)):
            with self.subTest(command=command):
                result = self.call(*command, "--help")
                self.assertEqual(0, result.returncode, result.stderr)

    def test_archive_preserves_success_optional_required_and_invalid_report_exits(self):
        self.write()
        self.assertEqual(0, self.call("verify", self.directory, "--tests", "mod:Holder.test").returncode)
        self.result["tests"][0].update(status="failed", required=False)
        self.result["counts"].update(passed=0, failed=1, optionalFailures=1, junitSkipped=1)
        self.write(outcome="skipped")
        self.assertEqual(0, self.call("verify", self.directory, "--tests", "mod:Holder.test").returncode)
        self.result.update(status="failed", exitCode=1)
        self.result["tests"][0]["required"] = True
        self.result["counts"].update(optionalFailures=0, junitSkipped=0, requiredFailures=1, junitFailures=1)
        self.write(outcome="failure")
        self.assertEqual(1, self.call("verify", self.directory, "--tests", "mod:Holder.test").returncode)
        self.write(outcome="skipped")
        self.assertEqual(2, self.call("verify", self.directory, "--tests", "mod:Holder.test").returncode)
        (self.directory / "horizonqa-result.json").unlink()
        self.assertEqual(2, self.call("verify", self.directory).returncode)

    def test_gradle_resource_and_jvm_flags_need_no_copied_helpers(self):
        if sys.platform != "linux":
            self.skipTest("Client process supervision targets Linux CI")
        display = self.directory / "display"
        display.write_text(f"#!{sys.executable}\nimport time\nprint('199', flush=True)\ntime.sleep(30)\n")
        display.chmod(0o700)
        focus = self.directory / "focus"
        focus.write_text(f"#!{sys.executable}\n")
        focus.chmod(0o700)
        gradle = self.directory / "gradlew"
        gradle.write_text(f"#!{sys.executable}\nimport json, pathlib, sys\n"
            "pathlib.Path('captured-argv.json').write_text(json.dumps(sys.argv[1:]))\n")
        gradle.chmod(0o700)
        report = self.directory / "reports"
        result = self.call("run-client", "--cwd", self.directory, "--gradle-task", ":mod:runClient25",
            "--tests", "mod:Holder.test", "--report-dir", report, "--timeout", "10",
            "--xvfb", display, "--xdotool", focus)
        # This probe establishes command/resource delivery, not Minecraft behavior. Missing game reports fail.
        self.assertEqual(2, result.returncode, result.stderr)
        command = json.loads((self.directory / "captured-argv.json").read_text())
        self.assertEqual(":mod:runClient25", command[0])
        self.assertIn("--mcJvmArgs=-Dhorizonqa.tests=mod:Holder.test", command)
        self.assertIn("--mcJvmArgs=-Dhorizonqa.client=true", command)
        adapter = Path(command[command.index("--init-script") + 1])
        self.assertEqual(report, adapter.parent)
        self.assertIn("task.workingDir", adapter.read_text())
        self.assertEqual(str(report / "gradle-cache"), command[command.index("--project-cache-dir") + 1])
        summary = json.loads((report / "launch-result.json").read_text())
        self.assertEqual(2, summary["exitCode"])
        self.assertNotEqual("development", summary["toolsVersion"])
