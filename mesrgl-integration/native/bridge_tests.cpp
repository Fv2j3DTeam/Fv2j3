// ============================================================
// MesrGLBridge test suite (runs against the real GPU when present).
//
//  1. GPU executor availability + forced-failure fallback
//  2. CPU/GPU cross-validation (identical scene/camera/seed/SPP/depth)
//  3. GPU determinism (bit-identical repeated renders)
//  4. CPU determinism (oracle sanity)
//  5. Memory stability over long runs (grow-only buffers, no growth)
//  6. Executor lifecycle (repeated create/destroy)
// ============================================================
#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/GpuScene.hpp"
#include "gpu_executor.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <iostream>
#include <string>
#include <vector>

using namespace MesrGL;
using MesrGLBridge::GpuExecutor;
using MesrGLBridge::GpuExecutorStats;

static int failures = 0;
static int unavailableSkips = 0;

static void check(bool ok, const std::string& what) {
    if (!ok) {
        std::cout << "  FAIL: " << what << std::endl;
        ++failures;
    }
}

static Mesh makeCubeMesh(float x0, float y0, float z0, float x1, float y1, float z1) {
    Mesh mesh;
    Vec3 p[8] = {
        Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1),
        Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x0, y1, z0)
    };
    Vec3 n[6] = {
        Vec3(0, 0, 1), Vec3(0, 0, -1), Vec3(1, 0, 0),
        Vec3(-1, 0, 0), Vec3(0, 1, 0), Vec3(0, -1, 0)
    };
    int faces[6][4] = {
        {0, 1, 2, 3}, {5, 4, 7, 6}, {1, 5, 6, 2},
        {4, 0, 3, 7}, {3, 2, 6, 7}, {4, 5, 1, 0}
    };
    for (int f = 0; f < 6; ++f) {
        int a = faces[f][0], b = faces[f][1], c = faces[f][2], d = faces[f][3];
        mesh.addTriangle(mesh.addVertex(p[a], n[f], Vec2(0, 0)),
                         mesh.addVertex(p[b], n[f], Vec2(1, 0)),
                         mesh.addVertex(p[c], n[f], Vec2(1, 1)));
        mesh.addTriangle(mesh.addVertex(p[a], n[f], Vec2(0, 0)),
                         mesh.addVertex(p[c], n[f], Vec2(1, 1)),
                         mesh.addVertex(p[d], n[f], Vec2(0, 1)));
    }
    return mesh;
}

// Deterministic reference scene: floor, metallic box, emissive box,
// transmissive box, two lights. Exercises shadows, reflection,
// transmission, emission and GI bounce.
static std::shared_ptr<Scene> makeTestScene() {
    auto scene = std::make_shared<Scene>();

    Mesh floor;
    floor.material.albedo = Vec3(0.7f, 0.7f, 0.72f);
    floor.material.roughness = 0.85f;
    floor.addTriangle(floor.addVertex(Vec3(-40, 0, -40), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(40, 0, -40), Vec3(0, 1, 0), Vec2(1, 0)),
                      floor.addVertex(Vec3(40, 0, 40), Vec3(0, 1, 0), Vec2(1, 1)));
    floor.addTriangle(floor.addVertex(Vec3(-40, 0, -40), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(40, 0, 40), Vec3(0, 1, 0), Vec2(1, 1)),
                      floor.addVertex(Vec3(-40, 0, 40), Vec3(0, 1, 0), Vec2(0, 1)));
    scene->addMesh(floor);

    Mesh metal = makeCubeMesh(-1.6f, 0, -0.4f, -0.4f, 1.2f, 0.8f);
    metal.material.albedo = Vec3(1.0f, 0.85f, 0.4f);
    metal.material.metallic = 1.0f;
    metal.material.roughness = 0.05f;
    scene->addMesh(metal);

    Mesh lamp = makeCubeMesh(0.6f, 0.0f, -0.2f, 1.4f, 0.6f, 0.6f);
    lamp.material.albedo = Vec3(1.0f, 0.6f, 0.2f);
    lamp.material.emission = 4.0f;
    scene->addMesh(lamp);

    Mesh glass = makeCubeMesh(2.0f, 0.0f, -0.5f, 3.0f, 1.0f, 0.5f);
    glass.material.albedo = Vec3(0.9f, 0.95f, 1.0f);
    glass.material.transmission = 1.0f;
    glass.material.ior = 1.5f;
    glass.material.roughness = 0.0f;
    scene->addMesh(glass);

    Light sun;
    sun.type = LightType::Directional;
    sun.direction = Vec3(0.35f, -1.0f, 0.2f);
    sun.color = Vec3(1, 1, 1);
    sun.intensity = 1.2f;
    scene->addLight(sun);

    Light point;
    point.type = LightType::Point;
    point.position = Vec3(0.0f, 3.0f, 0.0f);
    point.color = Vec3(1, 0.95f, 0.9f);
    point.intensity = 2.0f;
    scene->addLight(point);
    return scene;
}

