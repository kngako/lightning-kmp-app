package fr.acinq.phoenix.managers

import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationFilesDirectoryPath
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

actual fun computePreferencePath(
    platformContext: PlatformContext,
    dataStoreFileName: String,
): Path = File(getApplicationFilesDirectoryPath(platformContext), dataStoreFileName).toOkioPath()
