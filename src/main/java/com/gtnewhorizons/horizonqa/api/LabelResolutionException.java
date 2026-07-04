package com.gtnewhorizons.horizonqa.api;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
public class LabelResolutionException extends GameTestInfrastructureException {

    public static final String KIND = "LABEL_ERROR";

    public LabelResolutionException(String message) {
        super(KIND, message);
    }
}
