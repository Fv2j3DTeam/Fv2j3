package com.fv2j3.universe.smoke;

import java.util.Objects;

/** Gas definition (§47, §48). Extensible. */
public final class GasDefinition {
    private final String id;
    private final double density; // kg/m^3
    private final double dynamicViscosity;
    private final double heatCapacity;
    private final double thermalConductivity;
    private final double extinction; // optical extinction 0..1 per metre
    private final double buoyancyPerTempDiff; // upward acceleration per kelvin above ambient
    private final double diffusivity; // m^2/s

    public GasDefinition(String id,
                          double density,
                          double dynamicViscosity,
                          double heatCapacity,
                          double thermalConductivity,
                          double extinction,
                          double buoyancyPerTempDiff,
                          double diffusivity) {
        this.id = Objects.requireNonNull(id, "id");
        if (density < 0) throw new IllegalArgumentException("density>=0");
        if (dynamicViscosity < 0) throw new IllegalArgumentException("visc>=0");
        if (heatCapacity < 0) throw new IllegalArgumentException("cp>=0");
        if (thermalConductivity < 0) throw new IllegalArgumentException("k>=0");
        if (extinction < 0) throw new IllegalArgumentException("extinction>=0");
        if (buoyancyPerTempDiff < 0) throw new IllegalArgumentException("buoyancy>=0");
        if (diffusivity < 0) throw new IllegalArgumentException("diffusivity>=0");
        this.density = density;
        this.dynamicViscosity = dynamicViscosity;
        this.heatCapacity = heatCapacity;
        this.thermalConductivity = thermalConductivity;
        this.extinction = extinction;
        this.buoyancyPerTempDiff = buoyancyPerTempDiff;
        this.diffusivity = diffusivity;
    }

    public String id() { return id; }
    public double density() { return density; }
    public double dynamicViscosity() { return dynamicViscosity; }
    public double heatCapacity() { return heatCapacity; }
    public double thermalConductivity() { return thermalConductivity; }
    public double extinction() { return extinction; }
    public double buoyancyPerTempDiff() { return buoyancyPerTempDiff; }
    public double diffusivity() { return diffusivity; }

    public static GasDefinition smoke() {
        return new GasDefinition("smoke", 1.2, 1.5e-5, 1100, 0.05, 0.5, 0.5, 2.0e-5);
    }
    public static GasDefinition steam() {
        return new GasDefinition("steam", 0.6, 1.2e-5, 2080, 0.025, 0.2, 0.6, 2.5e-5);
    }
    public static GasDefinition volcanicGas() {
        return new GasDefinition("volcanic_gas", 1.8, 1.7e-5, 1100, 0.04, 0.6, 0.7, 1.5e-5);
    }
    public static GasDefinition air() {
        return new GasDefinition("air", 1.225, 1.8e-5, 1005, 0.025, 0.0, 0.0, 1.5e-5);
    }
}
