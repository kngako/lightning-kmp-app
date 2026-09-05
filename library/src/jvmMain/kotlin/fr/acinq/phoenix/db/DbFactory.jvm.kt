package fr.acinq.phoenix.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import fr.acinq.phoenix.db.migrations.v10.AfterVersion10
import fr.acinq.phoenix.db.migrations.v11.AfterVersion11
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import fr.acinq.phoenix.db.sqldelight.ChannelsDatabase
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getDatabaseFilesDirectoryPath
import java.io.File
import java.util.Properties

/**
 * Structurally this follows the ios actual rather than the android one: an explicit
 * directory plus a file name, because the jvm has no `Context` to hand a bare name to.
 *
 * Schema creation and migration are not hand-rolled. The `JdbcSqliteDriver(url,
 * properties, schema, ...)` factory -- distinct from the constructor of the same name,
 * which does none of this -- creates the schema on an empty file, migrates an existing
 * one, and maintains `PRAGMA user_version` itself.
 */
private fun driver(
    ctx: PlatformContext,
    fileName: String,
    schema: app.cash.sqldelight.db.SqlSchema<app.cash.sqldelight.db.QueryResult.Value<Unit>>,
    vararg callbacks: app.cash.sqldelight.db.AfterVersion,
): SqlDriver {
    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val dbFile = File(dbDir, fileName)
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${dbFile.absolutePath}",
        properties = sqliteProperties(),
        schema = schema,
        callbacks = callbacks,
    )
}

/**
 * Foreign keys are off by default in SQLite and the pragma is *per connection*, so it
 * cannot be issued once against the driver -- `JdbcSqliteDriver` opens a connection per
 * thread. Passing it as a connection property is what makes it apply to every one of
 * them: xerial reads pragma-named properties back through `SQLiteConfig(Properties)` and
 * applies them as each connection is opened.
 *
 * Android gets this from `setForeignKeyConstraintsEnabled` in its driver callback, and
 * ios from `DatabaseConfiguration.Extended(foreignKeyConstraints = true)`. All three
 * platforms have to say it separately; none of them inherits it from the schema.
 */
private fun sqliteProperties(): Properties = Properties().apply {
    setProperty("foreign_keys", "true")
}

actual fun createChannelsDbDriver(ctx: PlatformContext, fileName: String): SqlDriver =
    driver(ctx, fileName, ChannelsDatabase.Schema)

actual fun createPaymentsDbDriver(
    ctx: PlatformContext,
    fileName: String,
    onError: (String) -> Unit,
): SqlDriver = driver(
    ctx,
    fileName,
    PaymentsDatabase.Schema,
    AfterVersion10(onError),
    AfterVersion11(onError),
)

/**
 * Android names this file "appdb.sqlite" and ios names it "app.sqlite". Following ios,
 * since the rest of this file does. A jvm install starts empty either way, so the choice
 * only matters if a database is ever moved between platforms -- which nothing supports.
 */
actual fun createAppDbDriver(ctx: PlatformContext): SqlDriver =
    driver(ctx, "app.sqlite", AppDatabase.Schema)
