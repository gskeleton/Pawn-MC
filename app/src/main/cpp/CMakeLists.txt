cmake_minimum_required(VERSION 3.22.1)
project("pawnc_jni")

add_library(pawnc_jni SHARED
    pawnc_jni.cpp
)

add_library(pawnc SHARED IMPORTED)
set_target_properties(pawnc PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpawnc.so
)

target_link_libraries(pawnc_jni
    android
    log
    pawnc
)
