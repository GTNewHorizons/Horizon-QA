package com.gtnewhorizons.horizonqa.api;

public class LabelResolutionException extends GameTestInfrastructureException {

    public static final String ERROR_KIND = "LABEL_ERROR";

    public LabelResolutionException(String message) {
        super(ERROR_KIND, message);
    }
}
