#pragma once

#include <array>
#include <cstdint>
#include <iomanip>
#include <limits>
#include <ostream>
#include <string>

#include "runtime/errors.h"

class BigInt final {
public:
    static constexpr std::uint32_t kBase = 1'000'000'000u;
    static constexpr std::uint32_t kBaseDigits = 9;
    static constexpr std::size_t kMaxDigits = 20;

    BigInt() = default;

    explicit BigInt(std::int64_t v) { assign(v); }

    bool is_zero() const noexcept { return len_ == 0; }
    bool is_negative() const noexcept { return negative_; }

    std::string ToString() const {
        if (len_ == 0) return "0";

        std::string out;
        if (negative_) out.push_back('-');

        out += std::to_string(digits_[len_ - 1]);
        for (std::int32_t i = static_cast<std::int32_t>(len_) - 2; i >= 0; --i) {
            std::string part = std::to_string(digits_[static_cast<std::size_t>(i)]);
            out.append(kBaseDigits - part.size(), '0');
            out += part;
        }
        return out;
    }

    bool TryToInt64(std::int64_t& out) const noexcept {
        if (len_ == 0) {
            out = 0;
            return true;
        }

        std::uint64_t limit = negative_
            ? (static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()) + 1u)
            : static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max());

        std::uint64_t acc = 0;
        for (std::int32_t i = static_cast<std::int32_t>(len_) - 1; i >= 0; --i) {
            std::uint64_t digit = digits_[static_cast<std::size_t>(i)];
            if (acc > (limit - digit) / kBase) return false;
            acc = acc * kBase + digit;
        }

        if (!negative_) {
            out = static_cast<std::int64_t>(acc);
            return true;
        }

        if (acc == (static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()) + 1u)) {
            out = std::numeric_limits<std::int64_t>::min();
            return true;
        }

        out = -static_cast<std::int64_t>(acc);
        return true;
    }

    friend bool operator==(const BigInt& a, const BigInt& b) noexcept {
        if (a.len_ != b.len_) return false;
        if (a.len_ == 0) return true;
        if (a.negative_ != b.negative_) return false;
        for (std::size_t i = 0; i < a.len_; ++i) {
            if (a.digits_[i] != b.digits_[i]) return false;
        }
        return true;
    }

    friend bool operator!=(const BigInt& a, const BigInt& b) noexcept { return !(a == b); }

    friend bool operator<(const BigInt& a, const BigInt& b) noexcept {
        if (a.negative_ != b.negative_) return a.negative_;
        int cmp = compare_abs(a, b);
        return a.negative_ ? (cmp > 0) : (cmp < 0);
    }

    friend bool operator<=(const BigInt& a, const BigInt& b) noexcept { return !(b < a); }
    friend bool operator>(const BigInt& a, const BigInt& b) noexcept { return b < a; }
    friend bool operator>=(const BigInt& a, const BigInt& b) noexcept { return !(a < b); }

    BigInt operator-() const noexcept {
        BigInt out = *this;
        if (!out.is_zero()) out.negative_ = !out.negative_;
        return out;
    }

    BigInt& operator+=(const BigInt& other) {
        *this = *this + other;
        return *this;
    }

    BigInt& operator-=(const BigInt& other) {
        *this = *this - other;
        return *this;
    }

    BigInt& operator*=(const BigInt& other) {
        *this = *this * other;
        return *this;
    }

    BigInt& operator/=(const BigInt& other) {
        *this = *this / other;
        return *this;
    }

    BigInt& operator%=(const BigInt& other) {
        *this = *this % other;
        return *this;
    }

    friend BigInt operator+(const BigInt& a, const BigInt& b) {
        if (a.negative_ == b.negative_) {
            BigInt out = add_abs(a, b);
            out.negative_ = a.negative_;
            out.normalize_zero();
            return out;
        }

        int cmp = compare_abs(a, b);
        if (cmp == 0) return BigInt{};

        if (cmp > 0) {
            BigInt out = sub_abs(a, b);
            out.negative_ = a.negative_;
            out.normalize_zero();
            return out;
        }

        BigInt out = sub_abs(b, a);
        out.negative_ = b.negative_;
        out.normalize_zero();
        return out;
    }

    friend BigInt operator-(const BigInt& a, const BigInt& b) { return a + (-b); }

    friend BigInt operator*(const BigInt& a, const BigInt& b) {
        if (a.is_zero() || b.is_zero()) return BigInt{};

        BigInt out = mul_abs(a, b);
        out.negative_ = a.negative_ != b.negative_;
        out.normalize_zero();
        return out;
    }

    friend BigInt operator/(const BigInt& a, const BigInt& b) {
        return divmod(a, b).first;
    }

    friend BigInt operator%(const BigInt& a, const BigInt& b) {
        return divmod(a, b).second;
    }

    friend std::ostream& operator<<(std::ostream& out, const BigInt& v) {
        out << v.ToString();
        return out;
    }

