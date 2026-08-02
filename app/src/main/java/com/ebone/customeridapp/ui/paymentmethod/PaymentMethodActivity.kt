package com.ebone.customeridapp.ui.paymentmethod

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.databinding.ActivityPaymentMethodBinding
import kotlinx.coroutines.launch

/**
 * Screen 1 of the payment flow: shows the outstanding amount and lets the
 * customer pick a payment source. Selecting a row moves to
 * PaymentVerificationActivity where the actual TID / SMS / OCR / AI
 * verification happens.
 */
class PaymentMethodActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }

    private lateinit var binding: ActivityPaymentMethodBinding
    private val repository by lazy { FirestoreRepository() }
    private var customerId: String = ""
    private var outstandingAmount: Double = 1500.0 // preview default matches reference design

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentMethodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""

        if (customerId.isNotEmpty()) {
            // Real customer: don't flash a fake placeholder amount — show a
            // loading state until the actual packagePrice arrives from Firestore.
            binding.tvOutstandingAmount.text = "Rs. …"
            loadOutstandingAmount()
        } else {
            // Design-preview mode only (no customerId): show sample amount.
            binding.tvOutstandingAmount.text = "Rs. %.2f".format(outstandingAmount)
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.rowEasypaisa.setOnClickListener { openVerification("Easypaisa") }
        binding.rowJazzCash.setOnClickListener { openVerification("JazzCash") }
        binding.rowSadaPay.setOnClickListener { openVerification("SadaPay") }
        binding.rowFaysalBank.setOnClickListener { openVerification("Faysal Bank") }
        binding.rowRaastId.setOnClickListener { openVerification("Raast ID") }
        binding.rowBankAlfalah.setOnClickListener { openVerification("Bank Alfalah") }
        binding.rowOtherBank.setOnClickListener { openVerification("Other Bank Transfer") }

        binding.btnAddMoreBanks.setOnClickListener {
            // TODO: Admin-configurable list of additional manual banks (future feature).
        }
    }

    private fun loadOutstandingAmount() {
        lifecycleScope.launch {
            val customer = repository.getCustomer(customerId) ?: return@launch
            outstandingAmount = (customer.packagePrice - customer.currentBalance).coerceAtLeast(0.0)
            binding.tvOutstandingAmount.text = "Rs. %.2f".format(outstandingAmount)
        }
    }

    private fun openVerification(methodName: String) {
        startActivity(
            Intent(this, com.ebone.customeridapp.ui.paymentmethod.PaymentVerificationActivity::class.java)
                .putExtra(PaymentVerificationActivity.EXTRA_CUSTOMER_ID, customerId)
                .putExtra(PaymentVerificationActivity.EXTRA_METHOD_NAME, methodName)
                .putExtra(PaymentVerificationActivity.EXTRA_AMOUNT, outstandingAmount)
        )
    }
}
