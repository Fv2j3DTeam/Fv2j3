package io.github.fv2j3dteam.universe.environment;

import io.github.fv2j3dteam.universe.units.Units;

import java.util.Objects;

/**
 * Temperature field abstraction (§33). Combines ambient climate, altitude lapse,
 * local heat sources, and conduction. Samples are finite, with NaN/inf rejected.
 */
public final class TemperatureField {

    public double sample(double ambientK, double altitudeM, double localHeatOffsetK) {
        Objects.requireNonNull(ambientK >= 0 ? this : null, "ambient must be finite");
        if (!Double.isFinite(ambientK) || ambientK < Units.ABSOLUTE_ZERO_K) {
            return Units.ABSOLUTE_ZERO_K;
        }
        double lapse = Math.exp(-altitudeM / 8500.0);
        return Units.clamp(ambientK * lapse + localHeatOffsetK, 0.0, 100000.0, "temperature");
    }

    public double conductionStep(double temperatureK, double neighbourK, double conductivity, double dt, double cellMassKg) {
        if (!Double.isFinite(temperatureK) || !Double.isFinite(neighbourK)) return temperatureK;
        if (conductivity < 0 || dt < 0 || cellMassKg <= 0) return temperatureK;
        // Q = k * A * dT / dx. Here we use a unit-area unit-thickness simplification.
        double heatFlow = conductivity * (neighbourK - temperatureK);
        double dT = heatFlow * dt / (cellMassKg * 1000.0);
        double result = temperatureK + dT;
        if (!Double.isFinite(result) || result < 0) return temperatureK;
        return result;
    }
}
