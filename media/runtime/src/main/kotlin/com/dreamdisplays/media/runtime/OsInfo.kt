package com.dreamdisplays.media.runtime

import java.util.*

/**
 * Single source of truth for OS / architecture detection.
 */
object OsInfo {
    private val os: String = System.getProperty("os.name", "").lowercase(Locale.ENGLISH)
    private val arch: String = System.getProperty("os.arch", "").lowercase(Locale.ENGLISH)

    val isWindows: Boolean = "win" in os
    val isMac: Boolean = "mac" in os

    /**
     * True when running on Android (ART/Dalvik). PojavLauncher-style OpenJDK installs report a plain
     * `Linux` `os.name`, so this also honors an explicit `dreamdisplays.isAndroid` override.
     */
    val isAndroid: Boolean =
        "android" in os
            || "android" in System.getProperty("java.runtime.name", "").lowercase(Locale.ENGLISH)
            || System.getProperty("java.vm.name", "").lowercase(Locale.ENGLISH).contains("dalvik")
            || System.getProperty("dreamdisplays.isAndroid", "").equals("true", ignoreCase = true)

    /** True on any 64-bit or 32-bit ARM architecture (aarch64, arm64, armv7, ...). */
    val isArm: Boolean = "aarch64" in arch || "arm64" in arch || "arm" in arch

    /** True specifically on 64-bit ARM. */
    val isArm64: Boolean = "aarch64" in arch || "arm64" in arch

    /** True on 64-bit x86 (x86_64/amd64). */
    val isX86_64: Boolean = "x86_64" in arch || "amd64" in arch

    /** True on 32-bit x86 (i386/i486/i586/i686/x86). */
    val isX86: Boolean = "x86" in arch && !isX86_64
}
