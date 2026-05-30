package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record InvalidBatchHook(HookPhase phase, String batch, Method method, List<DiscoveryIssue> issues) {

    public enum HookPhase {
        BEFORE,
        AFTER
    }
}
