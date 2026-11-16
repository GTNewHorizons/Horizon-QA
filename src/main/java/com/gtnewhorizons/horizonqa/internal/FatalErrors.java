package com.gtnewhorizons.horizonqa.internal;

public final class FatalErrors {

    private FatalErrors() {}

    public static boolean isFatal(Throwable error) {
        return error instanceof VirtualMachineError || error instanceof ThreadDeath || error instanceof LinkageError;
    }

    public static void rethrow(Throwable error) {
        if (isFatal(error)) throw (Error) error;
    }
}