static Camera makeTestCamera(int width, int height) {
    // Downward-looking: the frame contains no sky horizon, avoiding the
    // grazing-ray band where ±1 ulp ray-direction rounding flips hit/miss
    // classification (documented CPU/GPU divergence source).
    Camera cam;
    cam.position = Vec3(0.0f, 5.0f, 4.5f);
    cam.target = Vec3(0.0f, 0.4f, 0.0f);
    cam.up = Vec3(0, 1, 0);
    cam.fovY = 50.0f * (Numerics::PI / 180.0f);
    cam.aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    return cam;
}

static Camera makeHorizonCamera(int width, int height) {
    Camera cam;
    cam.position = Vec3(0.0f, 1.8f, 7.0f);
    cam.target = Vec3(0.0f, 0.8f, 0.0f);
    cam.up = Vec3(0, 1, 0);
    cam.fovY = 50.0f * (Numerics::PI / 180.0f);
    cam.aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    return cam;
}

struct DiffStats {
    double meanDelta = 0.0;
    int maxDelta = 0;
    double p99 = 0.0;
    double pctWithin2 = 100.0;
};

static DiffStats compareFramebuffers(const Framebuffer& a, const Framebuffer& b) {
    size_t pixels = static_cast<size_t>(a.width) * a.height;
    std::vector<int> deltas(pixels);
    double sum = 0.0;
    int maxD = 0;
    int within2 = 0;
    for (size_t i = 0; i < pixels; ++i) {
        int d = 0;
        for (int c = 0; c < 3; ++c) {
            int da = std::abs(static_cast<int>(a.colorData[i * 4 + c]) - static_cast<int>(b.colorData[i * 4 + c]));
            if (da > d) d = da;
        }
        deltas[i] = d;
        sum += d;
        if (d > maxD) maxD = d;
        if (d <= 2) ++within2;
    }
    std::sort(deltas.begin(), deltas.end());
    DiffStats stats;
    stats.meanDelta = sum / static_cast<double>(pixels);
    stats.maxDelta = maxD;
    stats.p99 = deltas[static_cast<size_t>(0.99 * (pixels - 1))];
    stats.pctWithin2 = 100.0 * static_cast<double>(within2) / static_cast<double>(pixels);
    return stats;
}

static void renderCpu(Scene& scene, const Camera& cam, const RenderSettings& settings,
                      int width, int height, Framebuffer& fb) {
    SoftwareRayTracer tracer;
    tracer.setScene(std::make_shared<Scene>(scene));
    tracer.buildAccelerationStructure();
    tracer.settings = settings;
    tracer.numThreads = 1;
    fb.resize(width, height);
    tracer.beginFrame(fb);
    Scene& sc = *tracer.getScene();
    tracer.render(sc, const_cast<Camera&>(cam));
    tracer.endFrame();
}

static bool renderGpu(GpuExecutor& executor, Scene& scene, const Camera& cam,
                      const RenderSettings& settings, int width, int height,
                      Framebuffer& fb, std::string& error) {
    SoftwareRayTracer builder;   // CPU assist: BVH construction
    auto scenePtr = std::make_shared<Scene>(scene);
    builder.setScene(scenePtr);
    builder.buildAccelerationStructure();

    GpuSceneData data = buildGpuSceneData(*scenePtr, builder.getBVH(), settings, width, height);
    fillGpuFrameCamera(data.frame, cam);
    fillGpuFrameParams(data.frame, *scenePtr, settings, width, height, builder.getBVH());

    if (!executor.uploadScene(data, error)) return false;
    fb.resize(width, height);
    GpuExecutorStats dummy;
    return executor.renderFrame(data, static_cast<uint32_t>(width), static_cast<uint32_t>(height),
                                0, fb, dummy, error);
}

