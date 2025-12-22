import subprocess
import tempfile
import re
import shutil
import os
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
VM_EXECUTABLE = PROJECT_ROOT / "frogitovm" / "build" / "frogvm"

passed = 0
failed = 0


def compile_code(code):
    temp_dir = Path(tempfile.mkdtemp())
    source_file = temp_dir / "test.frog"
    bytecode_file = temp_dir / "test.frogc"

    source_file.write_text(code, encoding="utf-8")

    gradlew = PROJECT_ROOT / "gradlew"
    gradle_wrapper_jar = PROJECT_ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"

    use_wrapper = gradlew.exists() and gradle_wrapper_jar.exists() and os.access(str(gradlew), os.X_OK)
    wrapper_error = None

    if use_wrapper:
        try:
            v = subprocess.run(
                [str(gradlew), "--version"],
                cwd=PROJECT_ROOT,
                capture_output=True,
                text=True,
                timeout=10,
            )
            if v.returncode != 0:
                wrapper_error = v.stderr or v.stdout or "unknown wrapper error"
                use_wrapper = False
        except Exception as e:
            wrapper_error = str(e)
            use_wrapper = False

    if use_wrapper:
        gradle_cmd = [str(gradlew)]
    else:
        gradle_bin = shutil.which("gradle")
        if gradle_bin:
            gradle_cmd = [gradle_bin]
        else:
            msg = (
                "Compilation failed: gradle wrapper is missing or invalid and system 'gradle' is not installed.\n"
                "Wrapper check details: " + (wrapper_error if wrapper_error else "wrapper not present") + "\n"
                "Fixes: install Gradle, or generate/commit a valid gradle/wrapper/gradle-wrapper.jar and ensure './gradlew' is executable."
            )
            raise RuntimeError(msg)

    build_cmd = gradle_cmd + ["-q", "run", "--args", f"build {source_file} -o {bytecode_file}"]
    result = subprocess.run(
        build_cmd,
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True
    )
    if result.returncode != 0:
        raise RuntimeError(
            "Compilation failed\n"
            f"STDOUT:\n{result.stdout}\n"
            f"STDERR:\n{result.stderr}"
        )
    if not bytecode_file.exists():
        raise RuntimeError("Bytecode file was not created")

    disasm_cmd = gradle_cmd + ["-q", "run", "--args", f"disasm {bytecode_file}"]
    dres = subprocess.run(
        disasm_cmd,
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True
    )
    if dres.returncode != 0:
        raise RuntimeError(
            "Disasm failed\n"
            f"STDOUT:\n{dres.stdout}\n"
            f"STDERR:\n{dres.stderr}"
        )

    disasm_output = dres.stdout
    return bytecode_file, disasm_output

def execute_bytecode(bytecode_file):
    if not VM_EXECUTABLE.exists():
        raise RuntimeError(
            f"VM executable not found at {VM_EXECUTABLE}. "
            "Build it first: cd frogitovm && mkdir -p build && cd build && cmake .. && make"
        )
    result = subprocess.run(
        [str(VM_EXECUTABLE), "run", str(bytecode_file)],
        capture_output=True,
        text=True,
        timeout=5
    )
    return result.stdout, result.stderr, result.returncode


def test(name, func):
    global passed, failed
    try:
        func()
        print(f"[PASS] {name}")
        passed += 1
    except Exception as e:
        print(f"[FAIL] {name}: {str(e)[:200]}")
        failed += 1


def assert_bytecode_contains(disasm, *expected):
    for instr in expected:
        if instr not in disasm:
            raise AssertionError(f"Expected '{instr}' not found in:\n{disasm}")


def assert_bytecode_not_contains(disasm, *unexpected):
    for instr in unexpected:
        if instr in disasm:
            raise AssertionError(f"Unexpected '{instr}' found in:\n{disasm}")


def assert_bytecode_matches(disasm, pattern):
    if not re.search(pattern, disasm):
        raise AssertionError(f"Pattern '{pattern}' not found in:\n{disasm}")


def assert_bytecode_instruction_count(disasm, instruction, expected_count):
    count = len(re.findall(r"\b" + re.escape(instruction) + r"\b", disasm))
    if count != expected_count:
        raise AssertionError(f"Expected {expected_count} '{instruction}' but found {count}")


def assert_bytecode_size(bytecode_file, min_size=0, max_size=None):
    size = bytecode_file.stat().st_size
    if size < min_size:
        raise AssertionError(f"Bytecode too small: {size} < {min_size}")
    if max_size and size > max_size:
        raise AssertionError(f"Bytecode too large: {size} > {max_size}")


