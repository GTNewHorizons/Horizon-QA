package com.gtnewhorizons.gametest.internal;

import java.lang.reflect.Method;

public final class GameTestDefinition {

    private final String testId;
    private final Method method;
    private final String templateName;
    private final int timeoutTicks;
    private final String batch;
    private final boolean required;
    private final int rotation;

    public GameTestDefinition(String testId, Method method, String templateName, int timeoutTicks, String batch,
        boolean required, int rotation) {
        this.testId = testId;
        this.method = method;
        this.templateName = templateName;
        this.timeoutTicks = timeoutTicks;
        this.batch = batch;
        this.required = required;
        this.rotation = rotation;
    }

    public String getTestId() {
        return testId;
    }

    public Method getMethod() {
        return method;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getTimeoutTicks() {
        return timeoutTicks;
    }

    public String getBatch() {
        return batch;
    }

    public boolean isRequired() {
        return required;
    }

    public int getRotation() {
        return rotation;
    }

    @Override
    public String toString() {
        return testId;
    }
}
