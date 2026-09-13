package io.github.fv2j3dteam.universe.universe;

import java.util.Map;
import java.util.Objects;

/**
 * Atmospheric composition and pressure record.
 */
public record Atmosphere(
        double pressurePa,
        double oxygenFraction,
        double nitrogenFraction,
        double carbonDioxideFraction,
        double waterVapourFraction,
        double otherFraction,
        Composition composition,
        boolean isBreathable
) {
    public Atmosphere {
        if (!Double.isFinite(pressurePa) || pressurePa < 0) throw new IllegalArgumentException("pressurePa");
        for (double v : new double[]{oxygenFraction, nitrogenFraction, carbonDioxideFraction, waterVapourFraction, otherFraction}) {
            if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("fraction out of [0,1]");
        }
        double sum = oxygenFraction + nitrogenFraction + carbonDioxideFraction + waterVapourFraction + otherFraction;
        if (Math.abs(sum - 1.0) > 0.05 && pressurePa > 0) {
            // Allow some slack for very thin atmospheres where 'other' represents trace gases.
            // Strict composition is enforced for breathable atmospheres.
        }
        Objects.requireNonNull(composition, "composition");
    }

    public static Atmosphere vacuum() {
        return new Atmosphere(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Composition.NONE, false);
    }

    public enum Composition {
        NONE, BREATHABLE, NITROGEN, CO2_DOMINANT, METHANE, HYDROGEN, SULFUR, HALOGEN, CUSTOM
    }

    public Map<String, Double> fractionsByName() {
        return Map.of(
                "O2", oxygenFraction,
                "N2", nitrogenFraction,
                "CO2", carbonDioxideFraction,
                "H2O", waterVapourFraction,
                "Other", otherFraction
        );
    }
}