package com.ebone.customeridapp.ui.paymentmethod

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.BuildConfig
import com.ebone.customeridapp.R
import com.ebone.customeridapp.data.AiPaymentInterpreter
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.data.OcrPaymentReader
import com.ebone.customeridapp.data.PaymentSource
import com.ebone.customeridapp.data.PaymentStatus
import com.ebone.customeridapp.data.PaymentRules
import com.ebone.customeridapp.data.PaymentTransaction
import com.ebone.customeridapp.data.SmsPaymentParser
import com.ebone.customeridapp.databinding.ActivityPaymentVerificationBinding
import kotlinx.coroutines.launch

/**
 * Screen 2 of the payment flow.
 *
 * Verification pipeline actually implemented on THIS screen (customer side):
 *   1. Customer manually enters the Transaction ID (T-ID) — always required.
 *   2. Customer optionally uploads a payment screenshot from their gallery.
 *      - OCR (ML Kit) reads the screenshot first.
 *      - If OCR confidently finds a TID/amount -> compare against the
 *        manually entered TID. Match -> green confirmation. Mismatch -> red
 *        English error, customer can re-upload.
 *      - If OCR text is ambiguous (no TID/amount pattern found) -> fall back
 *        to AI (OpenAI API) to re-read the same OCR text.
 *   3. On "Verify & Activate", a PaymentTransaction (status = PENDING) is
 *      written to Firestore.
 *
 * IMPORTANT — architecture note:
 *   The actual bank/wallet SMS (JazzCash, Easypaisa, SadaPay, Bank Alfalah,
 *   Raast ID...) arrive on the ADMIN'S phone, not the customer's — those
 *   accounts belong to the business. So the SMS-matching step does NOT
 *   happen in this app. It happens in the separate Ebone Admin Panel app,
 *   which reads its own SMS inbox, matches the TID against this Firestore
 *   PENDING transaction, and — if it matches — sets the customer's
 *   activationStatus to ACTIVE directly in Firestore. This screen just
 *   listens for that change (see showProcessingState()/TODO below).
 */
class PaymentVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        const val EXTRA_METHOD_NAME = "extra_method_name"
        const val EXTRA_AMOUNT = "extra_amount"
        private const val PROCESSING_MILLIS = 30 * 60 * 1000L // 30 minutes, per spec
    }

    private lateinit var binding: ActivityPaymentVerificationBinding
    private val repository by lazy { FirestoreRepository() }
    private var customerId: String = ""
    private var methodName: String = ""
    private var amount: Double = 0.0
    private var countDownTimer: CountDownTimer? = null
    private var transactionListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var screenshotVerified: Boolean = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handleScreenshotSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""
        methodName = intent.getStringExtra(EXTRA_METHOD_NAME) ?: "Payment"
        amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

        binding.tvMethodTitle.text = "$methodName Payment"

        // TODO: replace with real data once the Admin Panel's SMS-matching
        // side is wired up and reflects back into Firestore. Placeholder for now.
        binding.tvDetectedDateTime.text = "—"
        binding.tvDetectedAmount.text = "Rs. %.0f".format(if (amount > 0) amount else 1500.0)
        binding.tvDetectedName.text = "—"
        binding.tvDetectedPhone.text = "—"

        binding.btnBack.setOnClickListener { finish() }

        binding.btnUploadScreenshot.setOnClickListener {
            val tid = binding.etTransactionId.text.toString().trim()
            if (tid.isEmpty()) {
                showScreenshotStatus(getString(R.string.msg_enter_tid_first), isError = true)
                return@setOnClickListener
            }
            pickImageLauncher.launch("image/*")
        }

        binding.btnVerifyAndActivate.setOnClickListener { onVerifyClicked() }
    }

    private fun handleScreenshotSelected(uri: Uri) {
        val enteredTid = binding.etTransactionId.text.toString().trim()
        showScreenshotStatus(getString(R.string.msg_ocr_reading), isError = false)

        val bitmap: Bitmap = try {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } catch (e: Exception) {
            showScreenshotStatus(getString(R.string.msg_ai_failed), isError = true)
            return
        }

        OcrPaymentReader.readFromBitmap(
            bitmap,
            onSuccess = { parsed -> compareAndShowResult(parsed.rawText, enteredTid) },
            onAmbiguous = { rawText -> fallbackToAi(rawText, enteredTid) },
            onFailure = { fallbackToAi("", enteredTid) }
        )
    }

    private fun fallbackToAi(rawText: String, enteredTid: String) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            showScreenshotStatus(getString(R.string.msg_ai_failed), isError = true)
            return
        }
        showScreenshotStatus(getString(R.string.msg_ai_reading), isError = false)

        AiPaymentInterpreter.interpret(
            rawText = rawText,
            apiKey = apiKey,
            onResult = { parsed: SmsPaymentParser.ParsedResult ->
                runOnUiThread { compareAndShowResult(parsed.rawText.ifBlank { rawText }, enteredTid) }
            },
            onError = {
                runOnUiThread { showScreenshotStatus(getString(R.string.msg_ai_failed), isError = true) }
            }
        )
    }

    /**
     * Checks the manually entered TID against EVERY TID-looking candidate
     * found in the OCR text — a screenshot can show more than one
     * transaction. ALSO cross-checks the detected amount against the
     * billing amount (packagePrice, passed in as `amount`):
     *   - Underpaid  -> never approved, ask for the complete amount.
     *   - Slightly overpaid (+Rs 50 tolerance) -> approved.
     *   - Overpaid by more -> flagged, ask customer to verify their bill.
     */
    private fun compareAndShowResult(rawText: String, enteredTid: String) {
        val candidates = SmsPaymentParser.parseAllTidCandidates(rawText)
        val tidMatches = candidates.any { it.equals(enteredTid.trim(), ignoreCase = true) }

        if (!tidMatches) {
            screenshotVerified = false
            val foundText = if (candidates.isNotEmpty()) candidates.joinToString(", ") else "(none found)"
            showScreenshotStatus(
                "TID mismatch.\nYou entered: $enteredTid\nFound in screenshot: $foundText",
                isError = true
            )
            return
        }

        val detectedAmount = SmsPaymentParser.parse(rawText).amount
        if (detectedAmount != null && amount > 0) {
            val paymentStatus = PaymentRules.evaluatePaymentStatus(detectedAmount, amount)
            when (paymentStatus) {
                PaymentStatus.INSUFFICIENT -> {
                    screenshotVerified = false
                    showScreenshotStatus(
                        getString(R.string.msg_underpayment, "%.0f".format(amount)),
                        isError = true
                    )
                    return
                }
                PaymentStatus.OVERPAID -> {
                    screenshotVerified = false
                    showScreenshotStatus(
                        getString(R.string.msg_overpaid, "%.0f".format(amount)),
                        isError = true
                    )
                    return
                }
                else -> { /* VERIFIED -> fall through to success message below */ }
            }
        }

        screenshotVerified = true
        showScreenshotStatus(getString(R.string.msg_tid_match), isError = false)
    }

    private fun showScreenshotStatus(message: String, isError: Boolean) {
        binding.tvScreenshotStatus.visibility = View.VISIBLE
        binding.tvScreenshotStatus.text = message
        if (isError) {
            binding.tvScreenshotStatus.setBackgroundResource(R.drawable.bg_status_error)
            binding.tvScreenshotStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error_text))
        } else {
            binding.tvScreenshotStatus.setBackgroundResource(R.drawable.bg_status_success)
            binding.tvScreenshotStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success_text))
        }
    }

    private fun onVerifyClicked() {
        val tid = binding.etTransactionId.text.toString().trim()
        if (tid.isEmpty()) {
            binding.etTransactionId.error = getString(R.string.label_transaction_id)
            return
        }

        binding.btnVerifyAndActivate.isEnabled = false

        if (customerId.isNotEmpty()) {
            lifecycleScope.launch {
                // Block duplicate/fake entries: this TID must not already be
                // used by ANY customer (prevents re-submitting the same
                // payment, or one customer using another's TID).
                if (repository.isTidAlreadyUsed(tid)) {
                    showScreenshotStatus(
                        "This Transaction ID has already been used. Duplicate or fake entries are not allowed.",
                        isError = true
                    )
                    binding.btnVerifyAndActivate.isEnabled = true
                    return@launch
                }

                val transactionId = repository.recordPayment(
                    PaymentTransaction(
                        customerId = customerId,
                        source = mapMethodToSource(methodName),
                        amount = amount,
                        bankTransactionId = tid,
                        status = PaymentStatus.PENDING
                    )
                )
                startListeningForVerification(transactionId)
                showProcessingState()
            }
        } else {
            showProcessingState()
        }
    }

    /**
     * Real-time: the moment the Admin Panel's backend (SMS match) flips this
     * transaction to VERIFIED in Firestore, we react immediately — no need
     * to wait for the 30-minute countdown to finish. The countdown is only
     * a visual "time remaining" indicator, not a blocking wait.
     */
    private fun startListeningForVerification(transactionId: String) {
        transactionListener?.remove()
        transactionListener = repository.listenToTransactionStatus(transactionId) { status ->
            when (status) {
                PaymentStatus.VERIFIED -> {
                    countDownTimer?.cancel()
                    showSuccessState()
                }
                PaymentStatus.FAILED, PaymentStatus.INSUFFICIENT, PaymentStatus.OVERPAID -> {
                    countDownTimer?.cancel()
                    showScreenshotStatus(getString(R.string.msg_tid_mismatch), isError = true)
                }
                PaymentStatus.PENDING -> { /* still waiting, countdown keeps running */ }
            }
        }
    }

    private fun mapMethodToSource(name: String): PaymentSource = when (name) {
        "Easypaisa" -> PaymentSource.EASYPAISA
        "JazzCash" -> PaymentSource.JAZZCASH
        "SadaPay" -> PaymentSource.SADAPAY
        "Faysal Bank" -> PaymentSource.FAYSAL_BANK
        "Raast ID" -> PaymentSource.RAAST_ID
        "Bank Alfalah" -> PaymentSource.BANK_ALFALAH
        else -> PaymentSource.MANUAL_BANK
    }

    private fun showProcessingState() {
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusTitle.text = getString(R.string.status_processing_title)
        binding.tvStatusSubtitle.text = getString(R.string.status_processing_subtitle)

        // TODO: replace this local countdown with a Firestore listener on
        // transactions/{id}.status — the Admin Panel's SMS match is what
        // actually flips it to VERIFIED/ACTIVE. The countdown here is only
        // a visual placeholder until that listener is wired up.
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(PROCESSING_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvStatusTimer.text = "%02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                // 30 minutes passed with no verification yet — don't falsely
                // show success. Keep listening in the background; the
                // transactionListener will still fire the moment it's verified.
                binding.tvStatusTimer.text = "00:00"
                binding.tvStatusSubtitle.text = getString(R.string.status_still_processing)
            }
        }.start()
    }

    private fun showSuccessState() {
        binding.tvStatusTimer.text = "00:00"
        binding.tvStatusTitle.text = getString(R.string.status_success_title)
        binding.tvStatusSubtitle.text = getString(R.string.status_success_subtitle)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        transactionListener?.remove()
    }
}