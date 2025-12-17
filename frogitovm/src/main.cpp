#include <exception>
#include <filesystem>
#include <iostream>
#include <string_view>

#include "bytecode/bytecode_loader.h"
#include "runtime/errors.h"
#include "vm/vm.h"

namespace {

void PrintUsage() {
    std::cerr << "usage: frogvm run <file.frogc> [--trace] [--jit-log] [--gc-log]\n";
}

bool IsFlag(std::string_view s, std::string_view flag) {
    return s == flag;
}

}  // namespace

int main(int argc, char** argv) {
    try {
        if (argc < 3) {
            PrintUsage();
            return 1;
        }

        std::string_view cmd = argv[1];
        if (cmd != "run") {
            PrintUsage();
            return 1;
        }

        std::filesystem::path file_path = argv[2];
        VmOptions opts;

        for (int i = 3; i < argc; ++i) {
            std::string_view a = argv[i];
            if (IsFlag(a, "--trace")) {
                opts.trace = true;
                continue;
            }
            if (IsFlag(a, "--jit-log")) {
                opts.jit_log = true;
                continue;
            }
            if (IsFlag(a, "--gc-log")) {
                opts.gc_log = true;
                continue;
            }
            std::cerr << "unknown flag: " << a << "\n";
            PrintUsage();
            return 1;
        }

        BytecodeModule module = load_frogc(file_path);
        Vm vm(module, opts);
        return vm.run();
    } catch (const LoadError& e) {
        std::cerr << "load error: " << e.what() << "\n";
        return 1;
    } catch (const RuntimeError& e) {
        std::cerr << "runtime error: " << e.what() << "\n";
        return 1;
    } catch (const std::exception& e) {
        std::cerr << "runtime error: " << e.what() << "\n";
        return 1;
    }
}
