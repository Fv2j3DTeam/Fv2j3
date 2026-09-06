#include "VulkanExecutorDevice.hpp"

#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <chrono>
#include <cstring>

namespace MesrGLBridge {

namespace {

uint64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::high_resolution_clock::now().time_since_epoch())
        .count();
}

} // namespace

// ============================================================
// Function table
// ============================================================
struct VulkanExecutorDevice::Functions {
    PFN_vkGetInstanceProcAddr GetInstanceProcAddr = nullptr;
    PFN_vkCreateInstance CreateInstance = nullptr;
    PFN_vkEnumerateInstanceVersion EnumerateInstanceVersion = nullptr;
    PFN_vkEnumeratePhysicalDevices EnumeratePhysicalDevices = nullptr;
    PFN_vkGetPhysicalDeviceProperties GetPhysicalDeviceProperties = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties = nullptr;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties GetPhysicalDeviceQueueFamilyProperties = nullptr;
    PFN_vkCreateDevice CreateDevice = nullptr;
    PFN_vkDestroyInstance DestroyInstance = nullptr;
    PFN_vkGetDeviceProcAddr GetDeviceProcAddr = nullptr;

    PFN_vkDestroyDevice DestroyDevice = nullptr;
    PFN_vkGetDeviceQueue GetDeviceQueue = nullptr;
    PFN_vkCreateBuffer CreateBuffer = nullptr;
    PFN_vkDestroyBuffer DestroyBuffer = nullptr;
    PFN_vkGetBufferMemoryRequirements GetBufferMemoryRequirements = nullptr;
    PFN_vkAllocateMemory AllocateMemory = nullptr;
    PFN_vkFreeMemory FreeMemory = nullptr;
    PFN_vkBindBufferMemory BindBufferMemory = nullptr;
    PFN_vkMapMemory MapMemory = nullptr;
    PFN_vkUnmapMemory UnmapMemory = nullptr;
    PFN_vkCreateCommandPool CreateCommandPool = nullptr;
    PFN_vkDestroyCommandPool DestroyCommandPool = nullptr;
    PFN_vkAllocateCommandBuffers AllocateCommandBuffers = nullptr;
    PFN_vkFreeCommandBuffers FreeCommandBuffers = nullptr;
    PFN_vkCreateFence CreateFence = nullptr;
    PFN_vkDestroyFence DestroyFence = nullptr;
    PFN_vkResetFences ResetFences = nullptr;
    PFN_vkWaitForFences WaitForFences = nullptr;
    PFN_vkQueueSubmit QueueSubmit = nullptr;
    PFN_vkQueueWaitIdle QueueWaitIdle = nullptr;
    PFN_vkBeginCommandBuffer BeginCommandBuffer = nullptr;
    PFN_vkEndCommandBuffer EndCommandBuffer = nullptr;
    PFN_vkResetCommandBuffer ResetCommandBuffer = nullptr;
    PFN_vkCmdBindPipeline CmdBindPipeline = nullptr;
    PFN_vkCmdBindDescriptorSets CmdBindDescriptorSets = nullptr;
    PFN_vkCmdPushConstants CmdPushConstants = nullptr;
    PFN_vkCmdDispatch CmdDispatch = nullptr;
    PFN_vkCmdCopyBuffer CmdCopyBuffer = nullptr;
    PFN_vkCmdPipelineBarrier CmdPipelineBarrier = nullptr;
    PFN_vkCmdResetQueryPool CmdResetQueryPool = nullptr;
    PFN_vkCmdWriteTimestamp CmdWriteTimestamp = nullptr;
    PFN_vkCreateQueryPool CreateQueryPool = nullptr;
    PFN_vkDestroyQueryPool DestroyQueryPool = nullptr;
    PFN_vkGetQueryPoolResults GetQueryPoolResults = nullptr;
    PFN_vkCreateShaderModule CreateShaderModule = nullptr;
    PFN_vkDestroyShaderModule DestroyShaderModule = nullptr;
    PFN_vkCreateDescriptorSetLayout CreateDescriptorSetLayout = nullptr;
    PFN_vkDestroyDescriptorSetLayout DestroyDescriptorSetLayout = nullptr;
    PFN_vkCreateDescriptorPool CreateDescriptorPool = nullptr;
    PFN_vkDestroyDescriptorPool DestroyDescriptorPool = nullptr;
    PFN_vkAllocateDescriptorSets AllocateDescriptorSets = nullptr;
    PFN_vkUpdateDescriptorSets UpdateDescriptorSets = nullptr;
    PFN_vkCreatePipelineLayout CreatePipelineLayout = nullptr;
    PFN_vkDestroyPipelineLayout DestroyPipelineLayout = nullptr;
    PFN_vkCreateComputePipelines CreateComputePipelines = nullptr;
    PFN_vkDestroyPipeline DestroyPipeline = nullptr;
};

VulkanExecutorDevice::VulkanExecutorDevice() = default;

VulkanExecutorDevice::~VulkanExecutorDevice() {
    shutdown();
}

