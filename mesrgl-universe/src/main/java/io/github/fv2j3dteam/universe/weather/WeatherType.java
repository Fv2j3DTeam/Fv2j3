package io.github.fv2j3dteam.universe.weather;

import java.util.Objects;

/** Weather type definition (§25). */
public final class WeatherType {
    public enum Phase { CLEAR, RAIN, SNOW, HAIL, SLEET, DUST, SAND, ASH, FOG }
    private final String id;
    private final Phase phase;
    private final double intensity; // 0..1
    private final double temperatureK;

    public WeatherType(String id, Phase phase, double intensity, double temperatureK) {
        this.id = Objects.requireNonNull(id, "id");
        this.phase = Objects.requireNonNull(phase, "phase");
        if (intensity < 0 || intensity > 1) throw new IllegalArgumentException("intensity in [0,1]");
        this.intensity = intensity;
        this.temperatureK = temperatureK;
    }

    public String id() { return id; }
    public Phase phase() { return phase; }
    public double intensity() { return intensity; }
    public double temperatureK() { return temperatureK; }

    public static WeatherType clear() { return new WeatherType("clear", Phase.CLEAR, 0, 293.0); }
    public static WeatherType rain() { return new WeatherType("rain", Phase.RAIN, 0.5, 285.0); }
    public static WeatherType snow() { return new WeatherType("snow", Phase.SNOW, 0.5, 268.0); }
    public static WeatherType fog() { return new WeatherType("fog", Phase.FOG, 0.6, 285.0); }
    public static WeatherType ash() { return new WeatherType("ash", Phase.ASH, 0.4, 320.0); }
}
