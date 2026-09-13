package io.github.fv2j3dteam.universe.fluid;

import java.util.Objects;

/** Fluid definition (§43). Drives data-driven fluid behaviour. */
public final class FluidDefinition {
    private final String id;
    private final double density; // kg/m^3
    private final double dynamicViscosity; // Pa*s
    private final double kinematicViscosity; // m^2/s
    private final double heatCapacity; // J/(kg*K)
    private final double meltingPointK;
    private final double boilingPointK;
    private final double thermalConductivity;
    private final int phaseAtRoomTemp; // 0=solid, 1=liquid, 2=gas
    private final boolean isGas;

    public FluidDefinition(String id,
                           double density,
                           double dynamicViscosity,
                           double kinematicViscosity,
                           double heatCapacity,
                           double meltingPointK,
                           double boilingPointK,
                           double thermalConductivity,
                           int phaseAtRoomTemp,
                           boolean isGas) {
        this.id = Objects.requireNonNull(id, "id");
        if (density < 0) throw new IllegalArgumentException("density>=0");
        if (dynamicViscosity < 0) throw new IllegalArgumentException("visc>=0");
        if (kinematicViscosity < 0) throw new IllegalArgumentException("kinVisc>=0");
        if (heatCapacity < 0) throw new IllegalArgumentException("cp>=0");
        if (meltingPointK < 0) throw new IllegalArgumentException("melt>=0");
        if (boilingPointK < meltingPointK) throw new IllegalArgumentException("boil>=melt");
        this.density = density;
        this.dynamicViscosity = dynamicViscosity;
        this.kinematicViscosity = kinematicViscosity;
        this.heatCapacity = heatCapacity;
        this.meltingPointK = meltingPointK;
        this.boilingPointK = boilingPointK;
        this.thermalConductivity = thermalConductivity;
        this.phaseAtRoomTemp = phaseAtRoomTemp;
        this.isGas = isGas;
    }

    public String id() { return id; }
    public double density() { return density; }
    public double dynamicViscosity() { return dynamicViscosity; }
    public double kinematicViscosity() { return kinematicViscosity; }
    public double heatCapacity() { return heatCapacity; }
    public double meltingPointK() { return meltingPointK; }
    public double boilingPointK() { return boilingPointK; }
    public double thermalConductivity() { return thermalConductivity; }
    public int phaseAtRoomTemp() { return phaseAtRoomTemp; }
    public boolean isGas() { return isGas; }

    public static FluidDefinition water() {
        return new FluidDefinition("water", 1000, 0.001, 1.0e-6, 4184, 273.15, 373.15, 0.6, 1, false);
    }
    public static FluidDefinition lava() {
        return new FluidDefinition("lava", 2700, 5.0, 1.85e-3, 1100, 973.15, 1773.15, 1.0, 1, false);
    }
    public static FluidDefinition oil() {
        return new FluidDefinition("oil", 850, 0.05, 5.88e-5, 2000, 250, 600, 0.15, 1, false);
    }
    public static FluidDefinition air() {
        return new FluidDefinition("air", 1.225, 1.8e-5, 1.5e-5, 1005, 0, 100, 0.025, 2, true);
    }
    public static FluidDefinition steam() {
        return new FluidDefinition("steam", 0.6, 1.2e-5, 2.0e-5, 2080, 273.15, 373.15, 0.025, 2, true);
    }
}
