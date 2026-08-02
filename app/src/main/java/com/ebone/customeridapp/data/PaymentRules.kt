package com.ebone.customeridapp.data

/**
 * Pure business rules for payment amount matching — deliberately has NO
 * Firebase dependency, so it can be called safely even before Firebase is
 * initialized (e.g. in design-preview mode, or in unit tests).
 */
object PaymentRules {

    /** Rupees of overpayment still accepted automatically before flagging as OVERPAID. */
    const val OVERPAYMENT_TOLERANCE = 50.0

    /**
     * - Less than packagePrice  -> INSUFFICIENT (never approved — customer
     *   must send the complete amount).
     * - packagePrice up to packagePrice + OVERPAYMENT_TOLERANCE -> VERIFIED
     *   (small overpayment accepted, e.g. rounding or a bit extra).
     * - More than packagePrice + OVERPAYMENT_TOLERANCE -> OVERPAID (likely
     *   paying the wrong bill amount — ask customer to verify).
     */
    fun evaluatePaymentStatus(amountPaid: Double, packagePrice: Double): PaymentStatus {
        return when {
            amountPaid < packagePrice -> PaymentStatus.INSUFFICIENT
            amountPaid <= packagePrice + OVERPAYMENT_TOLERANCE -> PaymentStatus.VERIFIED
            else -> PaymentStatus.OVERPAID
        }
    }
}
