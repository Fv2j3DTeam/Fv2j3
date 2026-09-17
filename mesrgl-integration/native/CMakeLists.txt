cmake_minimum_required(VERSION 3.16)
project(MesrGLBridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ------------------------------------------------------------
# Inputs
#   MESRGL_ROOT      path to the MesrGL checkout (kernels + headers)
#   MESRGL_LIBRARY   path to libMesrGL.a
#   JAVA_HOME        JDK home for jni.h
# ------------------------------------------------------------
set(MESRGL_ROOT "" CACHE PATH "MesrGL checkout directory")
set(MESRGL_LIBRARY "" CACHE FILEPATH "libMesrGL.a")
set(JAVA_HOME "" CACHE PATH "JDK home")

if(NOT MESRGL_ROOT OR NOT EXISTS "${MESRGL_ROOT}")
    message(FATAL_ERROR "MESRGL_ROOT must point to the MesrGL checkout")
endif()
if(NOT MESRGL_LIBRARY OR NOT EXISTS "${MESRGL_LIBRARY}")
    message(FATAL_ERROR "MESRGL_LIBRARY must point to libMesrGL.a")
endif()

find_package(JNI REQUIRED)

# ------------------------------------------------------------
# Kernel SPIR-V compilation (build-time only; the runtime opens
# libvulkan dynamically and MesrGL itself contains no GPU code).
# If glslangValidator is missing, the bridge builds CPU-only and
# reports GPU Software RT as unavailable (never faked).
# ------------------------------------------------------------
find_program(GLSLANG_VALIDATOR glslangValidator)

set(GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${GENERATED_DIR}")

set(KERNEL_BODIES "")

if(GLSLANG_VALIDATOR)
    foreach(KERNEL pathtrace present)
        set(SRC "${MESRGL_ROOT}/MesrGL/Kernels/${KERNEL}.comp")
        set(SPV "${GENERATED_DIR}/${KERNEL}.spv")
        set(GEN "${GENERATED_DIR}/${KERNEL}_payload.cpp")
        add_custom_command(
            OUTPUT "${SPV}"
            COMMAND "${GLSLANG_VALIDATOR}" -V "${SRC}" -o "${SPV}"
            DEPENDS "${SRC}"
            COMMENT "Compiling MesrGL kernel ${KERNEL}.comp to SPIR-V"
        )
        add_custom_command(
            OUTPUT "${GEN}"
            COMMAND "${CMAKE_COMMAND}"
                    -DKERNEL_NAME=${KERNEL}
                    -DSPV_FILE="${SPV}"
                    -DOUT_FILE="${GEN}"
                    -P "${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpirv.cmake"
            DEPENDS "${SPV}"
            COMMENT "Embedding ${KERNEL} SPIR-V payload"
        )
        list(APPEND KERNEL_BODIES "${GEN}")
    endforeach()
    set(HAS_KERNELS TRUE)
    # Single generation point: without this, the parallel Makefile recursion
    # races several targets into writing the same payload/.spv concurrently.
    add_custom_target(mesrgl_kernel_payloads ALL DEPENDS ${KERNEL_BODIES})
else()
    message(WARNING "glslangValidator not found: building bridge WITHOUT GPU kernels; "
                    "GPU Software RT will report unavailable and the CPU fallback will be used.")
    set(HAS_KERNELS FALSE)
    set(STUB "${GENERATED_DIR}/kernel_payloads_empty.cpp")
    file(WRITE "${STUB}" "// Bridge built without glslangValidator: no GPU kernel payloads.
#include \"gpu_executor.hpp\"
namespace MesrGLBridge {
const uint32_t* pathtrace_spirv_data() { return nullptr; }
size_t pathtrace_spirv_words() { return 0; }
const uint32_t* present_spirv_data() { return nullptr; }
size_t present_spirv_words() { return 0; }
// gpuKernelsAvailable() defined in gpu_executor.cpp
}
")
    list(APPEND KERNEL_BODIES "${STUB}")
    # Create an empty target to satisfy dependencies
    add_custom_target(mesrgl_kernel_payloads ALL)
endif()

# ------------------------------------------------------------
# Bridge library
# ------------------------------------------------------------
add_library(MesrGLBridge SHARED
    mesrgl_bridge.cpp
    gpu_executor.cpp
    ${KERNEL_BODIES}
)

# Conditionally add VulkanExecutorDevice if Vulkan headers available
if(EXISTS "${MESRGL_ROOT}/third_party/Vulkan-Headers/include")
    target_sources(MesrGLBridge PRIVATE vulkan/VulkanExecutorDevice.cpp)
    target_include_directories(MesrGLBridge PRIVATE
        "${MESRGL_ROOT}/third_party/Vulkan-Headers/include"
    )
endif()

add_dependencies(MesrGLBridge mesrgl_kernel_payloads)

target_include_directories(MesrGLBridge PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${MESRGL_ROOT}/include"
    "${GENERATED_DIR}"
    "${JNI_INCLUDE_DIRS}"
)

target_link_libraries(MesrGLBridge PRIVATE
    "${MESRGL_LIBRARY}"
    ${CMAKE_DL_LIBS}
    pthread
)

# Keep MesrGL's isolation guarantee visible in the binary: no link-time
# Vulkan dependency is possible because we never link a Vulkan library.

# ------------------------------------------------------------
# Bridge test suite (native, runs the real GPU when present)
# ------------------------------------------------------------
# Helper function to add diagnostic test with conditional Vulkan includes
function(add_diagnostic_test target_name sources)
    add_executable(${target_name} ${sources})
    add_dependencies(${target_name} mesrgl_kernel_payloads)
    target_include_directories(${target_name} PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${MESRGL_ROOT}/include"
        "${GENERATED_DIR}"
    )
    if(EXISTS "${MESRGL_ROOT}/third_party/Vulkan-Headers/include")
        target_include_directories(${target_name} PRIVATE
            "${MESRGL_ROOT}/third_party/Vulkan-Headers/include"
        )
    endif()
    target_link_libraries(${target_name} PRIVATE
        "${MESRGL_LIBRARY}"
        ${CMAKE_DL_LIBS}
        pthread
    )
endfunction()

# Helper function for Vulkan-dependent tests (only built when Vulkan headers available)
function(add_vulkan_test target_name sources)
    if(EXISTS "${MESRGL_ROOT}/third_party/Vulkan-Headers/include")
        add_executable(${target_name} ${sources})
        add_dependencies(${target_name} mesrgl_kernel_payloads)
        target_include_directories(${target_name} PRIVATE
            "${CMAKE_CURRENT_SOURCE_DIR}"
            "${MESRGL_ROOT}"
            "${MESRGL_ROOT}/third_party/Vulkan-Headers/include"
            "${GENERATED_DIR}"
        )
        target_link_libraries(${target_name} PRIVATE
            "${MESRGL_LIBRARY}"
            ${CMAKE_DL_LIBS}
            pthread
        )
    else()
        message(WARNING "Skipping ${target_name} - Vulkan headers not found")
    endif()
endfunction()

# Add diagnostic tests that use Vulkan (only when Vulkan headers available)
add_vulkan_test(diag_crossval "diag_crossval.cpp;gpu_executor.cpp;vulkan/VulkanExecutorDevice.cpp;${KERNEL_BODIES}")
add_vulkan_test(diag_minimal "diag_minimal.cpp;gpu_executor.cpp;vulkan/VulkanExecutorDevice.cpp;${KERNEL_BODIES}")
add_vulkan_test(diag_bisect "diag_bisect.cpp;gpu_executor.cpp;vulkan/VulkanExecutorDevice.cpp;${KERNEL_BODIES}")
add_vulkan_test(diag_scanline "diag_scanline.cpp;gpu_executor.cpp;vulkan/VulkanExecutorDevice.cpp;${KERNEL_BODIES}")

# GPU scaling diagnosis: pathtrace time vs triangle count (fence-timeout probe).
add_vulkan_test(diag_scaled "diag_scaled.cpp;gpu_executor.cpp;vulkan/VulkanExecutorDevice.cpp;${KERNEL_BODIES}")

add_vulkan_test(diag_pcg "diag_pcg.cpp;vulkan/VulkanExecutorDevice.cpp")
if(EXISTS "${MESRGL_ROOT}/third_party/Vulkan-Headers/include")
    add_dependencies(diag_pcg mesrgl_kernel_payloads)
    target_include_directories(diag_pcg PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${MESRGL_ROOT}"
        "${MESRGL_ROOT}/third_party/Vulkan-Headers/include"
        "${GENERATED_DIR}"
    )
    target_link_libraries(diag_pcg PRIVATE
        "${MESRGL_LIBRARY}"
        ${CMAKE_DL_LIBS}
        pthread
    )

    add_custom_command(
        OUTPUT "${GENERATED_DIR}/pcg_ref.spv"
        COMMAND "${GLSLANG_VALIDATOR}" -V "${CMAKE_CURRENT_SOURCE_DIR}/pcg_ref.comp" -o "${GENERATED_DIR}/pcg_ref.spv"
        DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/pcg_ref.comp"
    )
    add_custom_target(pcg_spv ALL DEPENDS "${GENERATED_DIR}/pcg_ref.spv")
    add_dependencies(diag_pcg pcg_spv)
endif()

add_vulkan_test(bridge_tests "bridge_tests.cpp;gpu_executor.cpp;vulkan/VulkanExecutorDevice.cpp;${KERNEL_BODIES}")
