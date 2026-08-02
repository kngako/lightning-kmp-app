package fr.acinq.phoenix.utils.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.NSLogWriter
import co.touchlab.kermit.OSLogWriter
import fr.acinq.phoenix.utils.PlatformContext

actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> {
    return if (ctx.logger != null) {
        listOf(NSLogWriter())
    } else {
        // TODO: OSLogWriter is disabled for now, as the current version of OSLogStore is buggy
         listOf(OSLogWriter())
    }
}