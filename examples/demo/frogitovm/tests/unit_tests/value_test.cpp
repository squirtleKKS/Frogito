#include <gtest/gtest.h>

#include "runtime/object.h"
#include "runtime/value.h"

TEST(ValueTest, ConstructAndAccessInt) {
    Value v = Value::FromInt(123);
    EXPECT_EQ(v.tag, ValueTag::kInt);
    std::int64_t got = 0;
    ASSERT_TRUE(v.AsInt().TryToInt64(got));
    EXPECT_EQ(got, 123);
}

TEST(ValueTest, ConstructAndAccessFloat) {
    Value v = Value::FromFloat(3.14);
    EXPECT_EQ(v.tag, ValueTag::kFloat);
    EXPECT_DOUBLE_EQ(v.AsFloat(), 3.14);
}

TEST(ValueTest, ConstructAndAccessBool) {
    Value v = Value::FromBool(true);
    EXPECT_EQ(v.tag, ValueTag::kBool);
    EXPECT_TRUE(v.AsBool());
}

TEST(ValueTest, ConstructAndAccessString) {
    StringObject str;
    str.value = "frog";

    Value v = Value::FromRaw(&str, ValueTag::kString);
    EXPECT_EQ(v.tag, ValueTag::kString);
    EXPECT_EQ(v.AsString()->value, "frog");
}

TEST(ValueTest, ConstructAndAccessArray) {
    ArrayObject arr;
    arr.elements.push_back(Value::FromInt(42));

    Value v = Value::FromRaw(&arr, ValueTag::kArray);
    EXPECT_EQ(v.tag, ValueTag::kArray);
    ASSERT_EQ(v.AsArray()->elements.size(), 1u);
    std::int64_t got = 0;
    ASSERT_TRUE(v.AsArray()->elements[0].AsInt().TryToInt64(got));
    EXPECT_EQ(got, 42);
}

TEST(ValueTest, NullValueDefaults) {
    Value v = Value::Null();
    EXPECT_EQ(v.tag, ValueTag::kNull);
}

TEST(ValueTest, ThrowsOnWrongAccessor) {
    Value v = Value::FromInt(1);
    EXPECT_THROW(v.AsFloat(), std::runtime_error);
    EXPECT_THROW(v.AsBool(), std::runtime_error);
    EXPECT_THROW(v.AsString(), std::runtime_error);
    EXPECT_THROW(v.AsArray(), std::runtime_error);
}
