package com.ebone.customeridapp.util

import android.content.Context
import org.json.JSONArray

/**
 * Stores the list of Customer IDs successfully registered (PIN-verified) on
 * THIS device, plus which one is currently selected on the Home dashboard.
 * Supports multiple connections in one household (e.g. Abbas001, Abbas002).
 */
object AccountStorage {

    private const val PREFS_NAME = "customer_accounts"
    private const val KEY_IDS = "registered_ids"
    private const val KEY_SELECTED = "selected_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRegisteredIds(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_IDS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getString(it) }
    }

    /** Adds a newly-registered Customer ID and makes it the selected one. */
    fun addAccount(context: Context, customerId: String) {
        val current = getRegisteredIds(context).toMutableList()
        if (!current.contains(customerId)) current.add(customerId)
        val array = JSONArray(current)
        prefs(context).edit()
            .putString(KEY_IDS, array.toString())
            .putString(KEY_SELECTED, customerId)
            .apply()
    }

    fun getSelectedId(context: Context): String? {
        val selected = prefs(context).getString(KEY_SELECTED, null)
        val ids = getRegisteredIds(context)
        // Fall back to the first registered account if the saved selection is stale.
        return selected?.takeIf { it in ids } ?: ids.firstOrNull()
    }

    fun setSelectedId(context: Context, customerId: String) {
        prefs(context).edit().putString(KEY_SELECTED, customerId).apply()
    }

    fun hasAnyAccount(context: Context): Boolean = getRegisteredIds(context).isNotEmpty()
}
