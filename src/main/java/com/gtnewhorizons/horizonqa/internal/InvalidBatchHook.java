package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record InvalidBatchHook(HookPhase phase, String batch, Method method, List<DiscoveryIssue> issues) {

    public InvalidBatchHook {
        issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public enum HookPhase {
        BEFORE,
        AFTER
    }
}
