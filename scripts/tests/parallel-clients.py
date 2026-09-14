"""Boot two real clients and release their GUI tests only when both are running."""
import argparse
import json
import shutil
import subprocess
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SELECTOR = "horizonqaexamples:ClientParallelTests.clickWhileAnotherClientIsRunning"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--timeout-seconds", type=int, default=600)
    args = parser.parse_args()
    directory = args.report_dir.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    reports = [directory / "client-a", directory / "client-b"]
    launches = []
    deadline = time.monotonic() + args.timeout_seconds
    try:
        for report in reports:
            log = (directory / (report.name + ".log")).open("w", encoding="utf-8")
            command = [shutil.which("java"), "-cp", str(ROOT / "gradle/wrapper/gradle-wrapper.jar"),
                       "org.gradle.wrapper.GradleWrapperMain", "-p", str(ROOT / "scripts/client-tests"),
                       "runClientTests", "--configuration-cache", "--tests", SELECTOR,
                       "--report-dir", str(report), "--timeout-seconds", str(args.timeout_seconds)]
            process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            launches.append((process, log))
        while not all((report / "client-ready").is_file() for report in reports):
            assert all(process.poll() is None for process, _ in launches), "Client exited before both were ready"
            assert time.monotonic() < deadline, "Both clients did not become ready before the deadline"
            time.sleep(0.1)
        assert all(process.poll() is None for process, _ in launches), "Clients did not overlap"
        (directory / "both-clients-ready").touch()
        for report in reports:
            (report / "release-client").touch()
        summaries = []
        for report, (process, _) in zip(reports, launches):
            assert process.wait(timeout=max(1, deadline - time.monotonic()) + 30) == 0, str(report)
            launch = json.loads((report / "launch-result.json").read_text(encoding="utf-8"))
            result = json.loads((report / "horizonqa-result.json").read_text(encoding="utf-8"))
            assert launch["exitCode"] == result["exitCode"] == 0, (launch, result)
            assert not launch["timedOut"] and not launch["forcedTermination"], launch
            assert len(result["tests"]) == 1 and result["tests"][0]["status"] == "passed", result
            assert (report / "classpath").is_dir(), report
            assert Path(launch["gameDir"]) == report / "game", launch
            summaries.append(launch)
        assert summaries[0]["launchId"] != summaries[1]["launchId"], summaries
        print("Both clients overlapped and passed their independent GUI clicks:", directory, flush=True)
    finally:
        for report, (process, _) in zip(reports, launches):
            if process.poll() is None and report.is_dir():
                (report / "stop-client").touch(exist_ok=True)
        for process, log in launches:
            try:
                process.wait(timeout=max(1, deadline - time.monotonic()) + 30)
            finally:
                log.close()


if __name__ == "__main__":
    main()
