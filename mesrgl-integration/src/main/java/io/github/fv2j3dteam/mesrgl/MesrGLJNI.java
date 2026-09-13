package io.github.fv2j3dteam.mesrgl;

import java.nio.ByteBuffer;

/**
 * JNI bridge to the Fv2j3 MesrGL native bridge (libMesrGLBridge).
 * Provides coarse-grained operations to minimize JNI overhead.
 *
 * Backend kinds reported by {@link #getBackendKind(long)}:
 * <ul>
 *   <li>0 = None (invalid renderer)</li>
 *   <li>1 = CPU Software RT (MesrGL reference renderer)</li>
 *   <li>2 = GPU Software RT (MesrGL kernels on a Vulkan compute device)</li>
 * </ul>
 */
public final class MesrGLJNI {

    static {
        MesrGLNativeLoader.load();
    }

    private MesrGLJNI() {
    }

    /**
     * Sets the preferred backend before {@link #createRenderer()}.
     * 0 = auto (GPU when available, explicit CPU fallback),
     * 1 = force CPU (verification path),
     * 2 = prefer GPU.
     */
    public static native void setPreferredBackend(int mode);

    // ============================================================
    // Renderer lifecycle
    // ============================================================

    /**
     * Creates a new MesrGL renderer instance.
     *
     * @return opaque native handle to the renderer, or 0 on failure
     */
    public static native long createRenderer();

    /**
     * Destroys a MesrGL renderer instance.
     *
     * @param handle native renderer handle
     */
    public static native void destroyRenderer(long handle);

    /**
     * Initializes the renderer with settings.
     *
     * @param handle       renderer handle
     * @param width        initial framebuffer width
     * @param height       initial framebuffer height
     * @param spp          samples per pixel
     * @param maxBounces   maximum ray bounces
     * @param numThreads   number of worker threads (0 = auto)
     * @param deterministic whether to use deterministic rendering
     * @param rngSeed      random seed for deterministic mode
     * @return true on success, false on failure
     */
    public static native boolean initializeRenderer(
            long handle,
            int width,
            int height,
            int spp,
            int maxBounces,
            int numThreads,
            boolean deterministic,
            long rngSeed
    );

    /**
     * Resizes the renderer's framebuffer.
     *
     * @param handle renderer handle
     * @param width  new width
     * @param height new height
     * @return true on success
     */
    public static native boolean resizeRenderer(long handle, int width, int height);

    // ============================================================
    // Framebuffer operations
    // ============================================================

    /**
     * Gets the framebuffer width.
     */
    public static native int getFramebufferWidth(long handle);

    /**
     * Gets the framebuffer height.
     */
    public static native int getFramebufferHeight(long handle);

    /**
     * Copies the framebuffer color data to a direct ByteBuffer.
     * Buffer must have capacity >= width * height * 4 (RGBA8).
     *
     * @param handle renderer handle
     * @param buffer direct ByteBuffer to receive RGBA8 data
     * @return true on success
     */
    public static native boolean readFramebufferRGBA8(long handle, ByteBuffer buffer);

    /**
     * Copies the framebuffer color data to a float array (RGBA32F).
     * Array must have length >= width * height * 4.
     *
     * @param handle renderer handle
     * @param buffer float array to receive RGBA32F data
     * @return true on success
     */
    public static native boolean readFramebufferRGBA32F(long handle, float[] buffer);

    // ============================================================
    // Scene management
    // ============================================================

    /**
     * Creates a new scene.
     *
     * @param handle renderer handle
     * @return scene handle, or 0 on failure
     */
    public static native long createScene(long handle);

    /**
     * Destroys a scene.
     *
     * @param handle   renderer handle
     * @param sceneHandle scene handle
     */
    public static native void destroyScene(long handle, long sceneHandle);

    /**
     * Adds a mesh to the scene.
     *
     * @param handle       renderer handle
     * @param sceneHandle  scene handle
     * @param positions    flat float array of vertex positions (x,y,z per vertex)
     * @param normals      flat float array of vertex normals (x,y,z per vertex)
     * @param texCoords    flat float array of texture coordinates (u,v per vertex)
     * @param indices      int array of triangle indices
     * @param materialId   material ID for this mesh
     * @return mesh ID, or -1 on failure
     */
    public static native int addMesh(
            long handle,
            long sceneHandle,
            float[] positions,
            float[] normals,
            float[] texCoords,
            int[] indices,
            int materialId
    );

