package com.gtnewhorizons.horizonqa.structure;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnewhorizons.horizonqa.api.TestPos;

public final class StructureAnnotations {

    public static final StructureAnnotations EMPTY = new StructureAnnotations(Collections.emptyMap());

    public static final String ANNOTATIONS_KEY = "annotations";
    public static final String LABELS_KEY = "labels";

    private static final Pattern LABEL_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Map<String, TestPos> labels;

    public StructureAnnotations(Map<String, TestPos> labels) {
        if (labels == null || labels.isEmpty()) {
            this.labels = Collections.emptyMap();
        } else {
            this.labels = Collections.unmodifiableMap(new TreeMap<>(labels));
        }
    }

    public static StructureAnnotations fromJson(JsonObject root, String templateName, int sizeX, int sizeY, int sizeZ)
        throws TemplateException {
        JsonElement annotationsElement = root.get(ANNOTATIONS_KEY);
        if (annotationsElement == null) {
            return EMPTY;
        }
        if (!annotationsElement.isJsonObject()) {
            throw malformed(templateName, "'annotations' must be an object");
        }

        JsonObject annotationsObject = annotationsElement.getAsJsonObject();
        JsonElement labelsElement = annotationsObject.get(LABELS_KEY);
        if (labelsElement == null) {
            return EMPTY;
        }
        if (!labelsElement.isJsonObject()) {
            throw malformed(templateName, "'annotations.labels' must be an object");
        }

        TreeMap<String, TestPos> labels = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : labelsElement.getAsJsonObject()
            .entrySet()) {
            String label = entry.getKey();
            if (!isValidLabelName(label)) {
                throw malformed(
                    templateName,
                    "label '" + label + "' must match " + LABEL_NAME.pattern());
            }
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonArray()) {
                throw malformed(templateName, "label '" + label + "' must be a [x, y, z] array");
            }
            JsonArray coords = value.getAsJsonArray();
            if (coords.size() != 3) {
                throw malformed(templateName, "label '" + label + "' must contain exactly three coordinates");
            }
            int x = requiredInteger(coords.get(0), templateName, label, "x");
            int y = requiredInteger(coords.get(1), templateName, label, "y");
            int z = requiredInteger(coords.get(2), templateName, label, "z");
            if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                throw malformed(
                    templateName,
                    "label '" + label
                        + "' points outside template bounds: ["
                        + x
                        + ", "
                        + y
                        + ", "
                        + z
                        + "] not within size ["
                        + sizeX
                        + ", "
                        + sizeY
                        + ", "
                        + sizeZ
                        + "]");
            }
            labels.put(label, new TestPos(x, y, z));
        }
        return labels.isEmpty() ? EMPTY : new StructureAnnotations(labels);
    }

    public static boolean isValidLabelName(String label) {
        return label != null && LABEL_NAME.matcher(label)
            .matches();
    }

    public Map<String, TestPos> labels() {
        return labels;
    }

    public TestPos get(String label) {
        return labels.get(label);
    }

    public boolean isEmpty() {
        return labels.isEmpty();
    }

    public int size() {
        return labels.size();
    }

    public String availableLabels() {
        if (labels.isEmpty()) {
            return "<none>";
        }
        return String.join(", ", labels.keySet());
    }

    private static int requiredInteger(JsonElement element, String templateName, String label, String axis)
        throws TemplateException {
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive()
                .isNumber()) {
            throw malformed(templateName, "label '" + label + "' coordinate " + axis + " must be an integer");
        }
        String raw = element.getAsString();
        if (!raw.matches("-?\\d+")) {
            throw malformed(templateName, "label '" + label + "' coordinate " + axis + " must be an integer");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw malformed(templateName, "label '" + label + "' coordinate " + axis + " is out of integer range");
        }
    }

    private static TemplateException malformed(String templateName, String message) {
        return new TemplateException("Malformed template '" + templateName + "': " + message);
    }
}
