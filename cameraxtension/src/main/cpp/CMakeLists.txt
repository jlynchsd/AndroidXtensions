cmake_minimum_required(VERSION 3.18.1)

project("cameraxtension")

add_library(cameraxtension SHARED cameraxtension.cpp)

target_link_libraries(cameraxtension ${log-lib} GLESv3)