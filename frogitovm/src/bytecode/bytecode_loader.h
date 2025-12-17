#pragma once

#include <filesystem>

#include "bytecode_module.h"

BytecodeModule load_frogc(const std::filesystem::path& path);
