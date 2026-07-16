cmake_minimum_required(VERSION 3.22.1)

project("watchoutrf_native" LANGUAGES C CXX)

include(FetchContent)

# =============================================================================
# KissFFT — Fast Fourier Transform library
# =============================================================================
FetchContent_Declare(
    kissfft
    GIT_REPOSITORY https://github.com/mborgerding/kissfft.git
    GIT_TAG        131.1.0
    GIT_SHALLOW    TRUE
)
set(KISSFFT_STATIC ON CACHE BOOL "" FORCE)
set(KISSFFT_TEST   OFF CACHE BOOL "" FORCE)
set(KISSFFT_TOOLS  OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(kissfft)

# =============================================================================
# WatchoutRF Native Library
# =============================================================================

# Compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")

# ARM64 NEON SIMD
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -march=armv8-a+simd")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+simd")
endif()

add_library(
    watchoutrf_native
    SHARED
    watchoutrf_native.c
)

find_library(log-lib log)

target_link_libraries(
    watchoutrf_native
    PRIVATE
    kissfft::kissfft-float
    ${log-lib}
)

target_include_directories(
    watchoutrf_native
    PRIVATE
    ${kissfft_SOURCE_DIR}
)
