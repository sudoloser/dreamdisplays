import java.io.File

/** Native platform keys, e.g. `linux-x64`, `macos-x64`, `windows-x64`. */
val nativePlatformKeys = listOf(
    "linux-x64",
    "linux-aarch64",
    "macos-x64",
    "macos-aarch64",
    "windows-x64",
    "windows-aarch64",
    "android-arm",
    "android-aarch64",
    "android-x86",
    "android-x64",
)

/** Android native platform keys, one per Android ABI (armeabi-v7a, arm64-v8a, x86, x86_64). */
val androidNativePlatformKeys = listOf(
    "android-arm",
    "android-aarch64",
    "android-x86",
    "android-x64",
)

/** True when the platform key is an Android ABI. */
fun isAndroidPlatformKey(platformKey: String): Boolean = platformKey in androidNativePlatformKeys

/** Native library base names. */
val nativeLibraryBaseNames = listOf(
    "dreamdisplays_native",
    "dreamdisplays_lav",
)

/**
 * Native libraries required for a platform. Every platform bundles the full native pipeline
 * (`dreamdisplays_native`) plus the in-process libav backend (`dreamdisplays_lav`), which links
 * the FFmpeg shared libraries the bundle also carries: Android ships cross-compiled FFmpeg
 * SONAME libs, while desktop / macOS get them from the runtime downloader. Android additionally
 * bundles a prebuilt `sqlitejdbc` native, since sqlite-jdbc no longer ships Android binaries itself.
 */
fun requiredNativeLibrariesFor(platformKey: String): List<String> =
    if (isAndroidPlatformKey(platformKey)) nativeLibraryBaseNames + "sqlitejdbc"
    else nativeLibraryBaseNames

/** Set the native library name based on the platform key. */
fun nativeLibraryName(platformKey: String, baseName: String): String = when {
    platformKey.startsWith("windows-") -> "$baseName.dll"
    platformKey.startsWith("macos-") -> "lib$baseName.dylib"
    else -> "lib$baseName.so"
}

/** Host native key based on the current OS and architecture. */
fun hostNativeKey(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("win") -> if (arch.contains("aarch64") || arch.contains("arm")) "windows-aarch64" else "windows-x64"
        os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm")) "macos-aarch64" else "macos-x64"
        else -> if (arch.contains("aarch64") || arch.contains("arm")) "linux-aarch64" else "linux-x64"
    }
}

/** Convert a string to a strict boolean. */
fun String.toStrictBoolean(): Boolean = equals("true", ignoreCase = true)

/** True when a cargo executable is available (default rustup location, or anywhere on PATH). */
fun cargoAvailable(): Boolean {
    if (File(System.getProperty("user.home"), ".cargo/bin/cargo").canExecute()) return true
    val path = System.getenv("PATH") ?: return false
    return path.split(File.pathSeparator).any {
        File(it, "cargo").canExecute() || File(it, "cargo.exe").canExecute()
    }
}

/** Convert a string to a list of platform keys. */
fun String.toPlatformList(): List<String> =
    split(',', ' ', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
