package fr.acinq.phoenix.managers

import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun computePreferencePath(
    platformContext: PlatformContext,
    dataStoreFileName: String
): Path {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val path = requireNotNull(documentDirectory).path + "${Path.DIRECTORY_SEPARATOR}$dataStoreFileName"

    return path.toPath()
}