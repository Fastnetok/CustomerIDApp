package com.ebone.customeridapp.data

/**
 * Core customer record stored in Firestore: collection "customers", doc id = customerId
 */
data class Customer(
    val customerId: String = "",
    val name: String = "",
    val phone: String = "",
    val packageId: String = "",
    val packagePrice: Double = 0.0,
    val currentBalance: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCapturedAt: Long? = null, // set only once, on first GPS activation
    val isActive: Boolean = true,
    val activationStatus: String = "ACTIVE", // ACTIVE | PENDING_APPROVAL | DISABLED

    // --- Registration security (One-Time PIN + device lock) ---
    // Admin sets registrationPin when creating the customer. It is consumed
    // (set to null) the first time the app successfully claims this ID, so
    // it can never be reused. linkedDeviceId then locks this ID to that one
    // phone — re-registering on a new phone requires Admin to null it out.
    val registrationPin: String? = null,
    val linkedDeviceId: String? = null,
    val ispProvider: String = "EBONE", // EBONE | WATEEN | ZONG — jis Company ki Service hai

    // --- Billing cycle (subscription-style auto-expiry, admin-configurable) ---
    // e.g. electricity-bill style: package stays Active for N days after payment,
    // then auto-flips to Disabled until the customer pays again.
    val lastPaymentDate: Long? = null,   // timestamp of last verified payment
    val billingCycleDays: Int = 30       // admin sets this per customer/package (e.g. 10, 25, 30)
)

/**
 * A single payment transaction record: collection "transactions"
 */
data class PaymentTransaction(
    val transactionId: String = "",     // Firestore doc id
    val customerId: String = "",
    val source: PaymentSource = PaymentSource.MANUAL,
    val amount: Double = 0.0,
    val bankTransactionId: String = "", // Manual T-ID entered by customer/admin
    val rawSmsText: String? = null,
    val ocrExtractedText: String? = null,
    val aiInterpretedAmount: Double? = null,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PaymentSource {
    FAYSAL_BANK,
    BANK_ALFALAH,
    JAZZCASH,
    EASYPAISA,
    SADAPAY,
    RAAST_ID,
    MANUAL_BANK, // future: manually added banks
    MANUAL
}

enum class PaymentStatus {
    PENDING,
    VERIFIED,
    INSUFFICIENT,   // amount paid < package price -> recharge blocked
    OVERPAID,       // amount paid > package price + tolerance -> needs correct bill amount
    FAILED
}

/**
 * Computes the customer's real-time status based on the billing cycle —
 * similar to an electricity bill: package stays "ACTIVE" for
 * [Customer.billingCycleDays] days after [Customer.lastPaymentDate], then
 * automatically becomes "DISABLED" until the customer pays again.
 *
 * NOTE: This is a *display-only* calculation for the customer app. The
 * authoritative enforcement (actually cutting off service) must happen via
 * a scheduled Cloud Function on the backend that runs daily, checks every
 * customer's billing cycle, and updates activationStatus in Firestore —
 * a client-side check alone cannot be trusted to enforce billing.
 */
fun Customer.effectiveStatus(): String {
    if (activationStatus == "PENDING_APPROVAL") return "PENDING_APPROVAL"

    val lastPayment = lastPaymentDate ?: return "DISABLED" // never paid
    val cycleMillis = billingCycleDays * 24L * 60L * 60L * 1000L
    val expiresAt = lastPayment + cycleMillis

    return if (System.currentTimeMillis() < expiresAt) "ACTIVE" else "DISABLED"
}

/** Days remaining until the current billing cycle expires (0 if already expired). */
fun Customer.daysUntilExpiry(): Int {
    val lastPayment = lastPaymentDate ?: return 0
    val cycleMillis = billingCycleDays * 24L * 60L * 60L * 1000L
    val expiresAt = lastPayment + cycleMillis
    val remainingMillis = expiresAt - System.currentTimeMillis()
    return (remainingMillis / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
}
