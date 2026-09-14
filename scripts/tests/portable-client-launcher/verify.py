"""Run through Context Mode. Requires JDK 17+ and Python, never starts Minecraft."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time


ROOT = Path(__file__).resolve().parents[3]
FIXTURES = Path(__file__).resolve().parent
JAVA = Path(shutil.which('java')).resolve()
JAVAC = JAVA.with_name('javac' + JAVA.suffix)
JAR = JAVA.with_name('jar' + JAVA.suffix)


with tempfile.TemporaryDirectory(prefix='horizonqa-launcher-') as temp:
    target = Path(temp)
    classes = target / 'classes'
    classes.mkdir()
    subprocess.run([str(JAVAC), '--release', '17', '-d', str(classes), str(FIXTURES / 'GradleWrapperMain.java')], check=True)
    wrapper = target / 'gradle/wrapper/gradle-wrapper.jar'
    wrapper.parent.mkdir(parents=True)
    subprocess.run([str(JAR), 'cf', str(wrapper), '-C', str(classes), '.'], check=True)
    sentinel = subprocess.Popen([str(JAVA), '-cp', str(wrapper), 'org.gradle.wrapper.GradleWrapperMain', 'child'])
    try:
        for mode, expected in [('success', 0), ('success', 0), ('failure', 1), ('missing', 2),
                               ('malformed', 2), ('truncated', 2), ('worker-failure', 2),
                               ('hang', 2), ('leak', 2), ('short-leak', 0), ('client-hang', 2)]:
            command = [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                       'org.gradle.wrapper.GradleWrapperMain', '-p', str(ROOT / 'scripts/client-tests'),
                       'runClientTests', '--configuration-cache', '--project-root', str(target),
                       '--tests', mode, '--timeout-seconds', '3', '--shutdown-seconds', '1']
            completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
            reports = sorted((target / 'build/client-tests').glob('*/launch-result.json'), key=os.path.getmtime)
            assert reports, completed.stdout + completed.stderr
            result = json.loads(reports[-1].read_text())
            assert result['exitCode'] == expected, (result, completed.stdout, completed.stderr,
                                                    Path(result['launchLog']).read_text() if Path(result['launchLog']).exists() else '')
            assert (completed.returncode == 0) == (expected == 0), completed.stdout + completed.stderr
            assert sentinel.poll() is None, 'Unrelated process was stopped'
            if mode in ('hang', 'leak', 'client-hang'):
                assert result['timedOut'] == (mode != 'leak') and result['forcedTermination'], result
                for pid in reports[-1].with_name('pids.txt').read_text().splitlines():
                    # jcmd lists only live Java processes on both Windows and Linux.
                    live = subprocess.check_output([str(JAVA.with_name('jcmd' + JAVA.suffix)), '-l'], text=True)
                    assert not any(line.startswith(pid + ' ') for line in live.splitlines()), live
            if mode == 'client-hang':
                if 'Child arguments available: true' in Path(result['launchLog']).read_text():
                    assert 'Full thread dump' in reports[-1].with_name('threads.txt').read_text()
                else:
                    assert not reports[-1].with_name('threads.txt').exists()
                    print('JDK process arguments unavailable, thread dump safely skipped')
            else:
                assert not reports[-1].with_name('threads.txt').exists(), 'Dump targeted a non-client process'
            print(mode, 'passed', 'cache reused' if 'Reusing configuration cache' in completed.stdout else 'cache stored')
        launches = []
        def wait_for(path):
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if path.exists():
                    return
                time.sleep(0.05)
            raise AssertionError('Missing marker: ' + str(path))

        try:
            for name in ('overlap-a', 'overlap-b'):
                report = target / name
                args = [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                        'org.gradle.wrapper.GradleWrapperMain', '-p', str(ROOT / 'scripts/client-tests'),
                        'runClientTests', '--configuration-cache', '--project-root', str(target),
                        '--tests', 'overlap', '--report-dir', str(report), '--timeout-seconds', '40',
                        '--shutdown-seconds', '1']
                log = (target / (name + '.log')).open('w')
                launches.append((subprocess.Popen(args, cwd=ROOT, stdout=log, stderr=log), report, log))
                if name == 'overlap-a':
                    wait_for(report / 'preparation-entered')
            time.sleep(1)
            assert not (target / 'overlap-b/preparation-entered').exists(), 'Preparation was not serialized'
            blocked_report = target / 'lock-timeout'
            blocked_args = [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                            'org.gradle.wrapper.GradleWrapperMain', '-p', str(ROOT / 'scripts/client-tests'),
                            'runClientTests', '--configuration-cache', '--project-root', str(target),
                            '--tests', 'success', '--report-dir', str(blocked_report), '--timeout-seconds', '1']
            blocked = subprocess.run(blocked_args, cwd=ROOT, capture_output=True, text=True, timeout=20)
            blocked_result = json.loads((blocked_report / 'launch-result.json').read_text())
            assert blocked.returncode != 0 and blocked_result['exitCode'] == 2
            assert blocked_result['timedOut'] and not blocked_result['forcedTermination']
            assert not (blocked_report / 'launch.log').exists(), 'Target launched without preparation lock'
            print('Preparation lock wait respects launch deadline')
            (target / 'overlap-a/allow-snapshot').touch()
            wait_for(target / 'overlap-a/client-classpath-ready')
            wait_for(target / 'overlap-b/preparation-entered')
            (target / 'overlap-b/allow-snapshot').touch()
            wait_for(target / 'overlap-b/client-classpath-ready')
            assert all(process.poll() is None for process, _, _ in launches), 'Clients did not overlap'
            assert sentinel.poll() is None
            for _, report, _ in launches:
                (report / 'release-client').touch()
            for process, report, _ in launches:
                assert process.wait(timeout=20) == 0
                assert json.loads((report / 'launch-result.json').read_text())['exitCode'] == 0
            print('Concurrent clients passed with serialized preparation and configuration cache enabled')
        finally:
            for process, report, log in launches:
                if report.exists():
                    (report / 'allow-snapshot').touch()
                    (report / 'release-client').touch()
                process.wait(timeout=50)
                log.close()
    finally:
        sentinel.kill()
        sentinel.wait(timeout=10)
