package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record DiscoveryResult(List<GameTestDefinition> validTests, Map<String, List<Method>> beforeBatchMethods,
    Map<String, List<Method>> afterBatchMethods, List<InvalidTestDefinition> invalidTests,
    List<InvalidBatchHook> invalidHooks, List<DuplicateTestId> duplicateIds, List<DiscoveryIssue> issues) {

}
