"""Exercise the shipped command outside a source checkout, including its bundled Gradle adapter."""
import json
from pathlib import Path
import sys

from tool_test_case import ToolTestCase


class PackagedToolTest(ToolTestCase):
    def test_help_and_version_work_without_adjacent_source(self):
        result = self.call("--version")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(result.stdout.startswith("Horizon-QA tools "))
        self.assertNotIn("development", result.stdout)
        for command in ((), ("run-client",), ("verify",)):
            with self.subTest(command=command):
                result = self.call(*command, "--help")
                self.assertEqual(0, result.returncode, result.stderr)

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
        gradle.write_text(f"#!{sys.executable}\nimport json, os, pathlib, sys\n"
            "pathlib.Path('captured-argv.json').write_text(json.dumps(sys.argv[1:]))\n"
            "pathlib.Path('captured-env.json').write_text(json.dumps({"
            "key: os.environ.get(key) for key in ('DISPLAY', 'XDG_SESSION_TYPE', 'WAYLAND_DISPLAY', 'SDL_VIDEO_DRIVER')}))\n")
        gradle.chmod(0o700)
        launch_env = self.directory / "launch.env"
        launch_env.write_text("DISPLAY=:host\nXDG_SESSION_TYPE=wayland\nWAYLAND_DISPLAY=wayland-test\nSDL_VIDEO_DRIVER=wayland\n")
        report = self.directory / "reports"
        result = self.call("run-client", "--cwd", self.directory, "--gradle-task", ":mod:runClient25",
            "--tests", "mod:Holder.test", "--report-dir", report, "--timeout", "1200",
            "--xvfb", display, "--xdotool", focus, "--env-file", launch_env)
        # This probe establishes command/resource delivery, not Minecraft behavior. Missing game reports fail.
        self.assertEqual(2, result.returncode, result.stderr)
        command = json.loads((self.directory / "captured-argv.json").read_text())
        self.assertEqual(":mod:runClient25", command[0])
        self.assertIn("--mcJvmArgs=-Dhorizonqa.tests=mod:Holder.test", command)
        self.assertIn("--mcJvmArgs=-Dhorizonqa.client=true", command)
        self.assertIn("-PhqaHeadlessNH=true", command)
        gates = [value for value in command if value.startswith("--mcJvmArgs=-Dheadlessnh.gate.timeout=")]
        self.assertEqual(1, len(gates))
        self.assertGreater(int(gates[0].rsplit("=", 1)[1]), 1200 * 1000)
        adapter = Path(command[command.index("--init-script") + 1])
        self.assertEqual(report, adapter.parent)
        self.assertIn("task.workingDir", adapter.read_text())
        self.assertEqual(str(report / "gradle-cache"), command[command.index("--project-cache-dir") + 1])
        environment = json.loads((self.directory / "captured-env.json").read_text())
        self.assertEqual(":199", environment["DISPLAY"])
        self.assertEqual("x11", environment["XDG_SESSION_TYPE"])
        self.assertIsNone(environment["WAYLAND_DISPLAY"])
        self.assertEqual("x11", environment["SDL_VIDEO_DRIVER"])
        summary = json.loads((report / "launch-result.json").read_text())
        self.assertEqual(2, summary["exitCode"])
        self.assertNotEqual("development", summary["toolsVersion"])
