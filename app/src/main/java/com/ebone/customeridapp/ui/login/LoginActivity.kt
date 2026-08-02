package com.ebone.customeridapp.ui.login

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.databinding.ActivityLoginBinding
import com.ebone.customeridapp.ui.home.HomeActivity
import com.ebone.customeridapp.ui.location.LocationHelper
import com.ebone.customeridapp.util.AccountStorage
import com.ebone.customeridapp.util.DeviceId
import kotlinx.coroutines.launch

/**
 * Registration / Login screen.
 *
 * First-time flow: customer (or the Employee installing the app) enters the
 * Customer ID + the 6-digit One-Time PIN that Admin generated. On success:
 *   - The ID is permanently locked to this device in Firestore.
 *   - The PIN is consumed (cleared) so it can never be reused.
 *   - The ID is saved locally (AccountStorage) — this screen is skipped on
 *     every future app launch.
 *
 * Multiple connections in one household: launching this screen again later
 * (via Home's "+ Add Another Connection") with a NEW Customer ID + its own
 * PIN adds a second/third account to the same device without removing the
 * first — see AccountStorage.addAccount().
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        /** Pass true when launching from Home's "+ Add Another Connection" — bypasses the auto-skip. */
        const val EXTRA_ADD_MODE = "extra_add_mode"
    }

    private lateinit var binding: ActivityLoginBinding
    private val repository by lazy { FirestoreRepository() }

    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureLocationThenProceed(pendingCustomerId)
        else goToHome(pendingCustomerId)
    }

    private var pendingCustomerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Already have at least one registered account on this device -> skip straight to Home.
        // (Unless we were explicitly launched to ADD another connection.)
        val isAddMode = intent.getBooleanExtra(EXTRA_ADD_MODE, false)
        if (!isAddMode && AccountStorage.hasAnyAccount(this)) {
            goToHome(AccountStorage.getSelectedId(this) ?: "")
            return
        }

        if (isAddMode) {
            binding.tvSkip.visibility = android.view.View.GONE
        }

        binding.btnLogin.setOnClickListener { handleRegister() }

        // Testing/preview convenience only — lets you see the Home dashboard
        // with sample data before any real Firestore customers exist.
        binding.tvSkip.setOnClickListener { goToHome("") }
    }

    private fun handleRegister() {
        val customerId = binding.etCustomerId.text.toString().trim()
        val pin = binding.etPin.text.toString().trim()

        if (customerId.isEmpty() || pin.length != 6) {
            showError("Please enter a valid Customer ID and 6-digit PIN.")
            return
        }

        binding.btnLogin.isEnabled = false
        val deviceId = DeviceId.get(this)

        lifecycleScope.launch {
            when (repository.claimCustomerId(customerId, pin, deviceId)) {
                FirestoreRepository.ClaimResult.Success,
                FirestoreRepository.ClaimResult.AlreadyRegisteredOnThisDevice -> {
                    AccountStorage.addAccount(this@LoginActivity, customerId)
                    pendingCustomerId = customerId
                    requestLocationPermissionAndCapture(customerId)
                }
                FirestoreRepository.ClaimResult.NotFound ->
                    showError("Customer ID not found. Please check with support.")
                FirestoreRepository.ClaimResult.WrongPin ->
                    showError("Incorrect PIN. Please check the PIN provided by Admin.")
                FirestoreRepository.ClaimResult.LinkedToAnotherDevice ->
                    showError("This ID is already registered on another device. Contact support if this is an error.")
            }
            binding.btnLogin.isEnabled = true
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = android.view.View.VISIBLE
    }

    private fun requestLocationPermissionAndCapture(customerId: String) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            captureLocationThenProceed(customerId)
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun captureLocationThenProceed(customerId: String) {
        LocationHelper(this).captureOnFirstActivationOnly(customerId) {
            showRegistrationConfirmation(customerId)
        }
    }

    /**
     * Confirmed via discussion: right after a successful registration, the
     * customer/employee should immediately see which company + package this
     * device is now linked to — a clear "wow, my connection is set up
     * correctly" moment, rather than silently landing on Home.
     */
    private fun showRegistrationConfirmation(customerId: String) {
        lifecycleScope.launch {
            val customer = repository.getCustomer(customerId)
            val packageLabel = customer?.packageId ?: "—"
            val providerLabel = customer?.ispProvider ?: "—"

            androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                .setTitle("Registered Successfully! 🎉")
                .setMessage("Customer ID: $customerId\nPackage: $packageLabel\nProvider: $providerLabel")
                .setCancelable(false)
                .setPositiveButton("Continue") { _, _ -> goToHome(customerId) }
                .show()
        }
    }

    private fun goToHome(customerId: String) {
        if (intent.getBooleanExtra(EXTRA_ADD_MODE, false)) {
            // Underlying HomeActivity is still on the back stack — its onResume()
            // will notice the newly-selected account and reload automatically.
            finish()
            return
        }
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_CUSTOMER_ID, customerId)
        )
        finish()
    }
}
