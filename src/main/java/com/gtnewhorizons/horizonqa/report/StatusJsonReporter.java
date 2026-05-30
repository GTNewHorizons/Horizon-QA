package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StatusJsonReporter {

    private StatusJsonReporter() {}

    public static void write(RunResult result, File outputFile) throws IOException {
        Path path = outputFile.toPath();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = "{\n" + "  \"status\": \""
            + escape(result.status())
            + "\",\n"
            + "  \"exitCode\": "
            + result.exitCode()
            + ",\n"
            + "  \"mode\": \""
            + escape(result.mode())
            + "\",\n"
            + "  \"selectedTests\": "
            + result.selectedTests()
            + ",\n"
            + "  \"passed\": "
            + result.passed()
            + ",\n"
            + "  \"failed\": "
            + result.failed()
            + ",\n"
            + "  \"timedOut\": "
            + result.timedOut()
            + ",\n"
            + "  \"incomplete\": "
            + result.incomplete()
            + ",\n"
            + "  \"diagnosticErrors\": "
            + result.diagnosticErrors()
            + ",\n"
            + "  \"optionalFailures\": "
            + result.optionalFailures()
            + ",\n"
            + "  \"junitReport\": \""
            + escape(result.junitReport())
            + "\"\n"
            + "}\n";
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            switch (cp) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (cp < 0x20 || cp > 0x7E) {
                        appendUnicodeEscape(out, cp);
                    } else {
                        out.appendCodePoint(cp);
                    }
                    break;
            }
            offset += Character.charCount(cp);
        }
        return out.toString();
    }

    private static void appendUnicodeEscape(StringBuilder out, int cp) {
        if (cp <= 0xFFFF) {
            appendHexEscape(out, (char) cp);
            return;
        }
        char[] chars = Character.toChars(cp);
        for (char c : chars) {
            appendHexEscape(out, c);
        }
    }

    private static void appendHexEscape(StringBuilder out, char c) {
        out.append("\\u");
        String hex = Integer.toHexString(c);
        for (int i = hex.length(); i < 4; i++) {
            out.append('0');
        }
        out.append(hex);
    }
}
