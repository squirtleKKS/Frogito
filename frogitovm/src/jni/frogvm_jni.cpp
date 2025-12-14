#include <jni.h>
#include <memory>
#include <stdexcept>
#include <string>

#include "bytecode/bytecode_loader.h"
#include "vm/vm.h"

struct VmWrapper {
    std::unique_ptr<BytecodeModule> module;
    std::unique_ptr<Vm> vm;
};

std::string JStringToString(JNIEnv* env, jstring jstr) {
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string result(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return result;
}

void ThrowJavaException(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("java/io/IOException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

extern "C" {

JNIEXPORT jstring JNICALL Java_lang_vm_FrogVM_getVersion(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF("FrogitoVM 1.0 (JNI)");
}

JNIEXPORT jlong JNICALL Java_lang_vm_FrogVM_loadBytecode(JNIEnv* env, jobject obj, jstring jpath) {
    try {
        std::string path = JStringToString(env, jpath);
        auto wrapper = new VmWrapper();
        
        try {
            wrapper->module = std::make_unique<BytecodeModule>(load_frogc(path));
        } catch (const std::exception& e) {
            delete wrapper;
            ThrowJavaException(env, std::string("Failed to load bytecode: ") + e.what());
            return 0;
        }
        
        return reinterpret_cast<jlong>(wrapper);
    } catch (const std::exception& e) {
        ThrowJavaException(env, std::string("Error loading bytecode: ") + e.what());
        return 0;
    }
}

JNIEXPORT jint JNICALL Java_lang_vm_FrogVM_executeBytecode(JNIEnv* env, jobject obj, jlong vmHandle) {
    if (vmHandle == 0) {
        ThrowJavaException(env, "Invalid VM handle");
        return 1;
    }
    
    try {
        VmWrapper* wrapper = reinterpret_cast<VmWrapper*>(vmHandle);
        
        VmOptions options;
        options.trace = false;
        options.jit_log = false;
        options.gc_log = false;
        
        wrapper->vm = std::make_unique<Vm>(*wrapper->module, options);
        int result = wrapper->vm->run();
        
        return result;
    } catch (const std::exception& e) {
        ThrowJavaException(env, std::string("VM execution error: ") + e.what());
        return 1;
    }
}

JNIEXPORT void JNICALL Java_lang_vm_FrogVM_cleanup(JNIEnv* env, jobject obj, jlong vmHandle) {
    if (vmHandle != 0) {
        VmWrapper* wrapper = reinterpret_cast<VmWrapper*>(vmHandle);
        delete wrapper;
    }
}

}
