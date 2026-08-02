package fr.acinq.phoenixd.conf

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.conf.SeedSpec
import fr.acinq.lightning.Lightning.randomBytes
import fr.acinq.lightning.utils.toByteVector
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

data class PhoenixSeed(val seed: ByteVector, val isNew: Boolean, val path: Path?)

/**
 * @return a pair with the seed and a boolean indicating whether the seed was newly generated
 */
fun SeedSpec.SeedPath.getOrGenerateSeed(): PhoenixSeed {
    try {
        val (mnemonics, isNew) = if (SystemFileSystem.exists(path)) {
            val contents = SystemFileSystem.source(path).buffered().use { it.readString() }
            val mnemonics = Regex("[a-z]+").findAll(contents).map { it.value }.toList()
            mnemonics to false
        } else {
            val entropy = randomBytes(16)
            val mnemonics = MnemonicCode.toMnemonics(entropy)
            SystemFileSystem.sink(path).buffered().use { it.writeString(mnemonics.joinToString(" ")) }
            mnemonics to true
        }
        MnemonicCode.validate(mnemonics)
        return PhoenixSeed(seed = MnemonicCode.toSeed(mnemonics, "").toByteVector(), isNew = isNew, path = path)
    } catch (t: Throwable) {
        throw Throwable(t.message)
    }
}