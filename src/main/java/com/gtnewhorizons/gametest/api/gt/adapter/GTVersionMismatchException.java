package com.gtnewhorizons.gametest.api.gt.adapter;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

/** Thrown when the loaded GregTech jar does not match the expectations of a {@link GTAdapter} implementation. */
@Experimental
public class GTVersionMismatchException extends RuntimeException {

    public GTVersionMismatchException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
