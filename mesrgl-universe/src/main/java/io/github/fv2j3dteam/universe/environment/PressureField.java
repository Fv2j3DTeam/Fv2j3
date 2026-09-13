package io.github.fv2j3dteam.universe.environment;

/**
 * Pressure field abstraction (§34). Tracks atmospheric pressure at a point,
 * including pressure gradients (used for flow). For fluid pressure, see
 * {@link io.github.fv2j3dteam.universe.fluid.FluidGrid}.
 */
public final class PressureField {

    public double sampleAtmospheric(double surfacePressurePa, double altitudeM) {
        if (surfacePressurePa <= 0) return 0.0;
        return surfacePressurePa * Math.exp(-altitudeM / 8500.0);
    }

    public double gradient(double pressureA, double pressureB, double distanceM) {
        if (distanceM <= 0) return 0.0;
        return (pressureB - pressureA) / distanceM;
    }
}
