package com.ebone.customeridapp.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.R
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.data.effectiveStatus
import com.ebone.customeridapp.databinding.ActivityHomeBinding
import com.ebone.customeridapp.ui.packages.PackageActivity
import com.ebone.customeridapp.ui.paymentmethod.PaymentMethodActivity
import com.ebone.customeridapp.ui.support.SupportActivity
import com.ebone.customeridapp.ui.login.LoginActivity
import com.ebone.customeridapp.util.AccountStorage
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }

    private lateinit var binding: ActivityHomeBinding
    private val repository by lazy { FirestoreRepository() }
    private lateinit var customerId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.ebone.customeridapp.update.VersionChecker.checkForUpdate(this)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""

        if (customerId.isEmpty()) {
            // Design-preview mode: no Firebase/login wired up yet, show sample data
            // matching the reference screenshot so the UI can be checked immediately.
            loadPreviewData()
        } else {
            loadDashboard()
        }
        wireActions()
    }

    override fun onResume() {
        super.onResume()
        // Picks up the currently-selected account (handles switching accounts,
        // or a newly-added second connection returning from LoginActivity).
        val selected = AccountStorage.getSelectedId(this)
        if (!selected.isNullOrEmpty() && selected != customerId) {
            customerId = selected
            loadDashboard()
        }
    }

    private fun loadPreviewData() {
        binding.tvCustomerId.text = "ABBAS001"
        binding.tvStatusChip.text = getString(R.string.status_active)
        binding.tvPackageSpeed.text = "6 Mbps"
        binding.tvIspProvider.text = "Powered by Ebone"
        binding.tvExpiryDate.text = "03 Sep"
        binding.tvValidTill.text = "Valid Till: 30 Aug 2026"
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            val customer = repository.getCustomer(customerId) ?: return@launch

            binding.tvCustomerId.text = customer.customerId
            binding.tvPackageSpeed.text = customer.packageId // e.g. "6 Mbps"
            binding.tvIspProvider.text = "Powered by ${customer.ispProvider.lowercase().replaceFirstChar { it.uppercase() }}"
            if (!customer.ispExpiryDate.isNullOrBlank()) {
                binding.layoutExpiryBadge.visibility = android.view.View.VISIBLE
                binding.tvExpiryDate.text = customer.ispExpiryDate
            } else {
                binding.layoutExpiryBadge.visibility = android.view.View.GONE
            }

            // Status is derived from the billing cycle (electricity-bill style):
            // Active until billingCycleDays after lastPaymentDate, then Disabled.
            // NOTE: this client-side check is for display only — real enforcement
            // must happen via a scheduled Cloud Function on the backend.
            when (customer.effectiveStatus()) {
                "ACTIVE" -> {
                    binding.tvStatusChip.text = getString(R.string.status_active)
                    binding.tvStatusChip.setBackgroundResource(R.drawable.bg_chip_active)
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_success_text))
                }
                "PENDING_APPROVAL" -> {
                    binding.tvStatusChip.text = getString(R.string.status_pending_approval)
                    binding.tvStatusChip.setBackgroundResource(R.drawable.bg_chip_pending)
                }
                else -> { // DISABLED
                    binding.tvStatusChip.text = getString(R.string.status_disabled)
                    binding.tvStatusChip.setBackgroundResource(R.drawable.bg_chip_disabled)
                    binding.tvStatusChip.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_error_text))
                }
            }

            // Make Payment card is only relevant once real billing data is wired up;
            // for now it always shows — hide it later based on customer.currentBalance vs packagePrice.
            val paymentDue = customer.currentBalance < customer.packagePrice
            binding.cardMakePayment.visibility =
                if (paymentDue) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun wireActions() {
        binding.btnJoinNow.setOnClickListener {
            if (customerId.isEmpty()) return@setOnClickListener // preview mode: Firebase not wired yet
            startActivity(
                Intent(this, PackageActivity::class.java)
                    .putExtra(PackageActivity.EXTRA_CUSTOMER_ID, customerId)
            )
        }

        binding.btnPayNow.setOnClickListener {
            startActivity(
                Intent(this, PaymentMethodActivity::class.java)
                    .putExtra(PaymentMethodActivity.EXTRA_CUSTOMER_ID, customerId)
            )
        }

        binding.navHome.setOnClickListener { /* already on Home */ }
        binding.navPayments.setOnClickListener {
            startActivity(
                Intent(this, PaymentMethodActivity::class.java)
                    .putExtra(PaymentMethodActivity.EXTRA_CUSTOMER_ID, customerId)
            )
        }
        // navHistory, navProfile -> TODO: future screens
        binding.navHistory.setOnClickListener { }
        binding.navSupport.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }
        binding.navProfile.setOnClickListener { }

        binding.tvCustomerId.setOnClickListener { showAccountSwitcherDialog() }

        binding.btnMenu.setOnClickListener {
            showLanguageDialog()
        }
        binding.btnInfo.setOnClickListener {
            // TODO: show notifications list
        }
    }

    /**
     * Lets the customer switch between multiple registered connections on
     * this device (e.g. Abbas001, Abbas002 for a household with 2 lines),
     * or add a new one — which always requires an Admin-issued PIN, so
     * customers can never self-register extra fake IDs.
     */
    private fun showAccountSwitcherDialog() {
        val accounts = AccountStorage.getRegisteredIds(this)
        val options = accounts.toMutableList().apply { add(getString(R.string.action_add_connection)) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_switch_connection))
            .setItems(options.toTypedArray()) { _, which ->
                if (which == accounts.size) {
                    // "+ Add Another Connection" -> back to Login for a new ID + PIN.
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .putExtra(LoginActivity.EXTRA_ADD_MODE, true)
                    )
                } else {
                    val chosen = accounts[which]
                    if (chosen != customerId) {
                        AccountStorage.setSelectedId(this, chosen)
                        customerId = chosen
                        loadDashboard()
                    }
                }
            }
            .show()
    }

    /**
     * Simple English/Urdu switcher using AndroidX's per-app language API.
     * Persists automatically across app restarts — no extra storage code needed.
     */
    private fun showLanguageDialog() {
        val languages = arrayOf("English", "اردو (Urdu)")
        val codes = arrayOf("en", "ur")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_select_language))
            .setItems(languages) { _, which ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(codes[which]))
            }
            .show()
    }
}