#!/usr/bin/env python3
"""Supervise a Gradle or packaged client in a fresh directory and private Linux display."""
import argparse
from importlib.resources import files
import json
import os
from pathlib import Path
import re
import selectors
import shlex
import shutil
import signal
import subprocess
import sys
import time

from . import version
from . import verify as verifier


def terminate(process):
    if process is None:
        return False
    try:
        os.killpg(process.pid, 0)
    except ProcessLookupError:
        process.wait()
        return False
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait(timeout=5)
    return True


def dump_threads(process, report):
    # Gradle's single-use daemon starts another session; the managed report argument identifies its game JVM.
    report_argument = ("-Dhorizonqa.reportDir=" + str(report)).encode()
    for entry in Path("/proc").iterdir():
        if not entry.name.isdecimal():
            continue
        try:
            fields = (entry / "stat").read_text().rsplit(")", 1)[1].split()
            if (entry / "comm").read_text().strip() != "java":
                continue
            if int(fields[3]) == process.pid or report_argument in (entry / "cmdline").read_bytes().split(b"\0"):
                os.kill(int(entry.name), signal.SIGQUIT)
        except (OSError, ValueError):
            pass


def main(argv=None):
    parser = argparse.ArgumentParser(prog="hqa run-client", description=__doc__)
    parser.add_argument("--report-dir", required=True, type=Path, help="Must not already exist")
    parser.add_argument("--tests", required=True)
    parser.add_argument("--cwd", type=Path, default=Path.cwd())
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--grace", type=int, default=15)
    parser.add_argument("--gradle-task", help="For example :examples:runClient; forwards each JVM flag separately")
    parser.add_argument("--argv-file", type=Path, help="Packaged launcher argv, one literal argument per line, starting with java")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--headless", choices=("singleplayer", "existing"), default="singleplayer")
    parser.add_argument("--xvfb", default=shutil.which("Xvfb"))
    parser.add_argument("--xdotool", default=shutil.which("xdotool"))
    parser.add_argument("extra", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    report = args.report_dir.resolve()
    process = display = None
    summary = {"exitCode": 2, "timedOut": False, "forcedTermination": False,
        "reportDir": str(report), "toolsVersion": version()}
    created = False
    try:
        if not (0 < args.timeout <= 86400 and 0 < args.grace <= 60):
            raise ValueError("timeout must be 1..86400 and grace 1..60 seconds")
        if not args.tests.strip() or "*" in args.tests or any(not token.strip() for token in args.tests.split(",")):
            raise ValueError("a nonempty supported selector is required")
        if bool(args.gradle_task) == bool(args.argv_file):
            raise ValueError("choose exactly one of --gradle-task and --argv-file")
        report.parent.mkdir(parents=True, exist_ok=True)
        report.mkdir()
        created = True
        started_wall = time.time()
        deadline = time.monotonic() + args.timeout
        environment = os.environ.copy()
        if args.env_file:
            for line in args.env_file.read_text().splitlines():
                if not line.strip() or line.lstrip().startswith("#"):
                    continue
                name, separator, value = line.removeprefix("export ").partition("=")
                if not separator or not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", name):
                    raise ValueError("invalid launch environment assignment")
                values = shlex.split(value)
                if len(values) > 1:
                    raise ValueError("environment values containing spaces must be quoted")
                environment[name] = values[0] if values else ""
        if not args.xvfb or not args.xdotool:
            raise ValueError("Xvfb and xdotool are required for private native input (install xvfb xdotool)")
        with (report / "xvfb.log").open("wb") as display_log:
            display = subprocess.Popen([args.xvfb, "-displayfd", "1", "-screen", "0", "2560x1440x24", "-nolisten", "tcp"],
                stdout=subprocess.PIPE, stderr=display_log, start_new_session=True)
            with selectors.DefaultSelector() as selector:
                selector.register(display.stdout, selectors.EVENT_READ)
                if not selector.select(timeout=min(10, args.timeout)):
                    raise ValueError("private display did not become ready within 10 seconds")
                number = display.stdout.readline().decode().strip()
                if not number.isdecimal():
                    raise ValueError("private display failed; see xvfb.log")
            environment["DISPLAY"] = ":" + number
            # GLFW and SDL must use the private X11 display even when the host session uses Wayland.
            environment["XDG_SESSION_TYPE"] = "x11"
            environment["SDL_VIDEO_DRIVER"] = "x11"
            environment.pop("WAYLAND_DISPLAY", None)
            summary["display"] = environment["DISPLAY"]
            environment.setdefault("LIBGL_ALWAYS_SOFTWARE", "1")
            environment.setdefault("ALSOFT_DRIVERS", "null")
            jvm = ["-Dhorizonqa.mode=ci", "-Dhorizonqa.client=true", "-Dhorizonqa.world=normal",
                "-Dhorizonqa.autoRun=true", "-Dhorizonqa.stopServer=true", "-Dhorizonqa.turbo=1",
                "-Dhorizonqa.tests=" + args.tests, "-Dhorizonqa.reportDir=" + str(report),
                "-Djava.awt.headless=false", "-Dheadlessnh.gate.worldloaded=hqa-complete.gate",
                "-Dheadlessnh.gate.timeout=" + str((args.timeout + args.grace) * 1000)]
            if args.headless == "singleplayer":
                jvm += ["-Dheadlessnh.active=true", "-Dheadlessnh.singleplayer=true",
                    "-Dheadlessnh.combined=false", "-Dheadlessnh.gate.mainmenu=", "-Dheadlessnh.gate.serverloaded=",
                    "-Dheadlessnh.markerdir=" + str(report), "-Dheadlessnh.forcefocus=true"]
            extra = args.extra[1:] if args.extra[:1] == ["--"] else args.extra
            managed = {value.split("=", 1)[0] for value in jvm}
            for argument in extra:
                value = argument.removeprefix("--mcJvmArgs=")
                if value.split("=", 1)[0] in managed:
                    raise ValueError("extra arguments cannot override managed launch properties")
            if args.gradle_task:
                isolation = report / "client-test-isolation.init.gradle"
                isolation.write_text(files("hqa_tools").joinpath("client-test-isolation.init.gradle").read_text(encoding="utf-8"))
                command = [str(args.cwd.resolve() / "gradlew"), args.gradle_task, "--no-daemon", "--console=plain",
                    "--project-cache-dir", str(report / "gradle-cache"), "--init-script", str(isolation),
                    "-PhorizonQaClientTask=" + args.gradle_task, "-PhorizonQaClientReportDir=" + str(report),
                    "-PhqaHeadlessNH=true"]
                command += ["--mcJvmArgs=" + value for value in jvm]
                command += ["--mcArgs=--width", "--mcArgs=959", "--mcArgs=--height", "--mcArgs=541"] + extra
            else:
                command = args.argv_file.read_text().splitlines()
                if not command or not Path(command[0]).is_file():
                    raise ValueError("packaged argv must begin with a Java executable")
                if (args.cwd / "saves").exists():
                    raise ValueError("packaged launch requires an isolated game directory without saves")
                for index, argument in enumerate(command):
                    if argument == "--gameDir":
                        if index + 1 == len(command) or (args.cwd / command[index + 1]).resolve() != args.cwd.resolve():
                            raise ValueError("packaged --gameDir must match the isolated --cwd")
                    elif argument.startswith("--gameDir=") and (args.cwd / argument.split("=", 1)[1]).resolve() != args.cwd.resolve():
                        raise ValueError("packaged --gameDir must match the isolated --cwd")
                command = [argument for argument in command if argument.split("=", 1)[0] not in managed]
                command[1:1] = jvm + extra
            with (report / "launch.log").open("wb") as log:
                process = subprocess.Popen(command, cwd=args.cwd, env=environment, stdout=log,
                    stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True)
                try:
                    # LWJGL can replace its native window during startup; focus each replacement on our display.
                    while process.poll() is None and time.monotonic() < deadline:
                        windows = subprocess.run([args.xdotool, "search", "--onlyvisible", "--class", "."],
                            env=environment, capture_output=True, text=True, timeout=2)
                        visible = windows.stdout.splitlines()
                        candidates = [] if summary.get("focusedWindow") in visible else visible
                        for window in candidates:
                            position = subprocess.run([args.xdotool, "windowmove", window, "0", "0"], env=environment,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=2)
                            if position.returncode != 0:
                                continue
                            focus = subprocess.run([args.xdotool, "windowfocus", window], env=environment,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=2)
                            if focus.returncode == 0:
                                summary["focusedWindow"] = window
                                summary.setdefault("focusedWindows", []).append(window)
                                break
                        try:
                            process.wait(timeout=min(0.2, max(0.01, deadline - time.monotonic())))
                        except subprocess.TimeoutExpired:
                            pass
                    process.wait(timeout=max(0.01, deadline - time.monotonic()))
                except (subprocess.TimeoutExpired, KeyboardInterrupt):
                    summary["timedOut"] = True
                    (report / "stop-client").touch()
                    dump_threads(process, report)
                    try:
                        process.wait(timeout=args.grace)
                    except subprocess.TimeoutExpired:
                        summary["forcedTermination"] = terminate(process)
                summary["processExitCode"] = process.returncode
            summary["forcedTermination"] = terminate(process) or summary["forcedTermination"]
            game = report / "game" if args.gradle_task else args.cwd
            crashes = [path for pattern in ("crash-reports/*.txt", "hs_err_pid*.log") for path in game.glob(pattern)
                if path.stat().st_mtime >= started_wall]
            summary["crashReports"] = [str(path) for path in crashes]
            result = verifier.verify(report, args.tests, started_wall)
            summary["exitCode"] = result["exitCode"]
            expected_process_exit = (0 if result["exitCode"] == 0 else 1) if args.gradle_task else result["exitCode"]
            if summary["timedOut"] or summary["forcedTermination"] or crashes or process.returncode != expected_process_exit:
                summary["exitCode"] = 2
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError, verifier.ET.ParseError) as error:
        summary["error"] = str(error)
    finally:
        if process is not None:
            summary["forcedTermination"] = terminate(process) or summary["forcedTermination"]
        terminate(display)
        if created:
            (report / "launch-result.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps(summary))
    return summary["exitCode"]


if __name__ == "__main__":
    sys.exit(main())
