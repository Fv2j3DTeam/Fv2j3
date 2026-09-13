package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import io.github.fv2j3dteam.mesrgl.MesrGLRenderer;
import io.github.fv2j3dteam.mesrgl.RendererBackend;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * The Minecraft-facing MesrGL renderer: replaces the vanilla world render
 * pass with a MesrGL software-ray-traced frame.
 *
 * Pipeline (spec Phase 46):
 *
 *   Minecraft world  -[reflection extraction]-&gt;  ChunkSceneExtractor
 *                    -&gt; MesrGL scene (mesh per color group / BVH)
 *                    -&gt; MesrGLRenderer.renderFrame (CPU or GPU software RT)
 *                    -&gt; MesrGL framebuffer (RGBA8)
 *                    -&gt; MinecraftFramePresenter (glDrawPixels into the
 *                       vanilla framebuffer; GL is the display surface only)
 *                    -&gt; Minecraft display + activity overlay
 *
 * Lifecycle / threading:
 *  - initialize() and renderWorld() run on the Minecraft render thread (they
 *    are invoked from the renderWorld hook); shutdown() may run from any
 *    thread and is idempotent.
 *  - chunk extraction is budgeted per frame; the scene is uploaded and the
 *    BVH rebuilt only when the extracted chunk set actually changed, which
 *    keeps steady-state frames at pure render cost.
 *  - every failure path either falls back to the vanilla renderer for that
 *    frame (reported once, never silently) or shuts the renderer down.
 */
public final class MesrGLMinecraftRenderer {

    /** Backend selection / activation property values. */
    public static final String MODE_PROPERTY = "fv2j3.mesrgl.worldRenderer";
    private static final int INITIAL_CHUNK_BUDGET = 4096;
    private static final int STEADY_CHUNK_BUDGET = 2;
    // Full scene rebuilds re-scan every cached chunk reflectively; during the
    // first minute of a world the chunk stream would otherwise trigger a
    // rebuild per delivered chunk and starve the render thread. 2 s batches
    // the stream into a handful of rebuilds.
    private static final int SCENE_REBUILD_MIN_INTERVAL_MS = 2000;
    private static final float SUN_INTENSITY = 3.4f;
    private static final float DEFAULT_FOV_DEGREES = 70.0f;

    private static volatile MesrGLMinecraftRenderer instance;
    private static volatile boolean initializationFailed;

    private final MinecraftReflection mc;
    private final MinecraftFramePresenter presenter;
    private final ClassLoader mcClassLoader;
    private ChunkSceneExtractor extractor;

    // Phase 47 visual-quality pipeline.
    private final MinecraftSceneBuilder sceneBuilder = new MinecraftSceneBuilder();
    private final TemporalDenoiser denoiser = new TemporalDenoiser();
    private final MinecraftMaterials materials = new MinecraftMaterials();
    // Phase 47.1 visual correctness.
    private final MesrGLExposureController exposure = new MesrGLExposureController();
    private final CameraValidator cameraValidator = new CameraValidator();
    private final LightingDebug lightingDebug = new LightingDebug();
    private final float baseExposure = Float.parseFloat(
            System.getProperty("fv2j3.mesrgl.exposure.base", "0.8"));

    private MesrGLRenderer renderer;
    private Object boundWorld;          // world object the current scene belongs to
    private long pendingRebuildSinceMs;
    private boolean sceneDirty = true;
    private int lastTriangleCount;
    private int lastChunkCount;
    private long frameIndex;
    private double lastPresentMs;
    private String lastFailure;
    private boolean fallbackLogged;
    private int autoStopFrames = Integer.getInteger("fv2j3.mesrgl.autoStopFrames", 0);
    private float renderScale = Float.parseFloat(
            System.getProperty("fv2j3.mesrgl.renderScale", "0.75"));

    private MesrGLMinecraftRenderer(ClassLoader mcClassLoader) {
        this.mcClassLoader = mcClassLoader;
        this.mc = MinecraftReflection.get(mcClassLoader);
        this.presenter = new MinecraftFramePresenter(mcClassLoader);
    }

