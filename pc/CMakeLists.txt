project(keymapper)
cmake_minimum_required(VERSION 3.3)

find_package(X11 REQUIRED)

file(GLOB_RECURSE src_file ./src/*.cpp)
include_directories(./include)

add_executable(kmp ${src_file})
target_link_libraries(kmp X11 pthread)