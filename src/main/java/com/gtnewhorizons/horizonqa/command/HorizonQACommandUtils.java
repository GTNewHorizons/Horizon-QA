package com.gtnewhorizons.horizonqa.command;

import java.util.Collection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.horizonqa.internal.TestCell;

public final class HorizonQACommandUtils {

    private static final double RAY_LENGTH = 64.0;

    private HorizonQACommandUtils() {}

    public static TestCell findTestContaining(int x, int y, int z, Collection<TestCell> cells) {
        for (TestCell cell : cells) {
            if (x >= cell.minX() && x <= cell.maxX()
                && y >= cell.minY()
                && y <= cell.maxY()
                && z >= cell.minZ()
                && z <= cell.maxZ()) {
                return cell;
            }
        }
        return null;
    }

    public static TestCell findTestById(String testId, Collection<TestCell> cells) {
        for (TestCell cell : cells) {
            if (cell.testId()
                .equals(testId)) {
                return cell;
            }
        }
        return null;
    }

    public static TestCell findTestAlongLook(EntityPlayer player, Collection<TestCell> cells) {
        double ox = player.posX;
        double oy = player.posY + player.eyeHeight;
        double oz = player.posZ;
        Vec3 look = player.getLookVec();
        double fx = ox + look.xCoord * RAY_LENGTH;
        double fy = oy + look.yCoord * RAY_LENGTH;
        double fz = oz + look.zCoord * RAY_LENGTH;

        for (TestCell cell : cells) {
            if (rayIntersectsAABB(ox, oy, oz, fx, fy, fz, cell)) {
                return cell;
            }
        }
        return null;
    }

    public static TestCell findNearestTest(int x, int y, int z, Collection<TestCell> cells) {
        TestCell nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (TestCell cell : cells) {
            double cx = (cell.minX() + cell.maxX()) * 0.5;
            double cy = (cell.minY() + cell.maxY()) * 0.5;
            double cz = (cell.minZ() + cell.maxZ()) * 0.5;
            double dx = x - cx, dy = y - cy, dz = z - cz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = cell;
            }
        }
        return nearest;
    }

    private static boolean rayIntersectsAABB(double ox, double oy, double oz, double fx, double fy, double fz,
        TestCell cell) {
        double dx = fx - ox, dy = fy - oy, dz = fz - oz;
        double tmin = 0.0, tmax = 1.0;

        if (Math.abs(dx) < 1e-9) {
            if (ox < cell.minX() || ox > cell.maxX() + 1.0) return false;
        } else {
            double t1 = (cell.minX() - ox) / dx;
            double t2 = (cell.maxX() + 1.0 - ox) / dx;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return false;
        }

        if (Math.abs(dy) < 1e-9) {
            if (oy < cell.minY() || oy > cell.maxY() + 1.0) return false;
        } else {
            double t1 = (cell.minY() - oy) / dy;
            double t2 = (cell.maxY() + 1.0 - oy) / dy;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return false;
        }

        if (Math.abs(dz) < 1e-9) {
            if (oz < cell.minZ() || oz > cell.maxZ() + 1.0) return false;
        } else {
            double t1 = (cell.minZ() - oz) / dz;
            double t2 = (cell.maxZ() + 1.0 - oz) / dz;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return false;
        }

        return true;
    }
}
