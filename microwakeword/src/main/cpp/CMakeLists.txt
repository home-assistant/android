cmake_minimum_required(VERSION 3.22.1)
project("microwakeword")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Global optimization flags (applied to all targets including fetched dependencies)
add_compile_options(-O3 -ffunction-sections -fdata-sections)
add_link_options(-Wl,--gc-sections)

include(FetchContent)

FetchContent_Declare(
    kissfft
    URL https://github.com/mborgerding/kissfft/archive/7bce4153c6bc8aba2db0e889e576f9d00505cbe1.tar.gz # 131.2.0
    URL_HASH SHA256=7ad1124648a46977b16ddde03bf243bcd52fc452516c57016584ab4b4f2baadc
)
set(KISSFFT_STATIC ON CACHE BOOL "" FORCE)
set(KISSFFT_TEST OFF CACHE BOOL "" FORCE)
set(KISSFFT_TOOLS OFF CACHE BOOL "" FORCE)

# Fetch tflite-micro from GitHub (archive download, faster than git clone).
# When bumping tflite-micro, the dependencies below (flatbuffers, gemmlowp, ruy) must also
# be updated to match the versions pinned in tflite-micro's own third_party/ configuration.
# To update dependencies, check the tflite-micro repository at the pinned commit
# for the exact versions of flatbuffers, gemmlowp, and ruy.
FetchContent_Declare(
    tflite_micro
    URL https://github.com/tensorflow/tflite-micro/archive/2747abd5c82a95fb1624106a946fc671c31f16e8.tar.gz # January 22nd 2026
    URL_HASH SHA256=6ac0c8ef35f267cf3baf05dc094d4fd158864d4cc830d490b752994e58add8c8
)

