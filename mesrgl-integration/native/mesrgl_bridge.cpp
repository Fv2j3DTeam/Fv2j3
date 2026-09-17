// ============================================================
// MesrGLBridge - JNI implementation of com.fv2j3.mesrgl.MesrGLJNI.
//
// Renderer policy:
//   - MesrGL's CPU software ray tracer is always constructed and is
//     the correctness reference and fallback (explicit, never faked).
//   - When the GPU executor initializes (Vulkan compute, dlopen),
//     frames are rendered by executing MesrGL's software RT kernels
//     on the GPU; MesrGL still performs CPU assist work: scene
//     preparation, BVH construction, flattening, readback handling.
//   - getBackendKind() reports what ACTUALLY rendered the last frame.
//
// Backends are selected via setPreferredBackend() (Java) or the
// MESRGL_BACKEND environment variable (auto|cpu|gpu).
// ============================================================

#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/Framebuffer.hpp"
#include "MesrGL/GpuExecutor.hpp"

#include <jni.h>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "gpu_executor.hpp"

namespace {

// Backend kinds reported through getBackendKind().
constexpr jint BACKEND_NONE = 0;
constexpr jint BACKEND_CPU = 1;
constexpr jint BACKEND_GPU = 2;

// Renderer states.
constexpr jint STATE_UNINITIALIZED = 0;
constexpr jint STATE_INITIALIZING = 1;
constexpr jint STATE_READY = 2;
constexpr jint STATE_FAILED = 3;
constexpr jint STATE_SHUTTING_DOWN = 4;

enum class PreferredBackend { Auto, CPU, GPU };

PreferredBackend preferredBackend() {
    const char* env = std::getenv("MESRGL_BACKEND");
    if (env && std::strcmp(env, "cpu") == 0) return PreferredBackend::CPU;
    if (env && std::strcmp(env, "gpu") == 0) return PreferredBackend::GPU;
    return PreferredBackend::Auto;
}

struct BridgeRenderer {
    std::mutex mutex;

    // CPU reference / fallback renderer (MesrGL). Held by unique_ptr because
    // SoftwareRayTracer exposes proxy reference members (non-assignable).
    std::unique_ptr<MesrGL::SoftwareRayTracer> tracer = std::make_unique<MesrGL::SoftwareRayTracer>();
    std::shared_ptr<MesrGL::Scene> scene;
    MesrGL::Framebuffer framebuffer;
    MesrGL::RenderSettings settings;
    MesrGL::Camera camera;
    bool cameraSet = false;
    bool sceneBuilt = false;
    uint64_t sceneVersion = 1;      // bumped on every scene mutation
    uint64_t gpuUploadedVersion = 0;

    // GPU execution substrate (Fv2j3 integration layer).
    MesrGLBridge::GpuExecutor executor;
    MesrGL::GpuSceneData gpuScene;
    bool gpuActive = false;
    std::string gpuInitError;

    int width = 0;
    int height = 0;
    bool initialized = false;
    int state = STATE_UNINITIALIZED;
    std::string lastError;

    // Diagnostics of the last frame.
    MesrGLBridge::GpuExecutorStats lastGpuStats;
    double lastCpuRenderMs = 0.0;

    explicit BridgeRenderer(int w, int h)
        : framebuffer(w > 0 ? w : 1, h > 0 ? h : 1, MesrGL::FramebufferFormat::RGBA8),
          width(w > 0 ? w : 1), height(h > 0 ? h : 1) {}

    void bumpScene() {
        ++sceneVersion;
        sceneBuilt = false;
    }

    MesrGL::VoxelMaterial* materialById(int id) {
        if (!scene || id < 0 || id >= static_cast<int>(scene->meshes.size())) return nullptr;
        return &scene->meshes[id].material;
    }
};

inline BridgeRenderer* self(jlong handle) {
    return reinterpret_cast<BridgeRenderer*>(static_cast<uintptr_t>(handle));
}

inline jlong toHandle(BridgeRenderer* r) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(r));
}

jstring toJString(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

} // namespace

