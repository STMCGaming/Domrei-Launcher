package com.domrei.gameengine.runtime

import com.domrei.gameengine.MinecraftLaunchSpec
import com.sun.jna.JNIEnv
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.io.IOException

/**
 * Clean-room in-process OpenJDK launcher using JNA (`dlopen` + `JLI_Launch`).
 *
 * On Android 10+ (`targetSdk >= 29`), SELinux blocks `execve()` (`ProcessBuilder`) on binaries
 * inside `/data/data/<pkg>/files/` with `errno=13 (Permission denied)`.
 * Loading `libjli.so` and `libjvm.so` via `dlopen(..., RTLD_GLOBAL)` and invoking `JLI_Launch`
 * executes OpenJDK via `mmap(PROT_EXEC)`, which is permitted on all Android versions (8 through 15+).
 */
object DomreiJliRunner {

    private interface PosixLibC : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int
        fun unsetenv(name: String): Int
        fun chdir(path: String): Int
        fun dup2(oldfd: Int, newfd: Int): Int
        fun open(path: String, flags: Int, mode: Int): Int
        fun close(fd: Int): Int
        fun mallopt(param: Int, value: Int): Int
    }

    private interface PosixLibDl : Library {
        fun dlopen(filename: String?, flags: Int): Pointer?
        fun dlerror(): String?
    }

    private interface AndroidNativeWindowLib : Library {
        fun ANativeWindow_fromSurface(env: JNIEnv, surface: Any): Pointer?
        fun ANativeWindow_release(window: Pointer)
        fun ANativeWindow_getWidth(window: Pointer): Int
        fun ANativeWindow_getHeight(window: Pointer): Int
    }

    // POSIX open() flags on Linux/Android (O_WRONLY | O_CREAT | O_APPEND)
    private const val O_WRONLY = 1
    private const val O_CREAT = 64
    private const val O_APPEND = 1024

    // Android Bionic <malloc.h>: M_BIONIC_SET_HEAP_TAGGING_LEVEL = -204 (0 = M_HEAP_TAGGING_LEVEL_NONE)
    // Disables ARM64 TBI pointer tag checks so OpenJDK HotSpot (libjvm.so) does not trigger
    // "Pointer tag for 0x... was truncated" SIGABRT when manipulating upper pointer bits.
    private const val M_BIONIC_SET_HEAP_TAGGING_LEVEL = -204
    private const val M_HEAP_TAGGING_LEVEL_NONE = 0

    // Bionic dlfcn.h flags:
    // On 64-bit Android (__LP64__): RTLD_LAZY = 1, RTLD_NOW = 2, RTLD_GLOBAL = 0x00100 (256)
    // On 32-bit Android (__ILP32__): RTLD_LAZY = 1, RTLD_NOW = 0, RTLD_GLOBAL = 0x00002 (2)
    private val RTLD_LAZY = 0x00001
    private val RTLD_GLOBAL: Int
        get() = if (Native.POINTER_SIZE == 8) 0x00100 else 0x00002

    private val PRELOAD_ORDER = listOf(
        "libjli.so",
        "libjvm.so",
        "libverify.so",
        "libjava.so",
        "libzip.so",
        "libnet.so",
        "libnio.so",
        "libjimage.so",
        "libawt.so",
        "libawt_headless.so",
        "libfreetype.so",
        "libfontmanager.so",
        "liblcms.so",
        "libjavajpeg.so",
        "libinstrument.so",
        "libmanagement.so",
        "libmanagement_ext.so",
        "libprefs.so"
    )

    /**
     * Marks all `.so` and binary files in `dir` as read+execute and non-writable (`0555`),
     * satisfying Android 14+ (`targetSdk >= 34`) Dynamic Code Loading security checks.
     */
    fun enforceReadOnlyExecutablePermissions(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".so") || file.parentFile?.name == "bin")) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
                file.setWritable(false, false)
            }
        }
    }

    /**
     * Restores write permissions on `dir` before deleting or reinstalling a runtime/renderer.
     */
    fun restoreWritablePermissions(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().forEach { file ->
            file.setWritable(true, true)
        }
    }

    /**
     * Obtains the native `ANativeWindow*` pointer from an `android.view.Surface` in the current process.
     */
    fun acquireNativeWindowPointer(surface: Any): Long {
        return runCatching {
            val androidLib = Native.load(
                "android",
                AndroidNativeWindowLib::class.java,
                mapOf(Library.OPTION_ALLOW_OBJECTS to java.lang.Boolean.TRUE)
            )
            val ptr = androidLib.ANativeWindow_fromSurface(JNIEnv.CURRENT, surface)
            if (ptr != null) Pointer.nativeValue(ptr) else 0L
        }.getOrDefault(0L)
    }

    /**
     * Releases a previously acquired `ANativeWindow*` pointer.
     */
    fun releaseNativeWindowPointer(nativeWindowPtr: Long) {
        if (nativeWindowPtr == 0L) return
        runCatching {
            val androidLib = Native.load("android", AndroidNativeWindowLib::class.java)
            androidLib.ANativeWindow_release(Pointer(nativeWindowPtr))
        }
    }

    /**
     * Redirects native stdout (fd 1) and stderr (fd 2) to `logFile`, sets process environment
     * variables and working directory, preloads OpenJDK + renderer `.so` files with `RTLD_GLOBAL`,
     * and invokes `JLI_Launch` in `libjli.so`.
     */
    fun launchInCurrentProcess(
        spec: MinecraftLaunchSpec,
        logFile: File,
        nativeWindowPtr: Long = 0L,
        onDiagnosticLog: (String) -> Unit = {}
    ): Int {
        val libc = Native.load("c", PosixLibC::class.java)
        val libdl = Native.load("dl", PosixLibDl::class.java)

        logFile.parentFile?.mkdirs()
        val logFd = libc.open(logFile.absolutePath, O_WRONLY or O_CREAT or O_APPEND, 420 /* 0644 */)
        if (logFd >= 0) {
            libc.dup2(logFd, 1)
            libc.dup2(logFd, 2)
            if (logFd > 2) {
                libc.close(logFd)
            }
        }

        // Disable Android Bionic ARM64 TBI heap pointer tagging so OpenJDK HotSpot (libjvm.so)
        // does not trigger "Pointer tag for 0x... was truncated" when masking upper pointer bits.
        runCatching {
            libc.mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE)
        }

        // Enforce Android 14/15 DCL read-only permissions on runtime, renderer & native .so files
        enforceReadOnlyExecutablePermissions(spec.runtime.execHomeDir)
        enforceReadOnlyExecutablePermissions(spec.renderer.nativeLibDir)
        enforceReadOnlyExecutablePermissions(spec.nativesDir)

        // Apply environment variables & working directory.
        // IMPORTANT: Do NOT set LD_LIBRARY_PATH when invoking JLI_Launch in-process!
        // OpenJDK's CreateExecutionEnvironment (java_md_solinux.c) checks getenv("LD_LIBRARY_PATH");
        // if LD_LIBRARY_PATH is set and does not match its internal jvmpath prefix, libjli.so attempts
        // to re-execve("/data/user/0/.../bin/java"), which Android SELinux blocks with EACCES (Permission denied).
        // When LD_LIBRARY_PATH is unset (NULL), CreateExecutionEnvironment skips execve and calls LoadJavaVM directly.
        spec.environment.forEach { (key, value) ->
            if (key != "LD_LIBRARY_PATH") {
                libc.setenv(key, value, 1)
            }
        }
        libc.unsetenv("LD_LIBRARY_PATH")
        libc.setenv("_JAVA_VERSION_SET", "1", 1)
        if (nativeWindowPtr != 0L) {
            libc.setenv("DOMREI_NATIVE_WINDOW", nativeWindowPtr.toString(), 1)
        }
        libc.chdir(spec.gameDir.absolutePath)

        // Preload OpenJDK shared libraries in dependency order with RTLD_GLOBAL
        val flags = RTLD_LAZY or RTLD_GLOBAL
        for (libName in PRELOAD_ORDER) {
            val candidate = spec.runtime.libraryDirs
                .map { File(it, libName) }
                .firstOrNull { it.isFile }
            if (candidate != null) {
                val handle = libdl.dlopen(candidate.absolutePath, flags)
                if (handle == null) {
                    onDiagnosticLog("[DomreiJLI] Note: dlopen(${candidate.name}) -> ${libdl.dlerror()}")
                }
            }
        }

        // Preload remaining .so libraries in the JRE library directories
        spec.runtime.libraryDirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".so") && file.name !in PRELOAD_ORDER) {
                    libdl.dlopen(file.absolutePath, flags)
                }
            }
        }

        // Preload active renderer library (libmobileglues.so or libng_gl4es.so) and auxiliary natives
        if (spec.renderer.nativeLibFile.isFile) {
            val rendererHandle = libdl.dlopen(spec.renderer.nativeLibFile.absolutePath, flags)
            if (rendererHandle != null) {
                onDiagnosticLog("[DomreiJLI] Loaded renderer ${spec.renderer.nativeLibFile.name} via RTLD_GLOBAL")
            } else {
                onDiagnosticLog("[DomreiJLI] Renderer dlopen note: ${libdl.dlerror()}")
            }
        }
        spec.renderer.nativeLibDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".so") && file != spec.renderer.nativeLibFile) {
                libdl.dlopen(file.absolutePath, flags)
            }
        }
        spec.nativesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".so")) {
                libdl.dlopen(file.absolutePath, flags)
            }
        }

        val libJliFile = spec.runtime.libJliSo
            ?: spec.runtime.libraryDirs.map { File(it, "libjli.so") }.firstOrNull { it.isFile }
            ?: throw IOException("libjli.so not found in ${spec.runtime.execHomeDir.absolutePath}")

        val commandArgs = if (nativeWindowPtr != 0L) {
            buildList {
                add(spec.commandLine.first())
                add("-Ddomrei.egl.nativeWindow=$nativeWindowPtr")
                addAll(spec.commandLine.drop(1))
            }
        } else {
            spec.commandLine
        }
        val argsArray = commandArgs.toTypedArray()
        onDiagnosticLog("[DomreiJLI] Invoking JLI_Launch with ${argsArray.size} args from ${libJliFile.absolutePath}")

        val jliLibrary = NativeLibrary.getInstance(libJliFile.absolutePath)
        val jliLaunchFn = jliLibrary.getFunction("JLI_Launch")

        return jliLaunchFn.invokeInt(
            arrayOf(
                argsArray.size,
                argsArray,
                0,
                null,
                0,
                null,
                "${spec.runtime.version.major}.0.0-internal",
                "${spec.runtime.version.major}.0",
                "java",
                "openjdk",
                0.toByte(),
                1.toByte(),
                0.toByte(),
                0
            )
        )
    }
}
