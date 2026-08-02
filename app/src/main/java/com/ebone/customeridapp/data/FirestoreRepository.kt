package com.ebone.customeridapp.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Single place for all Firestore reads/writes.
 * Collections:
 *   customers/{customerId}
 *   transactions/{transactionId}
 */
class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val customersRef = db.collection("customers")
    private val transactionsRef = db.collection("transactions")

    suspend fun getCustomer(customerId: String): Customer? {
        val snap = customersRef.document(customerId).get().await()
        return snap.toObject(Customer::class.java)
    }

    /**
     * GPS is captured ONCE — only on first activation.
     * If locationCapturedAt already exists, this call is a no-op.
     */
    suspend fun saveLocationIfFirstActivation(customerId: String, lat: Double, lng: Double) {
        val customer = getCustomer(customerId)
        if (customer?.locationCapturedAt != null) {
            return // already captured once, never overwrite
        }
        customersRef.document(customerId)
            .update(
                mapOf(
                    "latitude" to lat,
                    "longitude" to lng,
                    "locationCapturedAt" to System.currentTimeMillis()
                )
            ).await()
    }

    suspend fun recordPayment(transaction: PaymentTransaction): String {
        val docRef = transactionsRef.document()
        val withId = transaction.copy(transactionId = docRef.id)
        docRef.set(withId).await()
        return docRef.id
    }

    /**
     * Real-time listener on a single transaction's status.
     * Fires immediately whenever the Admin Panel's backend (SMS match ->
     * Cloud Function) flips this transaction's status in Firestore —
     * no fixed wait needed. Caller is responsible for removing the
     * returned ListenerRegistration (e.g. in onDestroy) to avoid leaks.
     */
    fun listenToTransactionStatus(
        transactionId: String,
        onStatusChanged: (PaymentStatus) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return transactionsRef.document(transactionId)
            .addSnapshotListener { snapshot, _ ->
                val statusName = snapshot?.getString("status") ?: return@addSnapshotListener
                val status = runCatching { PaymentStatus.valueOf(statusName) }.getOrNull()
                if (status != null) onStatusChanged(status)
            }
    }

    /**
     * Prevents duplicate/fake TID submissions: if this exact TID has already
     * been recorded (regardless of which customer submitted it), block it.
     */
    suspend fun isTidAlreadyUsed(tid: String): Boolean {
        val snapshot = transactionsRef
            .whereEqualTo("bankTransactionId", tid)
            .whereIn("status", listOf("VERIFIED", "PENDING"))
            .get()
            .await()
        return !snapshot.isEmpty
    }

    /** Result of attempting to claim/register a Customer ID with a PIN on this device. */
    sealed class ClaimResult {
        object Success : ClaimResult()
        object AlreadyRegisteredOnThisDevice : ClaimResult()
        object NotFound : ClaimResult()
        object WrongPin : ClaimResult()
        object LinkedToAnotherDevice : ClaimResult()
    }

    /**
     * Registers a Customer ID to this device using the Admin-issued One-Time
     * PIN. On success, the PIN is consumed (cleared) so it can never be
     * reused, and the ID is permanently locked to [deviceId] until Admin
     * unlocks it (see earlier design discussion).
     */
    suspend fun claimCustomerId(customerId: String, pin: String, deviceId: String): ClaimResult {
        val customer = getCustomer(customerId) ?: return ClaimResult.NotFound

        if (customer.linkedDeviceId == deviceId) return ClaimResult.AlreadyRegisteredOnThisDevice
        if (!customer.linkedDeviceId.isNullOrEmpty()) return ClaimResult.LinkedToAnotherDevice
        if (customer.registrationPin != pin) return ClaimResult.WrongPin

        customersRef.document(customerId)
            .update(
                mapOf(
                    "linkedDeviceId" to deviceId,
                    "registrationPin" to null
                )
            ).await()
        return ClaimResult.Success
    }

    /** @see PaymentRules.evaluatePaymentStatus */
    fun evaluatePaymentStatus(amountPaid: Double, packagePrice: Double): PaymentStatus =
        PaymentRules.evaluatePaymentStatus(amountPaid, packagePrice)

    suspend fun updateCustomerBalanceAndRecharge(customerId: String, amountPaid: Double, packagePrice: Double) {
        val status = evaluatePaymentStatus(amountPaid, packagePrice)
        if (status != PaymentStatus.VERIFIED) {
            // INSUFFICIENT or OVERPAID -> recharge intentionally blocked,
            // caller should show the matching English message to the customer.
            return
        }
        customersRef.document(customerId)
            .update(
                mapOf(
                    "currentBalance" to amountPaid,
                    "isActive" to true,
                    "activationStatus" to "ACTIVE",
                    // Resets the billing cycle — customer stays Active for
                    // billingCycleDays from this moment (electricity-bill style).
                    "lastPaymentDate" to System.currentTimeMillis()
                )
            ).await()
    }
}
