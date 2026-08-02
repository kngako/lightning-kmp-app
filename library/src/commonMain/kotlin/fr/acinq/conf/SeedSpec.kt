package fr.acinq.conf

import fr.acinq.bitcoin.ByteVector
import kotlinx.io.files.Path

sealed class SeedSpec {
    data class Manual(val seed: ByteVector) : SeedSpec()
    data class SeedPath(val path: Path) : SeedSpec()
}