bool VulkanExecutorDevice::resolveFunctions(std::string& error) {
    const char* names[] = {"libvulkan.so.1", "libvulkan.so", nullptr};
    for (int i = 0; names[i]; ++i) {
        m_library = dlopen(names[i], RTLD_NOW | RTLD_LOCAL);
        if (m_library) break;
    }
    if (!m_library) {
        error = "Vulkan loader library could not be opened (dlopen libvulkan.so.1 failed)";
        return false;
    }

    auto vkGetInstanceProcAddr =
        reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(m_library, "vkGetInstanceProcAddr"));
    if (!vkGetInstanceProcAddr) {
        error = "vkGetInstanceProcAddr not found in Vulkan loader";
        return false;
    }

    m_fn = new Functions();
    m_fn->GetInstanceProcAddr = vkGetInstanceProcAddr;
    auto I = [&](const char* name) { return vkGetInstanceProcAddr(nullptr, name); };
    m_fn->CreateInstance = reinterpret_cast<PFN_vkCreateInstance>(I("vkCreateInstance"));
    m_fn->EnumerateInstanceVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(I("vkEnumerateInstanceVersion"));
    if (!m_fn->CreateInstance) {
        error = "vkCreateInstance unavailable in Vulkan loader";
        return false;
    }
    // Remaining instance functions are resolved after instance creation.
    return true;
}

std::string VulkanExecutorDevice::apiVersionString() const {
    VkPhysicalDeviceProperties props{};
    if (m_fn && m_physicalDevice) {
        m_fn->GetPhysicalDeviceProperties(m_physicalDevice, &props);
        char buf[64];
        std::snprintf(buf, sizeof(buf), "%u.%u.%u",
                      VK_API_VERSION_MAJOR(props.apiVersion),
                      VK_API_VERSION_MINOR(props.apiVersion),
                      VK_API_VERSION_PATCH(props.apiVersion));
        return std::string(buf);
    }
    return "N/A";
}

std::string VulkanExecutorDevice::driverVersionString() const {
    if (!m_fn || !m_physicalDevice) return "N/A";
    VkPhysicalDeviceProperties props{};
    m_fn->GetPhysicalDeviceProperties(m_physicalDevice, &props);
    char buf[64];
    // NVIDIA packs driver version differently; keep raw reporting for honesty.
    std::snprintf(buf, sizeof(buf), "%u (%u.%u.%u)", props.driverVersion,
                  VK_API_VERSION_MAJOR(props.driverVersion),
                  VK_API_VERSION_MINOR(props.driverVersion),
                  VK_API_VERSION_PATCH(props.driverVersion));
    return std::string(buf);
}

