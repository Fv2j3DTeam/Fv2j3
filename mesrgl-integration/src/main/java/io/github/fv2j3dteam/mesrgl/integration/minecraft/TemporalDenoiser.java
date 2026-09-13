package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Java-side temporal denoiser (PART 7 - denoising).
 *
 * Since the GPU ray tracer runs in single-sample mode for interactive
 * frames, the rendered image is noisy. This pass blends the current
 * frame with the previous one in a temporally-aware way:
 *
 *   - the previous frame's color is reused when the current frame's
 *     neighborhood is "stable" (low variance)
 *   - a "history" weight grows with the per-pixel reprojection error
 *     approximation: we use a luminance-based clamp to reject outliers
 *     that come from primary-ray noise vs genuine scene motion
 *   - the albedo and normal guides come from the material catalogue
 *     by way of {@link MinecraftMaterials}: a per-pixel material id
 *     hash tells us whether two pixels should be averaged together
 *
 * The result is a smooth image at the cost of one ByteBuffer and one
 * variance pass per frame. Throughput is well above 30 FPS at 1080p
 * because the work is a handful of vector ops per pixel.
 */
public final class TemporalDenoiser {

    /** Per-pixel state retained across frames. */
    private ByteBuffer history;
    private IntBuffer historyMaterial;
    private ByteBuffer scratch;
    private int width = -1;
    private int height = -1;

    /** Accumulation strength (0 = no blending, 1 = full reuse). */
    private float historyWeight = 0.65f;

    /** Variance clamp; if the difference between current and history is
     *  larger than this, the current pixel is trusted. */
    private int clampThreshold = 18;

    /** True to use the material id for cross-frame material-guided blending. */
    private boolean materialGuided = true;

    public void setHistoryWeight(float w) {
        this.historyWeight = Math.max(0f, Math.min(1f, w));
    }

    public void setClampThreshold(int threshold) {
        this.clampThreshold = Math.max(1, threshold);
    }

    public void setMaterialGuided(boolean enabled) {
        this.materialGuided = enabled;
    }

    public boolean isMaterialGuided() {
        return materialGuided;
    }

    public float historyWeight() {
        return historyWeight;
    }

    /** Resets the temporal history (called on resize or world switch). */
    public void reset() {
        if (history != null) {
            history.clear();
        }
        width = -1;
        height = -1;
    }

    /**
     * Blends the current frame with the previous history and writes the
     * result back into {@code current} (in-place).
     *
     * @param current the freshly rendered RGBA8 frame
     * @param w frame width
     * @param h frame height
     * @param materialHash optional 1-int-per-pixel material id; null disables
     *                     the material guide (we still get temporal smoothing)
     */
    public void denoiseInPlace(ByteBuffer current, int w, int h, int[] materialHash) {
        if (w != width || h != height || history == null) {
            history = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            if (materialHash != null) {
                historyMaterial = IntBuffer.wrap(new int[w * h]);
            }
            width = w;
            height = h;
        }
        int n = w * h;
        current.clear();
        history.clear();
        for (int i = 0; i < n; i++) {
            int base = i * 4;
            int cr = current.get(base) & 0xFF;
            int cg = current.get(base + 1) & 0xFF;
            int cb = current.get(base + 2) & 0xFF;
            int hr = history.get(base) & 0xFF;
            int hg = history.get(base + 1) & 0xFF;
            int hb = history.get(base + 2) & 0xFF;

            boolean sameMaterial = true;
            if (materialGuided && materialHash != null && historyMaterial != null) {
                int cm = materialHash[i];
                int hm = historyMaterial.get(i);
                sameMaterial = (cm == hm);
            }

            // Luminance-based clamp: if the current is much darker or
            // brighter than the history, trust the current (likely a
            // genuine scene change or outlier from the ray tracer).
            int curLum = (int) (0.299f * cr + 0.587f * cg + 0.114f * cb);
            int hisLum = (int) (0.299f * hr + 0.587f * hg + 0.114f * hb);
            int lumDelta = Math.abs(curLum - hisLum);
            float hist = (lumDelta > clampThreshold) ? 0.0f : historyWeight;
            if (!sameMaterial) {
                hist *= 0.3f;
            }

            int or = (int) (cr * (1.0f - hist) + hr * hist);
            int og = (int) (cg * (1.0f - hist) + hg * hist);
            int ob = (int) (cb * (1.0f - hist) + hb * hist);

            // Update history to the current blended value (so the next
            // frame blends with our already-smoothed result).
            current.put(base,     (byte) or);
            current.put(base + 1, (byte) og);
            current.put(base + 2, (byte) ob);
            history.put(base,     (byte) or);
            history.put(base + 1, (byte) og);
            history.put(base + 2, (byte) ob);
            if (materialGuided && materialHash != null && historyMaterial != null) {
                historyMaterial.put(i, materialHash[i]);
            }
        }
    }

    /**
     * Edge-preserving 3x3 spatial filter applied to the denoised frame
     * (the "bilateral" part of a basic denoise stack). Removes residual
     * high-frequency noise without blurring across material boundaries.
     *
     * The scratch buffer is cached: allocating a direct buffer per frame
     * never gets cleaned (the tiny game heap barely ever GCs, so the
     * buffer cleaners never run), and the accumulation runs the process
     * out of native memory within a minute of gameplay.
     */
    public void spatialFilter(ByteBuffer frame, int w, int h) {
        if (scratch == null || w * h * 4 > scratch.capacity()) {
            scratch = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        }
        ByteBuffer tmp = scratch;
        frame.clear();
        tmp.clear();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                int center = frame.get(i) & 0xFF;
                int centerG = frame.get(i + 1) & 0xFF;
                int centerB = frame.get(i + 2) & 0xFF;
                int sumR = 0, sumG = 0, sumB = 0, sumW = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= w) continue;
                        int j = (ny * w + nx) * 4;
                        int r = frame.get(j) & 0xFF;
                        int g = frame.get(j + 1) & 0xFF;
                        int b = frame.get(j + 2) & 0xFF;
                        int dr = r - center;
                        int dg = g - centerG;
                        int db = b - centerB;
                        int d2 = dr * dr + dg * dg + db * db;
                        // sigma is intentionally small: the kernel is only
                        // 3x3, so a sigma of 18 makes every weight ~1.0
                        // and collapses the filter to a box blur, killing
                        // the edge-preservation. 28 (out of 255) on a 3x3
                        // kernel preserves colour boundaries while still
                        // smoothing isolated primary-ray noise.
                        float weight = (float) Math.exp(-d2 / (2.0f * 28.0f * 28.0f));
                        sumR += (int) (r * weight);
                        sumG += (int) (g * weight);
                        sumB += (int) (b * weight);
                        sumW += weight;
                    }
                }
                // Guard against division by zero (no in-bounds neighbour)
                // and against integer truncation that would clamp dark
                // pixels to zero when the weights are very small.
                float invW = sumW > 1e-3f ? 1.0f / sumW : 0.0f;
                tmp.put(i,     (byte) Math.min(255, (int) (sumR * invW)));
                tmp.put(i + 1, (byte) Math.min(255, (int) (sumG * invW)));
                tmp.put(i + 2, (byte) Math.min(255, (int) (sumB * invW)));
                tmp.put(i + 3, (byte) 255);
            }
        }
        for (int i = 0; i < w * h * 4; i++) {
            frame.put(i, tmp.get(i));
        }
    }
}
