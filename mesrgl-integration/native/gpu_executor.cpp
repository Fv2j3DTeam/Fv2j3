#include "gpu_executor.hpp"

#include <cstring>
#include <chrono>
#include <mutex>

namespace MesrGLBridge {

namespace {
uint64_t nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::high_resolution_clock::now().time_since_epoch())
        .count();
}
} // namespace

bool GpuExecutor::initialize(bool forceFailure) {
    if (initialized_) return available();
    shutdown();

    if (forceFailure) {
        initError_ = "GPU failure forced by test harness (MESRGL_FORCE_GPU_FAILURE)";
        return false;
    }
    if (!gpuKernelsAvailable()) {
        initError_ = "GPU kernels were not embedded at build time (glslangValidator missing)";
        return false;
    }

    std::string error;
    if (!device_.initialize(error)) {
        initError_ = error;
        return false;
    }
    if (!createPipelines(error)) {
        initError_ = error;
        shutdown();
        return false;
    }
    initialized_ = true;
    return true;
}

bool GpuExecutor::createPipelines(std::string& error) {
    if (!device_.createComputePipeline(pathtrace_spirv_data(), pathtrace_spirv_words(), error)) {
        return false;
    }
    if (!device_.createComputePipeline(present_spirv_data(), present_spirv_words(), error)) {
        return false;
    }
    pipelinesReady_ = true;
    return true;
}

void GpuExecutor::shutdown() {
    device_.destroyBuffer(tri_);
    device_.destroyBuffer(bvh_);
    device_.destroyBuffer(mat_);
    device_.destroyBuffer(light_);
    device_.destroyBuffer(ubo_);
    device_.destroyBuffer(out_);
    device_.destroyBuffer(accum_);
    uploadedTriBytes_ = uploadedBvhBytes_ = uploadedMatBytes_ = uploadedLightBytes_ = 0;
    width_ = height_ = 0;
    buffersBound_ = false;
    pipelinesReady_ = false;
    device_.shutdown();
    initialized_ = false;
}

bool GpuExecutor::available() const {
    return initialized_ && pipelinesReady_ && device_.isAvailable();
}

std::string GpuExecutor::deviceName() const {
    return available() ? device_.deviceName() : std::string();
}

std::string GpuExecutor::apiVersionString() const {
    return available() ? device_.apiVersionString() : std::string("N/A");
}

std::string GpuExecutor::diagnosticInfo() const {
    if (!available()) {
        return "GPU executor unavailable: " + initError_;
    }
    GpuMemoryStats mem = memoryStats();
    char buf[512];
    std::snprintf(buf, sizeof(buf),
                  "GPU Software RT executor | device=%s | api=%s | wg=%ux%u | "
                  "geometry=%.1fKB bvh=%.1fKB materials=%.1fKB lights=%.1fKB ubo=%.0fB "
                  "accum=%.1fMB output=%.1fMB staging=%.1fMB total=%.1fMB",
                  device_.deviceName().c_str(),
                  device_.apiVersionString().c_str(),
                  device_.maxComputeWorkGroupSize(0), device_.maxComputeWorkGroupSize(1),
                  mem.geometryBytes / 1024.0, mem.bvhBytes / 1024.0,
                  mem.materialBytes / 1024.0, mem.lightBytes / 1024.0,
                  static_cast<double>(mem.uniformBytes),
                  mem.accumulatorBytes / (1024.0 * 1024.0),
                  mem.outputBytes / (1024.0 * 1024.0),
                  mem.stagingBytes / (1024.0 * 1024.0),
                  mem.totalBytes() / (1024.0 * 1024.0));
    return std::string(buf);
}

GpuMemoryStats GpuExecutor::memoryStats() const {
    GpuMemoryStats mem = device_.memoryStats();
    mem.geometryBytes = tri_.buffer ? tri_.bytes : 0;
    mem.bvhBytes = bvh_.buffer ? bvh_.bytes : 0;
    mem.materialBytes = mat_.buffer ? mat_.bytes : 0;
    mem.lightBytes = light_.buffer ? light_.bytes : 0;
    mem.uniformBytes = ubo_.buffer ? ubo_.bytes : 0;
    mem.accumulatorBytes = accum_.buffer ? accum_.bytes : 0;
    mem.outputBytes = out_.buffer ? out_.bytes : 0;
    return mem;
}

