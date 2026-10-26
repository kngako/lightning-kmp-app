package fr.acinq.phoenix.utils.logger

import android.content.pm.ApplicationInfo
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LogcatWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Severity.Assert
import co.touchlab.kermit.Severity.Debug
import co.touchlab.kermit.Severity.Info
import co.touchlab.kermit.Severity.Verbose
import co.touchlab.kermit.Severity.Warn
import fr.acinq.phoenix.utils.PlatformContext
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLoggerFactory

/**
 * Use SLF4J writer on Android. Note that writing logs to Logcat is already done in
 * phoenix-android SLF4J configuration.
 *
 * An app that installs no SLF4J provider gets SLF4J's no-operation fallback, which drops every
 * line: there, write to Logcat directly instead, so the node's logs are not silently lost.
 */
actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> = when {
    LoggerFactory.getILoggerFactory() !is NOPLoggerFactory -> listOf(Slf4jLogWriter())
    ctx.isDebuggable -> listOf(LogcatWriter())
    // A release build's Logcat can be read by anyone with the device on adb: keep payment preimages out of it.
    else -> listOf(RedactingLogWriter(LogcatWriter()))
}

private val PlatformContext.isDebuggable: Boolean
    get() = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** [delegate], with every message passed through [redactSecrets] first. */
class RedactingLogWriter(private val delegate: LogWriter) : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean = delegate.isLoggable(tag, severity)
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) =
        delegate.log(severity, redactSecrets(message), tag, throwable)
}

class Slf4jLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val logger = LoggerFactory.getLogger(tag)
        when (severity) {
            Verbose -> logger.trace(message, throwable)
            Debug -> logger.debug(message, throwable)
            Info -> logger.info(message, throwable)
            Warn -> logger.warn(message, throwable)
            Severity.Error -> logger.error(message, throwable)
            Assert -> logger.error(message, throwable)
        }
    }
}
