package com.gtnewhorizons.gametest.api.gt.adapter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import net.minecraft.world.chunk.Chunk;

import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.state.HatchTopology;
import com.gtnewhorizons.gametest.api.event.state.MaintenanceSnapshot;
import com.gtnewhorizons.gametest.api.event.state.RecipeStateSnapshot;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** {@link GTAdapter} targeting GTNH GT5-Unofficial; resolves all reflective lookups at construction time. */
@Experimental
public final class GT5UnofficialAdapter implements GTAdapter {

    private static final String POLLUTION_CLASS = "gregtech.common.pollution.Pollution";

    private final Method pollutionMethod;

    public GT5UnofficialAdapter() {
        this.pollutionMethod = resolvePollutionMethod();
    }

    private static Method resolvePollutionMethod() {
        try {
            Class<?> cls = Class.forName(POLLUTION_CLASS);
            Method m = cls.getMethod("getPollution", Chunk.class);
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new GTVersionMismatchException(
                    POLLUTION_CLASS + "#getPollution(Chunk) must be static for GT5UnofficialAdapter",
                    null);
            }
            return m;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new GTVersionMismatchException(
                "Expected " + POLLUTION_CLASS + " with static getPollution(Chunk) for GTNH GT5u",
                e);
        }
    }

    private static MTEMultiBlockBase asMultiBlock(IMetaTileEntity mte) {
        if (mte instanceof MTEMultiBlockBase multi) return multi;
        throw new IllegalArgumentException(
            "Expected an MTEMultiBlockBase but got " + (mte == null ? "null"
                : mte.getClass()
                    .getName()));
    }

    @Override
    public long getPollution(Chunk chunk) {
        try {
            Object result = pollutionMethod.invoke(null, chunk);
            return ((Number) result).longValue();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Pollution.getPollution is not accessible", e);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException("Pollution.getPollution threw", c);
        }
    }

    @Override
    public boolean isStructureFormed(IMetaTileEntity mte) {
        return asMultiBlock(mte).mMachine;
    }

    @Override
    public boolean isActive(IMetaTileEntity mte) {
        return asMultiBlock(mte).mMaxProgresstime > 0;
    }

    @Override
    public int getProgressTime(IMetaTileEntity mte) {
        return asMultiBlock(mte).mProgresstime;
    }

    @Override
    public int getMaxProgressTime(IMetaTileEntity mte) {
        return asMultiBlock(mte).mMaxProgresstime;
    }

    @Override
    public long getEUt(IMetaTileEntity mte) {
        MTEMultiBlockBase multi = asMultiBlock(mte);
        return effectiveEUt(multi);
    }

    /**
     * Resolves the energy-per-tick for any multiblock controller. {@link MTEExtendedPowerMultiBlockBase} subclasses
     * override {@code setEnergyUsage} to write to a {@code long lEUt} field and leave {@code mEUt} at zero, so a plain
     * {@code mEUt} read returns 0 for the entire high-tier hierarchy (EBFs, fusion reactors, etc.). This method picks
     * the canonical field for each subclass.
     */
    private static long effectiveEUt(MTEMultiBlockBase multi) {
        if (multi instanceof MTEExtendedPowerMultiBlockBase<?>extended) {
            return extended.lEUt;
        }
        return multi.mEUt;
    }

    @Override
    public int getEfficiency(IMetaTileEntity mte) {
        return asMultiBlock(mte).mEfficiency;
    }

    @Override
    public int getRepairStatus(IMetaTileEntity mte) {
        return asMultiBlock(mte).getRepairStatus();
    }

    @Override
    public void fixAllMaintenanceIssues(IMetaTileEntity mte) {
        asMultiBlock(mte).fixAllIssues();
    }

    @Override
    public long getRecipesDone(IMetaTileEntity mte) {
        return asMultiBlock(mte).recipesDone;
    }

    @Override
    public int getLastParallel(IMetaTileEntity mte) {
        return asMultiBlock(mte).lastParallel;
    }

    @Override
    public String getCheckRecipeResultId(IMetaTileEntity mte) {
        return asMultiBlock(mte).getCheckRecipeResult()
            .getID();
    }

    @Override
    public RecipeStateSnapshot snapshotRecipeState(IMetaTileEntity mte) {
        MTEMultiBlockBase multi = asMultiBlock(mte);
        return new RecipeStateSnapshot(
            multi.mMachine,
            multi.mProgresstime,
            multi.mMaxProgresstime,
            effectiveEUt(multi),
            multi.mEfficiency,
            multi.getCheckRecipeResult()
                .getID());
    }

    @Override
    public MaintenanceSnapshot snapshotMaintenance(IMetaTileEntity mte) {
        MTEMultiBlockBase multi = asMultiBlock(mte);
        int mask = 0;
        if (!multi.mWrench) mask |= MaintenanceSnapshot.WRENCH;
        if (!multi.mScrewdriver) mask |= MaintenanceSnapshot.SCREWDRIVER;
        if (!multi.mSoftMallet) mask |= MaintenanceSnapshot.SOFT_MALLET;
        if (!multi.mHardHammer) mask |= MaintenanceSnapshot.HARD_HAMMER;
        if (!multi.mSolderingTool) mask |= MaintenanceSnapshot.SOLDERING_TOOL;
        if (!multi.mCrowbar) mask |= MaintenanceSnapshot.CROWBAR;
        return mask == 0 ? MaintenanceSnapshot.OK : new MaintenanceSnapshot(mask);
    }

    @Override
    public HatchTopology snapshotHatches(IMetaTileEntity mte) {
        MTEMultiBlockBase multi = asMultiBlock(mte);
        return new HatchTopology(
            sizeOf(multi.mInputBusses),
            sizeOf(multi.mOutputBusses),
            sizeOf(multi.mInputHatches),
            countOutputHatches(multi),
            sizeOf(multi.mEnergyHatches));
    }

    /**
     * Returns the N-th fluid output hatch tile entity. For multiblocks that store output hatches in a
     * non-standard per-layer field (the Distillation Tower uses {@code mOutputHatchesByLayer}), this
     * indexes layers and returns the first hatch in that layer. Falls back to the standard
     * {@code mOutputHatches} list for all other multiblocks.
     */
    @Override
    public IGregTechTileEntity getOutputHatchTE(IMetaTileEntity mte, int index) {
        MTEMultiBlockBase multi = asMultiBlock(mte);
        List<List<MTEHatchOutput>> byLayer = readOutputHatchesByLayer(multi);
        if (byLayer != null) {
            if (index < 0 || index >= byLayer.size()
                || byLayer.get(index)
                    .isEmpty())
                return null;
            return byLayer.get(index)
                .get(0)
                .getBaseMetaTileEntity();
        }
        if (index < 0 || index >= multi.mOutputHatches.size()) return null;
        MTEHatchOutput h = multi.mOutputHatches.get(index);
        return h != null ? h.getBaseMetaTileEntity() : null;
    }

    private static int countOutputHatches(MTEMultiBlockBase multi) {
        List<List<MTEHatchOutput>> byLayer = readOutputHatchesByLayer(multi);
        if (byLayer == null) return sizeOf(multi.mOutputHatches);
        int total = 0;
        for (List<MTEHatchOutput> layer : byLayer) total += sizeOf(layer);
        return total;
    }

    private static List<List<MTEHatchOutput>> readOutputHatchesByLayer(MTEMultiBlockBase multi) {
        Field f = findFieldInHierarchy(multi.getClass(), "mOutputHatchesByLayer");
        if (f == null) return null;
        try {
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<List<MTEHatchOutput>> byLayer = (List<List<MTEHatchOutput>>) f.get(multi);
            return byLayer;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                "Cannot access mOutputHatchesByLayer on " + multi.getClass()
                    .getSimpleName(),
                e);
        }
    }

    private static Field findFieldInHierarchy(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static int sizeOf(java.util.Collection<?> list) {
        return list == null ? 0 : list.size();
    }
}
