// diag_bisect: enables features one at a time to isolate the CPU/GPU divergence.
#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/GpuScene.hpp"
#include "gpu_executor.hpp"

#include <cmath>
#include <cstring>
#include <iostream>

using namespace MesrGL;

static Mesh makeBox(float x0, float y0, float z0, float x1, float y1, float z1) {
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

struct CaseResult { std::string name; int maxDelta; double meanDelta; };

static CaseResult runCase(const std::string& name, std::shared_ptr<Scene> scene,
                          const RenderSettings& settings, const Camera& cam, int W, int H) {
    Framebuffer cpuFb;
    SoftwareRayTracer tracer;
    tracer.setScene(std::make_shared<Scene>(*scene));
    tracer.buildAccelerationStructure();
    tracer.settings = settings;
    tracer.numThreads = 1;
    cpuFb.resize(W, H);
    tracer.beginFrame(cpuFb);
    Scene& sc = *tracer.getScene();
    tracer.render(sc, const_cast<Camera&>(cam));
    tracer.endFrame();

    MesrGLBridge::GpuExecutor executor;
    if (!executor.initialize(false)) return {name + " (GPU UNAVAIL)", -1, 0.0};
    SoftwareRayTracer builder;
    auto scenePtr = std::make_shared<Scene>(*scene);
    builder.setScene(scenePtr);
    builder.buildAccelerationStructure();
    GpuSceneData data = buildGpuSceneData(*scenePtr, builder.getBVH(), settings, W, H);
    fillGpuFrameCamera(data.frame, cam);
    fillGpuFrameParams(data.frame, *scenePtr, settings, W, H, builder.getBVH());
    if (name == "1 floor+sun direct only") {
        std::cout << "  camMisc: " << data.frame.camMisc[0] << " " << data.frame.camMisc[1]
                  << " " << data.frame.camMisc[2] << " " << data.frame.camMisc[3] << std::endl;
        std::cout << "  render0: " << data.frame.render0[0] << " " << data.frame.render0[1]
                  << " " << data.frame.render0[2] << " " << data.frame.render0[3] << std::endl;
        std::cout << "  colorCfg: " << data.frame.colorCfg[0] << " " << data.frame.colorCfg[1]
                  << " " << data.frame.colorCfg[2] << " " << data.frame.colorCfg[3] << std::endl;
        std::cout << "  sceneCfg: " << data.frame.sceneCfg[0] << " " << data.frame.sceneCfg[1] << std::endl;
        std::cout << "  tri0: " << data.triangles[0].v0[0] << " " << data.triangles[0].v0[1]
                  << " " << data.triangles[0].v0[2] << " mat=" << data.triangles[0].faceNormalMaterial[3] << std::endl;
        std::cout << "  node0 bmin: " << data.bvhNodes[0].bminLeft[0] << " " << data.bvhNodes[0].bminLeft[1]
                  << " " << data.bvhNodes[0].bminLeft[2] << " left=" << data.bvhNodes[0].bminLeft[3]
                  << " count=" << data.bvhNodes[0].bmaxCount[3] << std::endl;
    }
    std::string error;
    if (!executor.uploadScene(data, error)) return {name + " (UPLOAD FAIL)", -1, 0.0};
    Framebuffer gpuFb;
    gpuFb.resize(W, H);
    MesrGLBridge::GpuExecutorStats stats;
    if (!executor.renderFrame(data, W, H, 0, gpuFb, stats, error)) return {name + " (RENDER FAIL)", -1, 0.0};

    long sum = 0;
    int maxD = 0;
    size_t pixels = static_cast<size_t>(W) * H;
    if (name == "1 floor+sun direct only") {
        for (int y : {10, 30, 40, 50}) {
            std::cout << "  row " << y << ": ";
            for (int x = 0; x < W; x += 16) {
                int i = (y * W + x) * 4;
                std::cout << "(" << (int)cpuFb.colorData[i] << "/" << (int)gpuFb.colorData[i] << ") ";
            }
            std::cout << std::endl;
        }
    }
    for (size_t i = 0; i < pixels; ++i) {
        int d = 0;
        for (int c = 0; c < 3; ++c) {
            int da = std::abs(static_cast<int>(cpuFb.colorData[i*4+c]) - static_cast<int>(gpuFb.colorData[i*4+c]));
            if (da > d) d = da;
        }
        sum += d;
        if (d > maxD) maxD = d;
    }
    return {name, maxD, static_cast<double>(sum) / pixels};
}

int main() {
    const int W = 96, H = 72;
    Camera cam;
    cam.position = Vec3(0, 1.8f, 5.0f);
    cam.target = Vec3(0, 0.6f, 0);
    cam.up = Vec3(0, 1, 0);
    cam.fovY = 50.0f * (Numerics::PI / 180.0f);
    cam.aspectRatio = static_cast<float>(W) / H;

    RenderSettings base;
    base.samplesPerPixel = 4;
    base.maxBounces = 4;
    base.numThreads = 1;
    base.deterministicMode = true;
    base.rngSeed = 0xC0FFEE;
    base.colorPipeline.toneMapping = ToneMapping::None;
    base.colorPipeline.srgbOutput = false;
    base.jitteredSampling = false;
    base.stratifiedSampling = false;

    auto floorScene = [&]() {
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
        sun.color = Vec3(1, 1, 1);
        sun.intensity = 1.2f;
        scene->addLight(sun);
        return scene;
    };

    {
        auto s = floorScene();
        auto r = runCase("1 floor+sun direct only", s, base, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        RenderSettings srgb = base;
        srgb.giEnabled = false;
        srgb.colorPipeline.srgbOutput = true;
        auto r = runCase("1b no-GI + sRGB", s, srgb, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        RenderSettings gi = base;
        gi.giEnabled = true;
        gi.colorPipeline.srgbOutput = true;
        auto r = runCase("1c GI + sRGB", s, gi, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        RenderSettings gilinear = base;
        gilinear.giEnabled = true;
        auto r = runCase("1d GI + linear(gamma2.2)", s, gilinear, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        s->addMesh(makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f));   // occluder -> shadows
        auto r = runCase("2 + shadow caster", s, base, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        s->addMesh(makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f));
        RenderSettings noshadow = base;
        noshadow.shadowsEnabled = false;
        auto r = runCase("2b box + shadows OFF", s, noshadow, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        s->addMesh(makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f));
        RenderSettings spp1 = base;
        spp1.samplesPerPixel = 1;
        spp1.giEnabled = false;
        auto r = runCase("2c box + shadows + SPP1", s, spp1, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        s->addMesh(makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f));
        RenderSettings gi = base;
        gi.giEnabled = true;
        auto r = runCase("3 + GI", s, gi, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh metal = makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f);
        metal.material.albedo = Vec3(1.0f, 0.85f, 0.4f);
        metal.material.metallic = 1.0f;
        metal.material.roughness = 0.05f;
        s->addMesh(metal);
        auto r = runCase("4 + metallic", s, base, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh glass = makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f);
        glass.material.albedo = Vec3(0.95f, 0.95f, 1.0f);
        glass.material.transmission = 1.0f;
        glass.material.ior = 1.5f;
        glass.material.roughness = 0.0f;
        s->addMesh(glass);
        auto r = runCase("5 + transmission", s, base, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh lamp = makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f);
        lamp.material.albedo = Vec3(1.0f, 0.6f, 0.2f);
        lamp.material.emission = 4.0f;
        s->addMesh(lamp);
        auto r = runCase("6 + emissive", s, base, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Light point;
        point.type = LightType::Point;
        point.position = Vec3(0, 3, 0);
        point.color = Vec3(1, 0.95f, 0.9f);
        point.intensity = 2.0f;
        s->addLight(point);
        auto r = runCase("7 + point light", s, base, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        RenderSettings jitter = base;
        jitter.jitteredSampling = true;
        auto r = runCase("8 + jittered sampling", s, jitter, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        RenderSettings strat = base;
        strat.jitteredSampling = true;
        strat.stratifiedSampling = true;
        auto r = runCase("9 + stratified sampling", s, strat, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        RenderSettings aces = base;
        aces.colorPipeline.toneMapping = ToneMapping::ACES;
        aces.colorPipeline.srgbOutput = true;
        auto r = runCase("10 + ACES + sRGB", s, aces, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }

    // GI + object material interactions (the earlier GI case was floor-only)
    {
        auto s = floorScene();
        s->addMesh(makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f));  // diffuse box
        RenderSettings gi = base;
        gi.giEnabled = true;
        auto r = runCase("11 GI + diffuse box", s, gi, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh metal = makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f);
        metal.material.albedo = Vec3(1.0f, 0.85f, 0.4f);
        metal.material.metallic = 1.0f;
        metal.material.roughness = 0.05f;
        s->addMesh(metal);
        RenderSettings gi = base;
        gi.giEnabled = true;
        auto r = runCase("12 GI + metallic box", s, gi, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh glass = makeBox(-0.8f, 0, -0.4f, 0.2f, 0.9f, 0.5f);
        glass.material.albedo = Vec3(0.95f, 0.95f, 1.0f);
        glass.material.transmission = 1.0f;
        glass.material.ior = 1.5f;
        glass.material.roughness = 0.0f;
        s->addMesh(glass);
        RenderSettings gi = base;
        gi.giEnabled = true;
        auto r = runCase("13 GI + glass box", s, gi, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh metal = makeBox(-1.4f, 0, -0.4f, -0.4f, 1.2f, 0.8f);
        metal.material.albedo = Vec3(1.0f, 0.85f, 0.4f);
        metal.material.metallic = 1.0f;
        metal.material.roughness = 0.05f;
        s->addMesh(metal);
        RenderSettings nomaxb = base;
        nomaxb.giEnabled = true;
        nomaxb.maxBounces = 1;
        auto r = runCase("14 GI + metal, bounces=1", s, nomaxb, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    {
        auto s = floorScene();
        Mesh metal = makeBox(-1.4f, 0, -0.4f, -0.4f, 1.2f, 0.8f);
        metal.material.albedo = Vec3(1.0f, 0.85f, 0.4f);
        metal.material.metallic = 1.0f;
        metal.material.roughness = 0.05f;
        s->addMesh(metal);
        RenderSettings nomaxb = base;
        nomaxb.giEnabled = true;
        nomaxb.maxBounces = 2;
        auto r = runCase("15 GI + metal, bounces=2", s, nomaxb, cam, W, H);
        std::cout << r.name << ": max=" << r.maxDelta << " mean=" << r.meanDelta << std::endl;
    }
    return 0;
}
