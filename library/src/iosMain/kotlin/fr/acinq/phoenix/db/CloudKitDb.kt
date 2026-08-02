package fr.acinq.phoenix.db

import fr.acinq.phoenix.db.payments.CloudKitInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

class CloudKitDb(
    appDb: SqliteAppDb,
    paymentsDb: SqlitePaymentsDb
): CloudKitInterface, CoroutineScope by MainScope() {

    val contacts = CloudKitContactsDb(paymentsDb)
    val payments = CloudKitPaymentsDb(paymentsDb)
}
