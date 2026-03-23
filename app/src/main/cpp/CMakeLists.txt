cmake_minimum_required(VERSION 3.22.1)

project("llama-jni" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# --------------------------------------------------------------------------
# ABI-specific settings
# --------------------------------------------------------------------------
if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_CPU_KLEIDIAI ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_CPU_KLEIDIAI OFF)
    endif()
endif()

# --------------------------------------------------------------------------
# llama.cpp as subdirectory
# --------------------------------------------------------------------------
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)

# Force-enable common library (OFF by default when not standalone)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} build-llama)

# --------------------------------------------------------------------------
# Vulkan backend (Phase 7 v1.6.0)
# --------------------------------------------------------------------------
find_library(vulkan-lib vulkan)

# --------------------------------------------------------------------------
# Our JNI bridge library
# --------------------------------------------------------------------------
add_library(${CMAKE_PROJECT_NAME} SHARED
        llama-jni.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        common
        android
        log
        ${vulkan-lib})
