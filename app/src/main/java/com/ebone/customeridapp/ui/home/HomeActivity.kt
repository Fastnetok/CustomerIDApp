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

    private val repository by lazy {
        FirestoreRepository()
    }

    private lateinit var customerId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)

        setContentView(binding.root)

        com.ebone.customeridapp.update.VersionChecker
            .checkForUpdate(this)

        customerId =
            intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""

        if (customerId.isEmpty()) {

            loadPreviewData()

        } else {

            loadDashboard()
        }

        wireActions()
    }

    override fun onResume() {
        super.onResume()

        /*
         * Picks up the currently selected account.
         *
         * This is important for:
         *
         * Add Another Connection
         * Account Switching
         * Account Removal
         * Automatic Replacement
         */
        val selected =
            AccountStorage.getSelectedId(this)

        if (!selected.isNullOrEmpty() &&
            selected != customerId
        ) {

            customerId = selected

            loadDashboard()
        }

        /*
         * If no account exists anymore,
         * send the user back to Login.
         */
        if (selected.isNullOrEmpty() &&
            AccountStorage.hasAnyAccount(this).not()
        ) {

            if (customerId.isNotEmpty()) {

                startActivity(
                    Intent(
                        this,
                        LoginActivity::class.java
                    )
                )

                finish()
            }
        }
    }

    private fun loadPreviewData() {

        binding.tvCustomerId.text =
            "ABBAS001"

        binding.tvStatusChip.text =
            getString(R.string.status_active)

        binding.tvPackageSpeed.text =
            "6 Mbps"

        binding.tvIspProvider.text =
            "Powered by Ebone"

        binding.tvExpiryDate.text =
            "03 Sep"

        binding.tvValidTill.text =
            "Valid Till: 30 Aug 2026"
    }

    private fun loadDashboard() {

        lifecycleScope.launch {

            val customer =
                repository.getCustomer(customerId)
                    ?: return@launch

            binding.tvCustomerId.text =
                customer.customerId

            binding.tvPackageSpeed.text =
                customer.packageId

            binding.tvIspProvider.text =
                "Powered by ${
                    customer.ispProvider
                        .lowercase()
                        .replaceFirstChar {
                            it.uppercase()
                        }
                }"

            if (!customer.ispExpiryDate.isNullOrBlank()) {

                binding.layoutExpiryBadge.visibility =
                    android.view.View.VISIBLE

                binding.tvExpiryDate.text =
                    customer.ispExpiryDate

            } else {

                binding.layoutExpiryBadge.visibility =
                    android.view.View.GONE
            }

            when (customer.effectiveStatus()) {

                "ACTIVE" -> {

                    binding.tvStatusChip.text =
                        getString(R.string.status_active)

                    binding.tvStatusChip
                        .setBackgroundResource(
                            R.drawable.bg_chip_active
                        )

                    binding.tvStatusChip.setTextColor(
                        ContextCompat.getColor(
                            this@HomeActivity,
                            R.color.status_success_text
                        )
                    )
                }

                "PENDING_APPROVAL" -> {

                    binding.tvStatusChip.text =
                        getString(
                            R.string.status_pending_approval
                        )

                    binding.tvStatusChip
                        .setBackgroundResource(
                            R.drawable.bg_chip_pending
                        )
                }

                else -> {

                    binding.tvStatusChip.text =
                        getString(
                            R.string.status_disabled
                        )

                    binding.tvStatusChip
                        .setBackgroundResource(
                            R.drawable.bg_chip_disabled
                        )

                    binding.tvStatusChip.setTextColor(
                        ContextCompat.getColor(
                            this@HomeActivity,
                            R.color.status_error_text
                        )
                    )
                }
            }

            val paymentDue =
                customer.currentBalance <
                        customer.packagePrice

            binding.cardMakePayment.visibility =
                if (paymentDue) {

                    android.view.View.VISIBLE

                } else {

                    android.view.View.GONE
                }
        }
    }

    private fun wireActions() {

        binding.btnJoinNow.setOnClickListener {

            if (customerId.isEmpty()) {
                return@setOnClickListener
            }

            startActivity(
                Intent(
                    this,
                    PackageActivity::class.java
                ).putExtra(
                    PackageActivity.EXTRA_CUSTOMER_ID,
                    customerId
                )
            )
        }

        binding.btnPayNow.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    PaymentMethodActivity::class.java
                ).putExtra(
                    PaymentMethodActivity.EXTRA_CUSTOMER_ID,
                    customerId
                )
            )
        }

        binding.navHome.setOnClickListener {
            // Already on Home.
        }

        binding.navPayments.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    PaymentMethodActivity::class.java
                ).putExtra(
                    PaymentMethodActivity.EXTRA_CUSTOMER_ID,
                    customerId
                )
            )
        }

        binding.navHistory.setOnClickListener {
            // Future screen.
        }

        binding.navSupport.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SupportActivity::class.java
                )
            )
        }

        binding.navProfile.setOnClickListener {
            // Future screen.
        }

        /*
         * Tap Customer ID to open Multi ID manager.
         */
        binding.tvCustomerId.setOnClickListener {
            showAccountSwitcherDialog()
        }

        binding.btnMenu.setOnClickListener {
            showLanguageDialog()
        }

        binding.btnInfo.setOnClickListener {
            // TODO: notifications
        }
    }

    /**
     * Multi Account Manager.
     *
     * Existing behavior:
     * - Switch account
     * - Add another connection
     *
     * New behavior:
     * - Remove account
     * - Automatic replacement when the currently
     *   displayed account is removed.
     */
    private fun showAccountSwitcherDialog() {

        val accounts =
            AccountStorage.getRegisteredIds(this)

        if (accounts.isEmpty()) {

            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                )
            )

            finish()

            return
        }

        val options =
            accounts.toMutableList()

        /*
         * Existing Add Another Connection option.
         */
        options.add(
            getString(
                R.string.action_add_connection
            )
        )

        /*
         * New Remove Account option.
         */
        options.add("Remove Account")

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.label_switch_connection
                )
            )
            .setItems(
                options.toTypedArray()
            ) { _, which ->

                /*
                 * ADD CONNECTION
                 */
                if (which == accounts.size) {

                    startActivity(
                        Intent(
                            this,
                            LoginActivity::class.java
                        ).putExtra(
                            LoginActivity.EXTRA_ADD_MODE,
                            true
                        )
                    )

                    return@setItems
                }

                /*
                 * REMOVE ACCOUNT
                 */
                if (which == accounts.size + 1) {

                    showRemoveAccountDialog(accounts)

                    return@setItems
                }

                /*
                 * NORMAL ACCOUNT SWITCH
                 */
                val chosen =
                    accounts[which]

                if (chosen != customerId) {

                    AccountStorage.setSelectedId(
                        this,
                        chosen
                    )

                    customerId = chosen

                    loadDashboard()
                }
            }
            .show()
    }

    /**
     * Shows the account list for removal.
     */
    private fun showRemoveAccountDialog(
        accounts: List<String>
    ) {

        AlertDialog.Builder(this)
            .setTitle("Remove Account")
            .setItems(
                accounts.toTypedArray()
            ) { _, which ->

                val accountToRemove =
                    accounts[which]

                showRemoveConfirmation(
                    accountToRemove
                )
            }
            .show()
    }

    /**
     * Final confirmation before removing an account.
     */
    private fun showRemoveConfirmation(
        accountToRemove: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Remove Account?")
            .setMessage(
                "Do you want to remove $accountToRemove from this device?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->

                removeAccountAndReplace(
                    accountToRemove
                )
            }
            .show()
    }

    /**
     * Removes the account and applies the
     * Automatic Replacement Rule.
     */
    private fun removeAccountAndReplace(
        accountToRemove: String
    ) {

        val wasCurrentAccount =
            accountToRemove == customerId

        /*
         * AccountStorage handles the actual removal
         * and chooses the replacement account.
         */
        val replacement =
            AccountStorage.removeAccount(
                this,
                accountToRemove
            )

        /*
         * If the account removed was NOT the one
         * currently displayed, leave Dashboard unchanged.
         */
        if (!wasCurrentAccount) {

            showAccountRemovedMessage(
                accountToRemove
            )

            return
        }

        /*
         * CURRENT ACCOUNT WAS REMOVED.
         *
         * Automatic replacement available.
         */
        if (!replacement.isNullOrEmpty()) {

            customerId =
                replacement

            loadDashboard()

            showAutomaticReplacementMessage(
                replacement
            )

            return
        }

        /*
         * No accounts remain.
         */
        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            )
        )

        finish()
    }

    private fun showAccountRemovedMessage(
        removedAccount: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Account Removed")
            .setMessage(
                "$removedAccount has been removed successfully."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAutomaticReplacementMessage(
        replacementAccount: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Account Removed")
            .setMessage(
                "Account removed successfully.\n\n" +
                        "Dashboard automatically switched to:\n" +
                        replacementAccount
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Language switcher.
     */
    private fun showLanguageDialog() {

        val languages =
            arrayOf(
                "English",
                "اردو (Urdu)"
            )

        val codes =
            arrayOf(
                "en",
                "ur"
            )

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.label_select_language
                )
            )
            .setItems(
                languages
            ) { _, which ->

                AppCompatDelegate
                    .setApplicationLocales(
                        LocaleListCompat
                            .forLanguageTags(
                                codes[which]
                            )
                    )
            }
            .show()
    }
}