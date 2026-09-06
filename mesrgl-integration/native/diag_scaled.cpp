// diag_scaled: measures GPU software-RT pathtrace time as the triangle
// count scales, to isolate the fence-timeout hang seen with the extracted
// Minecraft scene (~657k triangles, 640x360, SPP1).
//
// Scene: grid of axis-aligned cubes on a ground plane (Minecraft-like
// coplanar-heavy geometry), 12 triangles per cube + ground quad.
#include "MesrGL/Core.hpp"
#include "MesrGL/Renderer.hpp"
#include "MesrGL/GpuScene.hpp"
#include "gpu_executor.hpp"

#include <chrono>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>

using namespace MesrGL;
using MesrGLBridge::GpuExecutor;
using MesrGLBridge::GpuExecutorStats;

static void dumpFramebufferBmp(const MesrGL::Framebuffer& fb, const std::string& path) {
    int W = fb.width, H = fb.height;
    int rowBytes = W * 3, pad = (4 - rowBytes % 4) % 4;
    int dataSize = (rowBytes + pad) * H;
    std::ofstream out(path, std::ios::binary);
    unsigned char header[54] = {'B','M'};
    auto put32 = [&](int off, uint32_t v) { std::memcpy(header+off, &v, 4); };
    put32(2, 54 + dataSize); put32(10, 54); put32(14, 40);
    put32(18, W); put32(22, H); header[26] = 1; header[28] = 24;
    put32(34, dataSize); put32(38, 2835); put32(42, 2835);
    out.write((char*)header, 54);
    std::vector<unsigned char> row(rowBytes + pad, 0);
    for (int y = H - 1; y >= 0; --y) {
        for (int x = 0; x < W; ++x) {
            MesrGL::Vec3 c = fb.getPixelHDR(x, y);
            row[x*3] = (unsigned char)(Numerics::saturate(c.z) * 255.0f);
            row[x*3+1] = (unsigned char)(Numerics::saturate(c.y) * 255.0f);
            row[x*3+2] = (unsigned char)(Numerics::saturate(c.x) * 255.0f);
        }
        out.write((char*)row.data(), row.size());
    }
}