def assert_execution_success(bytecode):
    stdout, stderr, code = execute_bytecode(bytecode)
    if code != 0:
        raise AssertionError(f"VM execution failed with code {code}. stderr: {stderr}")
    return stdout


def assert_output_contains(bytecode, *expected_outputs):
    stdout = assert_execution_success(bytecode)
    for output in expected_outputs:
        if str(output) not in stdout:
            raise AssertionError(f"Expected output '{output}' not found. Got:\n{stdout}")


# Arithmetic
def test_simple_addition():
    code = "var int x = 5 + 3; print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_size(bytecode, min_size=10)
    assert_bytecode_contains(disasm, "PUSH_CONST INT(8)", "STORE_GLOBAL \"x\"")
    assert_bytecode_not_contains(disasm, "ADD")
    assert_output_contains(bytecode, "8")


def test_constant_folding():
    code = "var int x = (10 + 10 + 20 + 30); print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST INT(70)", "STORE_GLOBAL \"x\"")
    assert_bytecode_not_contains(disasm, "ADD")
    assert_output_contains(bytecode, "70")


def test_multiplication():
    code = "var int x = 7 * 8; print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST INT(56)")
    assert_bytecode_not_contains(disasm, "MUL")
    assert_output_contains(bytecode, "56")


def test_division():
    code = "var int x = 100 / 5; print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST INT(20)")
    assert_bytecode_not_contains(disasm, "DIV")
    assert_output_contains(bytecode, "20")


# Variables and booleans
def test_int_variable():
    code = "var int x = 42; print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST INT(42)", "STORE_GLOBAL \"x\"")
    assert_bytecode_instruction_count(disasm, "STORE_GLOBAL", 1)
    assert_output_contains(bytecode, "42")


def test_bool_variable():
    code = "var bool flag = true; print(flag);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(true)", "STORE_GLOBAL \"flag\"")
    assert_output_contains(bytecode, "true")


def test_bool_false():
    code = "var bool flag = false; print(flag);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(false)")
    assert_bytecode_not_contains(disasm, "BOOL(true)")
    assert_output_contains(bytecode, "false")


def test_multiple_variables():
    code = "var int x = 10; var int y = 20; var int z = x + y; print(z);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "STORE_GLOBAL \"x\"", "STORE_GLOBAL \"y\"", "STORE_GLOBAL \"z\"")
    assert_bytecode_instruction_count(disasm, "STORE_GLOBAL", 3)
    assert_bytecode_contains(disasm, "LOAD_GLOBAL \"x\"", "LOAD_GLOBAL \"y\"", "ADD")
    assert_output_contains(bytecode, "30")


# Comparisons
def test_greater_than():
    code = "print(10 > 3);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(true)")
    assert_output_contains(bytecode, "true")


def test_less_than():
    code = "print(3 < 10);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(true)")
    assert_output_contains(bytecode, "true")


def test_equality_true():
    code = "print(5 == 5);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(true)")
    assert_output_contains(bytecode, "true")


def test_equality_false():
    code = "print(5 == 3);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(false)")
    assert_output_contains(bytecode, "false")


# Control flow
def test_if_statement():
    code = "var int x = 5; if (x > 0) { x = 10; } print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "GT", "JUMP_FALSE", "PUSH_CONST INT(10)")
    assert_output_contains(bytecode, "10")


def test_if_else_statement():
    code = "var int x = -5; if (x > 0) { x = 10; } else { x = 0; } print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "JUMP_FALSE", "JUMP")
    assert_output_contains(bytecode, "0")


def test_while_loop():
    code = "var int i = 0; var int s = 0; while (i < 4) { s = s + i; i = i + 1; } print(s);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "JUMP_FALSE", "JUMP", "ADD")
    assert_output_contains(bytecode, "6")


def test_for_loop():
    code = "var int sum = 0; for (var int i = 0; i < 5; i = i + 1) { sum = sum + i; } print(sum);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "JUMP_FALSE", "ADD", "JUMP")
    assert_output_contains(bytecode, "10")


# Arrays
def test_array_creation_and_access():
    code = "var array<int> arr = {1, 2, 3}; print(arr[1]);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "NEW_ARRAY 3", "LOAD_INDEX")
    assert_output_contains(bytecode, "2")


def test_array_assignment():
    code = "var array<int> arr = {1, 2, 3}; arr[0] = 100; print(arr[0]);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "STORE_INDEX", "LOAD_INDEX")
    assert_output_contains(bytecode, "100")


