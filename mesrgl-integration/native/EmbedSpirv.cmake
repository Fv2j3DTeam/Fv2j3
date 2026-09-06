# EmbedSpirv.cmake — converts a SPIR-V binary into a C++ translation unit
# exposing:
#   const uint32_t <name>_spirv_data();  (word pointer)
#   size_t <name>_spirv_words();
# Usage:
#   cmake -DKERNEL_NAME=pathtrace -DSPV_FILE=x.spv -DOUT_FILE=x.cpp -P EmbedSpirv.cmake

set(KERNEL_NAME "" CACHE STRING "kernel name")
set(SPV_FILE "" CACHE FILEPATH "input .spv")
set(OUT_FILE "" CACHE FILEPATH "output .cpp")

file(READ "${SPV_FILE}" content HEX)
string(LENGTH "${content}" hexLength)
math(EXPR wordCount "${hexLength} / 8")

set(words "")
set(i 0)
while(i LESS hexLength)
    string(SUBSTRING "${content}" ${i} 8 byteHex)
    # byteHex is in file (little-endian) byte order: b0 b1 b2 b3.
    # The little-endian u32 value is b3b2b1b0.
    string(SUBSTRING "${byteHex}" 0 2 b0)
    string(SUBSTRING "${byteHex}" 2 2 b1)
    string(SUBSTRING "${byteHex}" 4 2 b2)
    string(SUBSTRING "${byteHex}" 6 2 b3)
    string(APPEND words "0x${b3}${b2}${b1}${b0}u,")
    math(EXPR i "${i} + 8")
endwhile()

file(WRITE "${OUT_FILE}" "// Generated from ${SPV_FILE} — embedded SPIR-V payload of MesrGL's ${KERNEL_NAME} kernel.
#include \"gpu_executor.hpp\"
namespace MesrGLBridge {
alignas(16) static const uint32_t ${KERNEL_NAME}_spirv_words_data[${wordCount}] = {${words}};
const uint32_t* ${KERNEL_NAME}_spirv_data() { return ${KERNEL_NAME}_spirv_words_data; }
size_t ${KERNEL_NAME}_spirv_words() { return ${wordCount}u; }
}
")
message(STATUS "Embedded ${KERNEL_NAME}: ${wordCount} SPIR-V words -> ${OUT_FILE}")
