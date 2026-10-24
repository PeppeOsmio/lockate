cmake_minimum_required(VERSION 3.22)
project(jpake C)

# Mbed TLS is only used as a crypto library: no programs, tests or code generation (the generated
# files are checked in on the 3.6 branch, so Python isn't needed)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(GEN_FILES OFF CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/mbedtls" mbedtls
                 EXCLUDE_FROM_ALL)

add_library(jpake SHARED jpake.c)
target_link_libraries(jpake PRIVATE mbedcrypto -Wl,--exclude-libs,ALL)
target_compile_options(jpake PRIVATE -Wall -Wextra -fvisibility=hidden)
set_target_properties(jpake PROPERTIES C_VISIBILITY_PRESET hidden)
