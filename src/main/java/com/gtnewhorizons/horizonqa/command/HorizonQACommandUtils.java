package com.gtnewhorizons.horizonqa.command;

import java.util.Collection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

public final class HorizonQACommandUtils {

    private static final double RAY_LENGTH = 64.0;

    private HorizonQACommandUtils() {}

    public static CellRecord findTestContaining(int x, int y, int z, Collection<CellRecord> cells) {
        for (CellRecord cell : cells) {
            if (x >= cell.minX && x <= cell.maxX
                && y >= cell.minY
                && y <= cell.maxY
                && z >= cell.minZ
                && z <= cell.maxZ) {
                return cell;
            }
        }
        return null;
    }

    public static CellRecord findTestById(String testId, Collection<CellRecord> cells) {
        for (CellRecord cell : cells) {
            if (cell.testId.equals(testId)) {
                return cell;
            }
        }
        return null;
    }

    public static CellRecord findTestAlongLook(EntityPlayer player, Collection<CellRecord> cells) {
        double ox = player.posX;
        double oy = player.posY + player.eyeHeight;
        double oz = player.posZ;
        Vec3 look = player.getLookVec();
        double fx = ox + look.xCoord * RAY_LENGTH;
        double fy = oy + look.yCoord * RAY_LENGTH;
        double fz = oz + look.zCoord * RAY_LENGTH;

        for (CellRecord cell : cells) {
            if (rayIntersectsAABB(ox, oy, oz, fx, fy, fz, cell)) {
                return cell;
            }
        }
        return null;
    }

    public static CellRecord findNearestTest(int x, int y, int z, Collection<CellRecord> cells) {
        CellRecord nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (CellRecord cell : cells) {
            double cx = (cell.minX + cell.maxX) * 0.5;
            double cy = (cell.minY + cell.maxY) * 0.5;
            double cz = (cell.minZ + cell.maxZ) * 0.5;
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
        CellRecord cell) {
        double dx = fx - ox, dy = fy - oy, dz = fz - oz;
        double tmin = 0.0, tmax = 1.0;

        if (Math.abs(dx) < 1e-9) {
            if (ox < cell.minX || ox > cell.maxX + 1.0) return false;
        } else {
            double t1 = (cell.minX - ox) / dx;
            double t2 = (cell.maxX + 1.0 - ox) / dx;
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
            if (oy < cell.minY || oy > cell.maxY + 1.0) return false;
        } else {
            double t1 = (cell.minY - oy) / dy;
            double t2 = (cell.maxY + 1.0 - oy) / dy;
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
            if (oz < cell.minZ || oz > cell.maxZ + 1.0) return false;
        } else {
            double t1 = (cell.minZ - oz) / dz;
            double t2 = (cell.maxZ + 1.0 - oz) / dz;
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

    public static final class CellRecord {

        public final String testId;

        public final int originX, originY, originZ;

        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public CellRecord(String testId, int originX, int originY, int originZ, int minX, int minY, int minZ, int maxX,
            int maxY, int maxZ) {
            this.testId = testId;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
