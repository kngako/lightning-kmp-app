package fr.acinq.phoenix

import co.touchlab.kermit.CommonWriter
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.phoenix.compose.AppVersion
import fr.acinq.phoenix.data.platformElectrumRegtestConf
import fr.acinq.phoenix.managers.computePreferencePath
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationFilesDirectoryPath
import fr.acinq.phoenix.utils.logger.phoenixLogWriters
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The one-line actuals. Each is too small to get wrong in an interesting way, and each is
 * a value some other platform's actual returns differently -- which is exactly the kind of
 * thing that gets "fixed" into consistency by someone reading one file and not the other.
 */
class JvmActualsTest {

    private val tempDir: File = Files.createTempDirectory("phoenix-jvm-actuals-test").toFile()
    private val ctx = PlatformContext(applicationDir = tempDir)

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    /**
     * The android actual points at 10.0.2.2, the emulator's alias for the host loopback.
     * A jvm process is already on the host, so copying android's value here would send
     * regtest traffic to an address that means nothing.
     */
    @Test
    fun `the regtest electrum server is the host loopback, not the emulator alias`() {
        val conf = platformElectrumRegtestConf()

        assertEquals("127.0.0.1", conf.host)
        assertNotEquals("10.0.2.2", conf.host, "that is the android emulator's host alias")
        assertEquals(51002, conf.port)
        assertEquals(TcpSocket.TLS.DISABLED, conf.tls, "regtest electrum is plaintext")
    }

    /**
     * `versionCode` is a string on every platform and common code has no reason to expect
     * an empty one; `versionName` must not be android's literal placeholder.
     */
    @Test
    fun `the app version is reported without a manifest to read`() {
        assertEquals("0", AppVersion.versionCode)
        assertTrue(AppVersion.versionName.isNotBlank(), "versionName was blank")
        assertNotEquals("TODO:LibraryVersionName", AppVersion.versionName)

        if (AppVersion::class.java.`package`?.implementationVersion == null) {
            assertEquals("0.0.0-dev", AppVersion.versionName, "the no-manifest fallback")
        }
    }

    /**
     * DataStore writes this path itself and will not create the parent, so the directory
     * has to already exist -- which it does only because the actual resolves through
     * `getApplicationFilesDirectoryPath` rather than joining strings.
     */
    @Test
    fun `a preference path lands in an application files directory that exists`() {
        val path = File(computePreferencePath(ctx, "global.preferences_pb").toString())

        assertEquals("global.preferences_pb", path.name)
        assertEquals(File(getApplicationFilesDirectoryPath(ctx)), path.parentFile)
        assertTrue(path.parentFile.isDirectory, "DataStore's parent directory was not created")
    }

    /** Android routes kermit into slf4j; a desktop process has no such convention. */
    @Test
    fun `logging goes to a single stdout writer`() {
        val writers = phoenixLogWriters(ctx)

        assertEquals(1, writers.size)
        assertIs<CommonWriter>(writers.single())
    }
}