    /**
     * Removes a mesh from the scene.
     */
    public static native boolean removeMesh(long handle, long sceneHandle, int meshId);

    /**
     * Updates a mesh's transform.
     *
     * @param handle      renderer handle
     * @param sceneHandle scene handle
     * @param meshId      mesh ID
     * @param transform   16 floats (column-major 4x4 matrix)
     */
    public static native boolean updateMeshTransform(long handle, long sceneHandle, int meshId, float[] transform);

    /**
     * Creates a material.
     *
     * @param handle renderer handle
     * @param albedoR albedo red
     * @param albedoG albedo green
     * @param albedoB albedo blue
     * @param roughness roughness [0,1]
     * @param metallic metallic [0,1]
     * @param emission emission [0,1000]
     * @param transmission transmission [0,1]
     * @param ior index of refraction [1,3]
     * @return material ID, or -1 on failure
     */
    public static native int createMaterial(
            long handle,
            float albedoR, float albedoG, float albedoB,
            float roughness, float metallic,
            float emission,
            float transmission, float ior
    );

    /**
     * Updates a material.
     */
    public static native boolean updateMaterial(
            long handle,
            int materialId,
            float albedoR, float albedoG, float albedoB,
            float roughness, float metallic,
            float emission,
            float transmission, float ior
    );

    /**
     * Creates a texture.
     *
     * @param handle   renderer handle
     * @param width    texture width
     * @param height   texture height
     * @param channels number of channels (3=RGB, 4=RGBA)
     * @param data     pixel data (byte array, length = width * height * channels)
     * @return texture ID, or -1 on failure
     */
    public static native int createTexture(
            long handle,
            int width,
            int height,
            int channels,
            byte[] data
    );

    /**
     * Updates texture data.
     */
    public static native boolean updateTexture(
            long handle,
            int textureId,
            int width,
            int height,
            int channels,
            byte[] data
    );

    /**
     * Destroys a texture.
     */
    public static native void destroyTexture(long handle, int textureId);

    /**
     * Assigns a texture to a material.
     */
    public static native boolean setMaterialTexture(long handle, int materialId, int textureId);

    /**
     * Builds the acceleration structure (BVH) for the scene.
     */
    public static native boolean buildAccelerationStructure(long handle, long sceneHandle);

    /**
     * Updates the acceleration structure for dynamic scenes.
     */
    public static native boolean updateAccelerationStructure(long handle, long sceneHandle);

    // ============================================================
    // Camera
    // ============================================================

    /**
     * Sets the camera for rendering.
     *
     * @param handle     renderer handle
     * @param sceneHandle scene handle
     * @param position   camera position (x,y,z)
     * @param target     camera target (x,y,z)
     * @param up         camera up vector (x,y,z)
     * @param fovY       vertical field of view in radians
     * @param aspect     aspect ratio (width/height)
     * @param zNear      near plane
     * @param zFar       far plane
     */
    public static native void setCamera(
            long handle,
            long sceneHandle,
            float posX, float posY, float posZ,
            float targetX, float targetY, float targetZ,
            float upX, float upY, float upZ,
            float fovY,
            float aspect,
            float zNear,
            float zFar
    );

    // ============================================================
    // Lights
    // ============================================================

    /**
     * Adds a directional light.
     *
     * @param handle     renderer handle
     * @param sceneHandle scene handle
     * @param dirX       direction X
     * @param dirY       direction Y
     * @param dirZ       direction Z
     * @param colorR     color red
     * @param colorG     color green
     * @param colorB     color blue
     * @param intensity  intensity
     * @return light ID
     */
    public static native int addDirectionalLight(
            long handle,
            long sceneHandle,
            float dirX, float dirY, float dirZ,
            float colorR, float colorG, float colorB,
            float intensity
    );

    /**
     * Adds a point light.
     */
    public static native int addPointLight(
            long handle,
            long sceneHandle,
            float posX, float posY, float posZ,
            float colorR, float colorG, float colorB,
            float intensity,
            float range
    );

    /**
     * Removes a light.
     */
    public static native void removeLight(long handle, long sceneHandle, int lightId);

