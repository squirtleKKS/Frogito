import subprocess
import tempfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
VM_EXECUTABLE = PROJECT_ROOT / "frogitovm" / "build" / "frogvm"


class Benchmark:
    def __init__(self, name, code_generator, validator):
        self.name = name
        self.code_generator = code_generator
        self.validator = validator

    def run(self):
        code = self.code_generator()
        bytecode_file = self._compile(code)
        stdout = self._execute(bytecode_file)
        self.validator(stdout)

    def _compile(self, code):
        temp_dir = tempfile.mkdtemp()
        bytecode_file = Path(temp_dir) / "test.frogc"
        
        code_arg = code.replace('"', '\\"').replace('$', '\\$')
        result = subprocess.run(
            ["bash", "-c", f'./gradlew run --args "\'{code_arg}\' {bytecode_file}"'],
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            raise RuntimeError(f"Compilation failed: {result.stderr}")
        if not bytecode_file.exists():
            raise RuntimeError("Bytecode file not created")
        
        return bytecode_file

    def _execute(self, bytecode_file):
        if not VM_EXECUTABLE.exists():
            raise RuntimeError(f"VM not found at {VM_EXECUTABLE}")
        
        result = subprocess.run(
            [str(VM_EXECUTABLE), "run", str(bytecode_file)],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode != 0:
            raise RuntimeError(f"Execution failed: {result.stderr}")
        
        return result.stdout


def factorial_20_gen():
    return """func int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

print(factorial(20));"""


def factorial_20_val(stdout):
    if "2432902008176640000" not in stdout:
        raise AssertionError(f"Expected 2432902008176640000, got: {stdout}")


def sorting_gen():
    array = list(range(10000, 0, -1))
    array_elements = ", ".join(str(i) for i in array)
    
    return f"""func void quicksort(array<int> arr, int low, int high) {{
    if (low < high) {{
        var int pivot = arr[high];
        var int i = low - 1;
        var int j = low;
        while (j < high) {{
            if (arr[j] < pivot) {{
                i = i + 1;
                var int temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }}
            j = j + 1;
        }}
        var int temp2 = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp2;
        var int pi = i + 1;
        quicksort(arr, low, pi - 1);
        quicksort(arr, pi + 1, high);
    }}
}}

var array<int> data = {{{array_elements}}};
quicksort(data, 0, 9999);
var int i = 0;
while (i < 100) {{
    print(data[i]);
    i = i + 1;
}}"""


def sorting_val(stdout):
    sorted_array = sorted(range(10000, 0, -1))
    output_lines = stdout.strip().split('\n')
    
    if len(output_lines) != 100:
        raise AssertionError(f"Expected 10000 lines, got {len(output_lines)}")
    
    for i, expected in enumerate(sorted_array[:100]):
        if str(expected) not in output_lines[i]:
            raise AssertionError(f"Position {i}: expected {expected}, got {output_lines[i]}")


def primes_gen():
    return """func int count_primes_up_to(int limit) {
    var int count = 0;
    var int n = 2;
    while (n < limit) {
        var bool is_prime = true;
        var int d = 2;
        while (d * d <= n) {
            if (n % d == 0) {
                is_prime = false;
            }
            d = d + 1;
        }
        if (is_prime) {
            count = count + 1;
        }
        n = n + 1;
    }
    return count;
}

print(count_primes_up_to(100000));"""


def primes_val(stdout):
    if "9592" not in stdout:
        raise AssertionError(f"Expected 9592, got: {stdout}")
