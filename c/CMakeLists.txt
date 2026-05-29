cmake_minimum_required(VERSION 3.10)
project(rustdroid_c C)

set(CMAKE_C_STANDARD 99)

# Include directory containing public headers
include_directories(include)

# Define the static library containing C glue layer
add_library(rustdroid_c STATIC
    src/android_glue.c
    src/mount_glue.c
    src/selinux_glue.c
    src/process_glue.c
)

# Enforce logging and compatibility flags
target_compile_definitions(rustdroid_c PRIVATE _GNU_SOURCE)
target_link_libraries(rustdroid_c log)
