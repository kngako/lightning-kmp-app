package fr.acinq.phoenix.utils.logger

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
actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> =
    if (LoggerFactory.getILoggerFactory() is NOPLoggerFactory) listOf(LogcatWriter()) else listOf(Slf4jLogWriter())

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
