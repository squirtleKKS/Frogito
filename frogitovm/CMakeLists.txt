cmake_minimum_required(VERSION 3.16)

project(frogvm LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_executable(frogvm
    src/main.cpp
    src/bytecode/bytecode_loader.cpp
    src/vm/vm.cpp
    src/runtime/heap.cpp
)

target_include_directories(frogvm PRIVATE src)

include(CTest)
enable_testing()

include(FetchContent)
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/refs/tags/v1.14.0.zip
)
FetchContent_MakeAvailable(googletest)

add_executable(frogvm_tests
    tests/unit_tests/bytecode_loader_test.cpp
    tests/unit_tests/value_test.cpp
    tests/unit_tests/vm_test.cpp
    tests/unit_tests/heap_test.cpp
    tests/integration_tests/bytecode_vm_test.cpp
    tests/integration_tests/error_handling_test.cpp
    tests/integration_tests/real_file_test.cpp
    src/bytecode/bytecode_loader.cpp
    src/vm/vm.cpp
    src/runtime/heap.cpp
)
target_include_directories(frogvm_tests PRIVATE src)
target_link_libraries(frogvm_tests PRIVATE GTest::gtest_main)
add_test(NAME frogvm_gtests COMMAND frogvm_tests)
