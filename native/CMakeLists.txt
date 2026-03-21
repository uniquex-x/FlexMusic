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
        core/logger/logger.cpp
        jni/JNILoader.cpp
        jni/bridge/PlayerBridge.cpp
        audio/OpenSlAudioRenderer.cpp
        audio/SoundTouchTempoProcessor.cpp
        io/FdFileIo.cpp
        io/FileIoRegistry.cpp
        io/PassthroughFileIo.cpp
        io/FfmpegStreamFileIo.cpp
        media/source/AvioDataSource.cpp
        media/demux/FfmpegDemuxer.cpp
        media/codec/FfmpegAudioDecoder.cpp
        player/PlayerSession.cpp
        third_party/soundtouch/source/SoundTouch/AAFilter.cpp
        third_party/soundtouch/source/SoundTouch/FIFOSampleBuffer.cpp
        third_party/soundtouch/source/SoundTouch/FIRFilter.cpp
        third_party/soundtouch/source/SoundTouch/InterpolateLinear.cpp
        third_party/soundtouch/source/SoundTouch/InterpolateCubic.cpp
        third_party/soundtouch/source/SoundTouch/InterpolateShannon.cpp
        third_party/soundtouch/source/SoundTouch/RateTransposer.cpp
        third_party/soundtouch/source/SoundTouch/SoundTouch.cpp
        third_party/soundtouch/source/SoundTouch/TDStretch.cpp
        third_party/soundtouch/source/SoundTouch/cpu_detect_x86.cpp)

target_include_directories(
        flexmusic_player
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/soundtouch/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/soundtouch/source/SoundTouch)

target_compile_definitions(
        flexmusic_player
        PRIVATE
        ST_NO_EXCEPTION_HANDLING
        SOUNDTOUCH_INTEGER_SAMPLES
        SOUNDTOUCH_DISABLE_X86_OPTIMIZATIONS)

find_library(log-lib log)
find_library(openSLES-lib OpenSLES)
find_library(android-lib android)

target_link_libraries(
        flexmusic_player
        ffmpegAvformat
        ffmpegAvcodec
        ffmpegAvutil
        ffmpegSwresample
        ffmpegSwscale
        ${openSLES-lib}
        ${android-lib}
        ${log-lib})