extern "C" {

// ------------------------------------------------------------
// Renderer lifecycle
// ------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_createRenderer(JNIEnv*, jclass) {
    auto* renderer = new BridgeRenderer(64, 64);
    renderer->scene = std::make_shared<MesrGL::Scene>();

    renderer->gpuInitError.clear();
    bool forceFailure = false;
    if (const char* f = std::getenv("MESRGL_FORCE_GPU_FAILURE")) {
        forceFailure = f[0] == '1' || f[0] == 't';
    }
    PreferredBackend pref = preferredBackend();
    bool wantGpu = pref != PreferredBackend::CPU;
    if (wantGpu && renderer->executor.initialize(forceFailure)) {
        renderer->gpuActive = true;
    } else {
        renderer->gpuActive = false;
        if (pref == PreferredBackend::GPU && !forceFailure) {
            renderer->gpuInitError = renderer->executor.available()
                                         ? std::string()
                                         : "GPU executor unavailable: " + renderer->executor.diagnosticInfo();
        } else if (wantGpu && !renderer->executor.available()) {
            renderer->gpuInitError = "GPU executor unavailable, CPU fallback active: " +
                                     renderer->executor.diagnosticInfo();
        }
    }
    renderer->state = STATE_READY;
    return toHandle(renderer);
}

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_destroyRenderer(JNIEnv*, jclass, jlong handle) {
    auto* renderer = self(handle);
    if (!renderer) return;
    {
        // Scope the lock so the mutex is released BEFORE the renderer
        // (and its mutex) are freed: unlocking a destroyed mutex writes
        // into freed heap memory.
        std::lock_guard<std::mutex> lock(renderer->mutex);
        renderer->state = STATE_SHUTTING_DOWN;
        renderer->executor.shutdown();
        renderer->tracer.reset();
        renderer->scene.reset();
    }
    delete renderer;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_initializeRenderer(
        JNIEnv*, jclass, jlong handle, jint width, jint height, jint spp, jint maxBounces,
        jint numThreads, jboolean deterministic, jlong rngSeed) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);

    if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
        renderer->lastError = "invalid framebuffer dimensions";
        return JNI_FALSE;
    }
    renderer->width = width;
    renderer->height = height;
    renderer->framebuffer.resize(width, height);

    renderer->settings.samplesPerPixel = spp > 0 ? spp : 1;
    renderer->settings.maxBounces = maxBounces > 0 ? maxBounces : 4;
    renderer->settings.numThreads = numThreads;
    renderer->tracer->setNumThreads(numThreads);   // the tracer's own worker count
    renderer->settings.deterministicMode = deterministic == JNI_TRUE;
    renderer->settings.rngSeed = static_cast<uint64_t>(rngSeed);

    renderer->initialized = true;
    renderer->state = STATE_READY;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_resizeRenderer(
        JNIEnv*, jclass, jlong handle, jint width, jint height) {
    auto* renderer = self(handle);
    if (!renderer || width <= 0 || height <= 0 || width > 16384 || height > 16384) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    renderer->width = width;
    renderer->height = height;
    renderer->framebuffer.resize(width, height);
    return JNI_TRUE;
}

