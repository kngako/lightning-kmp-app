package fr.acinq.phoenix.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `DbFactoryJvmTest` says everything else about the jvm target is checked by the compiler.
 * Two things in this file are not.
 *
 * The first is that the four accessors *create* the directory they name. The actual's own
 * doc says callers are "entitled to assume" that, because android and ios hand them a
 * sandbox where it is already true. Delete the `mkdirs()` and this still compiles; it
 * surfaces later as an IO error from inside DataStore or SeedManager, a long way from the
 * cause. The names are a shared assumption too -- `DbFactoryJvmTest` hardcodes
 * `databases/app.sqlite`.
 *
 * The second is [defaultApplicationDir], a chain of substring matches on `os.name` that no
 * compilation exercises and that decides where the node seed is written.
 */
class PlatformContextJvmTest {

    private val tempDir: File = Files.createTempDirectory("phoenix-platformcontext-test").toFile()
    private val ctx = PlatformContext(applicationDir = tempDir)

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `each accessor creates the directory it names`() {
        val cases = listOf(
            "files" to getApplicationFilesDirectoryPath(ctx),
            "databases" to assertNotNull(getDatabaseFilesDirectoryPath(ctx), "databases path was null"),
            "cache" to getApplicationCacheDirectoryPath(ctx),
            "cache" to getTemporaryDirectoryPath(ctx),
        )
        cases.forEach { (name, path) ->
            assertEquals(File(tempDir, name).absolutePath, path, "unexpected path for $name")
            assertTrue(File(path).isDirectory, "$name was not created")
        }
    }

    /**
     * Android returns null from the databases accessor and only `DbFactory.ios.kt` reads
     * it, so nothing in common code would notice this actual returning null either -- but
     * `DbFactory.jvm.kt` does read it, and would have nowhere to put the sqlite files.
     */
    @Test
    fun `the databases directory is a real path on this platform`() {
        val path = assertNotNull(getDatabaseFilesDirectoryPath(ctx))
        assertTrue(File(path).isDirectory)
    }

    /** Deliberate aliasing, matching what android does with `cacheDir` for both. */
    @Test
    fun `the temporary and cache directories are the same one`() {
        assertEquals(getApplicationCacheDirectoryPath(ctx), getTemporaryDirectoryPath(ctx))
    }

    /**
     * `mkdirs()` returns false for a directory that already exists and the actual ignores
     * the result, which is correct -- but only as long as nothing is cleared on the way.
     * The seed lives in this directory.
     */
    @Test
    fun `an existing directory is reused rather than replaced`() {
        val first = getApplicationFilesDirectoryPath(ctx)
        val seed = File(first, "node-data").apply { writeText("seed") }

        val second = getApplicationFilesDirectoryPath(ctx)

        assertEquals(first, second)
        assertTrue(seed.exists(), "the seed file did not survive a second call")
        assertEquals("seed", seed.readText())
    }

    /**
     * `mkdirs`, not `mkdir`: an embedding application can point at a profile directory
     * whose parents do not exist yet.
     */
    @Test
    fun `an application directory with missing parents is created in full`() {
        val nested = File(tempDir, "profiles/second/app")
        val path = getApplicationFilesDirectoryPath(PlatformContext(applicationDir = nested))
        assertTrue(File(path).isDirectory, "nested application directory was not created")
    }

    @Test
    fun `macos gets the application support directory`() {
        withOs("Mac OS X", FAKE_HOME) {
            assertEquals(File(FAKE_HOME, "Library/Application Support/phoenix"), defaultApplicationDir())
        }
    }

    /**
     * `"darwin".contains("win")` is true, so a darwin os name reaches the windows branch
     * unless the mac branch is tested first. The actual names darwin explicitly, so it is
     * meant to land in Application Support.
     */
    @Test
    fun `a darwin os name is treated as macos and not as windows`() {
        withOs("Darwin", FAKE_HOME) {
            assertEquals(File(FAKE_HOME, "Library/Application Support/phoenix"), defaultApplicationDir())
        }
    }

    /**
     * The env var cannot be set from inside the jvm, so the expectation is computed from
     * whatever this host has. Either way the branch itself is exercised.
     */
    @Test
    fun `linux honours XDG_DATA_HOME and otherwise falls back under the home directory`() {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        val expected = xdg?.let { File(it, "phoenix") } ?: File(FAKE_HOME, ".local/share/phoenix")
        withOs("Linux", FAKE_HOME) {
            assertEquals(expected, defaultApplicationDir())
        }
    }

    @Test
    fun `windows prefers LOCALAPPDATA and otherwise falls back under the home directory`() {
        val appData = (System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA"))?.takeIf { it.isNotBlank() }
        val expected = appData?.let { File(it, "phoenix") } ?: File(FAKE_HOME, "AppData/Local/phoenix")
        withOs("Windows 11", FAKE_HOME) {
            assertEquals(expected, defaultApplicationDir())
        }
    }

    @Test
    fun `an unrecognised os name falls back to the linux layout rather than failing`() {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        val expected = xdg?.let { File(it, "phoenix") } ?: File(FAKE_HOME, ".local/share/phoenix")
        withOs("SunOS", FAKE_HOME) {
            assertEquals(expected, defaultApplicationDir())
        }
    }

    /**
     * Called out in the actual as the reason it is not `java.io.tmpdir`: the node seed is
     * resolved against this, and most systems clear the temporary directory on reboot.
     * Only checked for the branches this host can reach without env vars.
     */
    @Test
    fun `the default directory is never the system temporary directory`() {
        val tmp = File(System.getProperty("java.io.tmpdir")).canonicalFile.toPath()
        val envFree = buildList {
            add("Mac OS X")
            if (System.getenv("XDG_DATA_HOME").isNullOrBlank()) add("Linux")
        }
        envFree.forEach { os ->
            withOs(os, FAKE_HOME) {
                val dir = defaultApplicationDir().canonicalFile.toPath()
                assertFalse(dir.startsWith(tmp), "$os default landed under the temporary directory: $dir")
            }
        }
    }

    private fun withOs(osName: String, home: File, block: () -> Unit) {
        val osBefore = System.getProperty("os.name")
        val homeBefore = System.getProperty("user.home")
        System.setProperty("os.name", osName)
        System.setProperty("user.home", home.absolutePath)
        try {
            block()
        } finally {
            System.setProperty("os.name", osBefore)
            System.setProperty("user.home", homeBefore)
        }
    }

    private companion object {
        /**
         * Never created and deliberately outside the temporary directory, so that
         * `the default directory is never the system temporary directory` is asserting
         * something about the actual rather than about where the test put its scratch dir.
         */
        val FAKE_HOME = File("/phoenix-jvm-test-home")
    }
}
