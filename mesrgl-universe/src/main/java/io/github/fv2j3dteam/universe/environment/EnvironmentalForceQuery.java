package io.github.fv2j3dteam.universe.environment;

import io.github.fv2j3dteam.universe.fluid.FluidDefinition;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.units.Units;

/**
 * Environmental force query API (§41, §73). Allows physics objects to query
 * forces (wind, drag, buoyancy) at a point. Caches last known values; queries
 * are non-blocking and may return last-known values for unloaded chunks.
 */
public final class EnvironmentalForceQuery {

    public static final class Forces {
        public final double windX, windY, windZ;
        public final double temperatureK;
        public final double pressurePa;
        public final double humidity; // 0..1
        public final double fluidVx, fluidVy, fluidVz;
        public final double fluidDensity;
        public Forces(double wx, double wy, double wz, double t, double p, double h,
                      double fvx, double fvy, double fvz, double fd) {
            this.windX = wx; this.windY = wy; this.windZ = wz;
            this.temperatureK = t; this.pressurePa = p; this.humidity = h;
            this.fluidVx = fvx; this.fluidVy = fvy; this.fluidVz = fvz;
            this.fluidDensity = fd;
        }
        public static final Forces ZERO = new Forces(0, 0, 0, 293.15, 101325, 0, 0, 0, 0, 0);
    }

    public double buoyancyForce(FluidDefinition fluid, double objectVolumeM3) {
        // F = (rho_fluid - rho_object) * g * V  (object density derived from material; here we use 0 buoyancy for non-fluids)
        if (fluid == null) return 0;
        return Units.clamp(fluid.density() * 9.81 * objectVolumeM3, -1.0e9, 1.0e9, "buoyancy");
    }

    public double dragForce(FluidDefinition fluid, double speedMs, double areaM2, double dragCoeff) {
        if (fluid == null || speedMs == 0) return 0;
        double f = 0.5 * fluid.density() * dragCoeff * areaM2 * speedMs * Math.abs(speedMs);
        return Units.clamp(f, -1.0e9, 1.0e9, "drag");
    }

    public Forces query(ChunkCoord coord, double x, double y, double z) {
        // Default stub; wired to live systems by an integration layer.
        return Forces.ZERO;
    }
}