// ------------------------------------------------------------
// Framebuffer
// ------------------------------------------------------------

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getFramebufferWidth(JNIEnv*, jclass, jlong handle) {
    auto* renderer = self(handle);
    return renderer ? static_cast<jint>(renderer->width) : 0;
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getFramebufferHeight(JNIEnv*, jclass, jlong handle) {
    auto* renderer = self(handle);
    return renderer ? static_cast<jint>(renderer->height) : 0;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_readFramebufferRGBA8(
        JNIEnv* env, jclass, jlong handle, jobject buffer) {
    auto* renderer = self(handle);
    if (!renderer || !buffer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);

    void* addr = env->GetDirectBufferAddress(buffer);
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (!addr) {
        renderer->lastError = "readFramebufferRGBA8 requires a direct ByteBuffer";
        return JNI_FALSE;
    }
    size_t needed = static_cast<size_t>(renderer->width) * renderer->height * 4;
    if (capacity >= 0 && static_cast<size_t>(capacity) < needed) {
        renderer->lastError = "framebuffer buffer too small";
        return JNI_FALSE;
    }
    // Single bulk copy (no per-pixel JNI, no temporary Java arrays).
    std::memcpy(addr, renderer->framebuffer.colorData.data(), needed);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_readFramebufferRGBA32F(
        JNIEnv* env, jclass, jlong handle, jfloatArray buffer) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (!renderer->framebuffer.hdrEnabled) {
        renderer->lastError = "framebuffer is not in HDR mode";
        return JNI_FALSE;
    }
    size_t pixels = static_cast<size_t>(renderer->width) * renderer->height;
    std::vector<float> tmp(pixels * 4, 1.0f);
    for (size_t i = 0; i < pixels; ++i) {
        const MesrGL::Vec3& c = renderer->framebuffer.hdrColorData[i];
        tmp[i * 4 + 0] = c.x;
        tmp[i * 4 + 1] = c.y;
        tmp[i * 4 + 2] = c.z;
    }
    env->SetFloatArrayRegion(buffer, 0, static_cast<jsize>(tmp.size()), tmp.data());
    return JNI_TRUE;
}

// ------------------------------------------------------------
// Scene management
// ------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_createScene(JNIEnv*, jclass, jlong) {
    // The bridge keeps one scene per renderer; the id is a stable token.
    return 1;
}

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_destroyScene(JNIEnv*, jclass, jlong handle, jlong) {
    auto* renderer = self(handle);
    if (!renderer) return;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    renderer->scene = std::make_shared<MesrGL::Scene>();
    renderer->bumpScene();
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_addMesh(
        JNIEnv* env, jclass, jlong handle, jlong, jfloatArray positions, jfloatArray normals,
        jfloatArray texCoords, jintArray indices, jint materialId) {
    auto* renderer = self(handle);
    if (!renderer || !positions || !indices) return -1;
    std::lock_guard<std::mutex> lock(renderer->mutex);

    jsize posLen = env->GetArrayLength(positions);
    jsize idxLen = env->GetArrayLength(indices);
    jsize nrmLen = normals ? env->GetArrayLength(normals) : 0;
    jsize uvLen = texCoords ? env->GetArrayLength(texCoords) : 0;

    auto* posPtr = env->GetFloatArrayElements(positions, nullptr);
    auto* idxPtr = env->GetIntArrayElements(indices, nullptr);
    auto* nrmPtr = normals ? env->GetFloatArrayElements(normals, nullptr) : nullptr;
    auto* uvPtr = texCoords ? env->GetFloatArrayElements(texCoords, nullptr) : nullptr;

    MesrGL::Mesh mesh;
    bool ok = posLen >= 3 && idxLen >= 3 && posLen % 3 == 0 && idxLen % 3 == 0;
    if (ok) {
        size_t vertexCount = static_cast<size_t>(posLen / 3);
        for (size_t v = 0; v < vertexCount; ++v) {
            MesrGL::Vec3 p(posPtr[v * 3 + 0], posPtr[v * 3 + 1], posPtr[v * 3 + 2]);
            MesrGL::Vec3 n = (nrmPtr && nrmLen >= posLen)
                                 ? MesrGL::Vec3(nrmPtr[v * 3 + 0], nrmPtr[v * 3 + 1], nrmPtr[v * 3 + 2])
                                 : MesrGL::Vec3(0.0f, 1.0f, 0.0f);
            MesrGL::Vec2 uv = (uvPtr && uvLen >= vertexCount * 2)
                                  ? MesrGL::Vec2(uvPtr[v * 2 + 0], uvPtr[v * 2 + 1])
                                  : MesrGL::Vec2(0.0f, 0.0f);
            mesh.addVertex(p, n, uv);
        }
        for (jsize i = 0; i + 2 < idxLen; i += 3) {
            mesh.addTriangle(idxPtr[i], idxPtr[i + 1], idxPtr[i + 2]);
        }
    }

    if (posPtr) env->ReleaseFloatArrayElements(positions, posPtr, JNI_ABORT);
    if (idxPtr) env->ReleaseIntArrayElements(indices, idxPtr, JNI_ABORT);
    if (nrmPtr) env->ReleaseFloatArrayElements(normals, nrmPtr, JNI_ABORT);
    if (uvPtr) env->ReleaseFloatArrayElements(texCoords, uvPtr, JNI_ABORT);

    if (!ok) {
        renderer->lastError = "addMesh: arrays are malformed";
        return -1;
    }
    if (MesrGL::VoxelMaterial* mat = renderer->materialById(materialId)) {
        mesh.material = *mat;
    }
    renderer->scene->meshes.push_back(mesh);
    renderer->bumpScene();
    return static_cast<jint>(renderer->scene->meshes.size() - 1);
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_removeMesh(
        JNIEnv*, jclass, jlong handle, jlong, jint meshId) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (!renderer->scene || meshId < 0 || meshId >= static_cast<int>(renderer->scene->meshes.size())) {
        return JNI_FALSE;
    }
    renderer->scene->meshes.erase(renderer->scene->meshes.begin() + meshId);
    renderer->bumpScene();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_updateMeshTransform(
        JNIEnv* env, jclass, jlong handle, jlong, jint meshId, jfloatArray transform) {
    auto* renderer = self(handle);
    if (!renderer || !transform) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (!renderer->scene || meshId < 0 || meshId >= static_cast<int>(renderer->scene->meshes.size())) {
        return JNI_FALSE;
    }
    jfloat* m = env->GetFloatArrayElements(transform, nullptr);
    MesrGL::Mat4 matrix;
    for (int r = 0; r < 4; ++r)
        for (int c = 0; c < 4; ++c)
            matrix.m[r][c] = m[r * 4 + c];
    env->ReleaseFloatArrayElements(transform, m, JNI_ABORT);

    // CPU assist: bake the transform into the mesh geometry.
    MesrGL::Mesh& mesh = renderer->scene->meshes[meshId];
    for (auto& p : mesh.positions) {
        p = (matrix * MesrGL::Vec4(p, 1.0f)).xyz();
    }
    if (mesh.normals.size() == mesh.positions.size()) {
        MesrGL::Mat4 rotation = matrix;
        rotation.m[0][3] = rotation.m[1][3] = rotation.m[2][3] = 0.0f;
        for (auto& n : mesh.normals) {
            n = MesrGL::Numerics::safeNormalize((rotation * MesrGL::Vec4(n, 0.0f)).xyz());
        }
    }
    renderer->bumpScene();
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_createMaterial(
        JNIEnv*, jclass, jlong handle, jfloat ar, jfloat ag, jfloat ab, jfloat roughness,
        jfloat metallic, jfloat emission, jfloat transmission, jfloat ior) {
    auto* renderer = self(handle);
    if (!renderer || !renderer->scene) return -1;
    std::lock_guard<std::mutex> lock(renderer->mutex);

    MesrGL::Mesh proxy;   // materials are stored per mesh; a proxy keeps ids stable
    proxy.material.albedo = MesrGL::Vec3(ar, ag, ab);
    proxy.material.roughness = roughness;
    proxy.material.metallic = metallic;
    proxy.material.emission = emission;
    proxy.material.transmission = transmission;
    proxy.material.ior = ior;
    proxy.material.opacity = 1.0f;
    proxy.material.materialId = 0;
    renderer->scene->meshes.push_back(proxy);
    renderer->bumpScene();
    return static_cast<jint>(renderer->scene->meshes.size() - 1);
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_updateMaterial(
        JNIEnv*, jclass, jlong handle, jint materialId, jfloat ar, jfloat ag, jfloat ab,
        jfloat roughness, jfloat metallic, jfloat emission, jfloat transmission, jfloat ior) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    MesrGL::VoxelMaterial* mat = renderer->materialById(materialId);
    if (!mat) return JNI_FALSE;
    mat->albedo = MesrGL::Vec3(ar, ag, ab);
    mat->roughness = roughness;
    mat->metallic = metallic;
    mat->emission = emission;
    mat->transmission = transmission;
    mat->ior = ior;
    renderer->bumpScene();
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_createTexture(
        JNIEnv*, jclass, jlong handle, jint width, jint height, jint channels, jbyteArray) {
    auto* renderer = self(handle);
    if (!renderer) return -1;
    // Texture sampling is not part of the software RT path (CPU parity:
    // the CPU reference ray tracer does not sample textures either). The
    // call remains API-compatible and returns a stable token.
    if (width <= 0 || height <= 0 || (channels != 3 && channels != 4)) {
        renderer->lastError = "createTexture: invalid texture parameters";
        return -1;
    }
    return 1;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_updateTexture(
        JNIEnv*, jclass, jlong, jint, jint, jint, jint, jbyteArray) {
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_destroyTexture(JNIEnv*, jclass, jlong, jint) {}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setMaterialTexture(
        JNIEnv*, jclass, jlong handle, jint materialId, jint textureId) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    MesrGL::VoxelMaterial* mat = renderer->materialById(materialId);
    if (!mat) return JNI_FALSE;
    // VoxelMaterial doesn't have textureId - this is a no-op for compatibility
    (void)textureId;
    renderer->bumpScene();
    return JNI_TRUE;
}

namespace {

// CPU assist: build the acceleration structure on the CPU and, when the
// GPU path is active, flatten + upload the scene for the executor.
bool rebuildAccelerationLocked(BridgeRenderer* renderer, std::string& error) {
    renderer->tracer->setScene(*renderer->scene);
    renderer->tracer->buildAccelerationStructure();
    renderer->sceneBuilt = true;

    if (renderer->gpuActive) {
        renderer->gpuScene = MesrGL::buildGpuSceneData(*renderer->scene, renderer->tracer->getBVH(),
                                                       renderer->settings, renderer->width,
                                                       renderer->height);
        if (!renderer->executor.uploadScene(renderer->gpuScene, error)) {
            // GPU upload failed: fall back to CPU for correctness.
            renderer->gpuActive = false;
            renderer->gpuInitError = "GPU upload failed, CPU fallback active: " + error;
            error.clear();
            return true;
        }
        renderer->gpuUploadedVersion = renderer->sceneVersion;
    }
    return true;
}

bool uploadFrameParamsLocked(BridgeRenderer* renderer, std::string& error) {
    MesrGL::fillGpuFrameCamera(renderer->gpuScene.frame, renderer->camera);
    MesrGL::fillGpuFrameParams(renderer->gpuScene.frame, *renderer->scene, renderer->settings,
                               renderer->width, renderer->height, renderer->tracer->getBVH());
    return true;
}

} // namespace

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_buildAccelerationStructure(
        JNIEnv*, jclass, jlong handle, jlong) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    std::string error;
    if (!rebuildAccelerationLocked(renderer, error)) {
        renderer->lastError = error;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_updateAccelerationStructure(
        JNIEnv*, jclass, jlong handle, jlong) {
    auto* renderer = self(handle);
    if (!renderer) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    std::string error;
    if (!rebuildAccelerationLocked(renderer, error)) {
        renderer->lastError = error;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// ------------------------------------------------------------
// Camera
// ------------------------------------------------------------

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setCamera(
        JNIEnv*, jclass, jlong handle, jlong, jfloat px, jfloat py, jfloat pz,
        jfloat tx, jfloat ty, jfloat tz, jfloat ux, jfloat uy, jfloat uz,
        jfloat fovY, jfloat aspect, jfloat zNear, jfloat zFar) {
    auto* renderer = self(handle);
    if (!renderer) return;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    renderer->camera.position = MesrGL::Vec3(px, py, pz);
    renderer->camera.target = MesrGL::Vec3(tx, ty, tz);
    renderer->camera.up = MesrGL::Vec3(ux, uy, uz);
    renderer->camera.fovY = fovY;
    renderer->camera.aspectRatio = aspect != 0.0f ? aspect : static_cast<float>(renderer->width) / static_cast<float>(renderer->height);
    renderer->camera.zNear = zNear;
    renderer->camera.zFar = zFar;
    renderer->cameraSet = true;
}

// ------------------------------------------------------------
// Lights
// ------------------------------------------------------------

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_addDirectionalLight(
        JNIEnv*, jclass, jlong handle, jlong, jfloat dx, jfloat dy, jfloat dz,
        jfloat r, jfloat g, jfloat b, jfloat intensity) {
    auto* renderer = self(handle);
    if (!renderer || !renderer->scene) return -1;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    MesrGL::Light light;
    light.type = MesrGL::Light::Type::Directional;
    light.direction = MesrGL::Vec3(dx, dy, dz);
    light.color = MesrGL::Vec3(r, g, b);
    light.intensity = intensity;
    renderer->scene->lights.push_back(light);
    renderer->bumpScene();
    return static_cast<jint>(renderer->scene->lights.size() - 1);
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_addPointLight(
        JNIEnv*, jclass, jlong handle, jlong, jfloat px, jfloat py, jfloat pz,
        jfloat r, jfloat g, jfloat b, jfloat intensity, jfloat range) {
    auto* renderer = self(handle);
    if (!renderer || !renderer->scene) return -1;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    MesrGL::Light light;
    light.type = MesrGL::Light::Type::Point;
    light.position = MesrGL::Vec3(px, py, pz);
    light.color = MesrGL::Vec3(r, g, b);
    light.intensity = intensity;
    light.range = range;
    renderer->scene->lights.push_back(light);
    renderer->bumpScene();
    return static_cast<jint>(renderer->scene->lights.size() - 1);
}

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_removeLight(
        JNIEnv*, jclass, jlong handle, jlong, jint lightId) {
    auto* renderer = self(handle);
    if (!renderer || !renderer->scene) return;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (lightId < 0 || lightId >= static_cast<int>(renderer->scene->lights.size())) return;
    renderer->scene->lights.erase(renderer->scene->lights.begin() + lightId);
    renderer->bumpScene();
}

// ------------------------------------------------------------
// Rendering
// ------------------------------------------------------------

JNIEXPORT jdouble JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_renderFrame(JNIEnv*, jclass, jlong handle, jlong) {
    auto* renderer = self(handle);
    if (!renderer || !renderer->scene) return -1.0;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (renderer->state != STATE_READY) return -1.0;

    std::string error;
    if (!renderer->sceneBuilt || renderer->gpuUploadedVersion != renderer->sceneVersion) {
        if (!rebuildAccelerationLocked(renderer, error)) {
            renderer->lastError = error;
            return -1.0;
        }
    }

    if (renderer->gpuActive) {
        uploadFrameParamsLocked(renderer, error);
        MesrGLBridge::GpuExecutorStats stats;
        if (renderer->executor.renderFrame(renderer->gpuScene,
                                           static_cast<uint32_t>(renderer->width),
                                           static_cast<uint32_t>(renderer->height),
                                           0, renderer->framebuffer, stats, error)) {
            renderer->lastGpuStats = stats;
            return stats.lastFrameMs;
        }
        // GPU render failed mid-frame: fall back to the CPU reference.
        renderer->gpuActive = false;
        renderer->gpuInitError = "GPU render failed, CPU fallback active: " + error;
    }

    // CPU reference rendering.
    auto t0 = std::chrono::high_resolution_clock::now();
    renderer->tracer->beginFrame(renderer->framebuffer);
    renderer->tracer->render(*renderer->scene, renderer->camera);
    renderer->tracer->endFrame();
    auto t1 = std::chrono::high_resolution_clock::now();
    renderer->lastCpuRenderMs = std::chrono::duration<double, std::milli>(t1 - t0).count();
    return renderer->lastCpuRenderMs;
}

JNIEXPORT jdouble JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_renderFrameProgressive(
        JNIEnv*, jclass, jlong handle, jlong, jint frameIndex) {
    auto* renderer = self(handle);
    if (!renderer || !renderer->scene) return -1.0;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (renderer->state != STATE_READY) return -1.0;

    std::string error;
    if (!renderer->sceneBuilt || renderer->gpuUploadedVersion != renderer->sceneVersion) {
        if (!rebuildAccelerationLocked(renderer, error)) {
            renderer->lastError = error;
            return -1.0;
        }
    }

    if (renderer->gpuActive) {
        uploadFrameParamsLocked(renderer, error);
        MesrGLBridge::GpuExecutorStats stats;
        if (renderer->executor.renderFrame(renderer->gpuScene,
                                           static_cast<uint32_t>(renderer->width),
                                           static_cast<uint32_t>(renderer->height),
                                           static_cast<uint64_t>(frameIndex < 0 ? 0 : frameIndex),
                                           renderer->framebuffer, stats, error)) {
            renderer->lastGpuStats = stats;
            return stats.lastFrameMs;
        }
        renderer->gpuActive = false;
        renderer->gpuInitError = "GPU render failed, CPU fallback active: " + error;
    }

    // CPU progressive accumulation is not implemented by the CPU renderer
    // (each call renders a fresh frame). Reported honestly.
    auto t0 = std::chrono::high_resolution_clock::now();
    renderer->tracer->beginFrame(renderer->framebuffer);
    renderer->tracer->render(*renderer->scene, renderer->camera);
    renderer->tracer->endFrame();
    auto t1 = std::chrono::high_resolution_clock::now();
    renderer->lastCpuRenderMs = std::chrono::duration<double, std::milli>(t1 - t0).count();
    return renderer->lastCpuRenderMs;
}

JNIEXPORT jlong JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getTotalRaysTraced(JNIEnv*, jclass, jlong) {
    // MesrGL's reference renderer does not count rays (returns 0 there too).
    return 0;
}

JNIEXPORT jdouble JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getLastRenderTimeMs(JNIEnv*, jclass, jlong handle) {
    auto* renderer = self(handle);
    if (!renderer) return -1.0;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (renderer->gpuActive) return renderer->lastGpuStats.lastFrameMs;
    return renderer->lastCpuRenderMs;
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getBVHNodeCount(JNIEnv*, jclass, jlong handle, jlong) {
    auto* renderer = self(handle);
    if (!renderer) return 0;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (!renderer->sceneBuilt) return 0;
    return static_cast<jint>(renderer->tracer->getBVH().nodes.size());
}

// ------------------------------------------------------------
// Settings
// ------------------------------------------------------------

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setSamplesPerPixel(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.samplesPerPixel = v > 0 ? v : 1;
}
JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setMaxSamplesPerPixel(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return JNI_FALSE;
    std::lock_guard<std::mutex> l(r->mutex);
    // Note: RenderSettings doesn't have maxSamplesPerPixel, using samplesPerPixel
    r->settings.samplesPerPixel = v > 0 ? v : 1;
    return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setClearColor(JNIEnv*, jclass, jlong handle, jfloat ar, jfloat ag, jfloat ab) {
    auto* r = self(handle); if (!r) return JNI_FALSE;
    std::lock_guard<std::mutex> l(r->mutex);
    if (!r->scene) return JNI_FALSE;
    r->scene->clearColor = MesrGL::Vec3(ar, ag, ab);
    return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_reserveSceneCapacity(
        JNIEnv*, jclass, jlong handle, jlong triangleBytes, jlong bvhBytes,
        jlong materialBytes, jlong lightBytes, jint width, jint height) {
    auto* r = self(handle); if (!r) return JNI_FALSE;
    std::lock_guard<std::mutex> l(r->mutex);
    r->executor.reserve(
        static_cast<uint64_t>(triangleBytes), static_cast<uint64_t>(bvhBytes),
        static_cast<uint64_t>(materialBytes), static_cast<uint64_t>(lightBytes),
        static_cast<uint32_t>(width), static_cast<uint32_t>(height), r->lastError);
    // A failed reserve is not fatal: ensureCapacity grows on demand.
    return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setMaxBounces(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.maxBounces = v > 0 ? v : 1;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setMaxDiffuseBounces(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.maxDiffuseBounces = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setMaxSpecularBounces(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.maxSpecularBounces = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setMaxTransmissionBounces(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.maxTransmissionBounces = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setShadowsEnabled(JNIEnv*, jclass, jlong handle, jboolean v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.shadowsEnabled = v == JNI_TRUE;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setGIEnabled(JNIEnv*, jclass, jlong handle, jboolean v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.giEnabled = v == JNI_TRUE;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setAdaptiveSampling(JNIEnv*, jclass, jlong handle, jboolean v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.adaptiveSampling = v == JNI_TRUE;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setVarianceThreshold(JNIEnv*, jclass, jlong handle, jfloat v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.varianceThreshold = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setDenoisingEnabled(JNIEnv*, jclass, jlong handle, jboolean v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.denoisingEnabled = v == JNI_TRUE;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setToneMapping(JNIEnv*, jclass, jlong handle, jint mode) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex);
    if (mode >= 0 && mode <= 6) {
        r->settings.colorPipeline.toneMapping = static_cast<MesrGL::RenderSettings::ToneMapping>(mode);
    }
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setExposure(JNIEnv*, jclass, jlong handle, jfloat v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.colorPipeline.exposure = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setGamma(JNIEnv*, jclass, jlong handle, jfloat v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.colorPipeline.gamma = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setNumThreads(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.numThreads = v;
}
JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setTileSize(JNIEnv*, jclass, jlong handle, jint v) {
    auto* r = self(handle); if (!r) return; std::lock_guard<std::mutex> l(r->mutex); r->settings.tileSize = v;
}

// ------------------------------------------------------------
// Diagnostics
// ------------------------------------------------------------

JNIEXPORT jstring JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getDiagnosticInfo(JNIEnv* env, jclass, jlong handle) {
    auto* renderer = self(handle);
    if (!renderer) return toJString(env, "renderer handle invalid");
    std::lock_guard<std::mutex> lock(renderer->mutex);
    std::string info;
    if (renderer->gpuActive) {
        info = renderer->executor.diagnosticInfo();
        char tail[256];
        std::snprintf(tail, sizeof(tail),
                      " | lastFrame=%.2fms submit=%.2fms gpuWait=%.2fms gpuTime=%.2fms "
                      "(pathtrace=%.2fms present=%.2fms) readback=%.2fms upload=%.2fms",
                      renderer->lastGpuStats.lastFrameMs,
                      renderer->lastGpuStats.lastDispatchSubmitMs,
                      renderer->lastGpuStats.lastGpuWaitMs,
                      renderer->lastGpuStats.lastGpuTimeMs,
                      renderer->lastGpuStats.lastPathtraceGpuMs,
                      renderer->lastGpuStats.lastPresentGpuMs,
                      renderer->lastGpuStats.lastReadbackMs,
                      renderer->lastGpuStats.lastUploadMs);
        info += tail;
    } else {
        info = "CPU Software RT (MesrGL reference renderer) | lastRender="
             + std::to_string(renderer->lastCpuRenderMs) + "ms";
        if (!renderer->gpuInitError.empty()) {
            info += " | " + renderer->gpuInitError;
        }
    }
    return toJString(env, info);
}

JNIEXPORT jstring JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getDeviceName(JNIEnv* env, jclass, jlong handle) {
    auto* renderer = self(handle);
    if (!renderer) return toJString(env, "N/A");
    std::lock_guard<std::mutex> lock(renderer->mutex);
    if (renderer->gpuActive) {
        return toJString(env, renderer->executor.deviceName());
    }
    return toJString(env, "CPU (MesrGL SoftwareRayTracer)");
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getBackendKind(JNIEnv*, jclass, jlong handle) {
    auto* renderer = self(handle);
    if (!renderer) return BACKEND_NONE;
    std::lock_guard<std::mutex> lock(renderer->mutex);
    return renderer->gpuActive ? BACKEND_GPU : BACKEND_CPU;
}

JNIEXPORT jint JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getRendererState(JNIEnv*, jclass, jlong handle) {
    auto* renderer = self(handle);
    return renderer ? static_cast<jint>(renderer->state) : STATE_FAILED;
}

JNIEXPORT jstring JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_getLastError(JNIEnv* env, jclass, jlong handle) {
    auto* renderer = self(handle);
    if (!renderer) return toJString(env, "renderer handle invalid");
    std::lock_guard<std::mutex> lock(renderer->mutex);
    return toJString(env, renderer->lastError);
}

// ------------------------------------------------------------
// Backend preference (called before createRenderer)
// ------------------------------------------------------------

JNIEXPORT void JNICALL Java_io_github_fv2j3dteam_mesrgl_MesrGLJNI_setPreferredBackend(JNIEnv*, jclass, jint mode) {
    // 0 = auto (GPU when available), 1 = force CPU, 2 = prefer GPU.
    switch (mode) {
        case 1: setenv("MESRGL_BACKEND", "cpu", 1); break;
        case 2: setenv("MESRGL_BACKEND", "gpu", 1); break;
        default: setenv("MESRGL_BACKEND", "auto", 1); break;
    }
}

} // extern "C"
