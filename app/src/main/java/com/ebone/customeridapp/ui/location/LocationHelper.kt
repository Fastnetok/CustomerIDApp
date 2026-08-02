package com.ebone.customeridapp.ui.location

import android.annotation.SuppressLint
import android.content.Context
import com.ebone.customeridapp.data.FirestoreRepository
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Workflow:
 *  1. App checks Firestore: does this customer already have locationCapturedAt?
 *  2. If NOT -> request current GPS fix ONCE and save lat/lng + timestamp to Firebase.
 *  3. If YES -> never touch location again (first activation is permanent).
 *
 * Call captureOnFirstActivationOnly() right after successful login / package activation.
 */
class LocationHelper(
    private val context: Context,
    private val repository: FirestoreRepository = FirestoreRepository()
) {

    @SuppressLint("MissingPermission") // caller must have already requested runtime permission
    fun captureOnFirstActivationOnly(customerId: String, onDone: (Boolean) -> Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        CoroutineScope(Dispatchers.IO).launch {
            val existing = repository.getCustomer(customerId)
            if (existing?.locationCapturedAt != null) {
                // Already captured once — do nothing further.
                onDone(false)
                return@launch
            }

            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location == null) {
                    onDone(false)
                    return@addOnSuccessListener
                }
                CoroutineScope(Dispatchers.IO).launch {
                    repository.saveLocationIfFirstActivation(
                        customerId,
                        location.latitude,
                        location.longitude
                    )
                    onDone(true)
                }
            }.addOnFailureListener {
                onDone(false)
            }
        }
    }
}
