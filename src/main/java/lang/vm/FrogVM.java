package lang.vm;

import java.io.IOException;
import java.nio.file.Path;

public class FrogVM {
    static {
        try {
            System.loadLibrary("frogitovm_jni");
        } catch (UnsatisfiedLinkError e) {
            throw new ExceptionInInitializerError(
                "Failed to load frogitovm_jni library. " +
                "Make sure to build the C++ VM with JNI support: " +
                "cd frogitovm && cmake -DBUILD_JNI=ON . && make"
            );
        }
    }

    private long vmHandle = 0;
    private boolean isRunning = false;

    public void load(Path bytecodeFile) throws IOException {
        if (isRunning) {
            throw new IllegalStateException("VM is already running");
        }
        vmHandle = loadBytecode(bytecodeFile.toString());
    }

    public int execute() {
        if (vmHandle == 0) {
            throw new IllegalStateException("No bytecode loaded. Call load() first.");
        }
        if (isRunning) {
            throw new IllegalStateException("VM is already running");
        }
        
        isRunning = true;
        try {
            return executeBytecode(vmHandle);
        } finally {
            isRunning = false;
        }
    }

    public int run(Path bytecodeFile) throws IOException {
        load(bytecodeFile);
        return execute();
    }

    public void close() {
        if (vmHandle != 0) {
            cleanup(vmHandle);
            vmHandle = 0;
        }
    }

    public static native String getVersion();

    private native long loadBytecode(String path);
    private native int executeBytecode(long vmHandle);
    private native void cleanup(long vmHandle);
}
