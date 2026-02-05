cmake_minimum_required(VERSION 3.22.1)
project("microfrontend")

# Global optimization flags (applied to all targets including fetched dependencies)
add_compile_options(-O3 -ffunction-sections -fdata-sections)
add_link_options(-Wl,--gc-sections)

include(FetchContent)

FetchContent_Declare(
    kissfft
    GIT_REPOSITORY https://github.com/mborgerding/kissfft.git
    GIT_TAG 131.2.0
)
set(KISSFFT_STATIC ON CACHE BOOL "" FORCE)
set(KISSFFT_TEST OFF CACHE BOOL "" FORCE)
set(KISSFFT_TOOLS OFF CACHE BOOL "" FORCE)

# Fetch tflite-micro from GitHub (no tags, use specific commit)
FetchContent_Declare(
    tflite_micro
    GIT_REPOSITORY https://github.com/tensorflow/tflite-micro.git
    GIT_TAG 2747abd5c82a95fb1624106a946fc671c31f16e8 # January 22nd 2026
)

FetchContent_MakeAvailable(kissfft tflite_micro)

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
# MicroFrontend JNI shared library
# ------------------------------------------------------------------------------
add_library(${CMAKE_PROJECT_NAME} SHARED
    MicroFrontend_jni.cpp
    MicroFrontendWrapper.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    tflite_micro_frontend
    android
    log
)
