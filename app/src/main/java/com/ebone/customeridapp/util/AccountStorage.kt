package com.ebone.customeridapp.util

import android.content.Context
import org.json.JSONArray

/**
 * Stores the list of Customer IDs successfully registered (PIN-verified) on
 * THIS device, plus which one is currently selected on the Home dashboard.
 *
 * Supports multiple connections in one household.
 *
 * Automatic Replacement Rule:
 * If the currently selected account is removed, the next available account
 * automatically becomes selected.
 */
object AccountStorage {

    private const val PREFS_NAME = "customer_accounts"
    private const val KEY_IDS = "registered_ids"
    private const val KEY_SELECTED = "selected_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns all registered Customer IDs.
     */
    fun getRegisteredIds(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_IDS, "[]") ?: "[]"

        val array = JSONArray(raw)

        return (0 until array.length()).map {
            array.getString(it)
        }
    }

    /**
     * Adds a newly registered Customer ID and makes it the selected account.
     */
    fun addAccount(
        context: Context,
        customerId: String
    ) {
        val current = getRegisteredIds(context).toMutableList()

        if (!current.contains(customerId)) {
            current.add(customerId)
        }

        val array = JSONArray(current)

        prefs(context)
            .edit()
            .putString(KEY_IDS, array.toString())
            .putString(KEY_SELECTED, customerId)
            .apply()
    }

    /**
     * Returns the currently selected Customer ID.
     *
     * If the saved selected account no longer exists,
     * the first available account is automatically selected.
     */
    fun getSelectedId(context: Context): String? {

        val selected =
            prefs(context).getString(KEY_SELECTED, null)

        val ids = getRegisteredIds(context)

        val validSelected =
            selected?.takeIf { it in ids }

        if (validSelected != null) {
            return validSelected
        }

        val fallback = ids.firstOrNull()

        prefs(context)
            .edit()
            .putString(KEY_SELECTED, fallback)
            .apply()

        return fallback
    }

    /**
     * Changes the currently selected account.
     */
    fun setSelectedId(
        context: Context,
        customerId: String
    ) {
        val ids = getRegisteredIds(context)

        if (customerId !in ids) return

        prefs(context)
            .edit()
            .putString(KEY_SELECTED, customerId)
            .apply()
    }

    /**
     * Removes an account.
     *
     * IMPORTANT:
     * If the removed account is currently displayed on Dashboard,
     * another available account is automatically selected.
     *
     * Replacement priority:
     *
     * 1. Account immediately after the removed account
     * 2. If none exists, previous account
     * 3. If no accounts remain, selected account becomes null
     *
     * Returns:
     * - New selected Customer ID
     * - null if no account remains
     */
    fun removeAccount(
        context: Context,
        customerId: String
    ): String? {

        val current =
            getRegisteredIds(context).toMutableList()

        if (!current.contains(customerId)) {
            return getSelectedId(context)
        }

        val selected =
            prefs(context).getString(KEY_SELECTED, null)

        val removedIndex =
            current.indexOf(customerId)

        // Remove the requested account.
        current.remove(customerId)

        // No accounts remain.
        if (current.isEmpty()) {

            prefs(context)
                .edit()
                .remove(KEY_IDS)
                .remove(KEY_SELECTED)
                .apply()

            return null
        }

        /*
         * If the removed account was NOT the currently selected account,
         * keep the current Dashboard account unchanged.
         */
        if (selected != customerId) {

            val stillSelected =
                selected?.takeIf { it in current }

            val finalSelected =
                stillSelected ?: current.first()

            prefs(context)
                .edit()
                .putString(
                    KEY_IDS,
                    JSONArray(current).toString()
                )
                .putString(
                    KEY_SELECTED,
                    finalSelected
                )
                .apply()

            return finalSelected
        }

        /*
         * The Dashboard account was removed.
         *
         * Automatic Replacement:
         *
         * Example:
         * [ABBAS001, ABBAS002, ABBAS003]
         *
         * Remove ABBAS002
         * -> ABBAS003 becomes selected.
         *
         * Remove ABBAS003
         * -> ABBAS001 becomes selected.
         */
        val replacementIndex =
            if (removedIndex < current.size) {
                removedIndex
            } else {
                current.lastIndex
            }

        val replacement =
            current[replacementIndex]

        prefs(context)
            .edit()
            .putString(
                KEY_IDS,
                JSONArray(current).toString()
            )
            .putString(
                KEY_SELECTED,
                replacement
            )
            .apply()

        return replacement
    }

    /**
     * Returns true if at least one account exists.
     */
    fun hasAnyAccount(context: Context): Boolean {
        return getRegisteredIds(context).isNotEmpty()
    }
}