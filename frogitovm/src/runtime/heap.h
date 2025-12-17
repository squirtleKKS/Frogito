#pragma once

#include <cstddef>
#include <functional>
#include <string>
#include <vector>

#include "runtime/object.h"

class Heap {
public:
    using RootVisitor = std::function<void(const Value&)>;
    using RootsEnumerator = std::function<void(const RootVisitor&)>;

    Heap() = default;
    ~Heap();

    StringObject* AllocateString(const std::string& s, bool gc_log, const RootsEnumerator& roots);
    ArrayObject* AllocateArray(std::size_t length, bool gc_log, const RootsEnumerator& roots);

    std::size_t object_count_for_testing() const { return objects_.size(); }
    std::size_t heap_bytes_for_testing() const { return heap_bytes_; }
    std::size_t threshold_for_testing() const { return threshold_; }

private:
    std::vector<HeapObject*> objects_;
    std::size_t heap_bytes_ = 0;
    std::size_t threshold_ = 1024 * 1024;

    void MaybeCollect(std::size_t upcoming_bytes, bool gc_log, const RootsEnumerator& roots);
    void Collect(bool gc_log, const RootsEnumerator& roots);

    void MarkFromRoots(const RootsEnumerator& roots, std::size_t& marked_count);
    void MarkValue(const Value& v, std::vector<HeapObject*>& worklist, std::size_t& marked_count);
    void Sweep(std::size_t& freed_count);
};
