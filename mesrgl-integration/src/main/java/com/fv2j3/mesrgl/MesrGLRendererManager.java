package com.fv2j3.mesrgl;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the MesrGL renderer used by the Fv2j3 client: backend selection with
 * explicit fallback, honest backend reporting, and the debug information
 * consumed by the loading screen and the renderer debug overlay.
 *
 * Selection policy (system property {@code fv2j3.mesrgl.backend}):
 *   auto (default) - GPU Software RT when the executor initializes,
 *                    otherwise the CPU Software RT reference renderer.
 *   cpu            - force the CPU reference renderer (verification path).
 *   gpu            - prefer GPU; fall back to CPU with a logged reason.
 *
 * The active backend is re-read from the native bridge after every frame,
 * so the report always reflects what ACTUALLY rendered the last frame.
 */
public final class MesrGLRendererManager {

    private static final MesrGLRendererManager INSTANCE = new MesrGLRendererManager();

    public static MesrGLRendererManager get() {
        return INSTANCE;
    }

    public enum SelectionPolicy {
        AUTO,
        FORCE_CPU,
        PREFER_GPU;

        static SelectionPolicy fromProperty(String value) {
            if (value == null) {
                return AUTO;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "cpu" -> FORCE_CPU;
                case "gpu" -> PREFER_GPU;
                default -> AUTO;
            };
        }
    }

    /** One line of the backend report (loading screen / debug overlay). */
    public record ReportLine(String label, String value) {
    }

    private volatile MesrGLRenderer renderer;
    private volatile boolean initializationAttempted;
    private volatile String initializationError;
    private SelectionPolicy policy = SelectionPolicy.AUTO;
    private final List<String> eventLog = new CopyOnWriteArrayList<>();

    private MesrGLRendererManager() {
    }

    /**
     * Initializes the renderer if needed. Safe to call repeatedly; failures
     * are remembered and reported (the caller decides how to degrade).
     */
    public synchronized MesrGLRenderer acquire(int width, int height) {
        if (renderer != null) {
            return renderer;
        }
        if (initializationAttempted && initializationError != null) {
            return null;
        }
        initializationAttempted = true;
        policy = SelectionPolicy.fromProperty(System.getProperty("fv2j3.mesrgl.backend"));

        try {
            MesrGLJNI.setPreferredBackend(switch (policy) {
                case FORCE_CPU -> 1;
                case PREFER_GPU -> 2;
                case AUTO -> 0;
            });
            long start = System.nanoTime();
            MesrGLRenderer created = new MesrGLRenderer(width, height, 1, 2, true, 0xC0FFEEL);
            double initMs = (System.nanoTime() - start) / 1e6;
            renderer = created;
            eventLog.add(String.format(Locale.ROOT,
                    "renderer initialized in %.1f ms (%dx%d, spp=1, bounces=2)",
                    initMs, width, height));
            eventLog.add("active backend: " + created.currentBackend().displayName()
                    + " | device: " + created.deviceName());
            return created;
        } catch (Throwable failure) {
            initializationError = failure.getMessage() == null
                    ? failure.getClass().getName()
                    : failure.getMessage();
            eventLog.add("renderer initialization FAILED: " + initializationError);
            return null;
        }
    }

    /** Backend report for the loading screen and debug overlay. */
    public List<ReportLine> backendReport() {
        MesrGLRenderer current = renderer;
        if (current == null) {
            return List.of(
                    new ReportLine("Renderer", "unavailable"),
                    new ReportLine("Reason", initializationError == null ? "not initialized" : initializationError),
                    new ReportLine("Hardware RT", "disabled (software algorithms only)"));
        }
        RendererBackend backend = current.currentBackend();
        return List.of(
                new ReportLine("Renderer", backend.displayName()),
                new ReportLine("CPU Assist", "enabled (scene prep, BVH, upload, readback)"),
                new ReportLine("Hardware RT", "disabled"),
                new ReportLine("GPU RT API", "not used"),
                new ReportLine("GPU Compute Backend", backend == RendererBackend.GPU_SOFTWARE_RT
                        ? "Vulkan compute (dlopen, no link-time dependency)"
                        : "inactive"),
                new ReportLine("GPU Device", current.deviceName()),
                new ReportLine("CPU Threads", String.valueOf(Runtime.getRuntime().availableProcessors())));
    }

    /**
     * Debug overlay lines: renderer state plus measured per-frame telemetry.
     * Unavailable metrics are reported as N/A by the diagnostics string.
     */
    public List<String> debugLines() {
        MesrGLRenderer current = renderer;
        if (current == null) {
            return List.of("MesrGL renderer: unavailable"
                    + (initializationError == null ? "" : " (" + initializationError + ")"));
        }
        return List.of(
                "MesrGL: " + current.currentBackend().displayName()
                        + " | " + current.framebufferWidth() + "x" + current.framebufferHeight(),
                "MesrGL frame: " + String.format(Locale.ROOT, "%.2f", current.lastFrameTimeMs()) + " ms"
                        + " | BVH nodes: " + current.bvhNodeCount(),
                "MesrGL device: " + current.deviceName(),
                "MesrGL " + current.diagnostics());
    }

    public synchronized void shutdown() {
        MesrGLRenderer current = renderer;
        if (current != null) {
            eventLog.add("renderer shutdown");
            current.close();
            renderer = null;
            initializationAttempted = false;
        }
    }

    public boolean isAvailable() {
        return renderer != null;
    }

    public String initializationError() {
        return initializationError;
    }

    public List<String> eventLogLines() {
        return List.copyOf(eventLog);
    }
}
