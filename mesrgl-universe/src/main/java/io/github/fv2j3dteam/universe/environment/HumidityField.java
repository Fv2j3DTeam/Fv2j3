package io.github.fv2j3dteam.universe.environment;

/**
 * Humidity / water-vapor field (§35). Tracks local saturation, evaporation,
 * condensation, and precipitation coupling. Values are mass fractions in [0,1].
 */
public final class HumidityField {

    public double saturate(double temperatureK) {
        // Tetens approximation: saturation vapour pressure in Pa over liquid water.
        if (temperatureK <= 0) return 0.0;
        double c = 17.27;
        double tC = temperatureK - 273.15;
        double pSat = 611.21 * Math.exp((c * tC) / (tC + 237.3));
        return pSat;
    }

    public double relativeHumidity(double actualPa, double saturationPa) {
        if (saturationPa <= 0) return 0.0;
        return Math.max(0, Math.min(1, actualPa / saturationPa));
    }

    public double evaporate(double surfaceAreaM2, double windSpeedMs, double humidityDeficit, double temperatureK, double dt) {
        // Dalton-style: E = k * A * (1 - RH) * v
        double k = 1.5e-5; // empirical
        if (humidityDeficit < 0) humidityDeficit = 0;
        if (humidityDeficit > 1) humidityDeficit = 1;
        double evapPa = k * surfaceAreaM2 * windSpeedMs * humidityDeficit * dt * 50.0;
        return evapPa;
    }

    public double condense(double humidityMassFraction, double temperatureK, double saturationMassFraction) {
        if (humidityMassFraction <= saturationMassFraction) return 0.0;
        return humidityMassFraction - saturationMassFraction;
    }
}
