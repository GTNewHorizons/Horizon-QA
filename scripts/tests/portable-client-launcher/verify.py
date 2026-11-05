"""Run through Context Mode. Requires JDK 17+ and Python, never starts Minecraft."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time


ROOT = Path(__file__).resolve().parents[3]
TOOLS = Path(os.environ.get('HQA_TOOLS_ROOT', ROOT))
SERVER = '--server' in sys.argv
TASK = 'runServerTests' if SERVER else 'runClientTests'
SIDE_OPTIONS = ['--accept-eula'] if SERVER else []
if SERVER and '--retention-only' not in sys.argv:
    raise ValueError('--server selects the server storage suite and requires --retention-only')
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
        if '--retention-only' in sys.argv:
            reports = target / 'retention'
            reports.mkdir()
            untouched = reports / 'unowned'
            untouched.mkdir()
            (untouched / 'keep.txt').write_text('not owned by the launcher')
            def storage_command(report, mode='success', extra=()):
                return [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                        'org.gradle.wrapper.GradleWrapperMain', '-p', str(TOOLS / 'scripts/client-tests'),
                        TASK, '--configuration-cache', '--project-root', str(target), '--tests', mode,
                        '--report-dir', str(report), '--timeout-seconds', '30'] + SIDE_OPTIONS + list(extra)

            def run_storage(number, extra=(), mode='success', expected=0):
                report = reports / str(number)
                completed = subprocess.run(storage_command(report, mode, extra), cwd=ROOT,
                                           capture_output=True, text=True, timeout=90)
                assert (completed.returncode == 0) == (expected == 0), completed.stdout + completed.stderr
                result = json.loads((report / 'launch-result.json').read_text())
                diagnostics = '\n'.join((report / name).read_text(errors='replace')
                                        for name in ('launch.log', 'verification.log') if (report / name).is_file())
                assert result['exitCode'] == expected, (result, diagnostics, completed.stdout, completed.stderr)
                return report

            for number in range(6):
                report = run_storage(number)
            assert not (reports / '0').exists(), 'Default retention kept more than five completed launches'
            assert all((reports / str(n) / 'launch-result.json').is_file() for n in range(1, 6))
            assert (untouched / 'keep.txt').read_text() == 'not owned by the launcher'
            assert not (report / 'game').exists() and not (report / 'classpath').exists()
            assert (report / 'diagnostics/logs/latest.log').read_text() == 'fixture game log'
            print('Default retention, heavy-data cleanup and unowned directory protection passed')
            settings = target / 'gradle.properties'
            settings.write_text('horizonQaRetainedRuns=2\nhorizonQaKeepRuntimeFiles=true\n')
            for number in range(6, 9):
                report = run_storage(number)
                assert (report / 'game/mods/fixture.jar').is_file() and (report / 'classpath').is_dir()
            assert sorted(p.name for p in reports.iterdir() if (p / 'launch-result.json').is_file()) == ['7', '8']
            outside = target / 'outside-retention'
            outside.mkdir()
            (outside / 'keep.txt').write_text('external data')
            try:
                (report / 'game/external-link').symlink_to(outside, target_is_directory=True)
            except OSError as error:
                print('Symlink fixture unavailable on this host:', error)
            report = run_storage(9, ['--retained-runs', '1', '--no-keep-runtime-files'])
            assert not (reports / '8').exists() and not (report / 'game').exists()
            assert (outside / 'keep.txt').read_text() == 'external data'
            report = run_storage(10, ['--retained-runs', '1', '--no-keep-runtime-files'], mode='failure', expected=1)
            assert not (reports / '9').exists() and not (report / 'game').exists()
            assert (report / 'diagnostics/logs/latest.log').is_file()
            settings.unlink()
            active_report = reports / 'active'
            active = subprocess.Popen(storage_command(active_report, 'overlap', ['--retained-runs', '1']),
                                      cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 30
                while not (active_report / 'preparation-entered').exists():
                    assert active.poll() is None and time.monotonic() < deadline
                    time.sleep(0.05)
                (active_report / 'allow-snapshot').touch()
                run_storage(11, ['--retained-runs', '1'])
                assert active.poll() is None and (active_report / 'game/mods/fixture.jar').is_file()
                (active_report / 'release-client').touch()
                output, _ = active.communicate(timeout=45)
                assert active.returncode == 0, output
                assert not (reports / '11').exists() and (active_report / 'launch-result.json').is_file()
                assert (untouched / 'keep.txt').is_file()
            finally:
                if active.poll() is None:
                    (active_report / 'release-client').touch()
                    active.communicate(timeout=45)
            print('Consumer defaults, CLI overrides, failed-run expiry and active-run protection passed')
            other = target / 'another-project'
            other_wrapper = other / 'gradle/wrapper/gradle-wrapper.jar'
            other_wrapper.parent.mkdir(parents=True)
            shutil.copyfile(wrapper, other_wrapper)
            foreign = reports / 'other-project'
            command = storage_command(foreign, extra=['--retained-runs', '1'])
            command[command.index('--project-root') + 1] = str(other)
            completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
            assert completed.returncode == 0, completed.stdout + completed.stderr
            command = storage_command(reports / '12', extra=['--retained-runs', '1'])
            command[command.index(TASK)] = 'runClientTests' if SERVER else 'runServerTests'
            if SERVER:
                command.remove('--accept-eula')
            else:
                command.append('--accept-eula')
            completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
            assert completed.returncode == 0, completed.stdout + completed.stderr
            assert foreign.is_dir() and not active_report.exists()
            invalid = reports / 'invalid-count'
            completed = subprocess.run(storage_command(invalid, extra=['--retained-runs', '0']), cwd=ROOT,
                                       capture_output=True, text=True, timeout=90)
            assert completed.returncode != 0 and not invalid.exists()
            assert foreign.is_dir() and (reports / '12/launch-result.json').is_file()
            if SERVER:
                report = reports / 'no-eula'
                command = storage_command(report)
                command.remove('--accept-eula')
                completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
                assert completed.returncode != 0 and not report.exists()
            report = reports / 'removed-option'
            completed = subprocess.run(storage_command(report, extra=['--keep-game']), cwd=ROOT,
                                       capture_output=True, text=True, timeout=90)
            assert completed.returncode != 0 and not report.exists()
            print('Other projects and external data protected, invalid limits rejected before launch')
            sys.exit(0)
        reserved_arguments = [
            ['--mcJvmArgs=-Dhorizonqa.client=false'],
            ['--mcJvmArgs=-Dhorizonqa.mode=off'],
            ['--mcJvmArgs=-Dhorizonqa.world=void'],
            ['--mcJvmArgs=-Dhorizonqa.autoRun=false'],
            ['--mcJvmArgs=-Dhorizonqa.stopServer=false'],
            ['--mcJvmArgs=-Dhorizonqa.turbo=10'],
            ['--mcJvmArgs=-Dhorizonqa.tests=other:Test'],
            ['--mcJvmArgs=-Dhorizonqa.reportDir=other'],
            ['--mcJvmArgs=-Dhorizonqa.reportFile=other.xml'],
            ['--mcJvmArgs=-Dhorizonqa.statusFile=other.json'],
            ['--mcJvmArgs=-Dhorizonqa.launchId=foreign'],
            ['-PhorizonQaClientTask=:other'],
            ['-PhorizonQaClientReportDir=other'],
            ['-PhorizonQaJar=other.jar'],
            ['--project-prop=horizonQaClientTask=:other'],
            ['--project-prop', 'horizonQaClientReportDir=other'],
            ['--mcJvmArgs', '-Dhorizonqa.client=false'],
            ['--mcArgs=--gameDir=other'],
            ['--mcArgs=--gameDir', '--mcArgs=other'],
            ['--daemon'],
            ['-Dorg.gradle.daemon=true'],
            ['--system-prop=org.gradle.daemon=true'],
        ]
        for number, arguments in enumerate(reserved_arguments):
            report = target / ('managed-' + str(number))
            command = [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                       'org.gradle.wrapper.GradleWrapperMain', '-p', str(TOOLS / 'scripts/client-tests'),
                       'runClientTests', '--configuration-cache', '--project-root', str(target),
                       '--tests', 'success', '--report-dir', str(report), '--timeout-seconds', '10']
            command += ['--gradle-argument=' + argument for argument in arguments]
            if arguments == ['-PhorizonQaJar=other.jar']:
                command += ['--horizon-qa-jar', str(target / 'owned-horizonqa.jar')]
            completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
            assert completed.returncode != 0, 'Managed override launched target: ' + str(arguments)
            assert (report / 'launch-result.json').is_file(), completed.stdout + completed.stderr
            result = json.loads((report / 'launch-result.json').read_text())
            assert result['exitCode'] == 2 and not result['timedOut'] and not result['forcedTermination'], result
            assert not (report / 'received-args.txt').exists(), 'Target started before argument validation'
            assert sentinel.poll() is None, 'Unrelated process was stopped'
        print('Managed overrides and daemon enablement rejected before target launch')

        legal_arguments = ['--mcJvmArgs=-Xmx512m', '--mcJvmArgs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005',
                           '--mcJvmArgs=-Dhorizonqa.client.sound=true', '--mcJvmArgs=-Dhorizonqa.events=false',
                           '-PfixtureCustom=value with spaces', '-Dorg.gradle.jvmargs=-Xmx512m',
                           '-Dorg.gradle.daemon=false', '--stacktrace', '--no-daemon']
        report = target / 'legal-arguments'
        command = [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                   'org.gradle.wrapper.GradleWrapperMain', '-p', str(TOOLS / 'scripts/client-tests'),
                   'runClientTests', '--configuration-cache', '--project-root', str(target),
                   '--tests', 'success', '--report-dir', str(report), '--timeout-seconds', '10']
        command += ['--gradle-argument=' + argument for argument in legal_arguments]
        completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
        assert completed.returncode == 0, completed.stdout + completed.stderr
        received = (report / 'received-args.txt').read_text().splitlines()
        assert all(argument in received for argument in legal_arguments), received
        print('Memory, debug and custom arguments preserved literally')
        if '--arguments-only' in sys.argv:
            sys.exit(0)

        for mode, expected in [('success', 0), ('success', 0), ('failure', 1), ('unexpected-worker-success', 2), ('missing', 2),
                               ('malformed', 2), ('truncated', 2), ('contradictory', 2), ('worker-failure', 2), ('no-python', 2),
                               ('hang', 2), ('leak', 2), ('short-leak', 0), ('client-hang', 2)]:
            command = [str(JAVA), '-cp', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
                       'org.gradle.wrapper.GradleWrapperMain', '-p', str(TOOLS / 'scripts/client-tests'),
                       'runClientTests', '--configuration-cache', '--project-root', str(target),
                       '--tests', mode, '--timeout-seconds', '3', '--shutdown-seconds', '1']
            if mode == 'no-python':
                command += ['--python', str(target / 'missing-python-executable')]
            completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=90)
            reports = sorted((target / 'build/horizonqa').glob('*/launch-result.json'), key=os.path.getmtime)
            assert reports, completed.stdout + completed.stderr
            result = json.loads(reports[-1].read_text())
            assert result['exitCode'] == expected, (result, completed.stdout, completed.stderr,
                                                    Path(result['launchLog']).read_text() if Path(result['launchLog']).exists() else '')
            assert (completed.returncode == 0) == (expected == 0), completed.stdout + completed.stderr
            assert sentinel.poll() is None, 'Unrelated process was stopped'
            if mode == 'missing':
                assert 'missing/unsafe report' in reports[-1].with_name('verification.log').read_text()
            if mode in ('hang', 'leak', 'client-hang'):
                assert result['timedOut'] == (mode != 'leak') and result['forcedTermination'], result
                for pid in reports[-1].with_name('pids.txt').read_text().splitlines():
                    # jcmd lists only live Java processes on both Windows and Linux
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
                        'org.gradle.wrapper.GradleWrapperMain', '-p', str(TOOLS / 'scripts/client-tests'),
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
                            'org.gradle.wrapper.GradleWrapperMain', '-p', str(TOOLS / 'scripts/client-tests'),
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
