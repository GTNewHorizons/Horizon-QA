package com.gtnewhorizons.gametest.api.gt.adapter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.world.chunk.Chunk;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** {@link GTAdapter} targeting GTNH GT5-Unofficial; resolves all reflective lookups at construction time. */
@Experimental
public final class GT5UnofficialAdapter implements GTAdapter {

    private static final String POLLUTION_CLASS = "gregtech.common.pollution.Pollution";

    private final Method pollutionMethod;
    private final Field efficiencyField;

    public GT5UnofficialAdapter() {
        this.pollutionMethod = resolvePollutionMethod();
        this.efficiencyField = resolveEfficiencyField();
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

    private static Field resolveEfficiencyField() {
        Class<?> probe = MTEMultiBlockBase.class;
        while (probe != null && probe != Object.class) {
            try {
                Field f = probe.getDeclaredField("mEfficiency");
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                probe = probe.getSuperclass();
            }
        }
        throw new GTVersionMismatchException(
            "mEfficiency field not found on " + MTEMultiBlockBase.class.getName() + " or its superclasses",
            null);
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
    public int getEfficiency(IMetaTileEntity mte) {
        try {
            return efficiencyField.getInt(mte);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                "Cannot read mEfficiency on " + mte.getClass()
                    .getName(),
                e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "mEfficiency field is not compatible with " + mte.getClass()
                    .getName(),
                e);
        }
    }
}
