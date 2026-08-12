package com.ebone.customeridapp.ui.payment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.data.PaymentSource
import com.ebone.customeridapp.data.PaymentStatus
import com.ebone.customeridapp.data.PaymentTransaction
import com.ebone.customeridapp.databinding.ActivityPaymentBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class PaymentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        private const val SUPPORT_NUMBER_1 = "03007951912"
        private const val SUPPORT_NUMBER_2 = "03211119966"
    }

    private lateinit var binding: ActivityPaymentBinding
    private val repository by lazy { FirestoreRepository() }
    private lateinit var customerId: String
    private var packagePrice: Double = 0.0
    private var failCount = 0
    private var currentTransactionId: String? = null
    private var statusListener: ListenerRegistration? = null

    private val sources = listOf(
        PaymentSource.FAYSAL_BANK,
        PaymentSource.BANK_ALFALAH,
        PaymentSource.JAZZCASH,
        PaymentSource.EASYPAISA,
        PaymentSource.SADAPAY,
        PaymentSource.RAAST_ID
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
            // TODO: launch image picker
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

        setUiWaiting(true)
        showStatus("Payment submitted — waiting for verification…")

        lifecycleScope.launch {
            val paymentStatus = repository.evaluatePaymentStatus(amount, packagePrice)

            if (paymentStatus == PaymentStatus.INSUFFICIENT) {
                setUiWaiting(false)
                showStatus("Amount Rs. $amount is less than package price Rs. $packagePrice. Please pay the correct amount.")
                return@launch
            }

            val transaction = PaymentTransaction(
                customerId = customerId,
                source = selectedSource,
                amount = amount,
                bankTransactionId = tid,
                status = PaymentStatus.PENDING
            )
            val txnId = repository.recordPayment(transaction)
            currentTransactionId = txnId

            // Start listening for status change from admin side
            listenForVerification(txnId)
        }
    }

    // ===================== LISTEN FOR VERIFICATION =====================

    private fun listenForVerification(transactionId: String) {
        statusListener?.remove()
        statusListener = FirebaseFirestore.getInstance()
            .collection("transactions")
            .document(transactionId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: return@addSnapshotListener

                when (status) {
                    "VERIFIED" -> {
                        statusListener?.remove()
                        onPaymentVerified()
                    }
                    "FAILED" -> {
                        statusListener?.remove()
                        onPaymentFailed()
                    }
                }
            }
    }

    // ===================== VERIFIED → GO HOME =====================

    private fun onPaymentVerified() {
        setUiWaiting(false)
        showStatus("✅ Package Activated! Redirecting to home…")

        binding.root.postDelayed({
            // Go back to home — finish all and restart main
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finishAffinity()
        }, 1500)
    }

    // ===================== FAILED → RETRY OR SUPPORT POPUP =====================

    private fun onPaymentFailed() {
        setUiWaiting(false)
        failCount++

        if (failCount >= 2) {
            showSupportDialog()
        } else {
            // 1st fail — simple error, let them try again
            showStatus("Payment not verified. Please check your TID and try again.")
        }
    }

    private fun showSupportDialog() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24*dp).toInt(), (16*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
        }

        val msg = TextView(this).apply {
            text = "Contact our support team for help with your payment."
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#5F5E5A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (16*dp).toInt() }
        }
        layout.addView(msg)

        // Number 1 row
        layout.addView(makeNumberRow("0300-7951912", SUPPORT_NUMBER_1, dp))
        // Number 2 row
        layout.addView(makeNumberRow("0321-1119966", SUPPORT_NUMBER_2, dp))

        AlertDialog.Builder(this)
            .setTitle("Payment keeps failing?")
            .setView(layout)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun makeNumberRow(display: String, dialNumber: String, dp: Float): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10*dp).toInt() }
        }

        val numText = TextView(this).apply {
            text = display
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#111111"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val callBtn = android.widget.Button(this).apply {
            text = "Call"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#1565C0"))
            setPadding((16*dp).toInt(), (4*dp).toInt(), (16*dp).toInt(), (4*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))
                startActivity(callIntent)
            }
        }

        row.addView(numText)
        row.addView(callBtn)
        return row
    }

    // ===================== UI HELPERS =====================

    private fun setUiWaiting(waiting: Boolean) {
        binding.btnSubmitPayment.isEnabled = !waiting
        binding.etTransactionId.isEnabled = !waiting
        binding.etAmount.isEnabled = !waiting
    }

    private fun showStatus(message: String) {
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.remove()
    }
}