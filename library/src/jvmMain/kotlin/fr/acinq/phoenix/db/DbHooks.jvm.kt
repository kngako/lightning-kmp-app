package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase

// Identical to the android actuals, and for the same reason: these hooks exist to drive
// the ios CloudKit mirror and have nothing to do on any other platform.
actual fun didSaveWalletPayment(id: UUID, database: PaymentsDatabase) {}
actual fun didDeleteWalletPayment(id: UUID, database: PaymentsDatabase) {}
actual fun didUpdateWalletPaymentMetadata(id: UUID, database: PaymentsDatabase) {}

actual fun didSaveContact(contactId: UUID, database: PaymentsDatabase) {}
actual fun didDeleteContact(contactId: UUID, database: PaymentsDatabase) {}

actual fun makeCloudKitDb(appDb: SqliteAppDb, paymentsDb: SqlitePaymentsDb): CloudKitInterface? = null