bool GpuExecutor::createBufferClassified(uint64_t bytes, bool deviceLocal,
                                         VulkanExecutorDevice::Buffer& buf,
                                         uint64_t& tracked, std::string& error) {
    if (buf.buffer && buf.bytes >= bytes) return true;   // grow-only persistent buffers
    device_.destroyBuffer(buf);
    if (!device_.createBuffer(bytes, deviceLocal, buf, error)) {
        tracked = 0;
        return false;
    }
    tracked = bytes;
    return true;
}

bool GpuExecutor::ensureCapacity(const MesrGL::GpuSceneData& data, uint32_t width, uint32_t height, std::string& error) {
    // Device-local for the static scene data, host-visible for UBO/output.
    // A grow-only buffer is DESTROYED and recreated when it must expand; the
    // descriptor set binds buffer handles, so any recreation invalidates the
    // bindings and they MUST be rewritten before the next dispatch. Missing
    // this made the first dispatch after a scene growth read destroyed
    // buffers and lose the device (vkQueueSubmit failed).
    bool rebound = false;
    auto grow = [&](uint64_t bytes, bool deviceLocal, VulkanExecutorDevice::Buffer& buf, uint64_t& tracked) {
        if (buf.buffer && buf.bytes >= bytes) return true;
        rebound = true;
        return createBufferClassified(bytes, deviceLocal, buf, tracked, error);
    };
    if (!grow(data.triangleBytes(), true, tri_, uploadedTriBytes_)) return false;
    if (!grow(data.bvhBytes() + sizeof(MesrGL::GpuBvhNodeRecord), true, bvh_, uploadedBvhBytes_)) return false;
    if (!grow(data.materialBytes() + sizeof(MesrGL::GpuMaterialRecord), true, mat_, uploadedMatBytes_)) return false;
    if (!grow(data.lightBytes() + sizeof(MesrGL::GpuLightRecord), true, light_, uploadedLightBytes_)) return false;
    if (!grow(sizeof(MesrGL::GpuFrameParams), false, ubo_, ubo_.bytes)) return false;

    if (width != width_ || height != height_) {
        if (!resizeOutput(width, height, error)) return false;
        rebound = true;
    }

    if ((rebound || !buffersBound_) && out_.buffer && accum_.buffer) {
        if (!device_.bindSceneBuffers(tri_, bvh_, mat_, light_, ubo_, out_, accum_, error)) {
            return false;
        }
        buffersBound_ = true;
    }
    return true;
}

bool GpuExecutor::resizeOutput(uint32_t width, uint32_t height, std::string& error) {
    uint64_t accumBytes = static_cast<uint64_t>(width) * height * sizeof(float) * 4;
    uint64_t outBytes = static_cast<uint64_t>(width) * height * sizeof(uint32_t);
    device_.destroyBuffer(accum_);
    device_.destroyBuffer(out_);
    if (!device_.createBuffer(accumBytes, true, accum_, error)) {
        return false;
    }
    if (!device_.createBuffer(outBytes, false, out_, error)) {
        return false;
    }
    width_ = width;
    height_ = height;
    buffersBound_ = false;   // rebind with the new output/accumulator
    return true;
}

bool GpuExecutor::uploadScene(const MesrGL::GpuSceneData& data, std::string& error) {
    double t0 = static_cast<double>(nowNs());
    double upload = 0.0;
    if (!ensureCapacity(data, width_ ? width_ : 64, height_ ? height_ : 64, error)) return false;
    if (!device_.uploadBuffer(tri_, data.triangles.data(), data.triangleBytes(), upload)) { error = "triangle upload failed"; return false; }
    if (!device_.uploadBuffer(bvh_, data.bvhNodes.data(), data.bvhBytes(), upload)) { error = "bvh upload failed"; return false; }
    if (!device_.uploadBuffer(mat_, data.materials.data(), data.materialBytes(), upload)) { error = "material upload failed"; return false; }
    if (!device_.uploadBuffer(light_, data.lights.data(), data.lightBytes(), upload)) { error = "light upload failed"; return false; }
    lastUploadMs_ = static_cast<double>(nowNs() - t0) / 1e6;
    return true;
}

