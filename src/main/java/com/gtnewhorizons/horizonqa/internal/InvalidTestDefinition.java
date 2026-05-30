package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record InvalidTestDefinition(String intendedTestId, Method method, List<DiscoveryIssue> issues) {

}
