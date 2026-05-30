package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record DuplicateTestId(String testId, List<Method> methods) {

}
