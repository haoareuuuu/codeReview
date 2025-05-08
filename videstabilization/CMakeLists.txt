cmake_minimum_required(VERSION 3.22.1)

project(videstabilization)

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加OpenGL ES库
find_library(GLES3-lib GLESv3)
find_library(EGL-lib EGL)

# 添加Android日志库
find_library(log-lib log)

# 添加Android NDK库
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# 添加源文件
file(GLOB_RECURSE CPP_SOURCES
     "src/main/cpp/*.cpp"
     "src/main/cpp/*.c"
)

# 添加头文件路径
include_directories(
    src/main/cpp
    src/main/cpp/algorithm
    src/main/cpp/jni
    src/main/cpp/util
)

# 创建共享库
#add_library(
#    videstabilization
#    SHARED
#    ${CPP_SOURCES}
#)

# 链接库
#target_link_libraries(
#    videstabilization
#    ${GLES3-lib}
#    ${EGL-lib}
#    ${log-lib}
#    ${android-lib}
#    ${jnigraphics-lib}
#)

# 启用OpenMP (如果需要)
# find_package(OpenMP)
# if(OpenMP_CXX_FOUND)
#     target_link_libraries(videstabilization OpenMP::OpenMP_CXX)
# endif()

# 添加OpenCV支持
#set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
#find_package(OpenCV REQUIRED)
#include_directories(${OpenCV_INCLUDE_DIRS})
#target_link_libraries(videstabilization ${OpenCV_LIBS})