# Functions
def test_simple_function():
    code = "func int add(int a, int b) { return a + b; } print(add(5, 3));"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "CALL add@", "ADD", "RET")
    assert_output_contains(bytecode, "8")


def test_recursive_function():
    code = "func int fact(int n) { if (n <= 1) return 1; return n * fact(n - 1); } print(fact(5));"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "CALL fact@", "MUL", "RET")
    assert_output_contains(bytecode, "120")


def test_void_function():
    code = "func void noop(int x) { return; } noop(42); print(1);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "CALL noop@", "RET")
    assert_output_contains(bytecode, "1")


# Nested and complex
def test_nested_loops():
    code = "var int sum = 0; for (var int i = 0; i < 3; i = i + 1) { for (var int j = 0; j < 3; j = j + 1) { sum = sum + 1; } } print(sum);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "JUMP_FALSE")
    assert_bytecode_instruction_count(disasm, "JUMP_FALSE", 2)
    assert_output_contains(bytecode, "9")


def test_nested_arrays():
    code = "var array<array<int>> matrix = {{1, 2}, {3, 4}}; print(matrix[1][0]);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "NEW_ARRAY", "LOAD_INDEX")
    assert_output_contains(bytecode, "3")


def test_complex_expression():
    code = "var int a = 5; var int b = 10; var int c = (a + b) * 2 - a / 2; print(c);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "ADD", "MUL", "SUB", "DIV")
    assert_output_contains(bytecode, "28")


def test_break_statement():
    code = "var int i = 0; while (i < 10) { if (i == 5) break; i = i + 1; } print(i);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "JUMP_FALSE", "JUMP")
    assert_output_contains(bytecode, "5")


def test_continue_statement():
    code = "var int i = 0; var int s = 0; while (i < 5) { i = i + 1; if (i == 2) continue; s = s + i; } print(s);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "JUMP_FALSE", "JUMP")
    assert_output_contains(bytecode, "13")


# Print builtins
def test_print_function():
    code = "print(42);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "CALL print@")
    assert_output_contains(bytecode, "42")


def test_print_variable():
    code = "var int x = 99; print(x);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "CALL print@", "LOAD_GLOBAL \"x\"")
    assert_output_contains(bytecode, "99")


def test_print_expression():
    code = "print(10 + 5);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "CALL print@", "PUSH_CONST INT(15)")
    assert_output_contains(bytecode, "15")


def test_multiple_operations():
    code = "var int x = 5; var int y = x + 3; var int z = y * 2; print(z);"
    bytecode, disasm = compile_code(code)
    assert_bytecode_instruction_count(disasm, "STORE_GLOBAL", 3)
    assert_bytecode_instruction_count(disasm, "LOAD_GLOBAL", 3)
    assert_bytecode_contains(disasm, "ADD", "MUL", "CALL print@")
    assert_output_contains(bytecode, "16")


def test_comparison_operations():
    code = "print((10 > 5) && (3 < 8) && (5 == 5));"
    bytecode, disasm = compile_code(code)
    assert_bytecode_contains(disasm, "PUSH_CONST BOOL(true)")
    assert_output_contains(bytecode, "true")


if __name__ == "__main__":
    print("=" * 70)
    print("FROGITO COMPILER FULL E2E TEST SUITE")
    print("=" * 70 + "\n")
    
    tests = [
        test_simple_addition,
        test_constant_folding,
        test_multiplication,
        test_division,
        test_int_variable,
        test_bool_variable,
        test_bool_false,
        test_multiple_variables,
        test_greater_than,
        test_less_than,
        test_equality_true,
        test_equality_false,
        test_if_statement,
        test_if_else_statement,
        test_while_loop,
        test_for_loop,
        test_array_creation_and_access,
        test_array_assignment,
        test_simple_function,
        test_recursive_function,
        test_void_function,
        test_nested_loops,
        test_nested_arrays,
        test_complex_expression,
        test_break_statement,
        test_continue_statement,
        test_print_function,
        test_print_variable,
        test_print_expression,
        test_multiple_operations,
        test_comparison_operations,
    ]

    for t in tests:
        test(t.__name__, t)
    
    print("\n" + "=" * 70)
    print(f"RESULTS: {passed} passed, {failed} failed out of {passed + failed} tests")
    print("=" * 70)
    
    if failed == 0:
        print("ALL E2E TESTS PASSED!")
    
    exit(0 if failed == 0 else 1)



