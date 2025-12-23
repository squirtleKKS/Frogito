#pragma once

#include <cstdint>
#include <stdexcept>
#include <variant>

#include "runtime/bigint.h"

struct HeapObject;
struct StringObject;
struct ArrayObject;

enum class ValueTag : std::uint8_t {
    kNull,
    kInt,
    kFloat,
    kBool,
    kString,
    kArray
};

using ValueStorage =
    std::variant<std::monostate, BigInt, double, bool, HeapObject*>;

struct Value {
    ValueTag tag = ValueTag::kNull;
    ValueStorage storage = std::monostate{};

    static Value Null() {
        return Value{};
    }

    static Value FromInt(std::int64_t v) {
        Value out;
        out.tag = ValueTag::kInt;
        out.storage = BigInt(v);
        return out;
    }

    static Value FromInt(const BigInt& v) {
        Value out;
        out.tag = ValueTag::kInt;
        out.storage = v;
        return out;
    }

    static Value FromFloat(double v) {
        Value out;
        out.tag = ValueTag::kFloat;
        out.storage = v;
        return out;
    }

    static Value FromBool(bool v) {
        Value out;
        out.tag = ValueTag::kBool;
        out.storage = v;
        return out;
    }

    static Value FromRaw(HeapObject* p, ValueTag t) {
        Value out;
        out.tag = t;
        out.storage = p;
        return out;
    }

    const BigInt& AsInt() const {
        if (tag != ValueTag::kInt) throw std::runtime_error("expected int");
        return std::get<BigInt>(storage);
    }

    BigInt& AsInt() {
        if (tag != ValueTag::kInt) throw std::runtime_error("expected int");
        return std::get<BigInt>(storage);
    }

    double AsFloat() const {
        if (tag != ValueTag::kFloat) throw std::runtime_error("expected float");
        return std::get<double>(storage);
    }

    bool AsBool() const {
        if (tag != ValueTag::kBool) throw std::runtime_error("expected bool");
        return std::get<bool>(storage);
    }

    StringObject* AsString() const {
        if (tag != ValueTag::kString) throw std::runtime_error("expected string");
        return reinterpret_cast<StringObject*>(std::get<HeapObject*>(storage));
    }

    ArrayObject* AsArray() const {
        if (tag != ValueTag::kArray) throw std::runtime_error("expected array");
        return reinterpret_cast<ArrayObject*>(std::get<HeapObject*>(storage));
    }
};
