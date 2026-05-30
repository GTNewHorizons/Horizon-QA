package com.gtnewhorizons.horizonqa.internal;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record DiscoveryIssue(String id, String kind, String message) {

}
