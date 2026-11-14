package org.lwjgl.opengl;

import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.SharedLibrary;

/**
 * Domrei clean-room implementation of {@code PojavRendererInit} for Android LWJGL 3.3.3.
 * <p>
 * Upstream Android LWJGL builds call {@link #onCreateCapabilities(FunctionProvider)} during
 * {@link GLCapabilities} construction and invoke {@link #nativeInitGl4esInternals(FunctionProvider)}
 * whenever the OpenGL library name ends with {@code libgl4es_114.so} or {@code libng_gl4es.so}
 * (Krypton Wrapper).
 * <p>
 * Domrei launches OpenJDK via {@code DomreiJliRunner} and manages EGL/GLES in {@code GLFW.java}
 * without bundling {@code libpojavexec.so}, while {@code libng_gl4es.so} loads {@code libGLESv3.so}
 * and {@code libEGL.so} directly and resolves symbols via {@code dlsym} and {@code eglGetProcAddress}.
 * Providing a Java implementation prevents {@link UnsatisfiedLinkError} while preserving binary
 * compatibility with all callers in {@code GLCapabilities}.
 */
public class PojavRendererInit {
    private static volatile boolean sLoggedInit = false;

    public static void onCreateCapabilities(FunctionProvider functionProvider) {
        String libname = null;
        if (functionProvider instanceof SharedLibrary) {
            libname = ((SharedLibrary) functionProvider).getName();
        }
        if (!isValidString(libname)) {
            libname = System.getProperty("org.lwjgl.opengl.libname");
        }
        if (!isValidString(libname)) {
            System.out.println("PojavRendererInit: Failed to find Pojav renderer name! Renderer-specific initialization may not work properly");
            return;
        }
        if (libname.endsWith("libgl4es_114.so") || libname.endsWith("libng_gl4es.so")) {
            nativeInitGl4esInternals(functionProvider);
        }
    }

    private static boolean isValidString(String value) {
        return value != null && !value.isEmpty();
    }

    public static void nativeInitGl4esInternals(FunctionProvider functionProvider) {
        if (functionProvider == null) {
            return;
        }
        long getProcAddress = functionProvider.getFunctionAddress("gl4es_GetProcAddress");
        if (!sLoggedInit) {
            sLoggedInit = true;
            String libName = (functionProvider instanceof SharedLibrary)
                ? ((SharedLibrary) functionProvider).getName()
                : System.getProperty("org.lwjgl.opengl.libname", "unknown");
            System.out.println(
                "[DomreiGL] Initialized GL4ES capabilities bridge for " + libName +
                " (gl4es_GetProcAddress=0x" + Long.toHexString(getProcAddress) + ")"
            );
        }
    }
}
