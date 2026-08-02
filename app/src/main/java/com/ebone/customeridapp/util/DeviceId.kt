package com.ebone.customeridapp.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/** Stable per-device identifier used to lock a Customer ID to one phone. */
object DeviceId {
    @SuppressLint("HardwareIds")
    fun get(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }
}
