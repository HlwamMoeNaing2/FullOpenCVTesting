cmake_minimum_required(VERSION 3.18.1)

project("lightweight_opencv")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find OpenCV
find_package(OpenCV REQUIRED)

# Include directories
include_directories(${OpenCV_INCLUDE_DIRS})

# Create the shared library
add_library(lightweight_opencv SHARED
    lightweight_opencv.cpp)

# Link against OpenCV libraries
target_link_libraries(lightweight_opencv
    ${OpenCV_LIBS}
    android
    log)

# Set properties for the target
set_target_properties(lightweight_opencv PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON) 