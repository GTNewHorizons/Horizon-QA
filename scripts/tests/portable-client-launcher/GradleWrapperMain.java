package org.gradle.wrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** A fake target wrapper exercises the supervisor's real process boundary without Minecraft */
public class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && (args[0].equals("child") || args[0].equals("short-child"))) {
            Thread.sleep(args[0].equals("short-child") ? 900 : 120000);
            return;
        }
        String mode = option(args, "--mcJvmArgs=-Dhorizonqa.tests=");
        Path report = Path.of(option(args, "-PhorizonQaClientReportDir="));
        Files.write(report.resolve("received-args.txt"), Arrays.asList(args));
        if (mode.equals("overlap")) {
            Files.writeString(report.resolve("preparation-entered"), "preparing");
            while (!Files.exists(report.resolve("allow-snapshot"))) Thread.sleep(50);
            Files.writeString(report.resolve("client-classpath-ready"), "ready");
            while (!Files.exists(report.resolve("release-client"))) Thread.sleep(50);
            writeResult(report, mode, 0);
            return;
        }
        if (mode.equals("hang") || mode.equals("leak") || mode.equals("short-leak") || mode.equals("client-hang")) {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String property = mode.equals("client-hang")
                    ? "-Dhorizonqa.launchId=" + option(args, "--mcJvmArgs=-Dhorizonqa.launchId=")
                    : "-Dfixture=true";
            Process child = new ProcessBuilder(java, property, "-cp", System.getProperty("java.class.path"),
                    GradleWrapperMain.class.getName(), mode.equals("short-leak") ? "short-child" : "child")
                    .inheritIO().start();
            Files.writeString(report.resolve("pids.txt"), ProcessHandle.current().pid() + "\n" + child.pid());
            System.out.println("Child arguments available: " + child.info().arguments().isPresent());
            if (mode.equals("hang") || mode.equals("client-hang")) {
                Thread.sleep(120000);
            } else {
                writeResult(report, mode, 0);
                Thread.sleep(500);
            }
        } else if (!mode.equals("missing")) {
            writeResult(report, mode, mode.equals("failure") || mode.equals("unexpected-worker-success") ? 1 : 0);
            if (mode.equals("malformed") || mode.equals("truncated")) {
                Files.writeString(report.resolve("horizonqa-result.json"), mode.equals("malformed") ? "{"
                        : "{\"exitCode\":0}");
            }
            if (mode.equals("contradictory")) {
                Path status = report.resolve("horizonqa-result.json");
                Files.writeString(status, Files.readString(status).replace("\"passed\":1", "\"passed\":0"));
            }
        }
        if (mode.equals("worker-failure") || mode.equals("failure")) System.exit(1);
    }

    private static void writeResult(Path report, String mode, int exitCode) throws Exception {
        int failed = exitCode == 1 ? 1 : 0;
        String state = failed == 0 ? "passed" : "failed";
        String result = """
                {"schemaVersion":5,"status":"%s","exitCode":%d,
                "configuration":{"mode":"ci","tests":"%s","allowNoTests":false},
                "reports":{"junit":"%s","status":"%s"},
                "tests":[{"id":"fixture:%s.run","classname":"fixture:%s","name":"run",
                "status":"%s","required":true,"ticks":1,"timeSeconds":0.05,
                "output":["holderClass=fixture.%s"]}],"issues":[],
                "counts":{"selectedTests":1,"passed":%d,"failed":%d,"timedOut":0,"skipped":0,"incomplete":0,
                "requiredFailures":%d,"optionalFailures":0,"issues":0,"diagnosticErrors":0,
                "junitFailures":%d,"junitErrors":0,"junitSkipped":0}}
                """.formatted(state, exitCode, mode, escaped(report.resolve("TEST-horizonqa.xml")),
                escaped(report.resolve("horizonqa-result.json")), mode, mode, state, mode,
                1 - failed, failed, failed, failed);
        Files.writeString(report.resolve("horizonqa-result.json"), result);
        Files.writeString(report.resolve("TEST-horizonqa.xml"), """
                <testsuite tests="1" failures="%d" errors="0" skipped="0">
                  <testcase classname="fixture:%s" name="run">%s</testcase>
                </testsuite>
                """.formatted(failed, mode, failed == 0 ? "" : "<failure message=\"expected fixture failure\"/>"));
    }

    private static String escaped(Path value) {
        return value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String option(String[] args, String prefix) {
        return Arrays.stream(args).filter(value -> value.startsWith(prefix)).findFirst().orElseThrow()
                .substring(prefix.length());
    }
}
