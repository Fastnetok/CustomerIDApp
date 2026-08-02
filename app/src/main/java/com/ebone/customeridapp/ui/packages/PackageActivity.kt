package com.ebone.customeridapp.ui.packages

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ebone.customeridapp.data.FirestoreRepository
import com.ebone.customeridapp.databinding.ActivityPackageBinding
import com.ebone.customeridapp.ui.payment.PaymentActivity
import kotlinx.coroutines.launch

class PackageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }

    private lateinit var binding: ActivityPackageBinding
    private val repository by lazy { FirestoreRepository() }
    private lateinit var customerId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: run { finish(); return }

        loadCustomer()

        binding.btnPayNow.setOnClickListener {
            startActivity(
                Intent(this, PaymentActivity::class.java)
                    .putExtra(PaymentActivity.EXTRA_CUSTOMER_ID, customerId)
            )
        }
    }

    private fun loadCustomer() {
        lifecycleScope.launch {
            val customer = repository.getCustomer(customerId) ?: return@launch
            binding.tvPackageName.text = customer.packageId
            // Package Price is always shown prominently, per business rule.
            binding.tvPackagePrice.text = "Rs. ${customer.packagePrice}"
            binding.tvCurrentBalance.text = "Current Balance: Rs. ${customer.currentBalance}"
        }
    }
}
