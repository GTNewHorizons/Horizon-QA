package com.gtnewhorizons.horizonqa.internal;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record TestCell(String testId, int originX, int originY, int originZ, int minX, int minY, int minZ, int maxX,
    int maxY, int maxZ) {

}
