// diag_crossval: renders the test scene CPU and GPU, dumps BMPs + a diff map.
#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/GpuScene.hpp"
#include "MesrGL/Image.hpp"
#include "gpu_executor.hpp"

#include <cmath>
#include <iostream>

using namespace MesrGL;

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

static std::shared_ptr<Scene> makeTestScene() {
    auto scene = std::make_shared<Scene>();
    Mesh floor;
    floor.material.albedo = Vec3(0.7f, 0.7f, 0.72f);
    floor.material.roughness = 0.85f;
    floor.addTriangle(floor.addVertex(Vec3(-6, 0, -6), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(6, 0, -6), Vec3(0, 1, 0), Vec2(1, 0)),
                      floor.addVertex(Vec3(6, 0, 6), Vec3(0, 1, 0), Vec2(1, 1)));
    floor.addTriangle(floor.addVertex(Vec3(-6, 0, -6), Vec3(0, 1, 0), Vec2(0, 0)),
                      floor.addVertex(Vec3(6, 0, 6), Vec3(0, 1, 0), Vec2(1, 1)),
                      floor.addVertex(Vec3(-6, 0, 6), Vec3(0, 1, 0), Vec2(0, 1)));
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
    return scene;
}

int main() {
    const int W = 96, H = 72;
    auto scene = makeTestScene();
    Camera cam;
    cam.position = Vec3(0.0f, 1.8f, 5.0f);
    cam.target = Vec3(0.0f, 0.6f, 0.0f);
    cam.up = Vec3(0, 1, 0);
    cam.fovY = 50.0f * (Numerics::PI / 180.0f);
    cam.aspectRatio = static_cast<float>(W) / static_cast<float>(H);

    Mesh box;
    {
        Vec3 p[8] = {
            Vec3(-0.8f, 0, 0.5f), Vec3(0.2f, 0, 0.5f), Vec3(0.2f, 0.9f, 0.5f), Vec3(-0.8f, 0.9f, 0.5f),
            Vec3(-0.8f, 0, -0.4f), Vec3(0.2f, 0, -0.4f), Vec3(0.2f, 0.9f, -0.4f), Vec3(-0.8f, 0.9f, -0.4f)
        };
        Vec3 nrm[6] = {
            Vec3(0, 0, 1), Vec3(0, 0, -1), Vec3(1, 0, 0),
            Vec3(-1, 0, 0), Vec3(0, 1, 0), Vec3(0, -1, 0)
        };
        int faces[6][4] = {
            {0, 1, 2, 3}, {5, 4, 7, 6}, {1, 5, 6, 2},
            {4, 0, 3, 7}, {3, 2, 6, 7}, {4, 5, 1, 0}
        };
        for (int f = 0; f < 6; ++f) {
            int a = faces[f][0], b = faces[f][1], cc = faces[f][2], d = faces[f][3];
            box.addTriangle(box.addVertex(p[a], nrm[f], Vec2(0, 0)),
                            box.addVertex(p[b], nrm[f], Vec2(1, 0)),
                            box.addVertex(p[cc], nrm[f], Vec2(1, 1)));
            box.addTriangle(box.addVertex(p[a], nrm[f], Vec2(0, 0)),
                            box.addVertex(p[cc], nrm[f], Vec2(1, 1)),
                            box.addVertex(p[d], nrm[f], Vec2(0, 1)));
        }
    }
    scene->addMesh(box);

    RenderSettings settings;
    settings.samplesPerPixel = 4;
    settings.maxBounces = 4;
    settings.numThreads = 1;
    settings.deterministicMode = true;
    settings.shadowsEnabled = false;
    settings.giEnabled = false;
    settings.rngSeed = 0xC0FFEE;
    settings.colorPipeline.toneMapping = ToneMapping::None;
    settings.colorPipeline.srgbOutput = false;

    // CPU
    Framebuffer cpuFb;
    {
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
    }
    writeBMP("cpu.bmp", W, H, cpuFb.colorData.data());

    // GPU
    MesrGLBridge::GpuExecutor executor;
    if (!executor.initialize(false)) {
        std::cout << "GPU unavailable: " << executor.diagnosticInfo() << std::endl;
        return 2;
    }
    Framebuffer gpuFb;
    {
        SoftwareRayTracer builder;
        auto scenePtr = std::make_shared<Scene>(*scene);
        builder.setScene(scenePtr);
        builder.buildAccelerationStructure();
        GpuSceneData data = buildGpuSceneData(*scenePtr, builder.getBVH(), settings, W, H);
        fillGpuFrameCamera(data.frame, cam);
        fillGpuFrameParams(data.frame, *scenePtr, settings, W, H, builder.getBVH());
        std::string error;
        if (!executor.uploadScene(data, error)) { std::cout << "upload fail " << error << std::endl; return 1; }
        MesrGLBridge::GpuExecutorStats stats;
        gpuFb.resize(W, H);
        if (!executor.renderFrame(data, W, H, 0, gpuFb, stats, error)) {
            std::cout << "render fail " << error << std::endl;
            return 1;
        }
        std::cout << executor.diagnosticInfo() << std::endl;
    }
    writeBMP("gpu.bmp", W, H, gpuFb.colorData.data());

    // Diff map (amplified 8x)
    std::vector<unsigned char> diff(W * H * 4, 0);
    for (size_t i = 0; i < static_cast<size_t>(W) * H; ++i) {
        for (int c = 0; c < 3; ++c) {
            int d = std::abs(static_cast<int>(cpuFb.colorData[i * 4 + c]) - static_cast<int>(gpuFb.colorData[i * 4 + c]));
            diff[i * 4 + c] = static_cast<unsigned char>(std::min(255, d * 8));
        }
        diff[i * 4 + 3] = 255;
    }
    writeBMP("diff.bmp", W, H, diff.data());
    std::cout << "wrote cpu.bmp gpu.bmp diff.bmp" << std::endl;
    return 0;
}
