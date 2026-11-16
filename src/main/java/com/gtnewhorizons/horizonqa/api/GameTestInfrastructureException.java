package com.gtnewhorizons.horizonqa.api;

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
