#pragma once

#include <vector>

#include "runtime/value.h"

struct ArrayObject {
    std::vector<Value> elements;
};
