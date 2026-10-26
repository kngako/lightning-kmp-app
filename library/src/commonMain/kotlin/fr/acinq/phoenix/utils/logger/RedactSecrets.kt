package fr.acinq.phoenix.utils.logger

/**
 * A preimage, as lightning-kmp prints one: `paymentPreimage=<64 hex>` in an `UpdateFulfillHtlc`,
 * `preimage=` in an `AddFeeCredit`, `preimages=[…]` in a liquidity payment's details.
 */
private val preimage = Regex("""\b(\w*[pP]reimages?)=(\[?)([0-9a-f]{64}(?:, ?[0-9a-f]{64})*)""")

/**
 * [message] with every payment preimage replaced by `<redacted>`.
 *
 * lightning-kmp logs a fulfilled HTLC's preimage at info, and a preimage is a proof of payment: whoever
 * reads it from a device's log can claim to have paid the invoice. lightning-kmp already writes
 * `<private_key>` for the per-commitment secrets it prints.
 */
fun redactSecrets(message: String): String =
    if ("reimage" !in message) message else preimage.replace(message) { "${it.groupValues[1]}=${it.groupValues[2]}<redacted>" }
