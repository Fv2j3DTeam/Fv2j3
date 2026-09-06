#ifndef MESRGL_BRIDGE_GPU_EXECUTOR_HPP
#define MESRGL_BRIDGE_GPU_EXECUTOR_HPP

// ============================================================
// GpuExecutor - Fv2j3 integration GPU execution substrate.
//
// Consumes MesrGL's CPU-prepared GpuSceneData and runs the embedded
// SPIR-V payloads of MesrGL's software ray-tracing kernels on a
// Vulkan compute device. This component lives in the Fv2j3
// integration, NOT in MesrGL: the MesrGL library contains no
// graphics API code. Without a GPU, MesrGL's CPU renderer is the
// explicit fallback.
//
// The kernels execute the SOFTWARE ray-tracing algorithm. No
// hardware RT, RT cores, or ray-tracing extensions are used.
// ============================================================

#include "MesrGL/GpuScene.hpp"
#include "MesrGL/Core.hpp"
#include "vulkan/VulkanExecutorDevice.hpp"

#include <string>
#include <cstdint>

namespace MesrGLBridge {

// Kernel payload accessors (generated at build time by EmbedSpirv.cmake,
// or provided as unavailable stubs when glslangValidator is missing).
const uint32_t* pathtrace_spirv_data();
size_t pathtrace_spirv_words();
const uint32_t* present_spirv_data();
size_t present_spirv_words();
bool gpuKernelsAvailable();

struct GpuExecutorStats {
    double lastUploadMs = 0.0;        // scene/UBO upload before dispatch (0 when clean)
    double lastFrameMs = 0.0;         // total submit+wait
    double lastDispatchSubmitMs = 0.0;
    double lastGpuWaitMs = 0.0;
    double lastGpuTimeMs = 0.0;       // GPU timestamps across both dispatches
    double lastPathtraceGpuMs = 0.0;
    double lastPresentGpuMs = 0.0;
    double lastReadbackMs = 0.0;
};

class GpuExecutor {
public:
    GpuExecutor() = default;
    ~GpuExecutor() { shutdown(); }

    GpuExecutor(const GpuExecutor&) = delete;
    GpuExecutor& operator=(const GpuExecutor&) = delete;

    // forceFailure simulates GPU unavailability (fallback verification).
    bool initialize(bool forceFailure = false);
    void shutdown();
    bool available() const;

    std::string deviceName() const;
    std::string apiVersionString() const;
    std::string diagnosticInfo() const;

    // Measured GPU memory footprint from live buffer allocations.
    GpuMemoryStats memoryStats() const;

    // Uploads dirty scene data into grow-only persistent GPU buffers.
    bool uploadScene(const MesrGL::GpuSceneData& data, std::string& error);

    // Pre-allocates scene buffers up front so the render path never has to
    // grow (grow = destroy+recreate, which invalidates descriptor bindings
    // and has proven driver-hostile on mid-session growth).
    bool reserve(uint64_t triangleBytes, uint64_t bvhBytes,
                 uint64_t materialBytes, uint64_t lightBytes,
                 uint32_t width, uint32_t height, std::string& error);

    // Renders one frame into fb (RGBA8). frameIndex 0 resets the
    // accumulator; >0 accumulates progressively (1 spp per dispatch).
    bool renderFrame(const MesrGL::GpuSceneData& data, uint32_t width, uint32_t height,
                     uint64_t frameIndex, MesrGL::Framebuffer& fb,
                     GpuExecutorStats& stats, std::string& error);

private:
    bool ensureCapacity(const MesrGL::GpuSceneData& data, uint32_t width, uint32_t height, std::string& error);
    bool createPipelines(std::string& error);
    bool resizeOutput(uint32_t width, uint32_t height, std::string& error);
    bool createBufferClassified(uint64_t bytes, bool deviceLocal,
                                VulkanExecutorDevice::Buffer& buf,
                                uint64_t& tracked, std::string& error);

    VulkanExecutorDevice device_;
    VulkanExecutorDevice::Buffer tri_;
    VulkanExecutorDevice::Buffer bvh_;
    VulkanExecutorDevice::Buffer mat_;
    VulkanExecutorDevice::Buffer light_;
    VulkanExecutorDevice::Buffer ubo_;
    VulkanExecutorDevice::Buffer out_;
    VulkanExecutorDevice::Buffer accum_;

    uint64_t uploadedTriBytes_ = 0;
    uint64_t uploadedBvhBytes_ = 0;
    uint64_t uploadedMatBytes_ = 0;
    uint64_t uploadedLightBytes_ = 0;
    double lastUploadMs_ = 0.0;
    uint32_t width_ = 0;
    uint32_t height_ = 0;
    bool pipelinesReady_ = false;
    bool buffersBound_ = false;
    bool initialized_ = false;
    std::string initError_;
};

} // namespace MesrGLBridge

#endif // MESRGL_BRIDGE_GPU_EXECUTOR_HPP
