package fr.acinq.phoenix.managers

import fr.acinq.phoenix.utils.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

actual fun computePreferencePath(
    platformContext: PlatformContext,
    dataStoreFileName: String,
): Path {
    return platformContext.applicationContext.filesDir.resolve(
        relative = dataStoreFileName
    ).toOkioPath()
}