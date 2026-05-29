cmake_minimum_required(VERSION 3.22.1)
project(rustdroid_native C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(
    rustdroid_native
    SHARED
    native_bridge.c
    boot_image_parser.c
    boot_image_patcher.c
    ramdisk_handler.c
    native_log.c
    checksum.c
)

target_include_directories(rustdroid_native PRIVATE include)

target_compile_definitions(rustdroid_native PRIVATE _GNU_SOURCE)

target_link_libraries(
    rustdroid_native
    log
)
