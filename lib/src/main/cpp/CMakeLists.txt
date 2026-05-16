cmake_minimum_required(VERSION 3.22.1)
project("llama" C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# llama.cpp source directory
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Build llama.cpp core + common
add_subdirectory(${LLAMA_CPP_DIR})

# JNI bridge library
add_library(llama-jni SHARED
    ai_chat.cpp
)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
)

target_link_libraries(llama-jni PRIVATE
    llama
    llama-common
    android
    log
)