private:
    bool negative_ = false;
    std::uint32_t len_ = 0;
    std::array<std::uint32_t, kMaxDigits> digits_{};

    void normalize_zero() noexcept {
        if (len_ == 0) negative_ = false;
    }

    void trim() noexcept {
        while (len_ > 0 && digits_[len_ - 1] == 0) {
            --len_;
        }
        normalize_zero();
    }

    void assign(std::int64_t v) {
        digits_.fill(0);
        negative_ = false;
        len_ = 0;

        if (v == 0) return;

        std::uint64_t mag = 0;
        if (v < 0) {
            negative_ = true;
            mag = static_cast<std::uint64_t>(-(v + 1)) + 1u;
        } else {
            mag = static_cast<std::uint64_t>(v);
        }

        while (mag > 0) {
            if (len_ >= kMaxDigits) throw RuntimeError("integer overflow");
            digits_[len_++] = static_cast<std::uint32_t>(mag % kBase);
            mag /= kBase;
        }
    }

    static int compare_abs(const BigInt& a, const BigInt& b) noexcept {
        if (a.len_ != b.len_) return (a.len_ < b.len_) ? -1 : 1;
        for (std::int32_t i = static_cast<std::int32_t>(a.len_) - 1; i >= 0; --i) {
            std::uint32_t ad = a.digits_[static_cast<std::size_t>(i)];
            std::uint32_t bd = b.digits_[static_cast<std::size_t>(i)];
            if (ad != bd) return (ad < bd) ? -1 : 1;
        }
        return 0;
    }

    static BigInt add_abs(const BigInt& a, const BigInt& b) {
        BigInt out;
        std::uint64_t carry = 0;
        std::uint32_t max_len = a.len_ > b.len_ ? a.len_ : b.len_;

        for (std::uint32_t i = 0; i < max_len || carry != 0; ++i) {
            if (i >= kMaxDigits) throw RuntimeError("integer overflow");

            std::uint64_t sum = carry;
            if (i < a.len_) sum += a.digits_[i];
            if (i < b.len_) sum += b.digits_[i];

            out.digits_[i] = static_cast<std::uint32_t>(sum % kBase);
            carry = sum / kBase;
            out.len_ = i + 1;
        }

        out.trim();
        return out;
    }

    static BigInt sub_abs(const BigInt& a, const BigInt& b) {
        BigInt out;
        std::int64_t borrow = 0;
        out.len_ = a.len_;
        for (std::uint32_t i = 0; i < a.len_; ++i) {
            std::int64_t cur = static_cast<std::int64_t>(a.digits_[i]) - borrow;
            if (i < b.len_) cur -= static_cast<std::int64_t>(b.digits_[i]);
            if (cur < 0) {
                cur += static_cast<std::int64_t>(kBase);
                borrow = 1;
            } else {
                borrow = 0;
            }
            out.digits_[i] = static_cast<std::uint32_t>(cur);
        }
        out.trim();
        return out;
    }

    static BigInt mul_abs(const BigInt& a, const BigInt& b) {
        BigInt out;
        if (a.len_ == 0 || b.len_ == 0) return out;

        for (std::uint32_t i = 0; i < a.len_; ++i) {
            std::uint64_t carry = 0;
            for (std::uint32_t j = 0; j < b.len_ || carry != 0; ++j) {
                std::uint32_t idx = i + j;
                if (idx >= kMaxDigits) throw RuntimeError("integer overflow");

                std::uint64_t cur = out.digits_[idx] + carry;
                if (j < b.len_) {
                    cur += static_cast<std::uint64_t>(a.digits_[i]) * static_cast<std::uint64_t>(b.digits_[j]);
                }

                out.digits_[idx] = static_cast<std::uint32_t>(cur % kBase);
                carry = cur / kBase;
                if (idx + 1 > out.len_) out.len_ = idx + 1;
            }
        }

        out.trim();
        return out;
    }

    static bool mul_uint_abs_no_throw(const BigInt& a, std::uint32_t m, BigInt& out) noexcept {
        out.digits_.fill(0);
        out.negative_ = false;
        out.len_ = 0;
        if (a.len_ == 0 || m == 0) return true;

        std::uint64_t carry = 0;
        for (std::uint32_t i = 0; i < a.len_; ++i) {
            std::uint64_t cur = carry + static_cast<std::uint64_t>(a.digits_[i]) * static_cast<std::uint64_t>(m);
            out.digits_[i] = static_cast<std::uint32_t>(cur % kBase);
            carry = cur / kBase;
            out.len_ = i + 1;
        }

        if (carry != 0) {
            if (out.len_ >= kMaxDigits) return false;
            out.digits_[out.len_++] = static_cast<std::uint32_t>(carry);
        }

        out.trim();
        return true;
    }

    void shift_base_add(std::uint32_t digit) {
        if (len_ == 0) {
            if (digit == 0) return;
            digits_[0] = digit;
            len_ = 1;
            return;
        }
        if (len_ >= kMaxDigits) throw RuntimeError("integer overflow");
        for (std::uint32_t i = len_; i > 0; --i) {
            digits_[i] = digits_[i - 1];
        }
        digits_[0] = digit;
        ++len_;
    }

    static std::pair<BigInt, BigInt> divmod_abs(const BigInt& a, const BigInt& b) {
        if (b.len_ == 0) throw RuntimeError("division by zero");
        if (a.len_ == 0) return {BigInt{}, BigInt{}};

        int cmp = compare_abs(a, b);
        if (cmp < 0) return {BigInt{}, a};
        if (cmp == 0) return {BigInt{1}, BigInt{}};

        if (b.len_ == 1) {
            std::uint32_t divisor = b.digits_[0];
            BigInt q;
            std::uint64_t rem = 0;
            q.len_ = a.len_;
            for (std::int32_t i = static_cast<std::int32_t>(a.len_) - 1; i >= 0; --i) {
                std::uint64_t cur = a.digits_[static_cast<std::size_t>(i)] + rem * kBase;
                q.digits_[static_cast<std::size_t>(i)] = static_cast<std::uint32_t>(cur / divisor);
                rem = cur % divisor;
            }
            q.trim();
            BigInt r(static_cast<std::int64_t>(rem));
            return {q, r};
        }

        BigInt q;
        BigInt r;
        q.digits_.fill(0);
        q.len_ = a.len_;

        for (std::int32_t i = static_cast<std::int32_t>(a.len_) - 1; i >= 0; --i) {
            r.shift_base_add(a.digits_[static_cast<std::size_t>(i)]);

            std::uint32_t lo = 0;
            std::uint32_t hi = kBase - 1;
            std::uint32_t best = 0;

            while (lo <= hi) {
                std::uint32_t mid = lo + ((hi - lo) / 2);
                BigInt prod;
                bool ok = mul_uint_abs_no_throw(b, mid, prod);
                if (!ok || compare_abs(prod, r) > 0) {
                    hi = mid - 1;
                } else {
                    best = mid;
                    lo = mid + 1;
                }
            }

            q.digits_[static_cast<std::size_t>(i)] = best;
            if (best != 0) {
                BigInt prod;
                bool ok = mul_uint_abs_no_throw(b, best, prod);
                if (!ok) throw RuntimeError("integer overflow");
                r = sub_abs(r, prod);
            }
        }

        q.trim();
        r.trim();
        return {q, r};
    }

    static std::pair<BigInt, BigInt> divmod(const BigInt& a, const BigInt& b) {
        if (b.len_ == 0) throw RuntimeError("division by zero");

        BigInt abs_a = a;
        abs_a.negative_ = false;
        BigInt abs_b = b;
        abs_b.negative_ = false;

        auto [q, r] = divmod_abs(abs_a, abs_b);
        q.negative_ = (a.negative_ != b.negative_) && !q.is_zero();
        r.negative_ = a.negative_ && !r.is_zero();
        q.normalize_zero();
        r.normalize_zero();
        return {q, r};
    }
};
