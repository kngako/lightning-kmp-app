package fr.acinq.phoenix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.compose.AppVersion
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream


object ContactsPhotoHelper {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun contactsDir(context: Context) = File(context.filesDir, "contacts")

    /** Creates a temporary file where the camera activity will store the picture in. File is deleted on app exit. */
    fun createTempContactPictureUri(
        context: Context,
    ): Uri? {
        val cacheContactsDir = File(context.cacheDir, "contacts") // using cacheDir !
        if (!cacheContactsDir.exists()) cacheContactsDir.mkdir()
        return try {
            val tempFile = File.createTempFile("contact_", ".png", cacheContactsDir)
            tempFile.deleteOnExit()
            FileProvider.getUriForFile(context, "${AppVersion.applicationId()}.provider", tempFile)
        } catch (e: Exception) {
            log.error("failed to write temporary file for contact: {}", e.localizedMessage)
            null
        }
    }

    /** Creates the final picture from the temporary picture URI returned by the camera activity. The picture is resized and compressed. Returns the file name (in filesDir/contacts). */
    fun createPermaContactPicture(
        context: Context,
        tempFileUri: Uri,
    ): String? {
        val contactsDir = contactsDir(context)
        if (!contactsDir.exists()) contactsDir.mkdir()

        return try {
            val orientation = context.contentResolver.openInputStream(tempFileUri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            } ?: ExifInterface.ORIENTATION_UNDEFINED
            val bitmap = context.contentResolver.openInputStream(tempFileUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            val scale = (480f / bitmap.width.coerceAtLeast(bitmap.height)).coerceAtMost(1f)
            val matrix = Matrix().apply {
                postScale(scale, scale)
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                }
            }
            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
            val photoFile = File(contactsDir, "contact_${currentTimestampMillis()}.jpg")
            FileOutputStream(photoFile).use { scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, it) }
            photoFile.name
        } catch (e: Exception) {
            log.error("failed to write contact photo to disk: {}", e.localizedMessage)
            null
        }
    }

    fun getPhotoForFile(
        context: Context,
        fileName: String
    ): ImageBitmap? {
        val contactsDir = contactsDir(context)
        if (!contactsDir.exists() || !contactsDir.canRead()) return null

        return try {
            val photoFile = File(contactsDir, fileName)
            val content = photoFile.readBytes()
            BitmapFactory.decodeByteArray(content, 0, content.size).asImageBitmap()
        } catch (e: Exception) {
            log.info("could not read contact photo=$fileName: ", e.localizedMessage)
            null
        }
    }
}
