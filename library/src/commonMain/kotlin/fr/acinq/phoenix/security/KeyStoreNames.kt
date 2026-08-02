package fr.acinq.phoenix.security

object KeyStoreNames {
    /** The alias of the key used to encrypt an [EncryptedSeed.V2] seed. */
    const val KEY_NO_AUTH = "PHOENIX_KEY_NO_AUTH"

    /** The alias of the key used to encrypt [EncryptedLockPin.KeystoreEncrypted], [EncryptedPinLock.V2], [EncryptedPinSpending.V1], [EncryptedPinSpending.V2]. */
    const val KEY_FOR_PINCODE_V1 = "PHOENIX_KEY_FOR_PINCODE_V1"
}