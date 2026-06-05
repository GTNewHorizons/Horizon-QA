package com.gtnewhorizons.horizonqa.structure;

import java.io.IOException;

public final class TemplateException extends IOException {

    public TemplateException(String message) {
        super(message);
    }

    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
