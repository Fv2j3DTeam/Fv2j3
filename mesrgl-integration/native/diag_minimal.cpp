// diag_minimal: minimal floor+light scene, probe specific pixels CPU vs GPU.
#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/GpuScene.hpp"
#include "gpu_executor.hpp"

#include <iostream>

using namespace MesrGL;

int main() {
    const int W = 64, H = 64;
    auto scene = std::make_shared<Scene>();

    Mesh floor;
    floor.material.albedo = Vec3(0.7f, 0.7f, 0.7f);
    floor.material.roughness = 0.9f;
    floor.addTriangle(floor.addVertex(Vec3(-5, 0, -5), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(5, 0, -5), Vec3(0, 1, 0), Vec2(1, 0)),
                      floor.addVertex(Vec3(5, 0, 5), Vec3(0, 1, 0), Vec2(1, 1)));
    floor.addTriangle(floor.addVertex(Vec3(-5, 0, -5), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(5, 0, 5), Vec3(0, 1, 0), Vec2(1, 1)),
                      floor.addVertex(Vec3(-5, 0, 5), Vec3(0, 1, 0), Vec2(0, 1)));
    scene->addMesh(floor);

    Light sun;
    sun.type = LightType::Directional;
    sun.direction = Vec3(0.0f, -1.0f, 0.0f);   // straight down
    sun.color = Vec3(1, 1, 1);
    sun.intensity = 1.0f;
    scene->addLight(sun);

    Camera cam;
    cam.position = Vec3(0, 2, 0);
    cam.target = Vec3(0, 0, 0);
    cam.up = Vec3(0, 0, -1);
    cam.fovY = 60.0f * (Numerics::PI / 180.0f);
    cam.aspectRatio = 1.0f;

    RenderSettings settings;
    settings.samplesPerPixel = 1;
    settings.maxBounces = 2;
    settings.numThreads = 1;
    settings.deterministicMode = true;
    settings.shadowsEnabled = true;
    settings.giEnabled = false;   // isolate direct lighting first
    settings.jitteredSampling = false;
    settings.stratifiedSampling = false;
    settings.rngSeed = 7;
    settings.colorPipeline.toneMapping = ToneMapping::None;
    settings.colorPipeline.exposure = 1.0f;
    settings.colorPipeline.srgbOutput = false;

    Framebuffer cpuFb;
    SoftwareRayTracer tracer;
    tracer.setScene(std::make_shared<Scene>(*scene));
    tracer.buildAccelerationStructure();
    tracer.settings = settings;
    tracer.numThreads = 1;
    cpuFb.resize(W, H);
    tracer.beginFrame(cpuFb);
    Scene& sc = *tracer.getScene();
    tracer.render(sc, cam);
    tracer.endFrame();

    MesrGLBridge::GpuExecutor executor;
    if (!executor.initialize(false)) {
        std::cout << "GPU unavailable" << std::endl;
        return 2;
    }
    SoftwareRayTracer builder;
    auto scenePtr = std::make_shared<Scene>(*scene);
    builder.setScene(scenePtr);
    builder.buildAccelerationStructure();
    GpuSceneData data = buildGpuSceneData(*scenePtr, builder.getBVH(), settings, W, H);
    fillGpuFrameCamera(data.frame, cam);
    fillGpuFrameParams(data.frame, *scenePtr, settings, W, H, builder.getBVH());
    std::string error;
    if (!executor.uploadScene(data, error)) { std::cout << "upload: " << error << std::endl; return 1; }
    Framebuffer gpuFb;
    gpuFb.resize(W, H);
    MesrGLBridge::GpuExecutorStats stats;
    if (!executor.renderFrame(data, W, H, 0, gpuFb, stats, error)) {
        std::cout << "render: " << error << std::endl;
        return 1;
    }

    auto probe = [&](const char* name, const Framebuffer& fb) {
        int x = W / 2, y = H * 3 / 4;   // lower-center: should be lit floor (0.7)
        int sx = W / 2, sy = H / 8;     // upper: sky = 0
        int i1 = (y * W + x) * 4, i2 = (sy * W + sx) * 4;
        std::cout << name << " floor(px " << x << "," << y << ") = ("
                  << (int)fb.colorData[i1] << "," << (int)fb.colorData[i1+1] << "," << (int)fb.colorData[i1+2] << ")"
                  << "  sky(px " << sx << "," << sy << ") = ("
                  << (int)fb.colorData[i2] << "," << (int)fb.colorData[i2+1] << "," << (int)fb.colorData[i2+2] << ")"
                  << std::endl;
    };
    probe("CPU", cpuFb);
    probe("GPU", gpuFb);

    // Expected: floor = 0.7 * dot(n,l) * intensity = 0.7 (cos=1 straight down, n=(0,1,0), l=(0,1,0))
    // Note camera looks straight down; floor fills the whole view -> both probes are floor.
    std::cout << "expected floor value ~178 (0.7*255), sky behind camera" << std::endl;
    return 0;
}
