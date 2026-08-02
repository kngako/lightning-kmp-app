package fr.acinq.phoenix

interface NFCActivity {

    fun stopHceService()

    fun stopNfcReader()
}