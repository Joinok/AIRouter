cmake_minimum_required(VERSION 3.22.1)
project("llama" C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Download llama.cpp using FetchContent
include(FetchContent)
FetchContent_Declare(
    llamacpp
    URL https://ghfast.top/https://github.com/ggml-org/llama.cpp/archive/b7340443d4cf6f89e8616de87ec6b3557055e581.zip
)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llamacpp)

# Set version for mtmd CMakeLists.txt
if (NOT DEFINED LLAMA_INSTALL_VERSION)
    set(LLAMA_INSTALL_VERSION "0.0.0")
endif()

# Build only mtmd (multimodal support) - skip all other tools
add_subdirectory(${llamacpp_SOURCE_DIR}/tools/mtmd ${CMAKE_CURRENT_BINARY_DIR}/mtmd-build)

# JNI bridge library
add_library(llama-jni SHARED
    ai_chat.cpp
)

target_include_directories(llama-jni PRIVATE
    ${llamacpp_SOURCE_DIR}/include
    ${llamacpp_SOURCE_DIR}/common
    ${llamacpp_SOURCE_DIR}/ggml/include
    ${llamacpp_SOURCE_DIR}/ggml/src
    ${llamacpp_SOURCE_DIR}/tools/mtmd
)

target_link_libraries(llama-jni PRIVATE
    llama
    llama-common
    mtmd
    android
    log
)
