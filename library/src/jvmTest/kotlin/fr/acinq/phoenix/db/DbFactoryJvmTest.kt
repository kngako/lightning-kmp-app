package fr.acinq.phoenix.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.phoenix.utils.PlatformContext
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Everything else about the jvm target is checked by the compiler. These two properties
 * are not: whether the schema actually lands in the file, and whether foreign keys are
 * actually on. Both were reasoned about rather than observed while writing
 * `DbFactory.jvm.kt`, and both fail silently in production if wrong -- an un-created
 * schema looks like a missing table at first query, and foreign keys being off means
 * cascading deletes quietly do not happen.
 */
class DbFactoryJvmTest {

    private val tempDir: File = Files.createTempDirectory("phoenix-dbfactory-test").toFile()
    private val ctx = PlatformContext(applicationDir = tempDir)
    private val drivers = mutableListOf<SqlDriver>()

    @AfterTest
    fun cleanup() {
        drivers.forEach { it.close() }
        tempDir.deleteRecursively()
    }

    private fun track(driver: SqlDriver): SqlDriver = driver.also { drivers += it }

    private fun SqlDriver.queryLong(sql: String): Long? = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        },
        parameters = 0,
    ).value

    private fun SqlDriver.tableCount(): Long =
        queryLong("SELECT count(*) FROM sqlite_master WHERE type = 'table'") ?: 0L

    @Test
    fun `app db driver creates its schema`() {
        val driver = track(createAppDbDriver(ctx))
        assertTrue(driver.tableCount() > 0, "expected the app schema to have been created")
        assertTrue(
            File(tempDir, "databases/app.sqlite").exists(),
            "expected the database file under the context's databases directory",
        )
    }

    @Test
    fun `channels db driver creates its schema`() {
        val driver = track(createChannelsDbDriver(ctx, "channels.sqlite"))
        assertTrue(driver.tableCount() > 0, "expected the channels schema to have been created")
    }

    @Test
    fun `payments db driver creates its schema`() {
        val driver = track(createPaymentsDbDriver(ctx, "payments.sqlite") { error("unexpected: $it") })
        assertTrue(driver.tableCount() > 0, "expected the payments schema to have been created")
    }

    /**
     * The pragma is per connection, so this asserts the connection *properties* took --
     * issuing it once against the driver would not have been enough.
     */
    @Test
    fun `foreign keys are enforced on every driver`() {
        listOf(
            "app" to track(createAppDbDriver(ctx)),
            "channels" to track(createChannelsDbDriver(ctx, "fk-channels.sqlite")),
            "payments" to track(createPaymentsDbDriver(ctx, "fk-payments.sqlite") { }),
        ).forEach { (name, driver) ->
            assertEquals(1L, driver.queryLong("PRAGMA foreign_keys"), "foreign_keys off for $name")
        }
    }

    /** Reopening must migrate-or-noop rather than re-create, and must not lose the schema. */
    @Test
    fun `reopening an existing database keeps its schema`() {
        val first = createAppDbDriver(ctx)
        val tablesOnCreate = first.tableCount()
        first.close()

        val second = track(createAppDbDriver(ctx))
        assertEquals(tablesOnCreate, second.tableCount(), "schema changed on reopen")
        assertTrue(tablesOnCreate > 0)
    }
}
