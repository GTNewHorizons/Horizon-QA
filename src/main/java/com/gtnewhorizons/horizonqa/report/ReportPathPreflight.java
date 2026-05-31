package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ReportPathPreflight {

    private static final byte[] SENTINEL_BYTES = "horizonqa preflight\n".getBytes(StandardCharsets.UTF_8);

    private ReportPathPreflight() {}

    public static List<IssueResult> check(File junitReportFile, File statusReportFile) {
        List<IssueResult> issues = new ArrayList<>();
        Path junitTarget = normalize(junitReportFile);
        Path statusTarget = normalize(statusReportFile);
        Map<String, ParentTarget> parents = new TreeMap<>();

        createParentAndCollect("junit", junitTarget, issues, parents);
        createParentAndCollect("status", statusTarget, issues, parents);

        if (junitTarget != null && statusTarget != null && pathKey(junitTarget).equals(pathKey(statusTarget))) {
            issues.add(
                issue(
                    "reportPath:sameOutput",
                    "same-output",
                    junitTarget,
                    "JUnit XML and status JSON report paths resolve to the same file: " + junitTarget,
                    null));
        }

        rejectTargetDirectory("junit", junitTarget, issues);
        rejectTargetDirectory("status", statusTarget, issues);

        for (ParentTarget parent : parents.values()) {
            verifySentinel(parent, issues);
        }

        return Collections.unmodifiableList(new ArrayList<>(issues));
    }

    private static Path normalize(File file) {
        if (file == null) {
            return null;
        }
        return file.toPath()
            .toAbsolutePath()
            .normalize();
    }

    private static void createParentAndCollect(String label, Path target, List<IssueResult> issues,
        Map<String, ParentTarget> parents) {
        if (target == null) {
            issues.add(
                issue(
                    "reportPath:" + label + ":missing",
                    label,
                    null,
                    "Missing " + label + " report output path",
                    null));
            return;
        }

        Path parent = target.getParent();
        if (parent == null) {
            issues.add(
                issue(
                    "reportPath:" + label + ":missingParent",
                    label,
                    target,
                    "Report output path has no parent directory: " + target,
                    null));
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException | SecurityException e) {
            issues.add(
                issue(
                    "reportPath:" + label + ":parent",
                    label,
                    target,
                    "Cannot create parent directory for " + label + " report '" + target + "': " + e.getMessage(),
                    asException(e)));
            return;
        }

        String key = pathKey(parent);
        ParentTarget parentTarget = parents.computeIfAbsent(key, ignored -> new ParentTarget(parent));
        parentTarget.labels.add(label);
    }

    private static void rejectTargetDirectory(String label, Path target, List<IssueResult> issues) {
        if (target == null) {
            return;
        }
        try {
            if (Files.isDirectory(target)) {
                issues.add(
                    issue(
                        "reportPath:" + label + ":targetDirectory",
                        label,
                        target,
                        label + " report target is a directory, expected a file: " + target,
                        null));
            }
        } catch (SecurityException e) {
            issues.add(
                issue(
                    "reportPath:" + label + ":targetAccess",
                    label,
                    target,
                    "Cannot inspect " + label + " report target '" + target + "': " + e.getMessage(),
                    asException(e)));
        }
    }

    private static void verifySentinel(ParentTarget parent, List<IssueResult> issues) {
        Path sentinel = null;
        Exception sentinelError = null;
        try {
            sentinel = Files.createTempFile(parent.path, ".horizonqa-preflight-", ".tmp");
            Files.write(sentinel, SENTINEL_BYTES);
        } catch (IOException | SecurityException e) {
            sentinelError = asException(e);
            issues.add(
                issue(
                    parentIssueId(parent, "sentinel-write"),
                    parentIssueName(parent),
                    parent.path,
                    "Cannot write sentinel report file in '" + parent.path + "': " + e.getMessage(),
                    sentinelError));
        } finally {
            if (sentinel != null) {
                try {
                    if (!Files.deleteIfExists(sentinel) && sentinelError == null) {
                        issues.add(
                            issue(
                                parentIssueId(parent, "sentinel-delete"),
                                parentIssueName(parent),
                                sentinel,
                                "Cannot delete sentinel report file '" + sentinel + "'",
                                null));
                    }
                } catch (IOException | SecurityException e) {
                    issues.add(
                        issue(
                            parentIssueId(parent, "sentinel-delete"),
                            parentIssueName(parent),
                            sentinel,
                            "Cannot delete sentinel report file '" + sentinel + "': " + e.getMessage(),
                            asException(e)));
                }
            }
        }
    }

    private static IssueResult issue(String id, String name, Path target, String message, Exception error) {
        return IssueResult.reportPath(id, name, target == null ? "" : target.toString(), message, error);
    }

    private static String parentIssueId(ParentTarget parent, String suffix) {
        return "reportPath:parent:" + Integer.toHexString(pathKey(parent.path).hashCode()) + ":" + suffix;
    }

    private static String parentIssueName(ParentTarget parent) {
        return String.join("+", parent.labels);
    }

    private static String pathKey(Path path) {
        Path resolved = path.toAbsolutePath()
            .normalize();
        try {
            if (Files.exists(resolved)) {
                resolved = resolved.toRealPath();
            } else {
                Path parent = resolved.getParent();
                if (parent != null && Files.exists(parent)) {
                    resolved = parent.toRealPath()
                        .resolve(resolved.getFileName())
                        .normalize();
                }
            }
        } catch (IOException | SecurityException ignored) {}

        String value = resolved.toString();
        if (isWindows()) {
            return value.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }

    private static Exception asException(Exception e) {
        return e;
    }

    private static final class ParentTarget {

        final Path path;
        final Set<String> labels = new TreeSet<>();

        ParentTarget(Path path) {
            this.path = path;
        }
    }
}
