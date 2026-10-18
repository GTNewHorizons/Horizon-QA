"""Ownership checks for timeout diagnostics; no signal reaches a real process."""
from pathlib import Path
import signal
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import call, patch

from hqa_tools.run_client import dump_threads


class ThreadDumpTest(unittest.TestCase):
    def test_dump_includes_tagged_game_across_sessions_and_excludes_foreign_jvms(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "reports"
            proc = root / "proc"
            processes = (
                (100, "java", 100, [b"java"]),
                (200, "java", 200, [b"java", f"-Dhorizonqa.reportDir={report}".encode()]),
                (300, "java", 300, [b"java", f"-Dhorizonqa.reportDir={report}-other".encode()]),
                (400, "java", 400, [b"java", f"--mcJvmArgs=-Dhorizonqa.reportDir={report}".encode()]),
                (500, "python3", 100, [b"python3"]),
            )
            for pid, name, session, arguments in processes:
                entry = proc / str(pid)
                entry.mkdir(parents=True)
                (entry / "stat").write_text(f"{pid} ({name}) S 1 {session} {session}")
                (entry / "comm").write_text(name + "\n")
                (entry / "cmdline").write_bytes(b"\0".join(arguments) + b"\0")
            with patch("hqa_tools.run_client.Path", return_value=proc), patch("hqa_tools.run_client.os.kill") as kill:
                dump_threads(SimpleNamespace(pid=100), report)
            self.assertCountEqual(kill.call_args_list, [call(100, signal.SIGQUIT), call(200, signal.SIGQUIT)])
