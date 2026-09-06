package com.fv2j3.mesrgl.integration.minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft 1.12.2 lighting model (PART 2 - lighting).
 *
 * Three contributions are converted into the MesrGL scene:
 *
 *  1. SUN: a single directional light whose direction tracks the world's
 *     time of day. Color shifts from warm sunrise (low altitude) to
 *     white at noon to deep red/orange at sunset, fading to moonlight
 *     at night. Intensity follows a smooth day/night envelope.
 *
 *  2. SKY: an ambient term added via scene clear color and a hemisphere
 *     color, both tracking the sun altitude (sky becomes darker and
 *     bluer at night, brighter and more saturated at midday).
 *
 *  3. BLOCK LIGHT: torches, glowstone, lava, fire, etc. - all turned
 *     into point lights at their world position. The MC light level
 *     (0..15) drives the point-light intensity.
 */
public final class MinecraftLighting {

    public static final class Sun {
        public final float dx, dy, dz;        // direction (towards sun)
        public final float r, g, b;          // color
        public final float intensity;
        public final float altitude;          // sin(altitude) [-1..1]

        public Sun(float dx, float dy, float dz, float r, float g, float b,
                   float intensity, float altitude) {
            this.dx = dx; this.dy = dy; this.dz = dz;
            this.r = r; this.g = g; this.b = b;
            this.intensity = intensity;
            this.altitude = altitude;
        }
    }

    public static final class Sky {
        public final float r, g, b;          // ambient color
        public final float intensity;        // ambient strength

        public Sky(float r, float g, float b, float intensity) {
            this.r = r; this.g = g; this.b = b; this.intensity = intensity;
        }
    }

    public static final class PointLight {
        public final float px, py, pz;
        public final float r, g, b;
        public final float intensity;
        public final float range;

        public PointLight(float px, float py, float pz, float r, float g, float b,
                          float intensity, float range) {
            this.px = px; this.py = py; this.pz = pz;
            this.r = r; this.g = g; this.b = b;
            this.intensity = intensity;
            this.range = range;
        }
    }

    public static final class Frame {
        public final Sun sun;
        public final Sky sky;
        public final List<PointLight> blockLights;
        public final float cloudCoverage;    // 0..1
        public final float cloudDensity;

        public Frame(Sun sun, Sky sky, List<PointLight> blockLights,
                     float cloudCoverage, float cloudDensity) {
            this.sun = sun;
            this.sky = sky;
            this.blockLights = blockLights;
            this.cloudCoverage = cloudCoverage;
            this.cloudDensity = cloudDensity;
        }
    }

    /**
     * Computes the lighting state for a given world time and a list of
     * emissive block positions with their MC light level (0..15).
     *
     * @param worldTime the long world time in ticks (0..24000)
     * @param blockLights light sources (one per torch / glowstone / lava)
     */
    public Frame compute(long worldTime, List<PointLight> blockLights) {
        // MC day: 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.
        float tod = (worldTime % 24000L) / 24000.0f;  // 0..1
        // Sun angle: 0 at sunrise, 0.25 at noon (highest), 0.5 at sunset,
        // 0.75 at midnight (lowest, negative altitude).
        float angle = tod * 2.0f * (float) Math.PI;
        // Sun in equatorial plane: y = sin(angle), x = -cos(angle)
        float sinA = (float) Math.sin(angle);
        float cosA = (float) Math.cos(angle);
        float alt = sinA;     // -1 at midnight, 1 at noon, 0 at horizon

        // Sun color and intensity as a function of altitude.
        float[] sunColor;
        float intensity;
        if (alt > 0.2f) {
            // Day: warm white, full intensity
            sunColor = new float[]{1.0f, 0.96f, 0.88f};
            intensity = 3.4f;
        } else if (alt > 0.0f) {
            // Sunrise / sunset: warm orange-red
            float t = alt / 0.2f;
            sunColor = lerp3(new float[]{1.0f, 0.55f, 0.25f},
                    new float[]{1.0f, 0.96f, 0.88f}, t);
            intensity = 1.5f + 1.9f * t;
        } else if (alt > -0.15f) {
            // Twilight: deep blue
            float t = (alt + 0.15f) / 0.15f;
            sunColor = lerp3(new float[]{0.18f, 0.20f, 0.40f},
                    new float[]{1.0f, 0.55f, 0.25f}, t);
            intensity = 0.2f + 1.3f * t;
        } else {
            // Night: cool moonlight (slight blue tint)
            sunColor = new float[]{0.18f, 0.22f, 0.40f};
            intensity = 0.2f;
        }

        // Sun direction: from the sun towards the origin (so primary light
        // comes from the sky).
        float dx = -cosA * 0.85f;
        float dy = -alt;
        float dz = 0.0f;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-5f) {
            dx /= len; dy /= len; dz /= len;
        }
        Sun sun = new Sun(-dx, -dy, -dz, sunColor[0], sunColor[1], sunColor[2], intensity, alt);

