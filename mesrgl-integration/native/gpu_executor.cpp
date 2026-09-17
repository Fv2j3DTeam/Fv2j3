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
    // Pipeline creation happens in device_ when Vulkan is available
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
    return initialized_ && pipelinesReady_ && device_.available();
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
    mem.geometryBytes = tri_.handle ? tri_.size : 0;
    mem.bvhBytes = bvh_.handle ? bvh_.size : 0;
    mem.materialBytes = mat_.handle ? mat_.size : 0;
    mem.lightBytes = light_.handle ? light_.size : 0;
    mem.uniformBytes = ubo_.handle ? ubo_.size : 0;
    mem.accumulatorBytes = accum_.handle ? accum_.size : 0;
    mem.outputBytes = out_.handle ? out_.size : 0;
    return mem;
}

bool GpuExecutor::createBufferClassified(uint64_t bytes, bool deviceLocal,
                                         VulkanExecutorDevice::Buffer& buf,
                                         uint64_t& tracked, std::string& error) {
    if (buf.handle && buf.size >= bytes) return true;   // grow-only persistent buffers
    device_.destroyBuffer(buf);
    if (!device_.createBuffer(bytes, deviceLocal, buf, error)) {
        tracked = 0;
        return false;
    }
    tracked = bytes;
    return true;
}

bool GpuExecutor::ensureCapacity(const MesrGL::GpuSceneData& data, uint32_t width, uint32_t height, std::string& error) {
    bool rebound = false;
    auto grow = [&](uint64_t bytes, bool deviceLocal, VulkanExecutorDevice::Buffer& buf, uint64_t& tracked) {
        if (buf.handle && buf.size >= bytes) return true;
        rebound = true;
        return createBufferClassified(bytes, deviceLocal, buf, tracked, error);
    };
    
    // GpuSceneData has svoNodes, materials, lights, bvhNodes vectors
    uint64_t svoBytes = data.svoNodes.size() * sizeof(MesrGL::GPUSVONode);
    uint64_t matBytes = data.materials.size() * sizeof(MesrGL::GPUMaterial);
    uint64_t lightBytes = data.lights.size() * sizeof(MesrGL::GPULight);
    uint64_t bvhBytes = data.bvhNodes.size() * sizeof(MesrGL::GPUBVHNode);
    
    if (!grow(svoBytes, true, tri_, uploadedTriBytes_)) return false;
    if (!grow(bvhBytes + sizeof(MesrGL::GPUBVHNode), true, bvh_, uploadedBvhBytes_)) return false;
    if (!grow(matBytes + sizeof(MesrGL::GPUMaterial), true, mat_, uploadedMatBytes_)) return false;
    if (!grow(lightBytes + sizeof(MesrGL::GPULight), true, light_, uploadedLightBytes_)) return false;
    if (!grow(sizeof(MesrGL::GPUFrameParams), false, ubo_, ubo_.size)) return false;

    if (width != width_ || height != height_) {
        if (!resizeOutput(width, height, error)) return false;
        rebound = true;
    }

    if ((rebound || !buffersBound_) && out_.handle && accum_.handle) {
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
    
    std::string uploadError;
    if (!data.svoNodes.empty()) {
        if (!device_.uploadBuffer(tri_, data.svoNodes.data(), data.svoNodes.size() * sizeof(MesrGL::GPUSVONode), 0, uploadError)) { error = "triangle upload failed: " + uploadError; return false; }
    }
    if (!data.bvhNodes.empty()) {
        if (!device_.uploadBuffer(bvh_, data.bvhNodes.data(), data.bvhNodes.size() * sizeof(MesrGL::GPUBVHNode), 0, uploadError)) { error = "bvh upload failed: " + uploadError; return false; }
    }
    if (!data.materials.empty()) {
        if (!device_.uploadBuffer(mat_, data.materials.data(), data.materials.size() * sizeof(MesrGL::GPUMaterial), 0, uploadError)) { error = "material upload failed: " + uploadError; return false; }
    }
    if (!data.lights.empty()) {
        if (!device_.uploadBuffer(light_, data.lights.data(), data.lights.size() * sizeof(MesrGL::GPULight), 0, uploadError)) { error = "light upload failed: " + uploadError; return false; }
    }
    lastUploadMs_ = static_cast<double>(nowNs() - t0) / 1e6;
    return true;
}

bool GpuExecutor::reserve(uint64_t triangleBytes, uint64_t bvhBytes,
                          uint64_t materialBytes, uint64_t lightBytes,
                          uint32_t width, uint32_t height, std::string& error) {
    bool rebound = false;
    auto grow = [&](uint64_t bytes, bool deviceLocal, VulkanExecutorDevice::Buffer& buf, uint64_t& tracked) {
        if (buf.handle && buf.size >= bytes) return true;
        rebound = true;
        return createBufferClassified(bytes, deviceLocal, buf, tracked, error);
    };
    if (!grow(triangleBytes, true, tri_, uploadedTriBytes_)) return false;
    if (!grow(bvhBytes, true, bvh_, uploadedBvhBytes_)) return false;
    if (!grow(materialBytes, true, mat_, uploadedMatBytes_)) return false;
    if (!grow(lightBytes, true, light_, uploadedLightBytes_)) return false;
    if (!grow(sizeof(MesrGL::GPUFrameParams), false, ubo_, ubo_.size)) return false;
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
    std::string uboUploadError;
    if (!device_.uploadBuffer(ubo_, &data.frame, sizeof(data.frame), 0, uboUploadError)) {
        error = "frame UBO upload failed: " + uboUploadError;
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