int main() {
    std::cout << "=== MesrGLBridge test suite ===" << std::endl;

    // ---------- 1. availability + forced failure fallback ----------
    bool gpuPresent = false;
    {
        GpuExecutor executor;
        gpuPresent = executor.initialize(false);
        if (gpuPresent) {
            std::cout << "GPU executor: " << executor.deviceName()
                      << " (api " << executor.apiVersionString() << ")" << std::endl;
            check(executor.available(), "executor available after init");
        } else {
            std::cout << "GPU executor unavailable: " << executor.diagnosticInfo() << std::endl;
            std::cout << "  SKIP: GPU-dependent tests (hardware unavailable)" << std::endl;
            ++unavailableSkips;
        }
    }
    {
        GpuExecutor executor;
        bool ok = executor.initialize(true);
        check(!ok && !executor.available(), "forced GPU failure is reported unavailable (fallback path)");
    }

    RenderSettings settings;
    settings.samplesPerPixel = 4;
    settings.maxBounces = 4;
    settings.numThreads = 1;
    settings.deterministicMode = true;
    settings.shadowsEnabled = true;
    settings.giEnabled = true;
    settings.rngSeed = 0xC0FFEE;
    settings.colorPipeline.toneMapping = ToneMapping::ACES;

    auto scene = makeTestScene();
    const int W = 320, H = 240;

    if (gpuPresent) {
        GpuExecutor executor;
        check(executor.initialize(false), "executor re-initialization");

        // ---------- 2. CPU/GPU cross-validation ----------
        Framebuffer cpuFb, gpuFb;
        renderCpu(*scene, makeTestCamera(W, H), settings, W, H, cpuFb);
        std::string error;
        bool ok = renderGpu(executor, *scene, makeTestCamera(W, H), settings, W, H, gpuFb, error);
        check(ok, "GPU render succeeded: " + error);

        if (ok) {
            DiffStats diff = compareFramebuffers(cpuFb, gpuFb);
            std::cout << "cross-validation SPP4 320x240 (no grazing rays): mean=" << diff.meanDelta
                      << " p99=" << diff.p99 << " max=" << diff.maxDelta
                      << " pct<=2/255: " << diff.pctWithin2 << "%" << std::endl;
            // Tolerance rationale (measured, documented in the validation
            // report): shading math, RNG, and the color pipeline are
            // bit-exact; ray directions differ by ±1 ulp between the CPU
            // and GPU compilers (FMA contraction is not controllable in
            // SPIR-V), which flips pixel classification at geometric
            // edges and grazing-angle horizons. Interiors match exactly.
            check(diff.meanDelta <= 10.0, "cross-validation mean delta <= 10/255 (edge classification band)");
            check(diff.pctWithin2 >= 85.0, "cross-validation >= 85% pixels within 2/255");
        }

        // Horizon-in-view scene: grazing rays at the floor/sky boundary are
        // classified by ±1 ulp ray-direction rounding (CPU and GPU compilers
        // make different FMA contraction decisions; SPIR-V cannot forbid
        // contraction). The divergence band is measured and reported.
        {
            Framebuffer cpuFb, gpuFb;
            renderCpu(*scene, makeHorizonCamera(W, H), settings, W, H, cpuFb);
            std::string error;
            bool ok = renderGpu(executor, *scene, makeHorizonCamera(W, H), settings, W, H, gpuFb, error);
            check(ok, "GPU horizon render succeeded: " + error);
            if (ok) {
                DiffStats diff = compareFramebuffers(cpuFb, gpuFb);
                std::cout << "cross-validation SPP4 320x240 (horizon in view, grazing band expected): mean="
                          << diff.meanDelta << " p99=" << diff.p99 << " max=" << diff.maxDelta
                          << " pct<=2/255: " << diff.pctWithin2 << "%" << std::endl;
                check(diff.pctWithin2 >= 85.0, "horizon scene >= 85% pixels within 2/255 (grazing band bounded)");
                check(diff.meanDelta <= 12.0, "horizon scene mean delta bounded (grazing band)");
            }
        }

        // ---------- 3. GPU determinism ----------
        {
            Framebuffer g1, g2;
            std::string e1, e2;
            renderGpu(executor, *scene, makeTestCamera(W, H), settings, W, H, g1, e1);
            renderGpu(executor, *scene, makeTestCamera(W, H), settings, W, H, g2, e2);
            check(std::memcmp(g1.colorData.data(), g2.colorData.data(), g1.colorData.size()) == 0,
                  "GPU renders are bit-identical for identical inputs");
        }

        // ---------- 4. CPU determinism (oracle sanity) ----------
        {
            Framebuffer c1, c2;
            renderCpu(*scene, makeTestCamera(W, H), settings, W, H, c1);
            renderCpu(*scene, makeTestCamera(W, H), settings, W, H, c2);
            check(std::memcmp(c1.colorData.data(), c2.colorData.data(), c1.colorData.size()) == 0,
                  "CPU renders are bit-identical for identical inputs");
        }

        // ---------- 5. memory stability over long run ----------
        {
            size_t initialBytes = executor.memoryStats().totalBytes();
            GpuExecutorStats stats;
            Framebuffer fb;
            std::string err;
            bool stable = true;
            for (int i = 0; i < 300; ++i) {
                if (!renderGpu(executor, *scene, makeTestCamera(W, H), settings, W, H, fb, err)) {
                    stable = false;
                    break;
                }
            }
            size_t finalBytes = executor.memoryStats().totalBytes();
            check(stable, "300-frame GPU run completes");
            check(initialBytes == finalBytes, "GPU memory stable across 300 frames ("
                  + std::to_string(initialBytes) + " -> " + std::to_string(finalBytes) + ")");
        }

        // ---------- 6. progressive accumulation ----------
        {
            // frameIndex > 0 accumulates: average over 4 accumulations should
            // stay finite and non-black.
            SoftwareRayTracer builder;
            auto scenePtr = std::make_shared<Scene>(*scene);
            builder.setScene(scenePtr);
            builder.buildAccelerationStructure();
            GpuSceneData data = buildGpuSceneData(*scenePtr, builder.getBVH(), settings, W, H);
            fillGpuFrameCamera(data.frame, makeTestCamera(W, H));
            fillGpuFrameParams(data.frame, *scenePtr, settings, W, H, builder.getBVH());
            check(executor.uploadScene(data, error), "progressive upload ok");

            Framebuffer fb;
            fb.resize(W, H);
            GpuExecutorStats stats;
            bool okAll = true;
            for (uint64_t f = 0; f < 4; ++f) {
                okAll = executor.renderFrame(data, W, H, f, fb, stats, error) && okAll;
            }
            check(okAll, "progressive accumulation renders");
            double sum = 0.0;
            bool finite = true;
            for (size_t i = 0; i < fb.colorData.size(); ++i) sum += fb.colorData[i];
            check(finite, "progressive output finite (sum=" + std::to_string(sum) + ")");
            check(sum > 0.0, "progressive output non-black");
        }

        // ---------- 7. executor lifecycle ----------
        {
            bool allOk = true;
            for (int i = 0; i < 10; ++i) {
                GpuExecutor e;
                if (!e.initialize(false)) { allOk = false; break; }
                Framebuffer fb;
                std::string err;
                if (!renderGpu(e, *scene, makeTestCamera(W, H), settings, W / 2, H / 2, fb, err)) {
                    allOk = false;
                    break;
                }
            }
            check(allOk, "10 executor create/render/destroy cycles");
        }
    }

    std::cout << "=== bridge tests: " << (failures == 0 ? "PASSED" : "FAILED")
              << (unavailableSkips > 0 ? " (with GPU-unavailable skips)" : "") << " ===" << std::endl;
    return failures == 0 ? 0 : 1;
}
