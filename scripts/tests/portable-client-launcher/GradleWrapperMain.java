package org.gradle.wrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** A fake target wrapper exercises the supervisor's real process boundary without Minecraft. */
public class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && (args[0].equals("child") || args[0].equals("short-child"))) {
            Thread.sleep(args[0].equals("short-child") ? 900 : 120000);
            return;
        }
        String mode = option(args, "--mcJvmArgs=-Dhorizonqa.tests=");
        Path report = Path.of(option(args, "-PhorizonQaClientReportDir="));
        if (mode.equals("overlap")) {
            Files.writeString(report.resolve("preparation-entered"), "preparing");
            while (!Files.exists(report.resolve("allow-snapshot"))) Thread.sleep(50);
            Files.writeString(report.resolve("client-classpath-ready"), "ready");
            while (!Files.exists(report.resolve("release-client"))) Thread.sleep(50);
            Files.writeString(report.resolve("horizonqa-result.json"), result(0));
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
                Files.writeString(report.resolve("horizonqa-result.json"), result(0));
                Thread.sleep(500);
            }
        } else if (!mode.equals("missing")) {
            Files.writeString(report.resolve("horizonqa-result.json"), mode.equals("malformed") ? "{"
                    : mode.equals("truncated") ? "{\"exitCode\":0}" : result(mode.equals("failure") ? 1 : 0));
        }
        if (mode.equals("worker-failure")) System.exit(1);
    }

    private static String result(int exitCode) {
        return "{\"schemaVersion\":5,\"status\":\"" + (exitCode == 0 ? "passed" : "failed")
                + "\",\"exitCode\":" + exitCode + ",\"tests\":[],\"issues\":[]}";
    }

    private static String option(String[] args, String prefix) {
        return Arrays.stream(args).filter(value -> value.startsWith(prefix)).findFirst().orElseThrow()
                .substring(prefix.length());
    }
}
