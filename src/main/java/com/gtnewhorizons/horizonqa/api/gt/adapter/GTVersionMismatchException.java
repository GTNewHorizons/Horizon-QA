package com.gtnewhorizons.horizonqa.api.gt.adapter;

/** Thrown when the loaded GregTech jar does not match the expectations of a {@link GTAdapter} implementation. */
public class GTVersionMismatchException extends RuntimeException {

    public GTVersionMismatchException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
