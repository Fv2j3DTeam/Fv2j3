#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/GpuScene.hpp"
#include "gpu_executor.hpp"
#include <cmath>
#include <iostream>
using namespace MesrGL;

int main() {
    const int W = 96, H = 72;
    auto scene = std::make_shared<Scene>();
    Mesh floor;
    floor.material.albedo = Vec3(0.7f, 0.7f, 0.7f);
    floor.material.roughness = 0.9f;
    floor.addTriangle(floor.addVertex(Vec3(-6, 0, -6), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(6, 0, -6), Vec3(0, 1, 0), Vec2(1, 0)),
                      floor.addVertex(Vec3(6, 0, 6), Vec3(0, 1, 0), Vec2(1, 1)));
    floor.addTriangle(floor.addVertex(Vec3(-6, 0, -6), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(6, 0, 6), Vec3(0, 1, 0), Vec2(1, 1)),
                      floor.addVertex(Vec3(-6, 0, 6), Vec3(0, 1, 0), Vec2(0, 1)));
    scene->addMesh(floor);
    Light sun;
    sun.type = LightType::Directional;
    sun.direction = Vec3(0.3f, -1.0f, 0.1f);
    scene->addLight(sun);

    Camera cam;
    cam.position = Vec3(0, 1.8f, 5.0f);
    cam.target = Vec3(0, 0.6f, 0);
    cam.up = Vec3(0, 1, 0);
    cam.fovY = 50.0f * (Numerics::PI / 180.0f);
    cam.aspectRatio = static_cast<float>(W) / H;

    RenderSettings settings;
    settings.samplesPerPixel = 4;
    settings.numThreads = 1;
    settings.deterministicMode = true;
    settings.giEnabled = false;
    settings.jitteredSampling = false;
    settings.stratifiedSampling = false;
    settings.colorPipeline.toneMapping = ToneMapping::None;
    settings.colorPipeline.srgbOutput = true;

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
    if (!executor.initialize(false)) return 2;
    SoftwareRayTracer builder;
    auto scenePtr = std::make_shared<Scene>(*scene);
    builder.setScene(scenePtr);
    builder.buildAccelerationStructure();
    GpuSceneData data = buildGpuSceneData(*scenePtr, builder.getBVH(), settings, W, H);
    fillGpuFrameCamera(data.frame, cam);
    fillGpuFrameParams(data.frame, *scenePtr, settings, W, H, builder.getBVH());
    std::string error;
    executor.uploadScene(data, error);
    Framebuffer gpuFb;
    gpuFb.resize(W, H);
    MesrGLBridge::GpuExecutorStats stats;
    executor.renderFrame(data, W, H, 0, gpuFb, stats, error);

    std::cout << "invView rows uploaded:" << std::endl;
    for (int r = 0; r < 4; ++r)
        std::printf("  row%d: %8.4f %8.4f %8.4f %8.4f\n", r,
            data.frame.invView[r*4], data.frame.invView[r*4+1],
            data.frame.invView[r*4+2], data.frame.invView[r*4+3]);

    // CPU's own invView for comparison
    Mat4 invView = cam.viewMatrix().inverse();
    std::cout << "CPU invView rows:" << std::endl;
    for (int r = 0; r < 4; ++r)
        std::printf("  row%d: %8.4f %8.4f %8.4f %8.4f\n", r,
            invView.m[r][0], invView.m[r][1], invView.m[r][2], invView.m[r][3]);

    for (int y : {10, 30, 36, 40, 50, 60}) {
        std::cout << "row " << y << ": ";
        for (int x = 0; x < W; x += 12) {
            int i = (y * W + x) * 4;
            std::cout << "(" << (int)cpuFb.colorData[i] << "/" << (int)gpuFb.colorData[i] << ") ";
        }
        std::cout << std::endl;
    }
    return 0;
}
