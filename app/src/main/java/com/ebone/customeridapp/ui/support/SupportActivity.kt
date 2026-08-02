package com.ebone.customeridapp.ui.support

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ebone.customeridapp.databinding.ActivitySupportBinding

class SupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // WhatsApp helpline — opens WhatsApp chat directly, falls back to dialer.
        binding.cardWhatsapp.setOnClickListener {
            val phone = "923007951912" // country code + number, no leading 0/dashes
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0300-7951912")))
            }
        }

        // Complaint line — opens the phone dialer.
        binding.cardComplaint.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0321-1119966")))
        }
    }
}
