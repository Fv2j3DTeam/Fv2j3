package com.fv2j3.universe.units;

import java.util.Objects;

/**
 * Normalised simulation units. Documented in §23/§33–§35 of the master spec.
 * Fields use SI-derived or normalised quantities; ranges are documented per field.
 */
public final class Units {
    private Units() {}

    public static final double KELVIN_OFFSET = 273.15;
    /** Absolute zero in Kelvin — lower bound for any temperature field. */
    public static final double ABSOLUTE_ZERO_K = 0.0;

    /** Acceleration in m·s⁻². Used by gravity, wind, fluid acceleration. */
    public static final String ACCELERATION = "m*s^-2";
    /** Velocity in m·s⁻¹. */
    public static final String VELOCITY = "m*s^-1";
    /** Distance in metres (for local coordinates). */
    public static final String DISTANCE = "m";
    /** Pressure in Pa. */
    public static final String PRESSURE = "Pa";
    /** Temperature in K. */
    public static final String TEMPERATURE = "K";
    /** Humidity as mass fraction (kg/kg), 0..1. */
    public static final String HUMIDITY = "kg/kg";
    /** Density in kg·m⁻³. */
    public static final String DENSITY = "kg*m^-3";
    /** Dynamic viscosity in Pa·s. */
    public static final String VISCOSITY = "Pa*s";
    /** Kinematic viscosity in m²·s⁻¹. */
    public static final String KINEMATIC_VISCOSITY = "m^2*s^-1";
    /** Smoke density (normalised extinction), 0..1. */
    public static final String SMOKE_DENSITY = "extinction(0..1)";
    /** Snow depth in metres. */
    public static final String SNOW_DEPTH = "m";
    /** Rain rate in mm·h⁻¹. */
    public static final String RAIN_RATE = "mm*h^-1";

    public static double celsiusToKelvin(double c) {
        return c + KELVIN_OFFSET;
    }

    public static double kelvinToCelsius(double k) {
        return k - KELVIN_OFFSET;
    }

    /** Clamp to a documented valid range; NaN inputs return the floor. */
    public static double clamp(double value, double min, double max, String name) {
        Objects.requireNonNull(name, "name");
        if (Double.isNaN(value)) {
            return min;
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? max : min;
        }
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Returns true if the value is physically sane (not NaN, not infinite). */
    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}