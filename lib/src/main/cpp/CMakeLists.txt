cmake_minimum_required(VERSION 3.18.1)
project(llama)

# 由于暂时没有 llama.cpp 源码，创建一个简化版本
# 后续可以替换为真实的 llama.cpp

# 创建静态库占位（实际应该从 llama.cpp 编译）
add_library(llama STATIC dummy.cpp)

add_library(llama-jni SHARED ai_chat.cpp)
target_link_libraries(llama-jni PRIVATE llama)
