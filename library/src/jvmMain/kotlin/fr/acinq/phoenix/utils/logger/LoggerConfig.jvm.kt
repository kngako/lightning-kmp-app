package fr.acinq.phoenix.utils.logger

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.LogWriter
import fr.acinq.phoenix.utils.PlatformContext

/**
 * Android routes kermit into slf4j because that is what android tooling reads back. A
 * desktop jvm process has no equivalent convention, so this writes to stdout -- which is
 * what the expect's own documentation says this factory is for. A packaged build that
 * wants log files should configure its own writer rather than widening this one.
 */
actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> = listOf(CommonWriter())
