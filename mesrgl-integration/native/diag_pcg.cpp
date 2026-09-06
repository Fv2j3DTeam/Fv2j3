// diag_pcg: compares the GLSL emulated PCG32 against the C++ MesrGL::PCG32.
#include "MesrGL/Core.hpp"
#include "gpu_executor.hpp"
#include "vulkan/VulkanExecutorDevice.hpp"

#include <cstring>
#include <iostream>

using namespace MesrGL;
using namespace MesrGLBridge;

int main() {
    const uint32_t count = 32;
    MesrGLBridge::VulkanExecutorDevice device;
    std::string error;
    if (!device.initialize(error)) {
        std::cout << "GPU unavailable: " << error << std::endl;
        return 2;
    }

    // Compile pcg_ref kernel via glslangValidator at runtime? No — use the
    // pipeline API with a pre-compiled SPIR-V loaded from file (built alongside).
    FILE* f = fopen("pcg_ref.spv", "rb");
    if (!f) { std::cout << "pcg_ref.spv missing" << std::endl; return 1; }
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    std::vector<uint32_t> code(sz / 4);
    if (fread(code.data(), 1, sz, f) != (size_t)sz) { fclose(f); return 1; }
    fclose(f);

    // Buffers: output (count*2), seed UBO (4 u32)
    MesrGLBridge::VulkanExecutorDevice::Buffer out, seeds;
    if (!device.createBuffer(count * 2 * sizeof(uint32_t), false, out, error)) { std::cout << error << std::endl; return 1; }
    if (!device.createBuffer(64, false, seeds, error)) { std::cout << error << std::endl; return 1; }

    uint64_t ctorSeed = 0xC0FFEE;
    uint64_t setSeed = 0xC0FFEE + 0x1234;
    struct { uint32_t lo, hi, lo2, hi2, count, pad, extra[4]; } seedData;
    seedData.lo = static_cast<uint32_t>(ctorSeed);
    seedData.hi = static_cast<uint32_t>(ctorSeed >> 32);
    seedData.lo2 = static_cast<uint32_t>(setSeed);
    seedData.hi2 = static_cast<uint32_t>(setSeed >> 32);
    seedData.count = count;
    seedData.pad = 0;
    for (int i = 0; i < 4; ++i) seedData.extra[i] = 0u;
    double t;
    device.uploadBuffer(seeds, &seedData, sizeof(seedData), t);

    // The device binds 7 fixed bindings; for this diagnostic we reuse the
    // generic path: binding 0 = out, binding 4 = seeds. But bindSceneBuffers
    // binds all 7. Simplest: create the pipeline and bind through the same
    // descriptor layout by treating 'out' as triangles and 'seeds' as UBO,
    // leaving others null — not possible (null buffers). Instead use a raw
    // mini-pipeline here via device internals... For simplicity we instead
    // run pcg_ref through a standalone dispatch using the executor device's
    // bindSceneBuffers with dummy buffers for unused slots.
    MesrGLBridge::VulkanExecutorDevice::Buffer dummy;
    if (!device.createBuffer(16, false, dummy, error)) return 1;

    if (!device.createComputePipeline(code.data(), code.size(), error)) { std::cout << error << std::endl; return 1; }
    // bind: 0=out 1=dummy 2=dummy 3=dummy 4=seeds 5=dummy 6=dummy
    if (!device.bindSceneBuffers(out, dummy, dummy, dummy, seeds, dummy, dummy, error)) { std::cout << error << std::endl; return 1; }

    MesrGLBridge::VulkanExecutorDevice::DispatchParams params;
    params.groupsX = 1; params.groupsY = 1;
    params.pathtracePipeline = 0;
    params.presentPipeline = 0;   // unused but must be valid; barrier harmless
    params.pushPathtrace[0] = 0; params.pushPathtrace[1] = 0;
    params.pushPresent = 0.0f;
    MesrGLBridge::GpuFrameStats stats;
    if (!device.submitFrame(params, stats, error)) { std::cout << error << std::endl; return 1; }

    uint32_t gpuOut[count * 2];
    std::memcpy(gpuOut, out.mapped, sizeof(gpuOut));

    // C++ reference
    PCG32 cppCtor(ctorSeed);
    uint32_t cppOut[count * 2];
    for (uint32_t i = 0; i < count; ++i) cppOut[i] = cppCtor.nextUInt();
    PCG32 cppSet;
    cppSet.setSeed(setSeed);
    for (uint32_t i = 0; i < count; ++i) cppOut[count + i] = cppSet.nextUInt();

    int mismatches = 0;
    for (uint32_t i = 0; i < count * 2; ++i) {
        if (gpuOut[i] != cppOut[i]) {
            if (mismatches < 5)
                std::cout << "  mismatch[" << i << "]: gpu=" << std::hex << gpuOut[i]
                          << " cpp=" << cppOut[i] << std::dec << std::endl;
            ++mismatches;
        }
    }
    std::cout << (mismatches == 0 ? "PCG32 GPU/CPU: BIT-EXACT MATCH"
                                  : "PCG32 MISMATCHES: " + std::to_string(mismatches)) << std::endl;
    return mismatches == 0 ? 0 : 1;
}
