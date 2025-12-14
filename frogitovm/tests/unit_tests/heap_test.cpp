#include <gtest/gtest.h>

#include <string>
#include <vector>

#include "runtime/heap.h"
#include "runtime/object.h"
#include "runtime/value.h"

namespace {

Heap::RootsEnumerator MakeRoots(std::vector<Value>& roots) {
    return [&roots](const Heap::RootVisitor& visit) {
        for (const Value& v : roots) {
            visit(v);
        }
    };
}

std::string MakeLargeString() {
    return std::string(2 * 1024 * 1024, 'x');
}

}  // namespace

TEST(HeapTest, AllocateStringStoresValue) {
    Heap heap;
    std::vector<Value> roots;
    auto roots_enum = MakeRoots(roots);

    StringObject* str = heap.AllocateString("frog", false, roots_enum);
    ASSERT_NE(str, nullptr);
    EXPECT_EQ(str->value, "frog");
    EXPECT_EQ(heap.object_count_for_testing(), 1u);
}

TEST(HeapTest, AllocateArrayInitializesElements) {
    Heap heap;
    std::vector<Value> roots;
    auto roots_enum = MakeRoots(roots);

    ArrayObject* arr = heap.AllocateArray(3, false, roots_enum);
    ASSERT_NE(arr, nullptr);
    ASSERT_EQ(arr->elements.size(), 3u);
    for (const Value& v : arr->elements) {
        EXPECT_EQ(v.tag, ValueTag::kNull);
    }
}

TEST(HeapTest, GarbageCollectorDropsUnreachableObjects) {
    Heap heap;
    std::vector<Value> roots;
    auto roots_enum = MakeRoots(roots);

    heap.AllocateString("tiny", false, roots_enum);
    StringObject* big = heap.AllocateString(MakeLargeString(), false, roots_enum);

    ASSERT_NE(big, nullptr);
    EXPECT_EQ(heap.object_count_for_testing(), 1u);
    EXPECT_GE(heap.heap_bytes_for_testing(), big->value.size());
}

TEST(HeapTest, GarbageCollectorRespectsRoots) {
    Heap heap;
    std::vector<Value> roots;
    auto roots_enum = MakeRoots(roots);

    StringObject* keep = heap.AllocateString("keep", false, roots_enum);
    roots.push_back(Value::FromRaw(keep, ValueTag::kString));

    heap.AllocateString("discard", false, roots_enum);
    StringObject* survivor = heap.AllocateString(MakeLargeString(), false, roots_enum);
    (void)survivor;

    EXPECT_EQ(heap.object_count_for_testing(), 2u);
    EXPECT_EQ(keep->value, "keep");
}

TEST(HeapTest, GarbageCollectorTraversesArrays) {
    Heap heap;
    std::vector<Value> roots;
    auto roots_enum = MakeRoots(roots);

    StringObject* nested = heap.AllocateString("nested", false, roots_enum);
    ArrayObject* arr = heap.AllocateArray(1, false, roots_enum);
    arr->elements[0] = Value::FromRaw(nested, ValueTag::kString);
    roots.push_back(Value::FromRaw(arr, ValueTag::kArray));

    heap.AllocateString("discard", false, roots_enum);
    StringObject* big = heap.AllocateString(MakeLargeString(), false, roots_enum);
    (void)big;

    EXPECT_EQ(heap.object_count_for_testing(), 3u);
    ASSERT_EQ(arr->elements.size(), 1u);
    EXPECT_EQ(arr->elements[0].AsString(), nested);
}
