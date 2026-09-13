package io.github.fv2j3dteam.universe.materials;

import java.util.Objects;

/**
 * Material/environmental property contract (§48). Externally extensible.
 *
 * Properties are documented; values are validated to reject nonphysical inputs.
 * Defaults are explicit and documented per property.
 */
public final class MaterialDefinition {

    private final String id;
    private final double density; // kg/m^3
    private final double friction; // 0..1
    private final double permeability; // 0..1
    private final double thermalConductivity; // W/(m*K)
    private final double heatCapacity; // J/(kg*K)
    private final double meltingPointK;
    private final double boilingPointK;
    private final double viscosity; // Pa*s (NaN if not applicable)
    private final double surfaceRoughness; // 0..1
    private final double porosity; // 0..1
    private final double flammability; // 0..1
    private final boolean isFluid;

    public MaterialDefinition(String id,
                              double density, double friction, double permeability,
                              double thermalConductivity, double heatCapacity,
                              double meltingPointK, double boilingPointK,
                              double viscosity, double surfaceRoughness,
                              double porosity, double flammability, boolean isFluid) {
        this.id = Objects.requireNonNull(id, "id");
        if (density < 0) throw new IllegalArgumentException("density must be >= 0");
        if (friction < 0 || friction > 1) throw new IllegalArgumentException("friction in [0,1]");
        if (permeability < 0 || permeability > 1) throw new IllegalArgumentException("permeability in [0,1]");
        if (thermalConductivity < 0) throw new IllegalArgumentException("k>=0");
        if (heatCapacity < 0) throw new IllegalArgumentException("cp>=0");
        if (meltingPointK < 0) throw new IllegalArgumentException("melt>=0");
        if (boilingPointK < meltingPointK) throw new IllegalArgumentException("boil>=melt");
        if (viscosity < 0) throw new IllegalArgumentException("viscosity>=0");
        if (surfaceRoughness < 0 || surfaceRoughness > 1) throw new IllegalArgumentException("roughness in [0,1]");
        if (porosity < 0 || porosity > 1) throw new IllegalArgumentException("porosity in [0,1]");
        if (flammability < 0 || flammability > 1) throw new IllegalArgumentException("flammability in [0,1]");
        this.density = density;
        this.friction = friction;
        this.permeability = permeability;
        this.thermalConductivity = thermalConductivity;
        this.heatCapacity = heatCapacity;
        this.meltingPointK = meltingPointK;
        this.boilingPointK = boilingPointK;
        this.viscosity = viscosity;
        this.surfaceRoughness = surfaceRoughness;
        this.porosity = porosity;
        this.flammability = flammability;
        this.isFluid = isFluid;
    }

    public String id() { return id; }
    public double density() { return density; }
    public double friction() { return friction; }
    public double permeability() { return permeability; }
    public double thermalConductivity() { return thermalConductivity; }
    public double heatCapacity() { return heatCapacity; }
    public double meltingPointK() { return meltingPointK; }
    public double boilingPointK() { return boilingPointK; }
    public double viscosity() { return viscosity; }
    public double surfaceRoughness() { return surfaceRoughness; }
    public double porosity() { return porosity; }
    public double flammability() { return flammability; }
    public boolean isFluid() { return isFluid; }

    public static MaterialDefinition stone() {
        return new MaterialDefinition("stone", 2700, 0.7, 0.0, 2.0, 800, 1473, 2873, Double.NaN, 0.9, 0.05, 0.0, false);
    }
    public static MaterialDefinition dirt() {
        return new MaterialDefinition("dirt", 1500, 0.6, 0.4, 1.0, 900, 1500, 2873, Double.NaN, 0.7, 0.4, 0.1, false);
    }
    public static MaterialDefinition wood() {
        return new MaterialDefinition("wood", 700, 0.5, 0.1, 0.15, 1700, 500, 873, Double.NaN, 0.7, 0.5, 0.7, false);
    }
    public static MaterialDefinition leaves() {
        return new MaterialDefinition("leaves", 200, 0.4, 0.3, 0.1, 1500, 273, 573, Double.NaN, 0.6, 0.6, 0.9, false);
    }
    public static MaterialDefinition water() {
        return new MaterialDefinition("water", 1000, 0.3, 0.0, 0.6, 4184, 273.15, 373.15, 0.001, 0.0, 0.0, 0.0, true);
    }
    public static MaterialDefinition lava() {
        return new MaterialDefinition("lava", 2700, 0.5, 0.0, 1.0, 1100, 973.15, 1773.15, 5.0, 0.7, 0.0, 0.0, true);
    }
    public static MaterialDefinition air() {
        return new MaterialDefinition("air", 1.225, 0.0, 1.0, 0.025, 1005, 0, 100, 0.000018, 0.0, 1.0, 0.0, false);
    }
    public static MaterialDefinition snow() {
        return new MaterialDefinition("snow", 100, 0.3, 0.6, 0.1, 2090, 273.15, 373.15, Double.NaN, 0.8, 0.7, 0.0, false);
    }
    public static MaterialDefinition ice() {
        return new MaterialDefinition("ice", 917, 0.3, 0.05, 2.18, 2090, 273.15, 373.15, Double.NaN, 0.3, 0.05, 0.0, false);
    }
    public static MaterialDefinition steam() {
        return new MaterialDefinition("steam", 0.6, 0.0, 1.0, 0.025, 2080, 273.15, 373.15, 0.000012, 0.0, 1.0, 0.0, true);
    }
}
