package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Auto-exposure controller (Phase 47.1 - PART 1).
 *
 * Reads the previous frame's RGBA8 buffer and estimates the average
 * scene luminance. The exposure is then driven toward the configured
 * target brightness using a slow temporal filter (so camera and sun
 * motion do not strobe the exposure). A clamp keeps the exposure in
 * the configured [min, max] range to avoid total blackness and total
 * blow-out.
 *
 * The system property block (with defaults):
 *   fv2j3.mesrgl.exposure.enabled = true
 *   fv2j3.mesrgl.exposure.target   = 0.18     // target linear luma (0..1)
 *   fv2j3.mesrgl.exposure.min      = 0.25     // never dimmer than this
 *   fv2j3.mesrgl.exposure.max      = 8.0      // never brighter than this
 *   fv2j3.mesrgl.exposure.speed    = 0.1      // temporal blend factor
 *   fv2j3.mesrgl.exposure.basis    = 1.0      // unity (see formula below)
 *
 * The exposure formula is
 *     desired = (target / linear) * basis
 * where `linear` is the gamma-2.2-inverse of the measured display luma.
 * At basis = 1 the controller asks for the exposure that would rescale
 * the measured linear luma up to the target. A basis of 1 means "do not
 * apply any extra scaling"; other values are a manual fudge factor for
 * scenes whose true radiance differs from the displayed luma (e.g. raw
 * HDR before ACES tone mapping). The previous default 0.0254 was off by
 * ~40x and pinned the exposure to {@code min} for any normal scene.
 *
 * The measured luma is computed from the RGB sample of the readback
 * buffer; we downsample by stride to keep the cost trivial (one pass
 * over W*H samples with a stride of 16 ≈ 1% sampling, well under a
 * millisecond at 1080p).
 */
public final class MesrGLExposureController {

    private static final float REC709_R = 0.2126f;
    private static final float REC709_G = 0.7152f;
    private static final float REC709_B = 0.0722f;

    private final boolean enabled;
    private final float target;
    private final float min;
    private final float max;
    private final float speed;
    private final float basis;
    private final int sampleStride;

    private float currentExposure = 1.0f;
    private float lastMeasuredLuma = 0.0f;
    private float lastSceneLuma = 0.0f;
    private float lastSkyLuma = 0.0f;
    private float lastMaterialLuma = 0.0f;
    private boolean primed;

    public MesrGLExposureController() {
        this.enabled = Boolean.parseBoolean(
                System.getProperty("fv2j3.mesrgl.exposure.enabled", "true"));
        this.target = Float.parseFloat(
                System.getProperty("fv2j3.mesrgl.exposure.target", "0.18"));
        this.min = Math.max(0.05f, Float.parseFloat(
                System.getProperty("fv2j3.mesrgl.exposure.min", "0.25")));
        this.max = Math.max(this.min, Float.parseFloat(
                System.getProperty("fv2j3.mesrgl.exposure.max", "8.0")));
        this.speed = clamp01(Float.parseFloat(
                System.getProperty("fv2j3.mesrgl.exposure.speed", "0.1")));
        this.basis = Math.max(1e-4f, Float.parseFloat(
                System.getProperty("fv2j3.mesrgl.exposure.basis", "1.0")));
        this.sampleStride = Math.max(1, Integer.parseInt(
                System.getProperty("fv2j3.mesrgl.exposure.sampleStride", "16")));
    }

    /** Returns the exposure multiplier to apply for the next frame. */
    public float currentExposure() {
        if (!enabled) return 1.0f;
        return currentExposure;
    }

    public float lastMeasuredLuma() { return lastMeasuredLuma; }
    public float lastSceneLuma()    { return lastSceneLuma; }
    public float lastSkyLuma()      { return lastSkyLuma; }
    public float lastMaterialLuma() { return lastMaterialLuma; }

    /**
     * Measures the previous frame and updates the exposure for the next
     * render. Should be called once per frame after the readback but
     * before the next render. Pass the same RGBA8 buffer returned by
     * the renderer; the controller will skip any "debug mode" frames
     * (single-color) automatically.
     */
    public void updateFromFrame(ByteBuffer frame, int w, int h) {
        if (!enabled || frame == null || w <= 0 || h <= 0) return;
        LumaStats stats = measureLuma(frame, w, h);
        lastSceneLuma = stats.overall;
        lastSkyLuma = stats.topBand;
        lastMaterialLuma = stats.bottomBand;
        // Weighted blend: the overall luma drives the exposure, with
        // a hint from the sky and the material/ground regions.
        float measured = 0.6f * stats.overall + 0.2f * stats.topBand + 0.2f * stats.bottomBand;
        lastMeasuredLuma = measured;
        if (measured < 1e-3f) {
            // All-black frame: don't crank exposure to infinity.
            // Hold the current exposure and let the scene come back.
            // Do NOT mark primed: when the scene returns we still want
            // the next valid reading to snap the exposure to the right
            // value, not slowly lerp from a stale hold.
            return;
        }
        // Convert measured 8-bit luma to linear radiance.
        float linear = measured * measured;  // approximate 2.2 gamma
        // Compute target exposure and blend temporally.
        float desired = (target / Math.max(linear, 1e-3f)) * basis;
        desired = clamp(desired, min, max);
        if (!primed) {
            currentExposure = desired;
            primed = true;
        } else {
            currentExposure = lerp(currentExposure, desired, speed);
        }
    }

    public void reset() {
        primed = false;
        currentExposure = 1.0f;
        lastMeasuredLuma = 0;
        lastSceneLuma = 0;
        lastSkyLuma = 0;
        lastMaterialLuma = 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String configString() {
        return String.format(Locale.ROOT,
                "exposure{enabled=%s target=%.2f min=%.2f max=%.2f speed=%.2f basis=%.4f}",
                enabled, target, min, max, speed, basis);
    }

    /** Luma summary returned by {@link #measureLuma}. */
    public static final class LumaStats {
        public final float overall;   // mean over the whole frame
        public final float topBand;   // top quarter (sky)
        public final float bottomBand; // bottom half (ground/materials)
        public LumaStats(float overall, float topBand, float bottomBand) {
            this.overall = overall;
            this.topBand = topBand;
            this.bottomBand = bottomBand;
        }
    }

    /**
     * Down-samples the framebuffer and returns the mean luma for the
     * whole frame, the top quarter, and the bottom half. The sampling
     * stride is large enough to be O(1ms) even at 4K.
     */
    public LumaStats measureLuma(ByteBuffer frame, int w, int h) {
        long sumAll = 0, sumTop = 0, sumBot = 0;
        long nAll = 0, nTop = 0, nBot = 0;
        int topRows = Math.max(1, h / 4);
        int botStart = h / 2;
        frame.clear();
        for (int y = 0; y < h; y++) {
            boolean inTop = y < topRows;
            boolean inBot = y >= botStart;
            for (int x = 0; x < w; x += sampleStride) {
                int i = (y * w + x) * 4;
                int r = frame.get(i) & 0xFF;
                int g = frame.get(i + 1) & 0xFF;
                int b = frame.get(i + 2) & 0xFF;
                float luma = REC709_R * r + REC709_G * g + REC709_B * b;
                sumAll += luma;
                nAll++;
                if (inTop) { sumTop += luma; nTop++; }
                if (inBot) { sumBot += luma; nBot++; }
            }
        }
        float o = nAll > 0 ? (float) sumAll / (255f * nAll) : 0f;
        float t = nTop > 0 ? (float) sumTop / (255f * nTop) : o;
        float b = nBot > 0 ? (float) sumBot / (255f * nBot) : o;
        return new LumaStats(o, t, b);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
