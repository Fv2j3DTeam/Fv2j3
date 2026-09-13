package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.util.Locale;

/**
 * Quality presets (PART 8 - quality settings).
 *
 * Each preset binds together the five knobs that control visual fidelity:
 *
 *   - renderScale   : back-buffer as a fraction of the window (0.4 .. 1.0)
 *   - samplesPerPx  : primary samples per pixel
 *   - maxBounces    : ray-tracing depth
 *   - denoiser      : "temporal" or "off"
 *   - cloudTier     : 0..4, drives the cloud-quad count in SkyDome.cloudQuads
 *   - shadowRays    : "single" or "multi" (multi = jittered PCF-like sampling)
 *
 * The preset can be selected at launch via -Dfv2j3.mesrgl.quality=low |
 * medium | high | ultra | extreme and changed at runtime via {@link #set}.
 */
public enum QualityPresets {
    LOW(0.45f, 1, 1, false, 0, "single"),
    MEDIUM(0.60f, 1, 2, true, 1, "single"),
    HIGH(0.80f, 1, 3, true, 2, "multi"),
    ULTRA(0.95f, 2, 4, true, 3, "multi"),
    EXTREME(1.00f, 4, 6, true, 4, "multi");

    public final float renderScale;
    public final int samplesPerPixel;
    public final int maxBounces;
    public final boolean denoiser;
    public final int cloudTier;
    public final String shadowRays;

    QualityPresets(float renderScale, int samplesPerPixel, int maxBounces,
                   boolean denoiser, int cloudTier, String shadowRays) {
        this.renderScale = renderScale;
        this.samplesPerPixel = samplesPerPixel;
        this.maxBounces = maxBounces;
        this.denoiser = denoiser;
        this.cloudTier = cloudTier;
        this.shadowRays = shadowRays;
    }

    private static volatile QualityPresets current = fromProperty();

    public static QualityPresets current() {
        return current;
    }

    public static void set(QualityPresets preset) {
        current = preset;
    }

    public static QualityPresets fromProperty() {
        String v = System.getProperty("fv2j3.mesrgl.quality", "medium");
        return fromString(v);
    }

    public static QualityPresets fromString(String name) {
        if (name == null) return MEDIUM;
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "low":     return LOW;
            case "medium":  return MEDIUM;
            case "high":    return HIGH;
            case "ultra":   return ULTRA;
            case "extreme": return EXTREME;
            default:        return MEDIUM;
        }
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }
}