bool GpuExecutor::reserve(uint64_t triangleBytes, uint64_t bvhBytes,
                          uint64_t materialBytes, uint64_t lightBytes,
                          uint32_t width, uint32_t height, std::string& error) {
    bool rebound = false;
    auto grow = [&](uint64_t bytes, bool deviceLocal, VulkanExecutorDevice::Buffer& buf, uint64_t& tracked) {
        if (buf.buffer && buf.bytes >= bytes) return true;
        rebound = true;
        return createBufferClassified(bytes, deviceLocal, buf, tracked, error);
    };
    if (!grow(triangleBytes, true, tri_, uploadedTriBytes_)) return false;
    if (!grow(bvhBytes, true, bvh_, uploadedBvhBytes_)) return false;
    if (!grow(materialBytes, true, mat_, uploadedMatBytes_)) return false;
    if (!grow(lightBytes, true, light_, uploadedLightBytes_)) return false;
    if (!grow(sizeof(MesrGL::GpuFrameParams), false, ubo_, ubo_.bytes)) return false;
    if (!resizeOutput(width_ ? width_ : 640, height_ ? height_ : 360, error)) return false;
    if (!device_.preGrowStaging(triangleBytes, error)) return false;
    device_.waitIdlePublic();
    if (rebound || !buffersBound_) {
        if (!device_.bindSceneBuffers(tri_, bvh_, mat_, light_, ubo_, out_, accum_, error)) {
            return false;
        }
        buffersBound_ = true;
    }
    return true;
}

bool GpuExecutor::renderFrame(const MesrGL::GpuSceneData& data, uint32_t width, uint32_t height,
                              uint64_t frameIndex, MesrGL::Framebuffer& fb,
                              GpuExecutorStats& stats, std::string& error) {
    if (!available()) {
        error = "GPU executor not available";
        return false;
    }
    uint64_t t0 = nowNs();
    stats = GpuExecutorStats{};

    if (!ensureCapacity(data, width, height, error)) return false;

    // Frame UBO changes every frame (camera): small upload through staging.
    double uboUpload = 0.0;
    if (!device_.uploadBuffer(ubo_, &data.frame, sizeof(data.frame), uboUpload)) {
        error = "frame UBO upload failed";
        return false;
    }

    VulkanExecutorDevice::DispatchParams params;
    params.groupsX = (width + 7) / 8;
    params.groupsY = (height + 7) / 8;
    params.pathtracePipeline = kPathtracePipelineIndex;
    params.presentPipeline = kPresentPipelineIndex;
    params.pushPathtrace[0] = static_cast<uint32_t>(frameIndex);
    params.pushPathtrace[1] = 1;
    params.pushPresent = 0.0f;

    GpuFrameStats deviceStats;
    if (!device_.submitFrame(params, deviceStats, error)) {
        return false;
    }

    // Readback: packed RGBA8 words -> Framebuffer colorData bytes.
    uint64_t tRead = nowNs();
    size_t pixels = static_cast<size_t>(width) * height;
    const uint32_t* src = static_cast<const uint32_t*>(out_.mapped);
    if (!src) {
        error = "output buffer is not host visible";
        return false;
    }
    unsigned char* dst = fb.colorData.data();
    for (size_t i = 0; i < pixels; ++i) {
        uint32_t c = src[i];
        dst[i * 4 + 0] = static_cast<unsigned char>(c & 0xFFu);
        dst[i * 4 + 1] = static_cast<unsigned char>((c >> 8) & 0xFFu);
        dst[i * 4 + 2] = static_cast<unsigned char>((c >> 16) & 0xFFu);
        dst[i * 4 + 3] = static_cast<unsigned char>((c >> 24) & 0xFFu);
    }
    double readbackMs = static_cast<double>(nowNs() - tRead) / 1e6;

    stats.lastUploadMs = lastUploadMs_ + uboUpload;
    stats.lastFrameMs = static_cast<double>(nowNs() - t0) / 1e6;
    stats.lastDispatchSubmitMs = deviceStats.dispatchSubmitTimeMs;
    stats.lastGpuWaitMs = deviceStats.gpuWaitTimeMs;
    stats.lastGpuTimeMs = deviceStats.gpuTimeMs;
    stats.lastPathtraceGpuMs = deviceStats.pathtraceGpuTimeMs;
    stats.lastPresentGpuMs = deviceStats.presentGpuTimeMs;
    stats.lastReadbackMs = readbackMs;
    lastUploadMs_ = 0.0;
    return true;
}

} // namespace MesrGLBridge

namespace MesrGLBridge {

bool gpuKernelsAvailable() {
    return pathtrace_spirv_data() != nullptr && pathtrace_spirv_words() > 0 &&
           present_spirv_data() != nullptr && present_spirv_words() > 0;
}

} // namespace MesrGLBridge