    /**
     * Process-wide instance; {@code mcClassLoader} is the classloader the
     * Minecraft classes live in (needed for reflection and LWJGL access).
     */
    public static MesrGLMinecraftRenderer acquire(ClassLoader mcClassLoader) {
        MesrGLMinecraftRenderer local = instance;
        if (local == null) {
            synchronized (MesrGLMinecraftRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new MesrGLMinecraftRenderer(mcClassLoader);
                    instance = local;
                }
            }
        }
        return local;
    }

    public static boolean isInitializationFailed() {
        return initializationFailed;
    }

    /** The live instance, or null before the first acquire. */
    public static MesrGLMinecraftRenderer current() {
        return instance;
    }

    /** True once the native renderer and reflection bindings exist. */
    public boolean isInitialized() {
        return renderer != null;
    }

    /** Creates the MesrGL renderer and the reflection bindings. Idempotent. */
    public void initialize(int windowWidth, int windowHeight) {
        if (renderer != null) {
            return;
        }
        int w = Math.max(64, (int) (windowWidth * renderScale));
        int h = Math.max(64, (int) (windowHeight * renderScale));
        MesrGLRenderer created;
        try {
            created = new MesrGLRenderer(w, h,
                    1,      // samples per pixel: interactive first-light target
                    2,      // bounces
                    true,   // deterministic
                    0xC0FFEE);
        } catch (RuntimeException ex) {
            initializationFailed = true;
            throw ex;
        }
        created.setShadowsEnabled(true);
        created.setGIEnabled(true);
        created.setMaxSamplesPerPixel(1); // fixed one-sample frames: interactive
        created.setToneMapping(4);        // ACES: soft highlight rolloff
        created.setExposure(1.6f);
        // Sky = background radiance (miss color), not geometry: an emissive
        // sky box would occlude every shadow ray from inside itself.
        created.setClearColor(0.50f, 0.68f, 0.92f);
        // Pre-size the GPU scene buffers for the worst-case extraction so the
        // render path never grows buffers mid-session (grow invalidates
        // descriptor bindings and has crashed the RADV driver).
        int reserveRadius = Integer.getInteger("fv2j3.mesrgl.radiusChunks", 4);
        int reserveMaxHeight = Integer.getInteger("fv2j3.mesrgl.maxHeight", 128);
        long chunks = (2L * (reserveRadius + 1)) * (2L * (reserveRadius + 1)); // outer ring included
        long tris = chunks * 16L * 16L * (reserveMaxHeight / 4L); // ~surface faces
        long triBytes = tris * 112L * 3L / 2L;                   // +50% headroom
        long bvhBytes = (tris * 3L / 2L / 2L) * 48L * 3L / 2L;
        created.reserveSceneCapacity(triBytes, bvhBytes, 4096L, 4096L, w, h);
        log("reserved GPU scene capacity: tris<=" + tris + " geometry=" + (triBytes / 1048576)
                + "MB bvh=" + (bvhBytes / 1048576) + "MB");
        this.renderer = created;
        int radius = Integer.getInteger("fv2j3.mesrgl.radiusChunks", 4);
        int maxHeight = Integer.getInteger("fv2j3.mesrgl.maxHeight", 128);
        this.extractor = new ChunkSceneExtractor(mc, radius, maxHeight);
        log("initialized software-RT renderer " + w + "x" + h
                + " scale=" + renderScale + " radius=" + radius + " chunks");
    }

    /**
     * Renders the world for the current frame. Called from the renderWorld
     * hook on the render thread. Returns true when MesrGL produced the frame
     * (the vanilla world render must be skipped); false falls back to vanilla.
     */
    public boolean renderWorld(Object mcInstance, float partialTicks) {
        if (renderer == null) {
            return false;
        }
        lastMinecraftInstance = mcInstance;
        Object world = mc.currentWorld(mcInstance);
        Object entity = mc.renderViewEntity(mcInstance);
        if (world == null || entity == null || world != boundWorld) {
            if (world != boundWorld) {
                extractor.reset();
                boundWorld = world;
                sceneDirty = true;
                denoiser.reset();
            }
            if (world == null || entity == null) {
                return false; // main menu / no world: vanilla handles it
            }
        }

        // Apply the current quality preset (PART 8).
        QualityPresets preset = QualityPresets.current();
        float effectiveScale = Math.max(0.4f, Math.min(1.0f, renderScale * preset.renderScale));

        // Window resize handling (spec: window resize must be handled).
        int windowW = DisplayAccess.width(mcClassLoader);
        int windowH = DisplayAccess.height(mcClassLoader);
        if (windowW <= 0 || windowH <= 0) {
            return false;
        }
        lastWindowW = windowW; lastWindowH = windowH;
        int desiredW = Math.max(64, (int) (windowW * effectiveScale));
        int desiredH = Math.max(64, (int) (windowH * effectiveScale));
        if (desiredW != renderer.framebufferWidth() || desiredH != renderer.framebufferHeight()) {
            renderer.resize(desiredW, desiredH);
            sceneDirty = true;
            denoiser.reset();
        }
        // Apply the preset's spp / max-bounces / denoiser settings every
        // frame (cheap; the native side is a single JNI setter call).
        renderer.setSamplesPerPixel(preset.samplesPerPixel);
        renderer.setMaxBounces(preset.maxBounces);
        denoiser.setHistoryWeight(preset.denoiser ? 0.65f : 0.0f);

        long now = System.currentTimeMillis();
        if (frameIndex % 600 == 1 && lastChunkCount == 0) {
            List<Object> probe = mc.loadedChunks(world);
            log("extract debug: loadedChunks=" + probe.size()
                    + " first=" + (probe.isEmpty() ? "-" : mc.chunkX(probe.get(0)) + "," + mc.chunkZ(probe.get(0)))
                    + " cached=" + extractor.cachedChunkCount());
        }
        int budget = sceneDirty ? INITIAL_CHUNK_BUDGET : STEADY_CHUNK_BUDGET;
        MinecraftReflection.CameraRay ray = mc.cameraRay(entity, partialTicks);
        int extracted = extractor.update(world, ray.eyeX(), ray.eyeZ(), budget, now);
        if (extracted > 0) {
            sceneDirty = true;
        }
        boolean rebuildDue = sceneDirty
                && (now - pendingRebuildSinceMs) >= SCENE_REBUILD_MIN_INTERVAL_MS;
        if (rebuildDue) {
            rebuildScene();
            pendingRebuildSinceMs = now;
        }

        setCamera(ray, windowW, windowH);
        lastCameraEyeX = ray.eyeX(); lastCameraEyeY = ray.eyeY(); lastCameraEyeZ = ray.eyeZ();
        lastCameraLookX = ray.lookX(); lastCameraLookY = ray.lookY(); lastCameraLookZ = ray.lookZ();
        cameraValidator.recordMcCamera(DEFAULT_FOV_DEGREES,
                ray.eyeX(), ray.eyeY(), ray.eyeZ(),
                ray.lookX(), ray.lookY(), ray.lookZ(),
                "first-person");
        cameraValidator.recordMesrglCamera(DEFAULT_FOV_DEGREES,
                ray.eyeX(), ray.eyeY(), ray.eyeZ(),
                ray.lookX(), ray.lookY(), ray.lookZ());
        if (frameIndex % 120 == 1) {
            log("camera eye=(" + String.format("%.2f,%.2f,%.2f", ray.eyeX(), ray.eyeY(), ray.eyeZ())
                    + ") look=(" + String.format("%.3f,%.3f,%.3f", ray.lookX(), ray.lookY(), ray.lookZ()) + ")");
        }
        // Apply the auto-exposure (PART 1) before the render so the next
        // frame's readback is sampled against the right EV. The exposure
        // is updated AFTER the readback, so the first frame uses the
        // base exposure.
        renderer.setExposure(baseExposure * exposure.currentExposure());
        double renderMs = renderer.renderFrame();
        ByteBuffer frame = renderer.readFramebufferRgba8();
        if (frame == null) {
            reportFailure("framebuffer readback returned null");
            return false;
        }

        // PART 1: feed the readback into the exposure controller so the
        // NEXT frame uses a properly balanced exposure. Avoids the dark
        // / blown-out frame problem and removes the need to hardcode
        // exposure per scene.
        if (exposure.isEnabled()) {
            exposure.updateFromFrame(frame, renderer.framebufferWidth(),
                    renderer.framebufferHeight());
        }

        // PART 7: temporal denoiser (in-place).
        if (preset.denoiser) {
            denoiser.denoiseInPlace(frame, renderer.framebufferWidth(),
                    renderer.framebufferHeight(), null);
            denoiser.spatialFilter(frame, renderer.framebufferWidth(),
                    renderer.framebufferHeight());
        }

        long p0 = System.nanoTime();
        presenter.present(frame, renderer.framebufferWidth(), renderer.framebufferHeight(),
                windowW, windowH, mc.fontRenderer(mcInstance), overlayText());
        lastPresentMs = (System.nanoTime() - p0) / 1e6;
        lastFrameMs = renderMs;
        frameIndex++;

        if (frameIndex % 120 == 1) {
            RendererBackend backend = renderer.activeBackend();
            log("frame " + frameIndex + " backend=" + backend
                    + " quality=" + preset.label()
                    + " scale=" + String.format("%.2f", effectiveScale)
                    + " renderMs=" + String.format("%.1f", renderMs)
                    + " presentMs=" + String.format("%.1f", lastPresentMs)
                    + " tris=" + lastTriangleCount + " chunks=" + lastChunkCount
                    + " diag=[" + renderer.diagnostics() + "]");
        }
        dumpFrameIfConfigured(frame);
        autoStopIfConfigured();
        return true;
    }

    /**
     * Graceful-shutdown verification: after N rendered MesrGL frames
     * (-Dfv2j3.mesrgl.autoStopFrames, 0 = disabled) the vanilla shutdown
     * path (bib.h()V, the "Quit Game" code path) is triggered so the run
     * exercises the full stop chain: renderer shutdown, loader onStop with
     * config flush, monitor stop, JVM exit 0.
     */
    private void autoStopIfConfigured() {
        int stopAfter = autoStopFrames;
        if (stopAfter > 0 && frameIndex >= stopAfter) {
            autoStopFrames = 0; // once
            log("auto-stop after " + frameIndex + " frames: triggering Minecraft shutdown");
            mc.shutdownMinecraft(lastMinecraftInstance);
        }
    }

    /**
     * Optional pixel-level proof: dumps the exact RGBA8 buffer that was
     * presented to the vanilla framebuffer as a BMP. Active when
     * -Dfv2j3.mesrgl.dumpFrame is set (value = file path prefix).
     */
    private void dumpFrameIfConfigured(ByteBuffer frame) {
        String prefix = System.getProperty("fv2j3.mesrgl.dumpFrame");
        if (prefix == null || frameIndex % 120 != 1) {
            return;
        }
        int w = renderer.framebufferWidth();
        int h = renderer.framebufferHeight();
        int rowBytes = w * 3;
        int pad = (4 - (rowBytes % 4)) % 4;
        int dataSize = (rowBytes + pad) * h;
        java.nio.ByteBuffer bmp = java.nio.ByteBuffer.allocate(54 + dataSize);
        java.nio.ByteBuffer le = bmp.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        le.put((byte) 'B').put((byte) 'M');
        le.putInt(54 + dataSize).putShort((short) 0).putShort((short) 0).putInt(54);
        le.putInt(40).putInt(w).putInt(h).putShort((short) 1).putShort((short) 24);
        le.putInt(0).putInt(dataSize).putInt(2835).putInt(2835).putInt(0).putInt(0);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                le.put(frame.get(i + 2)).put(frame.get(i + 1)).put(frame.get(i));
            }
            for (int p = 0; p < pad; p++) {
                le.put((byte) 0);
            }
        }
        try {
            java.nio.file.Path path = java.nio.file.Path.of(prefix + "-" + frameIndex + ".bmp");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, bmp.array());
            log("frame buffer dumped to " + path);
        } catch (java.io.IOException ex) {
            log("frame dump failed: " + ex);
        }
    }

    private double lastFrameMs;
    private double lastCameraEyeX, lastCameraEyeY, lastCameraEyeZ;
    private double lastCameraLookX, lastCameraLookY, lastCameraLookZ;
    private Object lastMinecraftInstance;
    private int lastWindowW, lastWindowH;

    private void rebuildScene() {
        ChunkSceneExtractor.ExtractedScene scene = extractor.buildScene();
        QualityPresets preset = QualityPresets.current();
        // The builder is responsible for: materials, lights, skydome,
        // weather, clouds, water, and the BVH build. It calls resetScene
        // internally.
        try {
            MinecraftSceneBuilder.Built built = sceneBuilder.build(
                    renderer, scene, mc, boundWorld, lastMinecraftInstance,
                    lastCameraEyeX, lastCameraEyeY, lastCameraEyeZ,
                    System.currentTimeMillis(),  // world time proxy; overwritten when reflection gives it
                    preset.cloudTier);
            lastTriangleCount = built.triangleCount;
            lastChunkCount = scene.chunkCount;
        } catch (RuntimeException ex) {
            reportFailure("scene build failed: " + ex.getMessage());
            return;
        }
        dumpSceneIfConfigured(scene);
        sceneDirty = false;
    }

    private void setCamera(MinecraftReflection.CameraRay ray, int windowW, int windowH) {
        float tx = (float) (ray.eyeX() + ray.lookX());
        float ty = (float) (ray.eyeY() + ray.lookY());
        float tz = (float) (ray.eyeZ() + ray.lookZ());
        float fovY = (float) Math.toRadians(DEFAULT_FOV_DEGREES);
        float aspect = windowH == 0 ? 1.0f : (float) windowW / (float) windowH;
        renderer.setCamera((float) ray.eyeX(), (float) ray.eyeY(), (float) ray.eyeZ(),
                tx, ty, tz,
                0f, 1f, 0f,
                fovY, aspect, 0.05f, 1024.0f);
    }

    /**
     * Reproduction helper: writes the exact extracted scene (color groups,
     * sky box, sun, camera) for the diag_scaled file-replay mode.
     */
    private void dumpSceneIfConfigured(ChunkSceneExtractor.ExtractedScene scene) {
        String path = System.getProperty("fv2j3.mesrgl.dumpScene");
        if (path == null) {
            return;
        }
        try (var out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(
                java.nio.file.Files.newOutputStream(java.nio.file.Path.of(path))))) {
            out.writeInt(0x4D53474C); // MSGC
            out.writeInt(scene.groups.size());
            for (ChunkSceneExtractor.ColorGroup group : scene.groups) {
                out.writeInt(group.rgb);
                out.writeInt(group.positions.size());
                for (float f : group.positionsArray()) out.writeFloat(f);
                out.writeInt(group.indices.size());
                for (int i : group.indicesArray()) out.writeInt(i);
            }
            out.writeFloat(SUN_INTENSITY);
            out.writeFloat((float) lastCameraEyeX);
            out.writeFloat((float) lastCameraEyeY);
            out.writeFloat((float) lastCameraEyeZ);
            out.writeFloat((float) lastCameraLookX);
            out.writeFloat((float) lastCameraLookY);
            out.writeFloat((float) lastCameraLookZ);
            out.writeInt(Integer.getInteger("fv2j3.mesrgl.radiusChunks", 4));
            log("scene dumped to " + path + " (groups=" + scene.groups.size()
                    + " tris=" + lastTriangleCount + ")");
        } catch (java.io.IOException ex) {
            log("scene dump failed: " + ex);
        }
    }

    private String overlayText() {
        RendererBackend backend = renderer.activeBackend();
        QualityPresets preset = QualityPresets.current();
        return "MesrGL ACTIVE | " + backend + " | " + renderer.deviceName()
                + " | " + String.format("%.1f", lastFrameMs) + "ms RT"
                + " | tris " + lastTriangleCount
                + " | chunks " + lastChunkCount
                + " | quality " + preset.label()
                + " | spp " + preset.samplesPerPixel
                + " | bounces " + preset.maxBounces
                + " | denoise " + (preset.denoiser ? "on" : "off")
                + " | clouds " + preset.cloudTier
                + " | exp " + String.format("%.2f", exposure.currentExposure())
                + " | luma " + String.format("%.2f", exposure.lastMeasuredLuma())
                + " | " + cameraValidator.overlayText()
                + " | " + lightingDebug.mode();
    }

    /** Marks the renderer dead and logs the cause; vanilla takes over. */
    private void reportFailure(String what) {
        lastFailure = what;
        if (!fallbackLogged) {
            fallbackLogged = true;
            log("FALLBACK TO VANILLA: " + what);
        }
    }

    public String lastFailure() {
        return lastFailure;
    }

    /** Releases the native renderer. Safe from any thread, idempotent. */
    public void shutdown() {
        MesrGLRenderer local;
        synchronized (this) {
            local = renderer;
            renderer = null;
        }
        if (local != null) {
            local.close();
            log("shutdown complete");
        }
    }

    private static void log(String message) {
        System.out.println("[MesrGL] " + message);
    }

    /** Display size via LWJGL2 reflection (window dimensions in pixels). */
    private static final class DisplayAccess {
        static int width(ClassLoader cl) {
            return displayDim(cl, "getWidth");
        }

        static int height(ClassLoader cl) {
            return displayDim(cl, "getHeight");
        }

        private static int displayDim(ClassLoader cl, String method) {
            try {
                Class<?> display = cl.loadClass("org.lwjgl.opengl.Display");
                return (Integer) display.getMethod(method).invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Display size unavailable: " + e, e);
            }
        }
    }
}
