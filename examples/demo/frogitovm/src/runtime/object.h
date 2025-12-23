#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "runtime/value.h"

enum class ObjectKind : std::uint8_t {
    kString,
    kArray
};

struct HeapObject {
    explicit HeapObject(ObjectKind kind) : kind(kind) {}

    ObjectKind kind;
    bool marked = false;
    std::size_t size_bytes = 0;

    virtual ~HeapObject() = default;
};

struct StringObject final : HeapObject {
    StringObject() : HeapObject(ObjectKind::kString) {}
    std::string value;
};

struct ArrayObject final : HeapObject {
    ArrayObject() : HeapObject(ObjectKind::kArray) {}
    std::vector<Value> elements;
};
