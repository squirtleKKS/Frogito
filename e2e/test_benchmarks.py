import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "benchmarks"))
from runner import (
    Benchmark,
    factorial_20_gen,
    factorial_20_val,
    sorting_gen,
    sorting_val,
    primes_gen,
    primes_val,
)


def test_benchmark_factorial_20():
    Benchmark("factorial(20)", factorial_20_gen, factorial_20_val).run()


def test_benchmark_sorting_10000():
    Benchmark("quicksort(10000)", sorting_gen, sorting_val).run()


def test_benchmark_primes_100000():
    Benchmark("count_primes(100000)", primes_gen, primes_val).run()


if __name__ == "__main__":
    benchmarks = [
        test_benchmark_factorial_20,
        test_benchmark_sorting_10000,
        test_benchmark_primes_100000,
    ]

    print("=" * 70)
    print("FROGITO BENCHMARK TESTS")
    print("=" * 70 + "\n")

    passed = failed = 0
    for test_func in benchmarks:
        try:
            test_func()
            print(f"[PASS] {test_func.__name__}")
            passed += 1
        except Exception as e:
            print(f"[FAIL] {test_func.__name__}: {str(e)[:200]}")
            failed += 1

    print("\n" + "=" * 70)
    print(f"RESULTS: {passed} passed, {failed} failed out of {passed + failed}")
    print("=" * 70)
