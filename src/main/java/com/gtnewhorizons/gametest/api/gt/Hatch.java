package com.gtnewhorizons.gametest.api.gt;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * View of a hatch tile resolved from a controller. Fluid methods use the meta tile entity as {@link IFluidHandler},
 * same idea as {@link GTNHGameTestHelper#fillHatch}.
 */
@Experimental
public final class Hatch {

    private final IGregTechTileEntity te;
    private final String label;

    Hatch(IGregTechTileEntity te, String label) {
        this.te = te;
        this.label = label;
    }

    public Hatch fill(FluidStack fluid) {
        if (fluid == null) return this;
        IFluidHandler handler = fluidHandler();
        int filled = handler.fill(ForgeDirection.UNKNOWN, fluid, true);
        if (filled < fluid.amount) {
            throw new GameTestAssertException(
                "Could not fill " + fluid.amount
                    + " mB of '"
                    + fluid.getLocalizedName()
                    + "' into "
                    + label
                    + "; only "
                    + filled
                    + " mB accepted",
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
        return this;
    }

    public void assertContains(FluidStack fluid) {
        if (fluid == null) return;
        IFluidHandler handler = fluidHandler();
        FluidStack drained = handler.drain(ForgeDirection.UNKNOWN, fluid.copy(), false);
        if (drained == null || drained.getFluidID() != fluid.getFluidID() || drained.amount < fluid.amount) {
            String actual = drained != null ? drained.amount + " mB " + drained.getLocalizedName() : "<empty>";
            throw new GameTestAssertException(
                "Expected " + fluid.amount
                    + " mB of '"
                    + fluid.getLocalizedName()
                    + "' in "
                    + label
                    + " but found "
                    + actual,
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
    }

    public void assertEmpty() {
        IFluidHandler handler = fluidHandler();
        FluidStack drained = handler.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, false);
        if (drained != null && drained.amount > 0) {
            throw new GameTestAssertException(
                label + " is not empty; contains " + drained.amount + " mB of " + drained.getLocalizedName(),
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
    }

    private IFluidHandler fluidHandler() {
        IMetaTileEntity mte = te.getMetaTileEntity();
        if (mte == null) {
            throw new GameTestAssertException(
                label + " has no meta tile entity (cannot access fluid hatch)",
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
        return mte;
    }
}