# Header-only: flatbuffers for model parsing (version matching tflite-micro)
FetchContent_Declare(
    flatbuffers
    URL https://github.com/google/flatbuffers/archive/0100f6a5779831fa7a651e4b67ef389a8752bd9b.tar.gz # v23.5.26
    URL_HASH SHA256=85db3520acc4010b21984e2fb5ead3ec0d2c48df8009b614cb73562a82846554
)
set(FLATBUFFERS_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(FLATBUFFERS_INSTALL OFF CACHE BOOL "" FORCE)
set(FLATBUFFERS_BUILD_FLATC OFF CACHE BOOL "" FORCE)
set(FLATBUFFERS_BUILD_FLATHASH OFF CACHE BOOL "" FORCE)

# Header-only: gemmlowp for fixed-point math (commit matching tflite-micro)
FetchContent_Declare(
    gemmlowp
    URL https://github.com/google/gemmlowp/archive/fda83bdc38b118cc6b56753bd540caa49e570745.tar.gz
    URL_HASH SHA256=0f990732a0d541be514dfc5c1c45969626e9f349faa851baec01b9a9dcb4ae4b
)

# Header-only: ruy profiler instrumentation (commit matching tflite-micro)
FetchContent_Declare(
    ruy
    URL https://github.com/google/ruy/archive/54774a7a2cf85963777289193629d4bd42de4a59.tar.gz
    URL_HASH SHA256=91993e7eb2aa56e62e9f4abc1158b2b46333574aa81f1a7431b06df80a42b7fa
)

FetchContent_MakeAvailable(kissfft tflite_micro flatbuffers gemmlowp ruy)

# TFLite micro frontend expects kiss_fftr in tools/ subdirectory, but kissfft has it at root.
# Create tools/ directory with copies of the required files.
file(MAKE_DIRECTORY ${kissfft_SOURCE_DIR}/tools)
file(COPY_FILE
    ${kissfft_SOURCE_DIR}/kiss_fftr.c
    ${kissfft_SOURCE_DIR}/tools/kiss_fftr.c
    ONLY_IF_DIFFERENT
)
file(COPY_FILE
    ${kissfft_SOURCE_DIR}/kiss_fftr.h
    ${kissfft_SOURCE_DIR}/tools/kiss_fftr.h
    ONLY_IF_DIFFERENT
)

# Path to tflite micro frontend library
set(TFLITE_FRONTEND "${tflite_micro_SOURCE_DIR}/tensorflow/lite/experimental/microfrontend/lib")

# Paths for TFLite Micro interpreter sources
set(TFLM "${tflite_micro_SOURCE_DIR}/tensorflow/lite/micro")
set(TFL "${tflite_micro_SOURCE_DIR}/tensorflow/lite")

# ------------------------------------------------------------------------------
# TFLite Micro Frontend static library (third-party code)
# ------------------------------------------------------------------------------
add_library(tflite_micro_frontend STATIC
    # Core FFT
    ${TFLITE_FRONTEND}/fft.cc
    ${TFLITE_FRONTEND}/fft_util.cc
    ${TFLITE_FRONTEND}/kiss_fft_int16.cc

    # Filterbank
    ${TFLITE_FRONTEND}/filterbank.c
    ${TFLITE_FRONTEND}/filterbank_util.c

    # Frontend
    ${TFLITE_FRONTEND}/frontend.c
    ${TFLITE_FRONTEND}/frontend_util.c

    # Log scale
    ${TFLITE_FRONTEND}/log_lut.c
    ${TFLITE_FRONTEND}/log_scale.c
    ${TFLITE_FRONTEND}/log_scale_util.c

    # Noise reduction
    ${TFLITE_FRONTEND}/noise_reduction.c
    ${TFLITE_FRONTEND}/noise_reduction_util.c

    # PCAN gain control
    ${TFLITE_FRONTEND}/pcan_gain_control.c
    ${TFLITE_FRONTEND}/pcan_gain_control_util.c

    # Window
    ${TFLITE_FRONTEND}/window.c
    ${TFLITE_FRONTEND}/window_util.c
)

target_include_directories(tflite_micro_frontend PUBLIC
    # For tensorflow/lite/experimental/microfrontend/lib/*.h includes
    ${tflite_micro_SOURCE_DIR}
    # For kiss_fft.h and tools/kiss_fftr.h includes
    ${kissfft_SOURCE_DIR}
)

# Fixed-point 16-bit arithmetic to match model training
target_compile_definitions(tflite_micro_frontend PUBLIC
    FIXED_POINT=16
)

# Suppress warnings from third-party code
target_compile_options(tflite_micro_frontend PRIVATE
    -Wno-unused-parameter
    -Wno-sign-compare
    -ffast-math  # Fast floating-point (safe: we use FIXED_POINT=16)
)

# ------------------------------------------------------------------------------
# TFLite Micro Interpreter static library
# ------------------------------------------------------------------------------
add_library(tflite_micro_interpreter STATIC
    # Core
    ${TFLM}/micro_interpreter.cc
    ${TFLM}/micro_allocator.cc
    ${TFLM}/micro_context.cc
    ${TFLM}/micro_op_resolver.cc
    ${TFLM}/micro_utils.cc
    ${TFLM}/micro_log.cc
    ${TFLM}/flatbuffer_utils.cc
    ${TFLM}/memory_helpers.cc
    ${TFLM}/micro_allocation_info.cc
    ${TFLM}/micro_interpreter_context.cc
    ${TFLM}/micro_interpreter_graph.cc
    ${TFLM}/micro_resource_variable.cc
    ${TFLM}/micro_profiler.cc
    ${TFLM}/micro_time.cc
    ${TFLM}/system_setup.cc
    ${TFLM}/fake_micro_context.cc

    # Debug log (platform-specific)
    ${TFLM}/debug_log.cc

    # Arena allocators
    ${TFLM}/arena_allocator/single_arena_buffer_allocator.cc
    ${TFLM}/arena_allocator/non_persistent_arena_buffer_allocator.cc
    ${TFLM}/arena_allocator/persistent_arena_buffer_allocator.cc

    # Memory planner
    ${TFLM}/memory_planner/greedy_memory_planner.cc
    ${TFLM}/memory_planner/linear_memory_planner.cc
    ${TFLM}/memory_planner/non_persistent_buffer_planner_shim.cc

    # TFLite bridge
    ${TFLM}/tflite_bridge/micro_error_reporter.cc
    ${TFLM}/tflite_bridge/flatbuffer_conversions_bridge.cc

    # Error reporter base class (in compiler/mlir tree)
    ${tflite_micro_SOURCE_DIR}/tensorflow/compiler/mlir/lite/core/api/error_reporter.cc

    # TFLite core C
    ${TFL}/core/c/common.cc

    # TFLite core API
    ${TFL}/core/api/flatbuffer_conversions.cc
    ${TFL}/core/api/tensor_utils.cc

    # Kernel utilities
    ${TFL}/kernels/kernel_util.cc
    ${TFL}/kernels/internal/common.cc
    ${TFL}/kernels/internal/quantization_util.cc
    ${TFL}/kernels/internal/portable_tensor_utils.cc
    ${TFL}/kernels/internal/tensor_ctypes.cc
    ${TFL}/kernels/internal/runtime_shape.cc
    ${TFL}/kernels/internal/reference/portable_tensor_utils.cc

    # Schema (moved to compiler/mlir in recent tflite-micro)
    ${tflite_micro_SOURCE_DIR}/tensorflow/compiler/mlir/lite/schema/schema_utils.cc

    # Micro kernels (operators needed for wake word models)
    ${TFLM}/kernels/conv.cc
    ${TFLM}/kernels/conv_common.cc
    ${TFLM}/kernels/depthwise_conv.cc
    ${TFLM}/kernels/depthwise_conv_common.cc
    ${TFLM}/kernels/fully_connected.cc
    ${TFLM}/kernels/fully_connected_common.cc
    ${TFLM}/kernels/pooling.cc
    ${TFLM}/kernels/pooling_common.cc
    ${TFLM}/kernels/reshape.cc
    ${TFLM}/kernels/reshape_common.cc
    ${TFLM}/kernels/softmax.cc
    ${TFLM}/kernels/softmax_common.cc
    ${TFLM}/kernels/logistic.cc
    ${TFLM}/kernels/logistic_common.cc
    ${TFLM}/kernels/add.cc
    ${TFLM}/kernels/add_common.cc
    ${TFLM}/kernels/mul.cc
    ${TFLM}/kernels/mul_common.cc
    ${TFLM}/kernels/quantize.cc
    ${TFLM}/kernels/quantize_common.cc
    ${TFLM}/kernels/dequantize.cc
    ${TFLM}/kernels/dequantize_common.cc
    ${TFLM}/kernels/activations.cc
    ${TFLM}/kernels/activations_common.cc
    ${TFLM}/kernels/pad.cc
    ${TFLM}/kernels/pad_common.cc
    ${TFLM}/kernels/concatenation.cc
    ${TFLM}/kernels/strided_slice.cc
    ${TFLM}/kernels/strided_slice_common.cc
    ${TFLM}/kernels/pack.cc
    ${TFLM}/kernels/unpack.cc
    ${TFLM}/kernels/kernel_util.cc
    ${TFLM}/kernels/micro_tensor_utils.cc

    # Streaming model variable ops (needed for stateful wake word models)
    ${TFLM}/kernels/call_once.cc
    ${TFLM}/kernels/var_handle.cc
    ${TFLM}/kernels/read_variable.cc
    ${TFLM}/kernels/assign_variable.cc
    ${TFLM}/kernels/split_v.cc
    ${TFLM}/kernels/reduce.cc
    ${TFLM}/kernels/reduce_common.cc
)

target_include_directories(tflite_micro_interpreter PUBLIC
    ${tflite_micro_SOURCE_DIR}
    ${flatbuffers_SOURCE_DIR}/include
    ${gemmlowp_SOURCE_DIR}
    ${ruy_SOURCE_DIR}
)

target_compile_definitions(tflite_micro_interpreter PUBLIC
    TF_LITE_STATIC_MEMORY
    TF_LITE_DISABLE_X86_NEON
)

# Suppress warnings from third-party code
target_compile_options(tflite_micro_interpreter PRIVATE
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-missing-field-initializers
    -Wno-unused-variable
    -Wno-unused-function
    -ffast-math
)

# ------------------------------------------------------------------------------
# MicroWakeWord JNI shared library
# ------------------------------------------------------------------------------
add_library(${CMAKE_PROJECT_NAME} SHARED
    MicroWakeWord_jni.cpp
    MicroWakeWordEngine.cpp
    MicroFrontendWrapper.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    tflite_micro_frontend
    tflite_micro_interpreter
    android
    log
)

# HWAddressSanitizer for debug builds on ARM64 only.
# Requires c++_shared STL (NDK default since r25, c++_static is incompatible).
# See https://developer.android.com/ndk/guides/hwasan
if(CMAKE_BUILD_TYPE STREQUAL "Debug" AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_options(${CMAKE_PROJECT_NAME} PUBLIC -fsanitize=hwaddress -fno-omit-frame-pointer)
    target_link_options(${CMAKE_PROJECT_NAME} PUBLIC -fsanitize=hwaddress)
endif()
