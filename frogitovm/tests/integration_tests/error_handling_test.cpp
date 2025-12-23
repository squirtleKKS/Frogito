#include <array>
#include <atomic>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <stdexcept>
#include <string>
#include <system_error>

#include <gtest/gtest.h>

#include "bytecode/bytecode_loader.h"
#include "bytecode/bytecode_module.h"
#include "runtime/errors.h"
#include "vm/vm.h"

namespace {

std::filesystem::path TempPath() {
    static std::atomic<std::uint64_t> counter{0};
    std::filesystem::path dir = std::filesystem::temp_directory_path();
    std::string name = "frogvm_err_" + std::to_string(counter.fetch_add(1, std::memory_order_relaxed)) + ".frogc";
    return dir / name;
}

template <std::size_t N>
BytecodeModule LoadModuleFromBytes(const std::array<std::uint8_t, N>& data) {
    std::filesystem::path tmp = TempPath();
    {
        std::ofstream out(tmp, std::ios::binary);
        if (!out) {
            throw std::runtime_error("failed to open temp file for writing");
        }
        out.write(reinterpret_cast<const char*>(data.data()),
                  static_cast<std::streamsize>(data.size()));
    }
    BytecodeModule module = load_frogc(tmp);
    std::error_code ec;
    std::filesystem::remove(tmp, ec);
    return module;
}

constexpr std::array<std::uint8_t, 18> kBadMagic = {
    0x42, 0x41, 0x44, 0x44, 0x00, 0x01, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};

constexpr std::array<std::uint8_t, 30> kInvalidConstIndex = {
    0x46, 0x52, 0x4F, 0x47, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x04, 0x00,
    0x00, 0x00, 0x01, 0x78, 0x00, 0x01, 0x00, 0x00, 0x00, 0x05,
};

constexpr std::array<std::uint8_t, 30> kUnknownGlobalLoad = {
    0x46, 0x52, 0x4F, 0x47, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x04, 0x00,
    0x00, 0x00, 0x01, 0x78, 0x03, 0x01, 0x00, 0x00, 0x00, 0x00,
};

constexpr std::array<std::uint8_t, 30> kJumpOutOfRange = {
    0x46, 0x52, 0x4F, 0x47, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x04, 0x00,
    0x00, 0x00, 0x01, 0x78, 0x14, 0x01, 0x00, 0x00, 0x00, 0x05,
};

constexpr std::array<std::uint8_t, 54> kDivisionByZero = {
    0x46, 0x52, 0x4F, 0x47, 0x00, 0x01, 0x00, 0x00, 0x00, 0x03,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x04, 0x00,
    0x00, 0x00, 0x01, 0x78, 0x01, 0x00, 0x00, 0x00, 0x0A, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x08, 0x00, 0x04, 0x01,
    0x00, 0x00, 0x00, 0x00,
};

constexpr std::array<std::uint8_t, 56> kLoadIndexTypeMismatch = {
    0x46, 0x52, 0x4F, 0x47, 0x00, 0x01, 0x00, 0x00, 0x00, 0x03,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x04, 0x00,
    0x00, 0x00, 0x03, 0x61, 0x72, 0x72, 0x01, 0x00, 0x00, 0x00,
    0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
    0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x19, 0x00,
    0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
};

}  // namespace

TEST(BytecodeErrorHandlingTest, LoadFailsOnBadMagic) {
    try {
        (void)LoadModuleFromBytes(kBadMagic);
        FAIL() << "expected runtime_error";
    } catch (const std::runtime_error& e) {
        EXPECT_NE(std::string(e.what()).find("bad magic"), std::string::npos);
    }
}

TEST(BytecodeErrorHandlingTest, LoadFailsOnInvalidConstIndex) {
    try {
        (void)LoadModuleFromBytes(kInvalidConstIndex);
        FAIL() << "expected runtime_error";
    } catch (const std::runtime_error& e) {
        EXPECT_NE(std::string(e.what()).find("invalid constIndex"), std::string::npos);
    }
}

TEST(BytecodeErrorHandlingTest, RuntimeFailsOnUnknownGlobal) {
    BytecodeModule module = LoadModuleFromBytes(kUnknownGlobalLoad);
    Vm vm(module, {});
    try {
        vm.run();
        FAIL() << "expected RuntimeError";
    } catch (const RuntimeError& e) {
        EXPECT_NE(std::string(e.what()).find("unknown global"), std::string::npos);
    }
}

TEST(BytecodeErrorHandlingTest, LoadFailsOnJumpOutOfRange) {
    try {
        (void)LoadModuleFromBytes(kJumpOutOfRange);
        FAIL() << "expected runtime_error";
    } catch (const std::runtime_error& e) {
        EXPECT_NE(std::string(e.what()).find("invalid jump target"), std::string::npos);
    }
}

TEST(BytecodeErrorHandlingTest, RuntimeFailsOnDivisionByZero) {
    BytecodeModule module = LoadModuleFromBytes(kDivisionByZero);
    Vm vm(module, {});
    try {
        vm.run();
        FAIL() << "expected RuntimeError";
    } catch (const RuntimeError& e) {
        EXPECT_NE(std::string(e.what()).find("division by zero"), std::string::npos);
    }
}