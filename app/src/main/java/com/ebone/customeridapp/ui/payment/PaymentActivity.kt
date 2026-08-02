package com.ebone.customeridapp.ui.payment

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.data.PaymentSource
import com.ebone.customeridapp.data.PaymentStatus
import com.ebone.customeridapp.data.PaymentTransaction
import com.ebone.customeridapp.databinding.ActivityPaymentBinding
import kotlinx.coroutines.launch

class PaymentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }

    private lateinit var binding: ActivityPaymentBinding
    private val repository by lazy { FirestoreRepository() }
    private lateinit var customerId: String
    private var packagePrice: Double = 0.0

    // Order matches the payment sources you specified.
    private val sources = listOf(
        PaymentSource.FAYSAL_BANK,
        PaymentSource.BANK_ALFALAH,
        PaymentSource.JAZZCASH,
        PaymentSource.EASYPAISA,
        PaymentSource.SADAPAY,
        PaymentSource.RAAST_ID
        // Future: PaymentSource.MANUAL_BANK entries added dynamically from admin config.
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: run { finish(); return }

        binding.spinnerPaymentSource.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            sources.map { it.name.replace("_", " ") }
        )

        loadPackagePrice()

        binding.btnSubmitPayment.setOnClickListener { submitPayment() }
        binding.btnUploadScreenshot.setOnClickListener {
            // TODO: launch image picker -> OcrPaymentReader.readFromBitmap(...)
        }
    }

    private fun loadPackagePrice() {
        lifecycleScope.launch {
            val customer = repository.getCustomer(customerId) ?: return@launch
            packagePrice = customer.packagePrice
            binding.tvPackagePriceReminder.text = "Package Price: Rs. $packagePrice"
        }
    }

    private fun submitPayment() {
        val amount = binding.etAmount.text.toString().toDoubleOrNull()
        val tid = binding.etTransactionId.text.toString().trim()

        if (amount == null || tid.isEmpty()) {
            showStatus("Please enter a valid amount and Transaction ID.")
            return
        }

        val selectedSource = sources[binding.spinnerPaymentSource.selectedItemPosition]

        lifecycleScope.launch {
            val status = repository.evaluatePaymentStatus(amount, packagePrice)

            val transaction = PaymentTransaction(
                customerId = customerId,
                source = selectedSource,
                amount = amount,
                bankTransactionId = tid,
                status = status
            )
            repository.recordPayment(transaction)

            if (status == PaymentStatus.INSUFFICIENT) {
                // Business rule: block recharge, show Insufficient Balance message.
                showStatus("Insufficient Balance: paid amount (Rs. $amount) is less than package price (Rs. $packagePrice). Recharge not applied.")
            } else {
                repository.updateCustomerBalanceAndRecharge(customerId, amount, packagePrice)
                showStatus("Payment verified. Recharge successful.")
            }
        }
    }

    private fun showStatus(message: String) {
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = android.view.View.VISIBLE
    }
}
