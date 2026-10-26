package fr.acinq.phoenix.utils.logger

import kotlin.test.Test
import kotlin.test.assertEquals

class RedactSecretsTest {
    private val a = "af222bcc660f5aa09e451e943f6941112d574991653d02c2faec6cac8a1b2c3d"
    private val b = "58787fe05678d4994e9a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60"

    @Test
    fun `a fulfilled htlc's preimage is redacted`() {
        assertEquals(
            "sending UpdateFulfillHtlc(channelId=3442db60, id=0, paymentPreimage=<redacted>, tlvStream=TlvStream(records=[]))",
            redactSecrets("sending UpdateFulfillHtlc(channelId=3442db60, id=0, paymentPreimage=$a, tlvStream=TlvStream(records=[]))"),
        )
    }

    @Test
    fun `a fee credit's preimage and a list of preimages are redacted`() {
        assertEquals("AddFeeCredit(chainHash=6fe2, preimage=<redacted>)", redactSecrets("AddFeeCredit(chainHash=6fe2, preimage=$a)"))
        assertEquals("FromFutureHtlcWithPreimage(preimages=[<redacted>])", redactSecrets("FromFutureHtlcWithPreimage(preimages=[$a, $b])"))
    }

    @Test
    fun `everything else is left as it is`() {
        val line = "received UpdateAddHtlc(channelId=3442db60, id=0, amountMsat=8558000 msat, paymentHash=$a)"
        assertEquals(line, redactSecrets(line))
        assertEquals("sending RevokeAndAck(perCommitmentSecret=<private_key>)", redactSecrets("sending RevokeAndAck(perCommitmentSecret=<private_key>)"))
    }
}
