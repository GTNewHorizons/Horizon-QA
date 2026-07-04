package com.gtnewhorizons.horizonqa.api;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
public class GameTestInfrastructureException extends RuntimeException {

    private final String kind;

    public GameTestInfrastructureException(String kind, String message) {
        super(message);
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }
}
