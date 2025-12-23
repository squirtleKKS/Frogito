#pragma once

#include <stdexcept>
#include <string>

class LoadError final : public std::runtime_error {
public:
    explicit LoadError(const std::string& msg) : std::runtime_error(msg) {}
};

class RuntimeError final : public std::runtime_error {
public:
    explicit RuntimeError(const std::string& msg) : std::runtime_error(msg) {}
};
