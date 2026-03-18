cmake_minimum_required(VERSION 3.22.1)

project(flexmusic_player)

set(FFMPEG_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg/libs/${ANDROID_ABI})

add_library(ffmpegAvutil SHARED IMPORTED)
set_target_properties(ffmpegAvutil PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libavutil.so)

add_library(ffmpegSwresample SHARED IMPORTED)
set_target_properties(ffmpegSwresample PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libswresample.so)

add_library(ffmpegSwscale SHARED IMPORTED)
set_target_properties(ffmpegSwscale PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libswscale.so)

add_library(ffmpegAvcodec SHARED IMPORTED)
set_target_properties(ffmpegAvcodec PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libavcodec.so)

add_library(ffmpegAvformat SHARED IMPORTED)
set_target_properties(ffmpegAvformat PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libavformat.so)

add_library(
        flexmusic_player
        SHARED
        jni/JNILoader.cpp
        jni/bridge/PlayerBridge.cpp
        io/FileIoRegistry.cpp
        io/PassthroughFileIo.cpp
        io/FfmpegStreamFileIo.cpp)

target_include_directories(
        flexmusic_player
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg/include)

find_library(log-lib log)

target_link_libraries(
        flexmusic_player
        ffmpegAvformat
        ffmpegAvcodec
        ffmpegAvutil
        ffmpegSwresample
        ffmpegSwscale
        ${log-lib})