static Mesh makeCube(float x0, float y0, float z0, float x1, float y1, float z1) {
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

static Mesh makeCube(float x0, float y0, float z0, float x1, float y1, float z1);

// Replays an exact scene captured by the Minecraft integration
// (-Dfv2j3.mesrgl.dumpScene=/path). argv: replay <file> <w> <h>
static int replay(const std::string& path, int width, int height, bool autoCam) {
    std::ifstream in(path, std::ios::binary);
    if (!in) { std::cout << "cannot open " << path << std::endl; return 1; }
    char magicBytes[4] = {0, 0, 0, 0};
    in.read(magicBytes, 4);
    if (std::memcmp(magicBytes, "MSGL", 4) != 0) { std::cout << "bad magic" << std::endl; return 1; }
    // Java DataOutputStream is big-endian throughout; decode integers accordingly.
    auto readInt = [&in]() { unsigned char b[4]; in.read((char*)b, 4);
        return (int)((b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3]); };
    auto readFloat = [&in]() { unsigned char b[4]; in.read((char*)b, 4);
        uint32_t u = ((uint32_t)b[0] << 24) | ((uint32_t)b[1] << 16) | ((uint32_t)b[2] << 8) | b[3];
        float f; std::memcpy(&f, &u, 4); return f; };
    int groups = readInt();
    auto scene = std::make_shared<Scene>();
    for (int g = 0; g < groups; ++g) {
        int rgb = readInt();
        int posCount = readInt();
        std::vector<float> pos(posCount);
        for (int i = 0; i < posCount; ++i) pos[i] = readFloat();
        int idxCount = readInt();
        std::vector<int> idx(idxCount);
        for (int i = 0; i < idxCount; ++i) idx[i] = readInt();
        Mesh mesh;
        std::vector<int> vertexIds(posCount / 3);
        for (int v = 0; v < posCount / 3; ++v) {
            vertexIds[v] = mesh.addVertex(Vec3(pos[v*3], pos[v*3+1], pos[v*3+2]),
                                          Vec3(0, 1, 0), Vec2(0, 0));
        }
        for (int i = 0; i + 2 < idxCount; i += 3) {
            mesh.addTriangle(idx[i], idx[i+1], idx[i+2]);
        }
        (void)rgb; (void)vertexIds;
        mesh.material.albedo = Vec3(((rgb>>16)&0xFF)/255.0f, ((rgb>>8)&0xFF)/255.0f, (rgb&0xFF)/255.0f);
        scene->addMesh(mesh);
    }
    float sunIntensity = readFloat();
    float ex = readFloat(), ey = readFloat(), ez = readFloat();
    float lx = readFloat(), ly = readFloat(), lz = readFloat();
    int radius = readInt();

    float s = radius * 16.0f + 128.0f;
    float ox = std::floor(ex / 64.0f) * 64.0f + 32.0f;
    float oz = std::floor(ez / 64.0f) * 64.0f + 32.0f;
    Mesh sky = makeCube(ox - s, -s, oz - s, ox + s, s, oz + s);
    sky.material.albedo = Vec3(0.55f, 0.75f, 1.0f);
    sky.material.emission = 0.9f;
    scene->addMesh(sky);
    Light sun;
    sun.type = LightType::Directional;
    sun.direction = Vec3(-0.45f, -0.85f, -0.28f);
    sun.color = Vec3(1.0f, 0.97f, 0.88f);
    sun.intensity = sunIntensity;
    scene->addLight(sun);
    scene->clearColor = Vec3(0.50f, 0.68f, 0.92f);

    SoftwareRayTracer tracer;
    tracer.setScene(scene);
    tracer.buildAccelerationStructure();
    std::cout << "replay: groups=" << groups << " tris~" << tracer.getBVH().primitives.size()
              << " bvhNodes=" << tracer.getBVH().nodeCount() << std::endl;

    RenderSettings settings;
    settings.samplesPerPixel = 1;
    settings.maxSamplesPerPixel = 1;
    settings.maxBounces = 2;
    settings.deterministicMode = true;
    settings.colorPipeline.toneMapping = ToneMapping::ACES;
    settings.colorPipeline.exposure = 1.6f;

    GpuSceneData data = buildGpuSceneData(*scene, tracer.getBVH(), settings, width, height);
    {
        Mat4 view = Mat4::lookAt(Vec3(ex, ey, ez), Vec3(ex + lx, ey + ly, ez + lz), Vec3(0, 1, 0));
        Mat4 invView = view.inverse();
        for (int r = 0; r < 4; ++r)
            for (int c = 0; c < 4; ++c)
                data.frame.invView[r * 4 + c] = invView.m[r][c];
        data.frame.camMisc[0] = std::tan(70.0f * 3.14159f / 180.0f * 0.5f);
        data.frame.camMisc[1] = (float)width / (float)height;
    }

    // CPU reference render of the SAME scene + camera (oracle for the GPU).
    // camera=auto overrides with a view of the whole BVH bounds.
    {
        Camera cam;
        if (autoCam) {
            AABB root = tracer.getBVH().nodes[0].bounds;
            Vec3 c = root.center();
            cam.position = Vec3(c.x, root.maxBounds.y + 40.0f, c.z + 60.0f);
            cam.target = c;
            std::cout << "auto cam: bounds min=(" << root.minBounds.x << "," << root.minBounds.y
                      << "," << root.minBounds.z << ") max=(" << root.maxBounds.x << ","
                      << root.maxBounds.y << "," << root.maxBounds.z << ")" << std::endl;
        } else {
            cam.position = Vec3(ex, ey, ez);
            cam.target = Vec3(ex + lx, ey + ly, ez + lz);
        }
        cam.fovY = 70.0f * 3.14159f / 180.0f;
        cam.aspectRatio = (float)width / (float)height;
        tracer.settings.samplesPerPixel = 1;
        tracer.settings.maxSamplesPerPixel = 1;
        tracer.settings.maxBounces = 2;
        tracer.settings.colorPipeline.toneMapping = ToneMapping::ACES;
        tracer.settings.colorPipeline.exposure = 1.6f;
        Framebuffer cpuFb(width, height);
        tracer.beginFrame(cpuFb);
        tracer.render(*scene, cam);
        tracer.endFrame();
        dumpFramebufferBmp(cpuFb, "/tmp/replay-cpu.bmp");
        std::cout << "CPU reference dumped to /tmp/replay-cpu.bmp" << std::endl;
    }

    GpuExecutor executor;
    executor.initialize();
    if (!executor.available()) { std::cout << "GPU unavailable" << std::endl; return 2; }
    std::string error;
    auto up0 = std::chrono::high_resolution_clock::now();
    if (!executor.uploadScene(data, error)) { std::cout << "upload FAILED: " << error << std::endl; return 3; }
    auto up1 = std::chrono::high_resolution_clock::now();
    std::cout << "upload " << std::chrono::duration<double, std::milli>(up1 - up0).count() << " ms" << std::endl;

    MesrGL::Framebuffer fb(width, height);
    for (int frame = 0; frame < 3; ++frame) {
        auto t0 = std::chrono::high_resolution_clock::now();
        GpuExecutorStats stats;
        if (!executor.renderFrame(data, width, height, 0, fb, stats, error)) {
            std::cout << "render FAILED on frame " << frame << ": " << error << std::endl;
            return 4;
        }
        auto t1 = std::chrono::high_resolution_clock::now();
        std::cout << "frame " << frame << ": wall="
                  << std::chrono::duration<double, std::milli>(t1 - t0).count()
                  << " ms gpu=" << stats.lastGpuTimeMs << " ms" << std::endl;
        if (frame == 0) {
            // Dump the rendered frame as BMP for visual inspection.
            int W = (int)data.frame.camMisc[2], H = (int)data.frame.camMisc[3];
            std::vector<unsigned char> rgba(W * H * 4);
            // fb stores HDR; blitToU8 applies nothing extra here (values are
            // already post-pipeline from the present kernel).
            fb.blitToU8();
            for (int y = 0; y < H; ++y)
                for (int x = 0; x < W; ++x) {
                    MesrGL::Vec3 c = fb.getPixelHDR(x, y);
                    int i = (y * W + x) * 4;
                    rgba[i] = (unsigned char)(Numerics::saturate(c.x) * 255.0f);
                    rgba[i+1] = (unsigned char)(Numerics::saturate(c.y) * 255.0f);
                    rgba[i+2] = (unsigned char)(Numerics::saturate(c.z) * 255.0f);
                    rgba[i+3] = 255;
                }
            int rowBytes = W * 3, pad = (4 - rowBytes % 4) % 4;
            int dataSize = (rowBytes + pad) * H;
            std::ofstream out("/tmp/replay-frame.bmp", std::ios::binary);
            unsigned char header[54] = {'B','M'};
            auto put32 = [&](int off, uint32_t v) { std::memcpy(header+off, &v, 4); };
            put32(2, 54 + dataSize); put32(10, 54); put32(14, 40);
            put32(18, W); put32(22, H); header[26] = 1; header[28] = 24;
            put32(34, dataSize); put32(38, 2835); put32(42, 2835);
            out.write((char*)header, 54);
            std::vector<unsigned char> row(rowBytes + pad, 0);
            for (int y = H - 1; y >= 0; --y) {
                for (int x = 0; x < W; ++x) {
                    row[x*3] = rgba[(y*W+x)*4+2];
                    row[x*3+1] = rgba[(y*W+x)*4+1];
                    row[x*3+2] = rgba[(y*W+x)*4];
                }
                out.write((char*)row.data(), row.size());
            }
            std::cout << "frame dumped to /tmp/replay-frame.bmp" << std::endl;
        }
    }
    std::cout << "OK" << std::endl;
    return 0;
}

int main(int argc, char** argv) {
    if (argc > 1 && std::string(argv[1]) == "replay") {
        return replay(argv[2], argc > 3 ? std::atoi(argv[3]) : 640, argc > 4 ? std::atoi(argv[4]) : 360,
                      argc > 5 && std::string(argv[5]) == "auto");
    }
    int grid = argc > 1 ? std::atoi(argv[1]) : 150;          // grid x grid cubes
    int width = argc > 2 ? std::atoi(argv[2]) : 320;
    int height = argc > 3 ? std::atoi(argv[3]) : 240;
    int spp = argc > 4 ? std::atoi(argv[4]) : 1;

    auto scene = std::make_shared<Scene>();
    auto material = std::make_shared<Material>();
    material->albedo = Vec3(0.6f, 0.55f, 0.5f);

    Mesh ground;
    Vec3 gn(0, 1, 0);
    ground.addTriangle(ground.addVertex(Vec3(-1000, 0, -1000), gn, Vec2(0, 0)),
                       ground.addVertex(Vec3(1000, 0, -1000), gn, Vec2(1, 0)),
                       ground.addVertex(Vec3(1000, 0, 1000), gn, Vec2(1, 1)));
    ground.addTriangle(ground.addVertex(Vec3(-1000, 0, -1000), gn, Vec2(0, 0)),
                       ground.addVertex(Vec3(1000, 0, 1000), gn, Vec2(1, 1)),
                       ground.addVertex(Vec3(-1000, 0, 1000), gn, Vec2(0, 1)));
    scene->addMesh(ground);

    for (int gx = 0; gx < grid; ++gx) {
        for (int gz = 0; gz < grid; ++gz) {
            float x0 = static_cast<float>(gx * 3);
            float z0 = static_cast<float>(gz * 3);
            float h = 1.0f + 2.0f * std::fmod(std::sin(gx * 12.9898f + gz * 78.233f) * 43758.5453f, 1.0f);
            scene->addMesh(makeCube(x0, 0.0f, z0, x0 + 1.5f, h, z0 + 1.5f));
        }
    }

    // Minecraft-integration parity: huge emissive sky box AROUND the camera
    // plus a directional sun (reproduces the game-scene composition).
    bool skybox = argc > 5 && std::string(argv[5]) == "sky";
    if (skybox) {
        float s = 400.0f;
        Mesh sky = makeCube(-s, -s, -s, s, s, s);
        sky.material.albedo = Vec3(0.55f, 0.75f, 1.0f);
        sky.material.emission = 0.9f;
        scene->addMesh(sky);
        Light sun;
        sun.type = LightType::Directional;
        sun.direction = Vec3(0.55f, 0.75f, 0.35f);
        sun.color = Vec3(1.0f, 0.97f, 0.88f);
        sun.intensity = 3.4f;
        scene->addLight(sun);
    }

    SoftwareRayTracer tracer;
    tracer.setScene(scene);
    tracer.buildAccelerationStructure();
    std::cout << "grid=" << grid << " meshes=" << scene->meshes.size()
              << " bvhNodes=" << tracer.getBVH().nodeCount() << std::endl;

    RenderSettings settings;
    settings.samplesPerPixel = spp;
    settings.maxSamplesPerPixel = spp;
    settings.maxBounces = 2;
    settings.deterministicMode = true;

    int width_ = width, height_ = height;
    GpuSceneData data = buildGpuSceneData(*scene, tracer.getBVH(), settings, width_, height_);
    if (skybox) {
        float cx = grid * 3.0f * 0.5f;
        Mat4 view = Mat4::lookAt(Vec3(cx, 5.0f, cx), Vec3(cx - 10.0f, 2.0f, cx - 10.0f), Vec3(0, 1, 0));
        Mat4 invView = view.inverse();
        for (int r = 0; r < 4; ++r)
            for (int c = 0; c < 4; ++c)
                data.frame.invView[r * 4 + c] = invView.m[r][c];
    }

    GpuExecutor executor;
    executor.initialize();
    if (!executor.available()) {
        std::cout << "GPU executor unavailable" << std::endl;
        return 2;
    }
    std::string error;
    std::cout << "GPU: " << executor.deviceName() << std::endl;
    auto up0 = std::chrono::high_resolution_clock::now();
    if (!executor.uploadScene(data, error)) {
        std::cout << "upload FAILED: " << error << std::endl;
        return 3;
    }
    auto up1 = std::chrono::high_resolution_clock::now();
    std::cout << "upload " << std::chrono::duration<double, std::milli>(up1 - up0).count()
              << " ms, triangles=" << (data.triangles.size() / 28) << std::endl;

    MesrGL::Framebuffer fb(width, height);
    for (int frame = 0; frame < 3; ++frame) {
        auto t0 = std::chrono::high_resolution_clock::now();
        GpuExecutorStats stats;
        if (!executor.renderFrame(data, width, height, 0, fb, stats, error)) {
            std::cout << "render FAILED on frame " << frame << ": " << error << std::endl;
            return 4;
        }
        auto t1 = std::chrono::high_resolution_clock::now();
        std::cout << "frame " << frame << ": wall="
                  << std::chrono::duration<double, std::milli>(t1 - t0).count()
                  << " ms gpu=" << stats.lastGpuTimeMs << " ms" << std::endl;
    }
    std::cout << "OK" << std::endl;
    return 0;
}