    // ============================================================
    // Rendering
    // ============================================================

    /**
     * Renders one frame.
     *
     * @param handle     renderer handle
     * @param sceneHandle scene handle
     * @return render time in milliseconds
     */
    public static native double renderFrame(long handle, long sceneHandle);

    /**
     * Renders one frame progressively (for progressive rendering mode).
     *
     * @param handle       renderer handle
     * @param sceneHandle  scene handle
     * @param frameIndex   frame index (for accumulation)
     * @return render time in milliseconds
     */
    public static native double renderFrameProgressive(long handle, long sceneHandle, int frameIndex);

    /**
     * Gets the total number of rays traced in the last frame.
     */
    public static native long getTotalRaysTraced(long handle);

    /**
     * Gets the last render time in milliseconds.
     */
    public static native double getLastRenderTimeMs(long handle);

    /**
     * Gets the BVH node count.
     */
    public static native int getBVHNodeCount(long handle, long sceneHandle);

    // ============================================================
    // Settings
    // ============================================================

    /**
     * Sets samples per pixel.
     */
    public static native void setSamplesPerPixel(long handle, int spp);

    /** Caps adaptive sampling; 1 = fixed one-sample interactive mode. */
    public static native boolean setMaxSamplesPerPixel(long handle, int v);

    /** Background radiance for rays that miss the scene (the software-RT sky). */
    public static native boolean setClearColor(long handle, float r, float g, float b);

    /** Pre-allocates GPU scene buffers so the render path never grows. */
    public static native boolean reserveSceneCapacity(long handle, long triangleBytes,
                                                      long bvhBytes, long materialBytes, long lightBytes,
                                                      int width, int height);

    /**
     * Sets max bounces.
     */
    public static native void setMaxBounces(long handle, int maxBounces);

    /**
     * Sets max diffuse bounces.
     */
    public static native void setMaxDiffuseBounces(long handle, int max);

    /**
     * Sets max specular bounces.
     */
    public static native void setMaxSpecularBounces(long handle, int max);

    /**
     * Sets max transmission bounces.
     */
    public static native void setMaxTransmissionBounces(long handle, int max);

    /**
     * Enables/disables shadows.
     */
    public static native void setShadowsEnabled(long handle, boolean enabled);

    /**
     * Enables/disables global illumination.
     */
    public static native void setGIEnabled(long handle, boolean enabled);

    /**
     * Enables/disables adaptive sampling.
     */
    public static native void setAdaptiveSampling(long handle, boolean enabled);

    /**
     * Sets adaptive sampling variance threshold.
     */
    public static native void setVarianceThreshold(long handle, float threshold);

    /**
     * Sets denoising enabled.
     */
    public static native void setDenoisingEnabled(long handle, boolean enabled);

    /**
     * Sets tone mapping mode.
     * 0=None, 1=Linear, 2=Reinhard, 3=ReinhardExtended, 4=ACES, 5=Uncharted2, 6=AgX
     */
    public static native void setToneMapping(long handle, int mode);

    /**
     * Sets exposure.
     */
    public static native void setExposure(long handle, float exposure);

    /**
     * Sets gamma.
     */
    public static native void setGamma(long handle, float gamma);

    /**
     * Sets number of threads.
     */
    public static native void setNumThreads(long handle, int threads);

    /**
     * Sets tile size.
     */
    public static native void setTileSize(long handle, int tileSize);

    // ============================================================
    // Diagnostics
    // ============================================================

    /**
     * Gets diagnostic information string (backend, device, last frame timings).
     */
    public static native String getDiagnosticInfo(long handle);

    /**
     * Gets the device name that actually rendered the last frame.
     */
    public static native String getDeviceName(long handle);

    /**
     * Gets renderer backend kind (0=None, 1=CPU Software RT, 2=GPU Software RT).
     * Reports what ACTUALLY rendered the last frame, never what was requested.
     */
    public static native int getBackendKind(long handle);

    // ============================================================
    // State
    // ============================================================

    /**
     * Renderer state enum.
     * 0=UNINITIALIZED, 1=INITIALIZING, 2=READY, 3=FAILED, 4=SHUTTING_DOWN
     */
    public static native int getRendererState(long handle);

    /**
     * Gets last error message.
     */
    public static native String getLastError(long handle);
}