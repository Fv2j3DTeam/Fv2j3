#ifndef MESRGL_BRIDGE_VULKAN_EXECUTOR_DEVICE_HPP
#define MESRGL_BRIDGE_VULKAN_EXECUTOR_DEVICE_HPP

// ============================================================
// VulkanExecutorDevice - minimal Vulkan compute device layer of the
// Fv2j3 mesrgl-integration native bridge (NOT part of MesrGL).
//
// This is the GPU execution substrate of the Fv2j3 integration. The
// MesrGL library itself contains no graphics API code of any kind:
// its CPU software ray tracer is the reference implementation and
// fallback. The Vulkan loader is opened at runtime (dlopen); the
// bridge has no link-time dependency on Vulkan and falls back to
// MesrGL's CPU renderer when Vulkan or a compute device is
// unavailable.
//
// The executor runs a kernel that is a cross-validated port of
// MesrGL's SOFTWARE ray-tracing algorithm (ray generation, BVH
// traversal, intersection, materials, shadows, indirect light) as a
// general compute workload. No ray-tracing hardware APIs, RT cores,
// or ray-tracing extensions are used anywhere.
// ============================================================

#include <cstdint>
#include <string>
#include <vector>

// Minimal forward declarations so this header does not include vulkan.h.
// Definitions are only needed by the .cpp implementation.
struct VkInstance_T;
struct VkPhysicalDevice_T;
struct VkDevice_T;
struct VkQueue_T;
struct VkCommandPool_T;
struct VkCommandBuffer_T;
struct VkBuffer_T;
struct VkDeviceMemory_T;
struct VkDescriptorSetLayout_T;
struct VkDescriptorPool_T;
struct VkDescriptorSet_T;
struct VkPipelineLayout_T;
struct VkPipeline_T;
struct VkShaderModule_T;
struct VkFence_T;
struct VkQueryPool_T;
struct VkSemaphore_T;

typedef VkInstance_T* VkInstanceH;
typedef VkPhysicalDevice_T* VkPhysicalDeviceH;
typedef VkDevice_T* VkDeviceH;
typedef VkQueue_T* VkQueueH;
typedef VkCommandPool_T* VkCommandPoolH;
typedef VkCommandBuffer_T* VkCommandBufferH;
typedef VkBuffer_T* VkBufferH;
typedef VkDeviceMemory_T* VkDeviceMemoryH;
typedef VkDescriptorSetLayout_T* VkDescriptorSetLayoutH;
typedef VkDescriptorPool_T* VkDescriptorPoolH;
typedef VkDescriptorSet_T* VkDescriptorSetH;
typedef VkPipelineLayout_T* VkPipelineLayoutH;
typedef VkPipeline_T* VkPipelineH;
typedef VkShaderModule_T* VkShaderModuleH;
typedef VkFence_T* VkFenceH;
typedef VkQueryPool_T* VkQueryPoolH;

namespace MesrGLBridge {

// ============================================================
// GPU compute memory tracking (explicit ownership, spec §12/§31)
// ============================================================
struct GpuMemoryStats {
    uint64_t geometryBytes = 0;
    uint64_t bvhBytes = 0;
    uint64_t materialBytes = 0;
    uint64_t lightBytes = 0;
    uint64_t uniformBytes = 0;
    uint64_t accumulatorBytes = 0;
    uint64_t outputBytes = 0;
    uint64_t stagingBytes = 0;
    uint64_t totalBytes() const {
        return geometryBytes + bvhBytes + materialBytes + lightBytes +
               uniformBytes + accumulatorBytes + outputBytes + stagingBytes;
    }
};

// ============================================================
// GpuFrameStats - measured per-frame telemetry (never fabricated)
// ============================================================
struct GpuFrameStats {
    double uploadTimeMs = 0.0;
    double dispatchSubmitTimeMs = 0.0;   // CPU time to record + submit
    double gpuWaitTimeMs = 0.0;          // CPU time blocked on fence
    double gpuTimeMs = 0.0;              // GPU timestamp delta across both dispatches
    double pathtraceGpuTimeMs = 0.0;     // GPU timestamp delta, pathtrace dispatch
    double presentGpuTimeMs = 0.0;       // GPU timestamp delta, present dispatch
    double readbackTimeMs = 0.0;         // memcpy from mapped output
};

// ============================================================
// VulkanExecutorDevice
// ============================================================
class VulkanExecutorDevice {
public:
    VulkanExecutorDevice();
    ~VulkanExecutorDevice();

    VulkanExecutorDevice(const VulkanExecutorDevice&) = delete;
    VulkanExecutorDevice& operator=(const VulkanExecutorDevice&) = delete;

    // Loads libvulkan at runtime and initializes a compute device.
    // Returns false with a human-readable error when unavailable.
    bool initialize(std::string& error);
    void shutdown();
    bool isAvailable() const { return m_initialized; }

