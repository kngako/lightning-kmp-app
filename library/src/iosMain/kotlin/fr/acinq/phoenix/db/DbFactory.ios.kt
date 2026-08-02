package fr.acinq.phoenix.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import fr.acinq.phoenix.db.migrations.v10.AfterVersion10
import fr.acinq.phoenix.db.migrations.v11.AfterVersion11
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import fr.acinq.phoenix.db.sqldelight.ChannelsDatabase
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getDatabaseFilesDirectoryPath

actual fun createChannelsDbDriver(
    ctx: PlatformContext,
    fileName: String
): SqlDriver {
    val schema = ChannelsDatabase.Schema

    // The foreign_keys constraint needs to be set via the DatabaseConfiguration:
    // https://github.com/cashapp/sqldelight/issues/1356

    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val configuration = DatabaseConfiguration(
        name = fileName,
        version = schema.version.toInt(),
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
        }
    )
    return NativeSqliteDriver(configuration)
}

actual fun createPaymentsDbDriver(
    ctx: PlatformContext,
    fileName: String,
    onError: (String) -> Unit
): SqlDriver {
    val schema = PaymentsDatabase.Schema

    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val configuration = DatabaseConfiguration(
        name = fileName,
        version = schema.version.toInt(),
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong(), AfterVersion10(onError), AfterVersion11(onError)) }
        }
    )
    return NativeSqliteDriver(configuration)
}

actual fun createAppDbDriver(
    ctx: PlatformContext
): SqlDriver {
    val schema = AppDatabase.Schema
    val name = "app.sqlite"

    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val configuration = DatabaseConfiguration(
        name = name,
        version = schema.version.toInt(),
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
        }
    )
    return NativeSqliteDriver(configuration)
}