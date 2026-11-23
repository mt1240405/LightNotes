cmake_minimum_required(VERSION 3.22.1)

project("notes_search")

add_library(
    notes_search
    SHARED
    native_search.cpp
)

target_compile_features(notes_search PRIVATE cxx_std_17)

target_compile_options(notes_search PRIVATE
    -std=c++17
    -O3
    -flto
    -fvisibility=hidden
)

target_link_libraries(
    notes_search
    log
)
