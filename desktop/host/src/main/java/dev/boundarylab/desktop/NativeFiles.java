package dev.boundarylab.desktop;

import java.io.IOException;

/** Internal POSIX bridge. Every successful open has one Kotlin close owner. */
final class NativeFiles {
    static {
        String explicit = System.getProperty("boundarylab.native.library");
        if (explicit == null) System.loadLibrary("boundarylab_files");
        else System.load(explicit);
    }
    private NativeFiles() {}
    static native int openRoot(String root) throws IOException;
    static native int openAlias(int rootFd, String alias) throws IOException;
    static native long[] snapshot(int fd) throws IOException;
    static native int pread(int fd, byte[] buffer, int bufferOffset, int length, long offset) throws IOException;
    static native boolean revalidate(int rootFd, String alias, int fd) throws IOException;
    static native void close(int fd) throws IOException;
}
