package com.fv2j3.mesrgl.integration.minecraft;

import java.util.Locale;

/**
 * Lighting debug mode (Phase 47.1 - PART 3, PART 4).
 *
 * Lets the operator confirm that the lighting pipeline is wired up
 * correctly: turn off the directional sun to verify emission-only,
 * turn off emission to verify direct-light-only, etc. The mode is
 * selected with -Dfv2j3.mesrgl.lighting.debug=NORMAL|DIRECT_LIGHT_ONLY
 * |GI_ONLY|EMISSION_ONLY|ALBEDO_ONLY.
 *
 * Each mode modifies the per-frame lighting before the scene is
 * uploaded, and the overlay shows the active mode and the current
 * block under the camera (for material validation).
 */
public final class LightingDebug {

    public enum Mode {
        NORMAL,
        DIRECT_LIGHT_ONLY,
        GI_ONLY,
        EMISSION_ONLY,
        ALBEDO_ONLY;

        public static Mode fromProperty() {
            String v = System.getProperty("fv2j3.mesrgl.lighting.debug", "NORMAL");
            if (v == null) return NORMAL;
            try {
                return Mode.valueOf(v.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NORMAL;
            }
        }
    }

    private final Mode mode;

    public LightingDebug() {
        this.mode = Mode.fromProperty();
    }

    public Mode mode() { return mode; }

    /**
     * Applies the debug mode by scaling sun / emission / GI contributions.
     * Returns the modified sun intensity and a scalar for emission that
     * the caller can multiply into per-block emissive multipliers.
     */
    public static final class Override {
        public final float sunScale;
        public final float emissionScale;
        public final float giScale;
        public final boolean useAlbedoAsLight;
        public Override(float sunScale, float emissionScale, float giScale, boolean albedo) {
            this.sunScale = sunScale;
            this.emissionScale = emissionScale;
            this.giScale = giScale;
            this.useAlbedoAsLight = albedo;
        }
    }

    public Override override() {
        switch (mode) {
            case DIRECT_LIGHT_ONLY: return new Override(1.0f, 0.0f, 0.0f, false);
            case GI_ONLY:           return new Override(0.0f, 0.0f, 1.0f, false);
            case EMISSION_ONLY:     return new Override(0.0f, 1.0f, 0.0f, false);
            case ALBEDO_ONLY:       return new Override(0.0f, 0.0f, 0.0f, true);
            case NORMAL:
            default:                return new Override(1.0f, 1.0f, 1.0f, false);
        }
    }

    /**
     * Compose a one-line overlay text describing the current debug state.
     */
    public String overlayText(String materialName,
                              float roughness, float metallic, float ior,
                              float exposure, float measuredLuma) {
        return String.format(Locale.ROOT,
                "LIGHT=%s | %s | rough=%.2f met=%.2f ior=%.2f | exp=%.2f luma=%.2f",
                mode, materialName, roughness, metallic, ior, exposure, measuredLuma);
    }
}