        // Sky ambient: tracks altitude. Bright blue day, dark blue night.
        float skyR, skyG, skyB, skyI;
        if (alt > 0.1f) {
            skyR = 0.45f; skyG = 0.62f; skyB = 0.85f;
            skyI = 0.55f;
        } else if (alt > 0.0f) {
            float t = alt / 0.1f;
            skyR = lerp(0.35f, 0.45f, t);
            skyG = lerp(0.30f, 0.62f, t);
            skyB = lerp(0.45f, 0.85f, t);
            skyI = lerp(0.30f, 0.55f, t);
        } else if (alt > -0.15f) {
            float t = (alt + 0.15f) / 0.15f;
            skyR = lerp(0.04f, 0.35f, t);
            skyG = lerp(0.04f, 0.30f, t);
            skyB = lerp(0.10f, 0.45f, t);
            skyI = lerp(0.10f, 0.30f, t);
        } else {
            skyR = 0.04f; skyG = 0.04f; skyB = 0.10f;
            skyI = 0.10f;
        }
        Sky sky = new Sky(skyR, skyG, skyB, skyI);

        // Clouds: derived from time. Sparse wispy at midday, fuller at sunset.
        float cloudCoverage = 0.35f + 0.15f * (float) Math.sin(worldTime / 8000.0);
        float cloudDensity = 0.6f + 0.4f * Math.max(0, alt);

        return new Frame(sun, sky, blockLights, cloudCoverage, cloudDensity);
    }

    /**
     * Light level (0..15) → point-light intensity used for torch / glowstone
     * / lava. The MC light level is a logarithmic-ish measure; we map it
     * linearly with a per-source multiplier.
     */
    public static PointLight fromBlockLight(String blockId, int lightLevel,
                                            float px, float py, float pz) {
        float r, g, b, mult;
        if (blockId == null) {
            r = 1.0f; g = 0.9f; b = 0.7f; mult = 1.0f;
        } else if (blockId.contains("torch")) {
            r = 1.0f; g = 0.85f; b = 0.5f; mult = 1.4f;
        } else if (blockId.contains("glowstone")) {
            r = 1.0f; g = 0.95f; b = 0.6f; mult = 1.6f;
        } else if (blockId.contains("lava")) {
            r = 1.0f; g = 0.55f; b = 0.15f; mult = 1.2f;
        } else {
            r = 1.0f; g = 0.9f; b = 0.7f; mult = 1.0f;
        }
        float norm = Math.max(0.0f, Math.min(1.0f, lightLevel / 15.0f));
        float intensity = 6.0f * norm * mult;
        float range = 12.0f + 6.0f * norm;
        return new PointLight(px, py, pz, r, g, b, intensity, range);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float[] lerp3(float[] a, float[] b, float t) {
        return new float[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }

    /**
     * Empty point-light list to avoid per-frame allocations.
     */
    public static List<PointLight> emptyLights() {
        return new ArrayList<>(0);
    }
}
