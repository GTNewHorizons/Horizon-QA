package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record StructurePlaced(int tick, String templateName, TestPos originAbs, int sizeX, int sizeY, int sizeZ)
    implements TestEvent {

    @Override
    public String category() {
        return Category.LIFECYCLE;
    }

    @Override
    public String summary() {
        return "Placed template '" + templateName
            + "' at "
            + originAbs
            + " ("
            + sizeX
            + "×"
            + sizeY
            + "×"
            + sizeZ
            + ")";
    }
}