    // Public wrappers: staging pre-growth and queue sync for GpuExecutor::reserve.
    bool preGrowStaging(uint64_t bytes, std::string& error) { return growStaging(bytes, error); }
    void waitIdlePublic() { waitIdle(); }

    const std::string& deviceName() const { return m_deviceName; }
    std::string apiVersionString() const;
    std::string driverVersionString() const;
    uint32_t maxComputeWorkGroupInvocations() const { return m_maxComputeInvocations; }
    uint32_t maxComputeWorkGroupSize(int axis) const { return m_maxWorkGroupSize[axis]; }
    uint64_t maxStorageBufferRange() const { return m_maxStorageBufferRange; }
    uint64_t maxBufferSize() const { return m_maxBufferSize; }

    // --- Buffers -------------------------------------------------
    struct Buffer {
        VkBufferH buffer = nullptr;
        VkDeviceMemoryH memory = nullptr;
        void* mapped = nullptr;          // non-null for host-visible allocations
        uint64_t bytes = 0;
        bool deviceLocal = false;
        bool hostVisible = false;
    };

    bool createBuffer(uint64_t bytes, bool deviceLocal, Buffer& out, std::string& error);
    void destroyBuffer(Buffer& buffer);
    // Copies host data into a device buffer through the persistent staging
    // buffer (single copy per upload, no per-frame allocations).
    bool uploadBuffer(Buffer& dst, const void* data, uint64_t bytes, double& uploadTimeMs);

    // --- Kernels -------------------------------------------------
    // Descriptor set bindings (fixed layout shared by all MesrGL RT kernels):
    //   0 triangles  1 bvh nodes  2 materials  3 lights
    //   4 frame UBO  5 output     6 accumulator
    bool createComputePipeline(const uint32_t* spirv, size_t spirvWordCount, std::string& error);
    bool bindSceneBuffers(const Buffer& triangles, const Buffer& bvh, const Buffer& materials,
                          const Buffer& lights, const Buffer& frameUbo,
                          const Buffer& output, const Buffer& accumulator, std::string& error);

    // --- Frame execution ----------------------------------------
    // Records: [timestamp0] pathtrace dispatch [timestamp1] present dispatch
    // [timestamp2], submits, waits on the fence.
    struct DispatchParams {
        uint32_t groupsX = 0;
        uint32_t groupsY = 0;
        uint32_t pathtracePipeline = 0;   // pipeline index
        uint32_t presentPipeline = 1;
        uint32_t pushPathtrace[2] = {0, 0};   // frameIndex, sppThisDispatch
        float pushPresent = 1.0f;             // invSppTotal
        bool queryTimestamps = true;
    };
    bool submitFrame(const DispatchParams& params, GpuFrameStats& stats, std::string& error);

    const GpuMemoryStats& memoryStats() const { return m_memory; }

private:
    bool resolveFunctions(std::string& error);
    bool createInstanceAndDevice(std::string& error);
    bool growStaging(uint64_t bytes, std::string& error);
    void waitIdle();
    uint32_t findMemoryType(uint32_t typeBits, uint32_t requiredProps) const;

    void* m_library = nullptr;

    VkInstanceH m_instance = nullptr;
    VkPhysicalDeviceH m_physicalDevice = nullptr;
    VkDeviceH m_device = nullptr;
    VkQueueH m_queue = nullptr;
    uint32_t m_queueFamily = 0;

    VkCommandPoolH m_commandPool = nullptr;
    VkCommandBufferH m_commandBuffer = nullptr;
    VkFenceH m_fence = nullptr;
    VkQueryPoolH m_queryPool = nullptr;
    bool m_timestampsSupported = false;
    float m_timestampPeriodNs = 1.0f;

    VkDescriptorSetLayoutH m_setLayout = nullptr;
    VkDescriptorPoolH m_descriptorPool = nullptr;
    VkDescriptorSetH m_descriptorSet = nullptr;
    std::vector<VkPipelineLayoutH> m_pipelineLayouts;
    std::vector<VkPipelineH> m_pipelines;

    Buffer m_staging;
    GpuMemoryStats m_memory;

    std::string m_deviceName;
    uint32_t m_maxComputeInvocations = 0;
    uint32_t m_maxWorkGroupSize[3] = {0, 0, 0};
    uint64_t m_maxStorageBufferRange = 0;
    uint64_t m_maxBufferSize = 0;
    bool m_initialized = false;

    // Function pointers (resolved via vkGetInstanceProcAddr/vkGetDeviceProcAddr).
    struct Functions;
    Functions* m_fn = nullptr;
};

// Pipeline indices inside the device's pipeline table.
constexpr uint32_t kPathtracePipelineIndex = 0;
constexpr uint32_t kPresentPipelineIndex = 1;

} // namespace MesrGLBridge

#endif // MESRGL_BRIDGE_VULKAN_EXECUTOR_DEVICE_HPP
