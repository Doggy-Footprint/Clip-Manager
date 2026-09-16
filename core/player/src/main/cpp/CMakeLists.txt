cmake_minimum_required(VERSION 3.22.1)
project(clipplayer CXX)
set(CMAKE_CXX_STANDARD 17)

set(FFMPEG_INCLUDE ${CMAKE_SOURCE_DIR}/include)
set(FFMPEG_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

foreach(FF_LIB avformat avcodec avutil swresample swscale)
    add_library(${FF_LIB} SHARED IMPORTED)
    set_target_properties(${FF_LIB} PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/lib${FF_LIB}.so)
endforeach()

add_library(clipplayer SHARED
    jni_bridge.cpp
    player/Player.cpp
    player/AudioOutput.cpp
)

target_include_directories(clipplayer PRIVATE ${FFMPEG_INCLUDE})

target_link_libraries(clipplayer
    log
    android
    mediandk
    avformat
    avcodec
    avutil
    swresample
    swscale
)
