cmake_minimum_required(VERSION 3.22.1)
project("llama" C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Download llama.cpp using FetchContent (instead of local submodule)
include(FetchContent)
FetchContent_Declare(
    llamacpp
    URL https://ghfast.top/https://github.com/ggml-org/llama.cpp/archive/refs/heads/master.zip
)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llamacpp)

# Build mtmd (multimodal support) from llama.cpp source
set(MTMD_SOURCES
    ${llamacpp_SOURCE_DIR}/tools/mtmd/mtmd.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/mtmd-audio.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/mtmd-image.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/mtmd-helper.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/clip.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/cogvlm.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/conformer.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/dotsocr.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/gemma4a.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/gemma4v.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/glm4v.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/granite-speech.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/hunyuanocr.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/internvl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/kimivl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/kimik25.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/nemotron-v2-vl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/llama4.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/llava.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/minicpmv.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/paddleocr.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/pixtral.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/qwen2vl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/qwen3vl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/mimovl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/qwen3a.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/step3vl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/siglip.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/whisper-enc.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/deepseekocr.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/mobilenetv5.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/youtuvl.cpp
    ${llamacpp_SOURCE_DIR}/tools/mtmd/models/yasa2.cpp
)
add_library(mtmd STATIC ${MTMD_SOURCES})
target_include_directories(mtmd PUBLIC
    ${llamacpp_SOURCE_DIR}/tools/mtmd
    ${llamacpp_SOURCE_DIR}/vendor
)
target_link_libraries(mtmd PUBLIC ggml llama)
target_compile_features(mtmd PRIVATE cxx_std_17)

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
