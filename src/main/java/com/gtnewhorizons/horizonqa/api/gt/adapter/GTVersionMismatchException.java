package com.gtnewhorizons.horizonqa.api.gt.adapter;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/** Thrown when the loaded GregTech jar does not match the expectations of a {@link GTAdapter} implementation. */
@Experimental
public class GTVersionMismatchException extends RuntimeException {

    public GTVersionMismatchException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
