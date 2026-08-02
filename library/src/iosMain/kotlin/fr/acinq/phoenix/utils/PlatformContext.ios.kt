package fr.acinq.phoenix.utils

import co.touchlab.kermit.Severity
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

actual class PlatformContext(
    val logger: ((Severity, String, String) -> Unit)? = null
)

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)[0] as String

actual fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)[0] as String

actual fun getDatabaseFilesDirectoryPath(ctx: PlatformContext): String? {
    return NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(
        groupIdentifier = "group.co.acinq.phoenix"
    )?.URLByAppendingPathComponent(
        pathComponent = "databases",
        isDirectory = true
    )?.path
}

actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    NSTemporaryDirectory()
