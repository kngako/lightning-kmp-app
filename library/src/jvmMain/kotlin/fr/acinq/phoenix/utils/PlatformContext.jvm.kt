package fr.acinq.phoenix.utils

import java.io.File
import java.util.Locale

/**
 * Android wraps a `Context` here and ios is all but empty, because both platforms hand
 * the application a sandbox and the four paths below are read straight out of it.
 *
 * The jvm has neither a sandbox nor any ambient application identity, so the location
 * has to be carried explicitly. An embedding application passes its own directory --
 * which is also what makes a scratch directory per test possible, and a second profile
 * on one machine possible later. The default exists only so that library-internal code
 * can build a context without ceremony.
 */
actual class PlatformContext(
    val applicationDir: File = defaultApplicationDir(),
)

/**
 * Created on demand rather than in the constructor: on android and ios these directories
 * already exist inside the sandbox, and callers are entitled to assume the same here
 * rather than each remembering to mkdirs first.
 */
private fun PlatformContext.subdirectory(name: String): String =
    File(applicationDir, name).apply { mkdirs() }.absolutePath

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    ctx.subdirectory("files")

/**
 * Android returns null and only `DbFactory.ios.kt` reads it, so nothing today depends on
 * the answer. A real directory is still the right one for the jvm: the DbFactory actual
 * has to put the SQLDelight files somewhere, and the alternative is each driver inventing
 * its own location.
 */
actual fun getDatabaseFilesDirectoryPath(ctx: PlatformContext): String? =
    ctx.subdirectory("databases")

actual fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String =
    ctx.subdirectory("cache")

/** The cache directory, which is what android does with `cacheDir` for both of these. */
actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    ctx.subdirectory("cache")

/**
 * The conventional per-user application data directory for the host os.
 *
 * Deliberately not `java.io.tmpdir`. The node seed lives under this -- `SeedManager`
 * resolves "node-data" against [getApplicationFilesDirectoryPath] -- and most systems
 * clear the temporary directory on reboot.
 */
private fun defaultApplicationDir(): File {
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    return when {
        os.contains("win") ->
            (System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA"))
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it, APPLICATION_DIR_NAME) }
                ?: File(home, "AppData/Local/$APPLICATION_DIR_NAME")

        os.contains("mac") || os.contains("darwin") ->
            File(home, "Library/Application Support/$APPLICATION_DIR_NAME")

        else ->
            System.getenv("XDG_DATA_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it, APPLICATION_DIR_NAME) }
                ?: File(home, ".local/share/$APPLICATION_DIR_NAME")
    }
}

private const val APPLICATION_DIR_NAME = "phoenix"