bool VulkanExecutorDevice::initialize(std::string& error) {
    if (m_initialized) return true;
    if (!m_fn && !resolveFunctions(error)) return false;

    // --- Instance (no extensions: pure offscreen compute) ---
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "MesrGL GPU Software RT";
    app.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    app.pEngineName = "MesrGL";
    app.apiVersion = VK_API_VERSION_1_1;

    // Request 1.1: the minimum needed for this backend. Newer drivers accept
    // instances requesting an older core version.
    app.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo ici{};
    ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ici.pApplicationInfo = &app;

    if (m_fn->CreateInstance(&ici, nullptr, &m_instance) != VK_SUCCESS) {
        error = "vkCreateInstance failed";
        shutdown();
        return false;
    }

    auto I = [&](const char* name) { return m_fn->GetInstanceProcAddr(m_instance, name); };
    m_fn->EnumeratePhysicalDevices = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(I("vkEnumeratePhysicalDevices"));
    m_fn->GetPhysicalDeviceProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(I("vkGetPhysicalDeviceProperties"));
    m_fn->GetPhysicalDeviceMemoryProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceMemoryProperties>(I("vkGetPhysicalDeviceMemoryProperties"));
    m_fn->GetPhysicalDeviceQueueFamilyProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(I("vkGetPhysicalDeviceQueueFamilyProperties"));
    m_fn->CreateDevice = reinterpret_cast<PFN_vkCreateDevice>(I("vkCreateDevice"));
    m_fn->DestroyInstance = reinterpret_cast<PFN_vkDestroyInstance>(I("vkDestroyInstance"));
    m_fn->GetDeviceProcAddr = reinterpret_cast<PFN_vkGetDeviceProcAddr>(I("vkGetDeviceProcAddr"));
    if (!m_fn->EnumeratePhysicalDevices || !m_fn->CreateDevice || !m_fn->GetDeviceProcAddr) {
        error = "Vulkan instance functions incomplete";
        shutdown();
        return false;
    }

    // --- Physical device selection: prefer discrete, then integrated ---
    uint32_t count = 0;
    m_fn->EnumeratePhysicalDevices(m_instance, &count, nullptr);
    if (count == 0) {
        error = "No Vulkan physical devices found";
        shutdown();
        return false;
    }
    std::vector<VkPhysicalDeviceH> devices(count);
    m_fn->EnumeratePhysicalDevices(m_instance, &count, devices.data());

    VkPhysicalDeviceH chosen = nullptr;
    for (uint32_t i = 0; i < count && !chosen; ++i) {
        VkPhysicalDeviceProperties props{};
        m_fn->GetPhysicalDeviceProperties(devices[i], &props);
        if (props.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) chosen = devices[i];
    }
    for (uint32_t i = 0; i < count && !chosen; ++i) {
        VkPhysicalDeviceProperties props{};
        m_fn->GetPhysicalDeviceProperties(devices[i], &props);
        if (props.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) chosen = devices[i];
    }
    if (!chosen) chosen = devices[0];
    m_physicalDevice = chosen;

    VkPhysicalDeviceProperties props{};
    m_fn->GetPhysicalDeviceProperties(m_physicalDevice, &props);
    m_deviceName = props.deviceName;
    m_maxComputeInvocations = props.limits.maxComputeWorkGroupInvocations;
    for (int a = 0; a < 3; ++a) m_maxWorkGroupSize[a] = props.limits.maxComputeWorkGroupSize[a];
    m_maxStorageBufferRange = props.limits.maxStorageBufferRange;
    m_maxBufferSize = 0; // not exposed by core VkPhysicalDeviceLimits; not needed by this backend
    m_timestampPeriodNs = props.limits.timestampPeriod;

    // --- Compute queue family ---
    uint32_t qfCount = 0;
    m_fn->GetPhysicalDeviceQueueFamilyProperties(m_physicalDevice, &qfCount, nullptr);
    std::vector<VkQueueFamilyProperties> qf(qfCount);
    m_fn->GetPhysicalDeviceQueueFamilyProperties(m_physicalDevice, &qfCount, qf.data());
    m_queueFamily = UINT32_MAX;
    for (uint32_t i = 0; i < qfCount; ++i) {
        if (qf[i].queueFlags & VK_QUEUE_COMPUTE_BIT) { m_queueFamily = i; break; }
    }
    if (m_queueFamily == UINT32_MAX) {
        error = "No Vulkan compute queue family available";
        shutdown();
        return false;
    }

    float prio = 1.0f;
    VkDeviceQueueCreateInfo qci{};
    qci.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qci.queueFamilyIndex = m_queueFamily;
    qci.queueCount = 1;
    qci.pQueuePriorities = &prio;

    // No device extensions: no swapchain, no ray-tracing, nothing beyond core compute.
    VkDeviceCreateInfo dci{};
    dci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;

    if (m_fn->CreateDevice(m_physicalDevice, &dci, nullptr, &m_device) != VK_SUCCESS) {
        error = "vkCreateDevice failed";
        shutdown();
        return false;
    }

    auto D = [&](const char* name) { return m_fn->GetDeviceProcAddr(m_device, name); };
    m_fn->DestroyDevice = reinterpret_cast<PFN_vkDestroyDevice>(D("vkDestroyDevice"));
    m_fn->GetDeviceQueue = reinterpret_cast<PFN_vkGetDeviceQueue>(D("vkGetDeviceQueue"));
    m_fn->CreateBuffer = reinterpret_cast<PFN_vkCreateBuffer>(D("vkCreateBuffer"));
    m_fn->DestroyBuffer = reinterpret_cast<PFN_vkDestroyBuffer>(D("vkDestroyBuffer"));
    m_fn->GetBufferMemoryRequirements = reinterpret_cast<PFN_vkGetBufferMemoryRequirements>(D("vkGetBufferMemoryRequirements"));
    m_fn->AllocateMemory = reinterpret_cast<PFN_vkAllocateMemory>(D("vkAllocateMemory"));
    m_fn->FreeMemory = reinterpret_cast<PFN_vkFreeMemory>(D("vkFreeMemory"));
    m_fn->BindBufferMemory = reinterpret_cast<PFN_vkBindBufferMemory>(D("vkBindBufferMemory"));
    m_fn->MapMemory = reinterpret_cast<PFN_vkMapMemory>(D("vkMapMemory"));
    m_fn->UnmapMemory = reinterpret_cast<PFN_vkUnmapMemory>(D("vkUnmapMemory"));
    m_fn->CreateCommandPool = reinterpret_cast<PFN_vkCreateCommandPool>(D("vkCreateCommandPool"));
    m_fn->DestroyCommandPool = reinterpret_cast<PFN_vkDestroyCommandPool>(D("vkDestroyCommandPool"));
    m_fn->AllocateCommandBuffers = reinterpret_cast<PFN_vkAllocateCommandBuffers>(D("vkAllocateCommandBuffers"));
    m_fn->FreeCommandBuffers = reinterpret_cast<PFN_vkFreeCommandBuffers>(D("vkFreeCommandBuffers"));
    m_fn->CreateFence = reinterpret_cast<PFN_vkCreateFence>(D("vkCreateFence"));
    m_fn->DestroyFence = reinterpret_cast<PFN_vkDestroyFence>(D("vkDestroyFence"));
    m_fn->ResetFences = reinterpret_cast<PFN_vkResetFences>(D("vkResetFences"));
    m_fn->WaitForFences = reinterpret_cast<PFN_vkWaitForFences>(D("vkWaitForFences"));
    m_fn->QueueSubmit = reinterpret_cast<PFN_vkQueueSubmit>(D("vkQueueSubmit"));
    m_fn->QueueWaitIdle = reinterpret_cast<PFN_vkQueueWaitIdle>(D("vkQueueWaitIdle"));
    m_fn->BeginCommandBuffer = reinterpret_cast<PFN_vkBeginCommandBuffer>(D("vkBeginCommandBuffer"));
    m_fn->EndCommandBuffer = reinterpret_cast<PFN_vkEndCommandBuffer>(D("vkEndCommandBuffer"));
    m_fn->ResetCommandBuffer = reinterpret_cast<PFN_vkResetCommandBuffer>(D("vkResetCommandBuffer"));
    m_fn->CmdBindPipeline = reinterpret_cast<PFN_vkCmdBindPipeline>(D("vkCmdBindPipeline"));
    m_fn->CmdBindDescriptorSets = reinterpret_cast<PFN_vkCmdBindDescriptorSets>(D("vkCmdBindDescriptorSets"));
    m_fn->CmdPushConstants = reinterpret_cast<PFN_vkCmdPushConstants>(D("vkCmdPushConstants"));
    m_fn->CmdDispatch = reinterpret_cast<PFN_vkCmdDispatch>(D("vkCmdDispatch"));
    m_fn->CmdCopyBuffer = reinterpret_cast<PFN_vkCmdCopyBuffer>(D("vkCmdCopyBuffer"));
    m_fn->CmdPipelineBarrier = reinterpret_cast<PFN_vkCmdPipelineBarrier>(D("vkCmdPipelineBarrier"));
    m_fn->CmdResetQueryPool = reinterpret_cast<PFN_vkCmdResetQueryPool>(D("vkCmdResetQueryPool"));
    m_fn->CmdWriteTimestamp = reinterpret_cast<PFN_vkCmdWriteTimestamp>(D("vkCmdWriteTimestamp"));
    m_fn->CreateQueryPool = reinterpret_cast<PFN_vkCreateQueryPool>(D("vkCreateQueryPool"));
    m_fn->DestroyQueryPool = reinterpret_cast<PFN_vkDestroyQueryPool>(D("vkDestroyQueryPool"));
    m_fn->GetQueryPoolResults = reinterpret_cast<PFN_vkGetQueryPoolResults>(D("vkGetQueryPoolResults"));
    m_fn->CreateShaderModule = reinterpret_cast<PFN_vkCreateShaderModule>(D("vkCreateShaderModule"));
    m_fn->DestroyShaderModule = reinterpret_cast<PFN_vkDestroyShaderModule>(D("vkDestroyShaderModule"));
    m_fn->CreateDescriptorSetLayout = reinterpret_cast<PFN_vkCreateDescriptorSetLayout>(D("vkCreateDescriptorSetLayout"));
    m_fn->DestroyDescriptorSetLayout = reinterpret_cast<PFN_vkDestroyDescriptorSetLayout>(D("vkDestroyDescriptorSetLayout"));
    m_fn->CreateDescriptorPool = reinterpret_cast<PFN_vkCreateDescriptorPool>(D("vkCreateDescriptorPool"));
    m_fn->DestroyDescriptorPool = reinterpret_cast<PFN_vkDestroyDescriptorPool>(D("vkDestroyDescriptorPool"));
    m_fn->AllocateDescriptorSets = reinterpret_cast<PFN_vkAllocateDescriptorSets>(D("vkAllocateDescriptorSets"));
    m_fn->UpdateDescriptorSets = reinterpret_cast<PFN_vkUpdateDescriptorSets>(D("vkUpdateDescriptorSets"));
    m_fn->CreatePipelineLayout = reinterpret_cast<PFN_vkCreatePipelineLayout>(D("vkCreatePipelineLayout"));
    m_fn->DestroyPipelineLayout = reinterpret_cast<PFN_vkDestroyPipelineLayout>(D("vkDestroyPipelineLayout"));
    m_fn->CreateComputePipelines = reinterpret_cast<PFN_vkCreateComputePipelines>(D("vkCreateComputePipelines"));
    m_fn->DestroyPipeline = reinterpret_cast<PFN_vkDestroyPipeline>(D("vkDestroyPipeline"));

    m_fn->GetDeviceQueue(m_device, m_queueFamily, 0, &m_queue);

    // --- Command pool/buffer/fence (resettable, reused every frame) ---
    VkCommandPoolCreateInfo cpoi{};
    cpoi.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    cpoi.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    cpoi.queueFamilyIndex = m_queueFamily;
    if (m_fn->CreateCommandPool(m_device, &cpoi, nullptr, &m_commandPool) != VK_SUCCESS) {
        error = "vkCreateCommandPool failed";
        shutdown();
        return false;
    }
    VkCommandBufferAllocateInfo cbai{};
    cbai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cbai.commandPool = m_commandPool;
    cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 1;
    if (m_fn->AllocateCommandBuffers(m_device, &cbai, &m_commandBuffer) != VK_SUCCESS) {
        error = "vkAllocateCommandBuffers failed";
        shutdown();
        return false;
    }
    VkFenceCreateInfo fci{};
    fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    if (m_fn->CreateFence(m_device, &fci, nullptr, &m_fence) != VK_SUCCESS) {
        error = "vkCreateFence failed";
        shutdown();
        return false;
    }

    // --- Timestamp query pool for honest GPU timing ---
    static const bool skipQuery = std::getenv("MESRGL_DEBUG_SKIP_QUERYPOOL") != nullptr;
    VkQueryPoolCreateInfo qpi{};
    qpi.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qpi.queryType = VK_QUERY_TYPE_TIMESTAMP;
    qpi.queryCount = 3;
    if (!skipQuery &&
        m_fn->CreateQueryPool(m_device, &qpi, nullptr, &m_queryPool) == VK_SUCCESS &&
        m_timestampPeriodNs > 0.0f) {
        m_timestampsSupported = true;
    }

    // --- Descriptor set layout (7 storage bindings, fixed) ---
    static const bool skipDescriptors = std::getenv("MESRGL_DEBUG_SKIP_DESCRIPTORS") != nullptr;
    VkDescriptorSetLayoutBinding bindings[7]{};
    for (uint32_t b = 0; b < 7; ++b) {
        bindings[b].binding = b;
        bindings[b].descriptorType = (b == 4) ? VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
                                              : VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        bindings[b].descriptorCount = 1;
        bindings[b].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    VkDescriptorSetLayoutCreateInfo dslci{};
    dslci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    dslci.bindingCount = skipDescriptors ? 0u : 7u;
    dslci.pBindings = skipDescriptors ? nullptr : bindings;
    if (m_fn->CreateDescriptorSetLayout(m_device, &dslci, nullptr, &m_setLayout) != VK_SUCCESS) {
        error = "vkCreateDescriptorSetLayout failed";
        shutdown();
        return false;
    }
    VkDescriptorPoolSize ps{};
    ps.type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    ps.descriptorCount = 1;
    VkDescriptorPoolSize psStorage{};
    psStorage.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    psStorage.descriptorCount = 6;
    VkDescriptorPoolCreateInfo dpci{};
    dpci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    dpci.maxSets = 1;
    dpci.poolSizeCount = 2;
    VkDescriptorPoolSize sizes[2] = {ps, psStorage};
    dpci.pPoolSizes = sizes;
    if (m_fn->CreateDescriptorPool(m_device, &dpci, nullptr, &m_descriptorPool) != VK_SUCCESS) {
        error = "vkCreateDescriptorPool failed";
        shutdown();
        return false;
    }
    VkDescriptorSetAllocateInfo dsai{};
    dsai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool = m_descriptorPool;
    dsai.descriptorSetCount = 1;
    dsai.pSetLayouts = &m_setLayout;
    if (m_fn->AllocateDescriptorSets(m_device, &dsai, &m_descriptorSet) != VK_SUCCESS) {
        error = "vkAllocateDescriptorSets failed";
        shutdown();
        return false;
    }

    m_initialized = true;
    return true;
}

void VulkanExecutorDevice::shutdown() {
    if (!m_library && !m_device) {
        if (m_fn) { delete m_fn; m_fn = nullptr; }
        return;
    }
    if (m_fn && m_device) {
        auto D = [&](const char* name) { return m_fn->GetDeviceProcAddr(m_device, name); };
        auto destroyPipeline = reinterpret_cast<PFN_vkDestroyPipeline>(D("vkDestroyPipeline"));
        auto destroyLayout = reinterpret_cast<PFN_vkDestroyPipelineLayout>(D("vkDestroyPipelineLayout"));
        auto destroyPool = reinterpret_cast<PFN_vkDestroyDescriptorPool>(D("vkDestroyDescriptorPool"));
        auto destroySetLayout = reinterpret_cast<PFN_vkDestroyDescriptorSetLayout>(D("vkDestroyDescriptorSetLayout"));
        auto freeMemory = reinterpret_cast<PFN_vkFreeMemory>(D("vkFreeMemory"));
        auto destroyBuffer = reinterpret_cast<PFN_vkDestroyBuffer>(D("vkDestroyBuffer"));
        auto destroyQueryPool = reinterpret_cast<PFN_vkDestroyQueryPool>(D("vkDestroyQueryPool"));
        auto destroyFence = reinterpret_cast<PFN_vkDestroyFence>(D("vkDestroyFence"));
        auto destroyCmdPool = reinterpret_cast<PFN_vkDestroyCommandPool>(D("vkDestroyCommandPool"));

        for (auto p : m_pipelines) if (p && destroyPipeline) destroyPipeline(m_device, p, nullptr);
        m_pipelines.clear();
        for (auto l : m_pipelineLayouts) if (l && destroyLayout) destroyLayout(m_device, l, nullptr);
        m_pipelineLayouts.clear();
        if (m_descriptorPool && destroyPool) destroyPool(m_device, m_descriptorPool, nullptr);
        if (m_setLayout && destroySetLayout) destroySetLayout(m_device, m_setLayout, nullptr);

        // Destroy remaining buffers owned through this API.
        // (All tracked buffers are destroyed by their owners before shutdown;
        //  staging is owned here.)
        if (m_staging.buffer && destroyBuffer) destroyBuffer(m_device, m_staging.buffer, nullptr);
        if (m_staging.memory && freeMemory) freeMemory(m_device, m_staging.memory, nullptr);
        m_staging = Buffer{};
        m_memory = GpuMemoryStats{};

        if (m_queryPool && destroyQueryPool) destroyQueryPool(m_device, m_queryPool, nullptr);
        if (m_fence && destroyFence) destroyFence(m_device, m_fence, nullptr);
        if (m_commandPool && destroyCmdPool) destroyCmdPool(m_device, m_commandPool, nullptr);

        if (m_fn->DestroyDevice) m_fn->DestroyDevice(m_device, nullptr);
    }
    m_device = nullptr;
    if (m_instance && m_fn && m_fn->DestroyInstance) m_fn->DestroyInstance(m_instance, nullptr);
    m_instance = nullptr;
    if (m_library) {
        dlclose(m_library);
        m_library = nullptr;
    }
    if (m_fn) { delete m_fn; m_fn = nullptr; }
    m_initialized = false;
}

uint32_t VulkanExecutorDevice::findMemoryType(uint32_t typeBits, uint32_t requiredProps) const {
    VkPhysicalDeviceMemoryProperties mem{};
    m_fn->GetPhysicalDeviceMemoryProperties(m_physicalDevice, &mem);
    for (uint32_t i = 0; i < mem.memoryTypeCount; ++i) {
        if ((typeBits & (1u << i)) && (mem.memoryTypes[i].propertyFlags & requiredProps) == requiredProps) {
            return i;
        }
    }
    return UINT32_MAX;
}

bool VulkanExecutorDevice::createBuffer(uint64_t bytes, bool deviceLocal, Buffer& out, std::string& error) {
    if (!m_initialized) {
        error = "GPU executor device not initialized";
        return false;
    }
    VkBufferCreateInfo bci{};
    bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bci.size = bytes;
    bci.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT |
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT |
                VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (m_fn->CreateBuffer(m_device, &bci, nullptr, &out.buffer) != VK_SUCCESS) {
        error = "vkCreateBuffer failed (" + std::to_string(bytes) + " bytes)";
        return false;
    }
    VkMemoryRequirements mr{};
    m_fn->GetBufferMemoryRequirements(m_device, out.buffer, &mr);

    uint32_t memType = UINT32_MAX;
    if (deviceLocal) {
        memType = findMemoryType(mr.memoryTypeBits,
                                 VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        if (memType == UINT32_MAX) {
            memType = findMemoryType(mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            out.deviceLocal = memType != UINT32_MAX;
        }
    }
    if (memType == UINT32_MAX) {
        memType = findMemoryType(mr.memoryTypeBits,
                                 VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        out.hostVisible = memType != UINT32_MAX;
    }
    if (memType == UINT32_MAX) {
        m_fn->DestroyBuffer(m_device, out.buffer, nullptr);
        out.buffer = nullptr;
        error = "no suitable Vulkan memory type for " + std::to_string(bytes) + " bytes";
        return false;
    }

    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = mr.size;
    mai.memoryTypeIndex = memType;
    if (m_fn->AllocateMemory(m_device, &mai, nullptr, &out.memory) != VK_SUCCESS) {
        m_fn->DestroyBuffer(m_device, out.buffer, nullptr);
        out.buffer = nullptr;
        error = "vkAllocateMemory failed (" + std::to_string(bytes) + " bytes)";
        return false;
    }
    if (m_fn->BindBufferMemory(m_device, out.buffer, out.memory, 0) != VK_SUCCESS) {
        m_fn->FreeMemory(m_device, out.memory, nullptr);
        m_fn->DestroyBuffer(m_device, out.buffer, nullptr);
        out.buffer = nullptr;
        out.memory = nullptr;
        error = "vkBindBufferMemory failed";
        return false;
    }
    out.bytes = bytes;
    if (out.hostVisible) {
        m_fn->MapMemory(m_device, out.memory, 0, VK_WHOLE_SIZE, 0, &out.mapped);
    }
    // Track memory (staging tracked separately in growStaging).
    if (out.buffer != m_staging.buffer) {
        // classification is applied by the caller via memoryStats update below
    }
    return true;
}

void VulkanExecutorDevice::destroyBuffer(Buffer& buffer) {
    if (!m_fn || !m_device) return;
    if (buffer.buffer) m_fn->DestroyBuffer(m_device, buffer.buffer, nullptr);
    if (buffer.memory) m_fn->FreeMemory(m_device, buffer.memory, nullptr);
    buffer = Buffer{};
}

void VulkanExecutorDevice::waitIdle() {
    if (m_initialized && m_queue) m_fn->QueueWaitIdle(m_queue);
}

bool VulkanExecutorDevice::growStaging(uint64_t bytes, std::string& error) {
    if (m_staging.buffer && m_staging.bytes >= bytes) return true;
    Buffer old = m_staging;
    m_staging = Buffer{};

    VkBufferCreateInfo bci{};
    bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bci.size = bytes;
    bci.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    if (m_fn->CreateBuffer(m_device, &bci, nullptr, &m_staging.buffer) != VK_SUCCESS) {
        m_staging = old;
        error = "vkCreateBuffer (staging) failed";
        return false;
    }
    VkMemoryRequirements mr{};
    m_fn->GetBufferMemoryRequirements(m_device, m_staging.buffer, &mr);
    uint32_t memType = findMemoryType(mr.memoryTypeBits,
                                      VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (memType == UINT32_MAX) {
        if (old.buffer) m_fn->DestroyBuffer(m_device, old.buffer, nullptr);
        if (old.memory) m_fn->FreeMemory(m_device, old.memory, nullptr);
        m_staging = Buffer{};
        error = "no host-visible memory type for staging";
        return false;
    }
    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = mr.size;
    mai.memoryTypeIndex = memType;
    if (m_fn->AllocateMemory(m_device, &mai, nullptr, &m_staging.memory) != VK_SUCCESS) {
        m_staging.memory = nullptr;
        destroyBuffer(m_staging);
        m_staging = old;
        error = "vkAllocateMemory (staging) failed";
        return false;
    }
    m_fn->BindBufferMemory(m_device, m_staging.buffer, m_staging.memory, 0);
    m_fn->MapMemory(m_device, m_staging.memory, 0, VK_WHOLE_SIZE, 0, &m_staging.mapped);
    m_staging.bytes = bytes;
    m_staging.hostVisible = true;
    m_memory.stagingBytes = bytes;

    if (old.buffer) m_fn->DestroyBuffer(m_device, old.buffer, nullptr);
    if (old.memory) m_fn->FreeMemory(m_device, old.memory, nullptr);
    return true;
}

bool VulkanExecutorDevice::uploadBuffer(Buffer& dst, const void* data, uint64_t bytes, double& uploadTimeMs) {
    uploadTimeMs = 0.0;
    if (!m_initialized || !dst.buffer) return false;
    uint64_t t0 = nowMs();
    std::string err;
    if (!growStaging(bytes, err)) return false;
    std::memcpy(m_staging.mapped, data, bytes);

    VkCommandBufferBeginInfo bbi{};
    bbi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    bbi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    m_fn->ResetCommandBuffer(m_commandBuffer, 0);
    m_fn->BeginCommandBuffer(m_commandBuffer, &bbi);
    VkBufferCopy region{};
    region.size = bytes;
    m_fn->CmdCopyBuffer(m_commandBuffer, m_staging.buffer, dst.buffer, 1, &region);
    m_fn->EndCommandBuffer(m_commandBuffer);
    VkSubmitInfo si{};
    si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &m_commandBuffer;
    m_fn->QueueSubmit(m_queue, 1, &si, VK_NULL_HANDLE);
    m_fn->QueueWaitIdle(m_queue);
    uploadTimeMs = static_cast<double>(nowMs() - t0) / 1e6;
    return true;
}

bool VulkanExecutorDevice::createComputePipeline(const uint32_t* spirv, size_t spirvWordCount, std::string& error) {
    VkShaderModuleCreateInfo smci{};
    smci.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    smci.codeSize = spirvWordCount * sizeof(uint32_t);
    smci.pCode = spirv;
    VkShaderModuleH module = nullptr;
    if (m_fn->CreateShaderModule(m_device, &smci, nullptr, &module) != VK_SUCCESS) {
        error = "vkCreateShaderModule failed";
        return false;
    }

    VkPushConstantRange pcRange{};
    pcRange.offset = 0;
    // Pathtrace uses {uint frameIndex, uint spp}; present uses {float invSpp}.
    // A single 16-byte range covers both; unused bytes are not read by the shaders.
    pcRange.size = 16;
    pcRange.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    VkPipelineLayoutCreateInfo plci{};
    plci.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    plci.setLayoutCount = 1;
    plci.pSetLayouts = &m_setLayout;
    plci.pushConstantRangeCount = 1;
    plci.pPushConstantRanges = &pcRange;
    VkPipelineLayoutH layout = nullptr;
    if (m_fn->CreatePipelineLayout(m_device, &plci, nullptr, &layout) != VK_SUCCESS) {
        m_fn->DestroyShaderModule(m_device, module, nullptr);
        error = "vkCreatePipelineLayout failed";
        return false;
    }

    VkPipelineShaderStageCreateInfo stage{};
    stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    stage.module = module;
    stage.pName = "main";
    VkComputePipelineCreateInfo cpci{};
    cpci.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    cpci.stage = stage;
    cpci.layout = layout;
    VkPipelineH pipeline = nullptr;
    VkResult r = m_fn->CreateComputePipelines(m_device, VK_NULL_HANDLE, 1, &cpci, nullptr, &pipeline);
    m_fn->DestroyShaderModule(m_device, module, nullptr);
    if (r != VK_SUCCESS) {
        m_fn->DestroyPipelineLayout(m_device, layout, nullptr);
        error = "vkCreateComputePipelines failed";
        return false;
    }
    m_pipelineLayouts.push_back(layout);
    m_pipelines.push_back(pipeline);
    return true;
}

bool VulkanExecutorDevice::bindSceneBuffers(const Buffer& triangles, const Buffer& bvh, const Buffer& materials,
                                    const Buffer& lights, const Buffer& frameUbo,
                                    const Buffer& output, const Buffer& accumulator, std::string& error) {
    if (!m_initialized) {
        error = "GPU executor device not initialized";
        return false;
    }
    const Buffer* bufs[7] = {&triangles, &bvh, &materials, &lights, &frameUbo, &output, &accumulator};
    for (const Buffer* b : bufs) {
        if (!b->buffer) {
            error = "bindSceneBuffers: null buffer (output/accumulator not created yet)";
            return false;
        }
    }
    VkDescriptorBufferInfo infos[7]{};
    VkWriteDescriptorSet writes[7]{};
    for (uint32_t b = 0; b < 7; ++b) {
        infos[b].buffer = bufs[b]->buffer;
        infos[b].offset = 0;
        infos[b].range = VK_WHOLE_SIZE;
        writes[b].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[b].dstSet = m_descriptorSet;
        writes[b].dstBinding = b;
        writes[b].descriptorCount = 1;
        writes[b].descriptorType = (b == 4) ? VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
                                            : VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[b].pBufferInfo = &infos[b];
    }
    m_fn->UpdateDescriptorSets(m_device, 7, writes, 0, nullptr);
    return true;
}

bool VulkanExecutorDevice::submitFrame(const DispatchParams& params, GpuFrameStats& stats, std::string& error) {
    if (!m_initialized) {
        error = "GPU executor device not initialized";
        return false;
    }
    uint64_t t0 = nowMs();

    VkCommandBufferBeginInfo bbi{};
    bbi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    bbi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    if (m_fn->ResetCommandBuffer(m_commandBuffer, 0) != VK_SUCCESS ||
        m_fn->BeginCommandBuffer(m_commandBuffer, &bbi) != VK_SUCCESS) {
        error = "vkBeginCommandBuffer failed";
        return false;
    }

    if (m_timestampsSupported) {
        m_fn->CmdResetQueryPool(m_commandBuffer, m_queryPool, 0, 3);
        m_fn->CmdWriteTimestamp(m_commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, m_queryPool, 0);
    }

    // Pathtrace dispatch
    m_fn->CmdBindPipeline(m_commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                          m_pipelines[params.pathtracePipeline]);
    m_fn->CmdBindDescriptorSets(m_commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                                m_pipelineLayouts[params.pathtracePipeline], 0, 1, &m_descriptorSet, 0, nullptr);
    m_fn->CmdPushConstants(m_commandBuffer, m_pipelineLayouts[params.pathtracePipeline],
                           VK_SHADER_STAGE_COMPUTE_BIT, 0, 8, params.pushPathtrace);
    m_fn->CmdDispatch(m_commandBuffer, params.groupsX, params.groupsY, 1);

    if (m_timestampsSupported) {
        m_fn->CmdWriteTimestamp(m_commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, m_queryPool, 1);
    }

    // Storage barrier: pathtrace writes accumulator, present reads it.
    VkMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER;
    barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    m_fn->CmdPipelineBarrier(m_commandBuffer,
                             VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                             0, 1, &barrier, 0, nullptr, 0, nullptr);

    // Present dispatch
    m_fn->CmdBindPipeline(m_commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                          m_pipelines[params.presentPipeline]);
    m_fn->CmdBindDescriptorSets(m_commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                                m_pipelineLayouts[params.presentPipeline], 0, 1, &m_descriptorSet, 0, nullptr);
    m_fn->CmdPushConstants(m_commandBuffer, m_pipelineLayouts[params.presentPipeline],
                           VK_SHADER_STAGE_COMPUTE_BIT, 0, 4, &params.pushPresent);
    m_fn->CmdDispatch(m_commandBuffer, params.groupsX, params.groupsY, 1);

    if (m_timestampsSupported) {
        m_fn->CmdWriteTimestamp(m_commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, m_queryPool, 2);
    }

    if (m_fn->EndCommandBuffer(m_commandBuffer) != VK_SUCCESS) {
        error = "vkEndCommandBuffer failed";
        return false;
    }

    uint64_t t1 = nowMs();
    VkSubmitInfo si{};
    si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &m_commandBuffer;
    if (m_fn->QueueSubmit(m_queue, 1, &si, m_fence) != VK_SUCCESS) {
        error = "vkQueueSubmit failed";
        return false;
    }
    if (m_fn->WaitForFences(m_device, 1, &m_fence, VK_TRUE, 60'000'000'000ull) != VK_SUCCESS) {
        error = "GPU fence wait timed out after 60s";
        return false;
    }
    m_fn->ResetFences(m_device, 1, &m_fence);
    uint64_t t2 = nowMs();

    stats.dispatchSubmitTimeMs = static_cast<double>(t1 - t0) / 1e6;
    stats.gpuWaitTimeMs = static_cast<double>(t2 - t1) / 1e6;
    stats.gpuTimeMs = 0.0;
    stats.pathtraceGpuTimeMs = 0.0;
    stats.presentGpuTimeMs = 0.0;
    if (m_timestampsSupported) {
        uint64_t ts[3] = {0, 0, 0};
        if (m_fn->GetQueryPoolResults(m_device, m_queryPool, 0, 3, sizeof(ts), ts, sizeof(uint64_t),
                                      VK_QUERY_RESULT_64_BIT) == VK_SUCCESS) {
            auto gpuMs = [&](uint64_t a, uint64_t b) {
                return static_cast<double>(b - a) * static_cast<double>(m_timestampPeriodNs) / 1e6;
            };
            stats.pathtraceGpuTimeMs = gpuMs(ts[0], ts[1]);
            stats.presentGpuTimeMs = gpuMs(ts[1], ts[2]);
            stats.gpuTimeMs = gpuMs(ts[0], ts[2]);
        }
        // If timestamp readback failed, stats stay zero and the caller reports
        // CPU-side timings only. Never fabricate GPU timings.
    }
    return true;
}

} // namespace MesrGLBridge
