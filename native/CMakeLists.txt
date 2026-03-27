cmake_minimum_required(VERSION 3.22.1)

project(flexmusic_player)

set(MODULE_AUDIO_PROCESS_PATH ${CMAKE_CURRENT_SOURCE_DIR}/audioProcess)
set(MODULE_DOWNLOAD_PATH ${CMAKE_CURRENT_SOURCE_DIR}/download)
set(MODULE_IO_PATH ${CMAKE_CURRENT_SOURCE_DIR}/io)
set(MODULE_JNI_PATH ${CMAKE_CURRENT_SOURCE_DIR}/jni)
set(MODULE_MEDIA_PATH ${CMAKE_CURRENT_SOURCE_DIR}/media)
set(MODULE_PLAYER_PATH ${CMAKE_CURRENT_SOURCE_DIR}/player)
set(MODULE_RECOMMEND_PATH ${CMAKE_CURRENT_SOURCE_DIR}/recommend)
set(MODULE_TRANSCODE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/transcode)
set(MODULE_UTILS_PATH ${CMAKE_CURRENT_SOURCE_DIR}/utils)

set(FFMPEG_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg/libs/${ANDROID_ABI})
set(FLAC_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/flac/libs/${ANDROID_ABI})
set(OGGVORBIS_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/oggvorbis/libs/${ANDROID_ABI})
set(MP3LAME_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/mp3lame/libs/${ANDROID_ABI})

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

add_library(flac SHARED IMPORTED)
set_target_properties(flac PROPERTIES IMPORTED_LOCATION ${FLAC_LIB_DIR}/libFLAC.so)

add_library(ogg SHARED IMPORTED)
set_target_properties(ogg PROPERTIES IMPORTED_LOCATION ${OGGVORBIS_LIB_DIR}/libogg.so)

add_library(vorbis SHARED IMPORTED)
set_target_properties(vorbis PROPERTIES IMPORTED_LOCATION ${OGGVORBIS_LIB_DIR}/libvorbis.so)

add_library(vorbisenc SHARED IMPORTED)
set_target_properties(vorbisenc PROPERTIES IMPORTED_LOCATION ${OGGVORBIS_LIB_DIR}/libvorbisenc.so)

add_library(mp3lame SHARED IMPORTED)
set_target_properties(mp3lame PROPERTIES IMPORTED_LOCATION ${MP3LAME_LIB_DIR}/libmp3lame.so)

add_library(
        flexmusic_player
        SHARED
        # 通用
        ${MODULE_UTILS_PATH}/logger/logger.cpp
        # JNI源文件
        ${MODULE_JNI_PATH}/JNILoader.cpp
        ${MODULE_JNI_PATH}/bridge/DownloadBridge.cpp
        ${MODULE_JNI_PATH}/bridge/PlayerBridge.cpp
        ${MODULE_JNI_PATH}/bridge/RecommendBridge.cpp
        ${MODULE_JNI_PATH}/bridge/TranscodeBridge.cpp
        # 下载源文件
        ${MODULE_DOWNLOAD_PATH}/NativeDownloader.cpp
        # 媒体处理核心源文件
        ${MODULE_MEDIA_PATH}/render/OpenSlAudioRenderer.cpp
        ${MODULE_MEDIA_PATH}/source/AvioDataSource.cpp
        ${MODULE_MEDIA_PATH}/demux/FfmpegDemuxer.cpp
        ${MODULE_MEDIA_PATH}/codec/FfmpegAudioDecoder.cpp
        # 音频处理源文件
        ${MODULE_AUDIO_PROCESS_PATH}/pitchShifter/SoundTouchTempoProcessor.cpp
        # 文件IO源文件
        ${MODULE_IO_PATH}/FdFileIo.cpp
        ${MODULE_IO_PATH}/FileIoRegistry.cpp
        ${MODULE_IO_PATH}/PassthroughFileIo.cpp
        ${MODULE_IO_PATH}/FfmpegStreamFileIo.cpp
        # 转码源文件
        ${MODULE_TRANSCODE_PATH}/AudioTranscoder.cpp
        # 播放器源文件
        ${MODULE_PLAYER_PATH}/PlayerSession.cpp
        # 推荐源文件
        ${MODULE_RECOMMEND_PATH}/RecommendKernel.cpp
        # 三方库源文件
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
        # 第三方库
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/flac/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/oggvorbis/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/mp3lame/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/soundtouch/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/soundtouch/source/SoundTouch
        # 音频处理
        ${MODULE_AUDIO_PROCESS_PATH}/pitchShifter
        # 下载
        ${MODULE_DOWNLOAD_PATH}
        # IO
        ${MODULE_IO_PATH}
        # JNI
        ${MODULE_JNI_PATH}
        ${MODULE_JNI_PATH}/bridge
        ${MODULE_JNI_PATH}/version
        # 媒体处理核心
        ${MODULE_MEDIA_PATH}/codec
        ${MODULE_MEDIA_PATH}/demux
        ${MODULE_MEDIA_PATH}/packet
        ${MODULE_MEDIA_PATH}/render
        ${MODULE_MEDIA_PATH}/source
        # 播放器
        ${MODULE_PLAYER_PATH}
        ${MODULE_PLAYER_PATH}/state
        # 推荐
        ${MODULE_RECOMMEND_PATH}
        # 转码
        ${MODULE_TRANSCODE_PATH}
        # 通用
        ${MODULE_UTILS_PATH}/error
        ${MODULE_UTILS_PATH}/logger
        ${MODULE_UTILS_PATH}/thread
)

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
        flac
        ogg
        vorbis
        vorbisenc
        mp3lame
        ${openSLES-lib}
        ${android-lib}
        ${log-lib})
