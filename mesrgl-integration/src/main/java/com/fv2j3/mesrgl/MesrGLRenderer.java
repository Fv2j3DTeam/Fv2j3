package com.fv2j3.mesrgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * High-level wrapper around the MesrGL native bridge.
 *
 * Responsibilities (CPU assist, per the architecture):
 *  - renderer lifecycle and settings
 *  - scene construction from Minecraft-facing data
 *  - bulk framebuffer readback (single memcpy per frame; no per-pixel JNI)
 *  - measured telemetry exposure (never fabricated)
 *
 * The GPU Software RT executor runs MesrGL's software ray-tracing kernels
 * on a Vulkan compute device; the CPU Software RT renderer is the always
 * available reference and fallback. {@link #activeBackend()} reports what
 * actually rendered the last frame.
 */
public final class MesrGLRenderer implements AutoCloseable {

    private long handle;
    private int width;
    private int height;
    private ByteBuffer frameBuffer;
    private RendererBackend lastBackend = RendererBackend.NONE;

    public MesrGLRenderer(int width, int height, int spp, int maxBounces,
                          boolean deterministic, long rngSeed) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        // Do NOT call setPreferredBackend(0) here: callers may have set a
        // preferred backend (e.g. tests forcing CPU). The native bridge
        // defaults to "auto" (GPU when available) when the env var is
        // absent, which is the right default for production launchers.
        this.handle = MesrGLJNI.createRenderer();
        if (handle == 0) {
            throw new IllegalStateException("MesrGL renderer creation failed");
        }
        boolean ok = MesrGLJNI.initializeRenderer(handle, this.width, this.height,
                spp, maxBounces, 0, deterministic, rngSeed);
        if (!ok) {
            String error = MesrGLJNI.getLastError(handle);
            MesrGLJNI.destroyRenderer(handle);
            handle = 0;
            throw new IllegalStateException("MesrGL renderer initialization failed: " + error);
        }
        this.frameBuffer = ByteBuffer.allocateDirect(this.width * this.height * 4)
                .order(ByteOrder.nativeOrder());
    }

    // --------------------------------------------------------
    // Scene construction (CPU assist)
    // --------------------------------------------------------

    /** Creates the scene token (the bridge keeps one scene per renderer). */
    public long createScene() {
        return MesrGLJNI.createScene(handle);
    }

    /**
     * Replaces the scene with a fresh empty one: materials and lights are
     * scene-scoped, so a full scene rebuild re-creates them. This is the
     * rebuild primitive for dynamic worlds (never accumulate stale meshes).
     */
    public void resetScene() {
        MesrGLJNI.destroyScene(handle, 1);
    }

    /**
     * Adds a mesh; returns the mesh id used as its material id index.
     * Arrays are flat: positions (x,y,z), normals (x,y,z), texcoords (u,v).
     */
    public int addMesh(float[] positions, float[] normals, float[] texCoords,
                       int[] indices, int materialId) {
        return MesrGLJNI.addMesh(handle, 1, positions, normals, texCoords, indices, materialId);
    }

    public int createMaterial(float r, float g, float b, float roughness, float metallic,
                              float emission, float transmission, float ior) {
        return MesrGLJNI.createMaterial(handle, r, g, b, roughness, metallic,
                emission, transmission, ior);
    }

    public boolean updateMaterial(int materialId, float r, float g, float b, float roughness,
                                  float metallic, float emission, float transmission, float ior) {
        return MesrGLJNI.updateMaterial(handle, materialId, r, g, b, roughness, metallic,
                emission, transmission, ior);
    }

    public int addDirectionalLight(float dx, float dy, float dz,
                                   float r, float g, float b, float intensity) {
        return MesrGLJNI.addDirectionalLight(handle, 1, dx, dy, dz, r, g, b, intensity);
    }

    public int addPointLight(float px, float py, float pz,
                             float r, float g, float b, float intensity, float range) {
        return MesrGLJNI.addPointLight(handle, 1, px, py, pz, r, g, b, intensity, range);
    }

    public void setCamera(float px, float py, float pz,
                          float tx, float ty, float tz,
                          float ux, float uy, float uz,
                          float fovYRadians, float aspect, float zNear, float zFar) {
        MesrGLJNI.setCamera(handle, 1, px, py, pz, tx, ty, tz, ux, uy, uz,
                fovYRadians, aspect, zNear, zFar);
    }

    /** Builds the BVH on the CPU (MesrGL) and uploads the flattened scene. */
    public boolean buildAccelerationStructure() {
        return MesrGLJNI.buildAccelerationStructure(handle, 1);
    }

    // --------------------------------------------------------
    // Rendering
    // --------------------------------------------------------

    /** Renders one frame; returns the measured frame time in milliseconds. */
    public double renderFrame() {
        double time = MesrGLJNI.renderFrame(handle, 1);
        lastBackend = RendererBackend.fromNativeKind(MesrGLJNI.getBackendKind(handle));
        return time;
    }

    /**
     * Bulk framebuffer readback into a reusable direct ByteBuffer (RGBA8).
     * No per-pixel JNI, no temporary arrays.
     */
    public ByteBuffer readFramebufferRgba8() {
        boolean ok = MesrGLJNI.readFramebufferRGBA8(handle, frameBuffer);
        if (!ok) {
            return null;
        }
        return frameBuffer;
    }

    public void resize(int width, int height) {
        if (MesrGLJNI.resizeRenderer(handle, width, height)) {
            this.width = width;
            this.height = height;
            this.frameBuffer = ByteBuffer.allocateDirect(width * height * 4)
                    .order(ByteOrder.nativeOrder());
        }
    }

    // --------------------------------------------------------
    // Settings
    // --------------------------------------------------------

    public void setSamplesPerPixel(int spp) {
        MesrGLJNI.setSamplesPerPixel(handle, spp);
    }

    /** Caps adaptive sampling; 1 disables it (fixed one-sample frames). */
    public void setMaxSamplesPerPixel(int maxSpp) {
        MesrGLJNI.setMaxSamplesPerPixel(handle, maxSpp);
    }

    public void setExposure(float exposure) {
        MesrGLJNI.setExposure(handle, exposure);
    }

    /** Background radiance for missed rays (environment color). */
    public void setClearColor(float r, float g, float b) {
        MesrGLJNI.setClearColor(handle, r, g, b);
    }

    /** Pre-allocates GPU scene buffers (see ChunkSceneExtractor estimates). */
    public void reserveSceneCapacity(long triangleBytes, long bvhBytes,
                                     long materialBytes, long lightBytes, int width, int height) {
        MesrGLJNI.reserveSceneCapacity(handle, triangleBytes, bvhBytes, materialBytes, lightBytes,
                width, height);
    }

    public void setMaxBounces(int bounces) {
        MesrGLJNI.setMaxBounces(handle, bounces);
    }

    public void setShadowsEnabled(boolean enabled) {
        MesrGLJNI.setShadowsEnabled(handle, enabled);
    }

    public void setGIEnabled(boolean enabled) {
        MesrGLJNI.setGIEnabled(handle, enabled);
    }

    public void setToneMapping(int mode) {
        MesrGLJNI.setToneMapping(handle, mode);
    }

    // --------------------------------------------------------
    // Telemetry (measured values only; the bridge reports N/A
    // through diagnostics when a metric is unavailable)
    // --------------------------------------------------------

    public RendererBackend activeBackend() {
        return lastBackend;
    }

    public RendererBackend currentBackend() {
        return RendererBackend.fromNativeKind(MesrGLJNI.getBackendKind(handle));
    }

    public String deviceName() {
        return MesrGLJNI.getDeviceName(handle);
    }

    public String diagnostics() {
        return MesrGLJNI.getDiagnosticInfo(handle);
    }

    public double lastFrameTimeMs() {
        return MesrGLJNI.getLastRenderTimeMs(handle);
    }

    public int bvhNodeCount() {
        return MesrGLJNI.getBVHNodeCount(handle, 1);
    }

    public int framebufferWidth() {
        return width;
    }

    public int framebufferHeight() {
        return height;
    }

    @Override
    public void close() {
        if (handle != 0) {
            MesrGLJNI.destroyRenderer(handle);
            handle = 0;
        }
    }
}
