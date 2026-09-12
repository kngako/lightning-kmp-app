package fr.acinq.phoenix.utils

import co.touchlab.kermit.Logger
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * Writes a file that must never be left half-written.
 *
 * The discipline `SeedManager` has always used for `seed.dat`, lifted out so the
 * sibling files this library keeps (`nostr-keys.dat`) get it by calling rather than by
 * copying: write to a temporary file, read it back and check it is what was meant,
 * then `atomicMove` it over the target. A plain write is not safe here -- an
 * interruption partway through leaves a truncated file, and a truncated seed file is an
 * unrecoverable wallet, a truncated key file an unrecoverable identity.
 *
 * The temporary file is removed on any failure before the move; the move consumes it.
 */
object AtomicFileWrite {
    private val log = Logger.withTag("AtomicFileWrite")

    /**
     * @param check given the bytes read back from the temporary file; return false to abort
     *   rather than replace a good file with a corrupted one.
     * @param onMismatch the exception to throw when [check] says no, so each caller keeps
     *   the type its own callers know.
     */
    fun writeVerified(
        dir: Path,
        fileName: String,
        temporaryFileName: String,
        bytes: ByteArray,
        check: (ByteArray) -> Boolean,
        onMismatch: () -> Exception,
    ) {
        if (!FileSystem.SYSTEM.exists(dir)) {
            FileSystem.SYSTEM.createDirectories(dir)
        }

        val temp = dir.resolve(temporaryFileName.toPath())

        try {
            FileSystem.SYSTEM.write(temp) {
                write(bytes)
            }

            val readBack = FileSystem.SYSTEM.source(temp).buffer().use { it.readByteArray() }
            if (!check(readBack)) {
                log.e("$fileName check does not match, aborting write")
                throw onMismatch()
            }

            FileSystem.SYSTEM.atomicMove(
                source = temp,
                target = dir.resolve(fileName.toPath())
            )
        } catch (e: Exception) {
            try {
                FileSystem.SYSTEM.delete(temp, mustExist = false)
            } catch (cleanupError: Exception) {
                log.w("could not clean up temporary file $temporaryFileName: ${cleanupError.message}")
            }
            throw e
        }
    }
}
