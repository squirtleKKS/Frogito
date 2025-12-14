#include "runtime/heap.h"

#include <algorithm>
#include <iostream>

Heap::~Heap() {
    for (HeapObject* obj : objects_) {
        delete obj;
    }
    objects_.clear();
}

void Heap::MaybeCollect(std::size_t upcoming_bytes, bool gc_log, const RootsEnumerator& roots) {
    if (heap_bytes_ + upcoming_bytes > threshold_) {
        Collect(gc_log, roots);
        threshold_ = std::max(threshold_ * 2, heap_bytes_ * 2);
    }
}

StringObject* Heap::AllocateString(const std::string& s, bool gc_log, const RootsEnumerator& roots) {
    std::size_t estimate = sizeof(StringObject) + s.size();
    MaybeCollect(estimate, gc_log, roots);

    auto* obj = new StringObject();
    obj->value = s;
    obj->size_bytes = estimate;

    objects_.push_back(obj);
    heap_bytes_ += estimate;
    return obj;
}

ArrayObject* Heap::AllocateArray(std::size_t length, bool gc_log, const RootsEnumerator& roots) {
    std::size_t estimate = sizeof(ArrayObject) + sizeof(Value) * length;
    MaybeCollect(estimate, gc_log, roots);

    auto* obj = new ArrayObject();
    obj->elements.resize(length, Value::Null());
    obj->size_bytes = estimate;

    objects_.push_back(obj);
    heap_bytes_ += estimate;
    return obj;
}

void Heap::Collect(bool gc_log, const RootsEnumerator& roots) {
    if (gc_log) {
        std::cout << "GC START heap=" << heap_bytes_ << " objects=" << objects_.size() << "\n";
    }

    for (HeapObject* obj : objects_) {
        obj->marked = false;
    }

    std::size_t marked_count = 0;
    MarkFromRoots(roots, marked_count);

    if (gc_log) {
        std::cout << "GC MARKED=" << marked_count << "\n";
    }

    std::size_t freed_count = 0;
    Sweep(freed_count);

    if (gc_log) {
        std::cout << "GC SWEPT freed=" << freed_count << " heap=" << heap_bytes_ << "\n";
    }
}

void Heap::MarkFromRoots(const RootsEnumerator& roots, std::size_t& marked_count) {
    std::vector<HeapObject*> worklist;
    roots([&](const Value& v) { MarkValue(v, worklist, marked_count); });

    while (!worklist.empty()) {
        HeapObject* obj = worklist.back();
        worklist.pop_back();

        if (obj->kind == ObjectKind::kArray) {
            auto* arr = static_cast<ArrayObject*>(obj);
            for (const Value& e : arr->elements) {
                MarkValue(e, worklist, marked_count);
            }
        }
    }
}

void Heap::MarkValue(const Value& v, std::vector<HeapObject*>& worklist, std::size_t& marked_count) {
    if (v.tag != ValueTag::kString && v.tag != ValueTag::kArray) {
        return;
    }
    HeapObject* obj = std::get<HeapObject*>(v.storage);
    if (!obj || obj->marked) {
        return;
    }
    obj->marked = true;
    ++marked_count;
    worklist.push_back(obj);
}

void Heap::Sweep(std::size_t& freed_count) {
    std::size_t write = 0;
    for (std::size_t i = 0; i < objects_.size(); ++i) {
        HeapObject* obj = objects_[i];
        if (!obj->marked) {
            heap_bytes_ -= obj->size_bytes;
            delete obj;
            ++freed_count;
        } else {
            obj->marked = false;
            objects_[write++] = obj;
        }
    }
    objects_.resize(write);
}
