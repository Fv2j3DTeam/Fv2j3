package com.fv2j3.mesrgl.integration.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weather bridge (PART 6 - weather connection).
 *
 * The Fv2j3 universe module has a {@code WeatherSystem} that drives
 * rain/snow/smoke/storms, but the MC integration does not have a live
 * reference to that system. Instead we read the weather state through
 * the Minecraft world itself (rainStrength, thunderStrength are public
 * fields on {@code World} in 1.12.2) and drive our visual weather from
 * the same sources.
 *
 * If the integration is later wired to a real universe instance via the
 * loader, this class can be promoted to consume the live
 * {@code WeatherSystem}/{@code SnowSystem}/{@code SmokeSystem} events
 * without changing the renderer's call sites.
 */
public final class WeatherBridge {

    public enum WeatherMode {
        CLEAR,
        RAIN,
        THUNDERSTORM,
        SNOW
    }

    public static final class State {
        public final WeatherMode mode;
        public final float rainStrength;   // 0..1
        public final float thunderStrength; // 0..1
        public final float temperature;    // K (Minecraft default: 287 K for overworld)
        public final long timeMs;

        public State(WeatherMode mode, float rainStrength, float thunderStrength,
                     float temperature, long timeMs) {
            this.mode = mode;
            this.rainStrength = rainStrength;
            this.thunderStrength = thunderStrength;
            this.temperature = temperature;
            this.timeMs = timeMs;
        }
    }

    public static final class Particles {
        public final List<float[]> rainQuads;     // 12 floats per quad
        public final List<float[]> snowQuads;     // 12 floats per quad
        public final List<float[]> smokeQuads;    // 12 floats per quad

        public Particles(List<float[]> rainQuads, List<float[]> snowQuads, List<float[]> smokeQuads) {
            this.rainQuads = rainQuads;
            this.snowQuads = snowQuads;
            this.smokeQuads = smokeQuads;
        }
    }

    /** Computes the weather state. Falls back to a sysprop-driven mode
     *  if reflection can't reach the live world. */
    public State computeState(Object world, Object mcInstance) {
        // Default: clear. The renderer can be forced into a weather state
        // with -Dfv2j3.mesrgl.weather=rain|thunder|snow for stress tests.
        String forced = System.getProperty("fv2j3.mesrgl.weather", "auto");
        float rain = 0f, thunder = 0f;
        WeatherMode mode = WeatherMode.CLEAR;
        if (forced.equalsIgnoreCase("rain")) {
            rain = 0.8f; thunder = 0.3f; mode = WeatherMode.RAIN;
        } else if (forced.equalsIgnoreCase("thunder")) {
            rain = 1.0f; thunder = 1.0f; mode = WeatherMode.THUNDERSTORM;
        } else if (forced.equalsIgnoreCase("snow")) {
            rain = 0.8f; thunder = 0.0f; mode = WeatherMode.SNOW;
        } else {
            // Try to read the live world fields via MinecraftReflection.
            try {
                if (mcInstance != null) {
                    Object world0 = MinecraftReflection.get(mcInstance.getClass().getClassLoader())
                            .currentWorld(mcInstance);
                    if (world0 != null) {
                        rain = readFloatField(world0, "r", 0f);  // World.rainingStrength
                        thunder = readFloatField(world0, "s", 0f);  // World.thunderingStrength
                        if (thunder > 0.5f) mode = WeatherMode.THUNDERSTORM;
                        else if (rain > 0.1f) mode = WeatherMode.RAIN;
                    }
                }
            } catch (Throwable ignored) {
                // reflection failure: keep defaults
            }
        }
        float temperature = mode == WeatherMode.SNOW ? 263f : 287f;
        return new State(mode, rain, thunder, temperature, System.currentTimeMillis());
    }

    private static float readFloatField(Object instance, String name, float fallback) {
        try {
            java.lang.reflect.Field f = instance.getClass().getField(name);
            return f.getFloat(instance);
        } catch (Throwable e) {
            return fallback;
        }
    }

    /**
     * Generates the particle quads for one frame, given the camera position
     * and the current weather state. Rain and snow are billboarded planes;
     * smoke is a slowly rising translucent puff.
     *
     * @param cameraX, cameraY, cameraZ the camera world position
     * @param state the current weather state
     * @param intensity how many particles (driven by the quality preset)
     */
    public Particles buildParticles(double cameraX, double cameraY, double cameraZ,
                                    State state, int intensity) {
        List<float[]> rain = new ArrayList<>();
        List<float[]> snow = new ArrayList<>();
        List<float[]> smoke = new ArrayList<>();
        if (state.mode == WeatherMode.CLEAR) {
            return new Particles(rain, snow, smoke);
        }
        long time = state.timeMs;
        Random rng = new Random(time / 33L);
        int n = Math.max(0, intensity);
        for (int i = 0; i < n; i++) {
            float rx = (float) (cameraX - 12.0 + rng.nextDouble() * 24.0);
            float ry = (float) (cameraY - 8.0 + rng.nextDouble() * 16.0);
            float rz = (float) (cameraZ - 12.0 + rng.nextDouble() * 24.0);
            if (state.mode == WeatherMode.SNOW) {
                snow.add(snowQuad(rx, ry, rz));
            } else if (state.mode == WeatherMode.RAIN || state.mode == WeatherMode.THUNDERSTORM) {
                rain.add(rainQuad(rx, ry, rz));
            }
        }
        // Smoke columns: random vertical pillars at world corners.
        for (int i = 0; i < Math.min(intensity / 4, 32); i++) {
            float sx = (float) (cameraX + rng.nextDouble() * 20.0 - 10.0);
            float sz = (float) (cameraZ + rng.nextDouble() * 20.0 - 10.0);
            float sy = (float) (cameraY - 6.0 + rng.nextDouble() * 8.0);
            smoke.add(smokeQuad(sx, sy, sz));
        }
        return new Particles(rain, snow, smoke);
    }

    private static float[] rainQuad(float x, float y, float z) {
        // Vertical streak: tall thin quad.
        float w = 0.04f, h = 0.6f;
        return new float[]{
                x - w, y, z, x + w, y, z,
                x + w, y + h, z, x - w, y + h, z
        };
    }

    private static float[] snowQuad(float x, float y, float z) {
        // Small white disc.
        float s = 0.06f;
        return new float[]{
                x - s, y - s, z, x + s, y - s, z,
                x + s, y + s, z, x - s, y + s, z
        };
    }

    private static float[] smokeQuad(float x, float y, float z) {
        float s = 0.4f;
        return new float[]{
                x - s, y, z, x + s, y, z,
                x + s, y + s * 2, z, x - s, y + s * 2, z
        };
    }
}
