package io.github.fv2j3dteam.universe.life;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.math.XorShift64;
import io.github.fv2j3dteam.universe.seeds.SeedDerivation;

import java.util.Objects;

/**
 * Species definition (§14). Identity is stable and deterministic. Traits are
 * derived from seed; morphology/locomotion/diet/tolerances parameterised.
 */
public final class SpeciesDefinition {
    public enum Diet { HERBIVORE, CARNIVORE, OMNIVORE, AUTOTROPH, DETRITIVORE }
    public enum Locomotion { TERRESTRIAL, AQUATIC, AVIAN, BURROWING, ARBOREAL }
    private final String id;
    private final String displayName;
    private final double bodySizeM;
    private final Diet diet;
    private final Locomotion locomotion;
    private final double minTempK;
    private final double maxTempK;
    private final double minPressurePa;
    private final double minOxygenFraction;
    private final double maxCO2Fraction;
    private final String preferredBiome;

    public SpeciesDefinition(String id, String displayName, double bodySizeM,
                             Diet diet, Locomotion locomotion,
                             double minTempK, double maxTempK,
                             double minPressurePa, double minOxygenFraction,
                             double maxCO2Fraction, String preferredBiome) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName;
        this.bodySizeM = bodySizeM;
        this.diet = diet;
        this.locomotion = locomotion;
        this.minTempK = minTempK;
        this.maxTempK = maxTempK;
        this.minPressurePa = minPressurePa;
        this.minOxygenFraction = minOxygenFraction;
        this.maxCO2Fraction = maxCO2Fraction;
        this.preferredBiome = preferredBiome == null ? "" : preferredBiome;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public double bodySizeM() { return bodySizeM; }
    public Diet diet() { return diet; }
    public Locomotion locomotion() { return locomotion; }
    public double minTempK() { return minTempK; }
    public double maxTempK() { return maxTempK; }
    public double minPressurePa() { return minPressurePa; }
    public double minOxygenFraction() { return minOxygenFraction; }
    public double maxCO2Fraction() { return maxCO2Fraction; }
    public String preferredBiome() { return preferredBiome; }

    public boolean matchesEnvironment(double temperatureK, double pressurePa,
                                      double oxygenFraction, double co2Fraction,
                                      String biomeId) {
        if (temperatureK < minTempK || temperatureK > maxTempK) return false;
        if (pressurePa < minPressurePa) return false;
        if (oxygenFraction < minOxygenFraction) return false;
        if (co2Fraction > maxCO2Fraction) return false;
        if (!preferredBiome.isEmpty() && !preferredBiome.equals(biomeId)) return false;
        return true;
    }

    /** Procedurally generate a species at a planet+id. */
    public static SpeciesDefinition generate(UniverseId planetId, long seed) {
        XorShift64 rng = new XorShift64(seed);
        double body = rng.nextDouble(0.05, 5.0);
        Diet diet = switch (rng.nextInt(0, 5)) {
            case 0 -> Diet.HERBIVORE; case 1 -> Diet.CARNIVORE; case 2 -> Diet.OMNIVORE;
            case 3 -> Diet.AUTOTROPH; default -> Diet.DETRITIVORE;
        };
        Locomotion loco = switch (rng.nextInt(0, 5)) {
            case 0 -> Locomotion.TERRESTRIAL; case 1 -> Locomotion.AQUATIC; case 2 -> Locomotion.AVIAN;
            case 3 -> Locomotion.BURROWING; default -> Locomotion.ARBOREAL;
        };
        double minT = 260 + rng.nextDouble(0, 30);
        double maxT = minT + 20 + rng.nextDouble(0, 40);
        String id = "sp-" + Long.toHexString(planetId.stableHash() ^ seed);
        return new SpeciesDefinition(id, "species-" + id, body, diet, loco,
                minT, maxT, 1000, 0.05, 0.10, "");
    }
}
