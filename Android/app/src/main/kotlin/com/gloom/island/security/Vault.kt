package com.gloom.island.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gloom.island.automation.A11yIme
import com.gloom.island.automation.ScreenAutomation
import com.gloom.island.service.GloomIslandAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Keystore AES-256-GCM encrypted credential vault.
 *
 * Allows storing credentials securely on-device and provides an autofill hook
 * for local automation tasks. Passwords never pass through the AI model or network;
 * they are committed directly into target fields through the accessibility IME.
 */
object Vault {

    private const val TAG = "GloomVault"
    private const val PREFS_FILE = "gloom_vault_secure"
    private const val KEY_ENTRIES = "vault_entries_json"
    data class UserProfile(
        val fullName: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val address: String = "",
        val city: String = "",
        val state: String = "",
        val zipCode: String = "",
        val country: String = "",
        val company: String = ""
    )

    private const val KEY_PROFILE = "vault_user_profile_json"

    data class VaultEntry(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val account: String,
        val secret: String,
        val targetPackageOrDomain: String = "",
        val updatedAtMs: Long = System.currentTimeMillis()
    )

    private fun getSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Synchronized
    fun listEntries(context: Context): List<VaultEntry> {
        return try {
            val prefs = getSecurePrefs(context)
            val jsonStr = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<VaultEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VaultEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        account = obj.optString("account", ""),
                        secret = obj.optString("secret", ""),
                        targetPackageOrDomain = obj.optString("target", ""),
                        updatedAtMs = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.updatedAtMs }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading vault entries", e)
            emptyList()
        }
    }

    @Synchronized
    fun saveEntry(context: Context, entry: VaultEntry) {
        try {
            val current = listEntries(context).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == entry.id }
            if (existingIndex >= 0) {
                current[existingIndex] = entry.copy(updatedAtMs = System.currentTimeMillis())
            } else {
                current.add(0, entry.copy(updatedAtMs = System.currentTimeMillis()))
            }

            val arr = JSONArray()
            for (item in current) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("account", item.account)
                    put("secret", item.secret)
                    put("target", item.targetPackageOrDomain)
                    put("updatedAt", item.updatedAtMs)
                }
                arr.put(obj)
            }
            getSecurePrefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving vault entry", e)
        }
    }

    @Synchronized
    fun deleteEntry(context: Context, id: String) {
        try {
            val current = listEntries(context).filter { it.id != id }
            val arr = JSONArray()
            for (item in current) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("account", item.account)
                    put("secret", item.secret)
                    put("target", item.targetPackageOrDomain)
                    put("updatedAt", item.updatedAtMs)
                }
                arr.put(obj)
            }
            getSecurePrefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting vault entry", e)
        }
    }

    fun findMatching(context: Context, query: String): VaultEntry? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val entries = listEntries(context)
        return entries.firstOrNull {
            it.title.lowercase().contains(q) ||
                it.targetPackageOrDomain.lowercase().contains(q) ||
                it.account.lowercase().contains(q)
        }
    }

    /**
     * Fills credentials into the active screen's username and password fields.
     * The password is injected directly through the Accessibility IME, ensuring
     * it never appears in logs, transcript buffers, or LLM prompts.
     */
    fun autofillActiveScreen(context: Context, entry: VaultEntry): Boolean {
        val svc = GloomIslandAccessibilityService.instance ?: return false
        val elements = ScreenAutomation.refresh()
        if (elements.isEmpty()) return false

        // Look for username/email field and password field
        val passwordElement = elements.firstOrNull {
            it.editable && (
                it.className.contains("password", ignoreCase = true) ||
                it.label.contains("password", ignoreCase = true) ||
                it.viewId.contains("password", ignoreCase = true)
            )
        }

        val accountElement = elements.firstOrNull {
            it.editable && it != passwordElement && (
                it.label.contains("email", ignoreCase = true) ||
                it.label.contains("user", ignoreCase = true) ||
                it.label.contains("phone", ignoreCase = true) ||
                it.viewId.contains("user", ignoreCase = true) ||
                it.viewId.contains("email", ignoreCase = true)
            )
        } ?: elements.firstOrNull { it.editable && it != passwordElement }

        var success = false
        if (accountElement != null && entry.account.isNotBlank()) {
            ScreenAutomation.tap(accountElement.eid)
            android.os.SystemClock.sleep(250)
            A11yIme.replace(svc, entry.account)
            success = true
            android.os.SystemClock.sleep(300)
        }

        if (passwordElement != null && entry.secret.isNotBlank()) {
            ScreenAutomation.tap(passwordElement.eid)
            android.os.SystemClock.sleep(250)
            A11yIme.replace(svc, entry.secret)
            success = true
        }

        return success
    }

    fun getUserProfile(context: Context): UserProfile {
        return try {
            val prefs = getSecurePrefs(context)
            val jsonStr = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
            val obj = JSONObject(jsonStr)
            UserProfile(
                fullName = obj.optString("fullName", ""),
                firstName = obj.optString("firstName", ""),
                lastName = obj.optString("lastName", ""),
                email = obj.optString("email", ""),
                phone = obj.optString("phone", ""),
                address = obj.optString("address", ""),
                city = obj.optString("city", ""),
                state = obj.optString("state", ""),
                zipCode = obj.optString("zipCode", ""),
                country = obj.optString("country", ""),
                company = obj.optString("company", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading user profile", e)
            UserProfile()
        }
    }

    fun saveUserProfile(context: Context, profile: UserProfile) {
        try {
            val obj = JSONObject().apply {
                put("fullName", profile.fullName)
                put("firstName", profile.firstName)
                put("lastName", profile.lastName)
                put("email", profile.email)
                put("phone", profile.phone)
                put("address", profile.address)
                put("city", profile.city)
                put("state", profile.state)
                put("zipCode", profile.zipCode)
                put("country", profile.country)
                put("company", profile.company)
            }
            getSecurePrefs(context).edit().putString(KEY_PROFILE, obj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
        }
    }

    /**
     * Scans active screen (e.g. Chrome / Web page) and fills all detected form fields
     * using the user's stored profile data securely.
     */
    fun autofillWebForm(context: Context): Int {
        val svc = GloomIslandAccessibilityService.instance ?: return 0
        val profile = getUserProfile(context)
        val elements = ScreenAutomation.refresh()
        if (elements.isEmpty()) return 0

        var filledCount = 0
        val editables = elements.filter { it.editable }

        for (el in editables) {
            val l = el.label.lowercase()
            val id = el.viewId.lowercase()
            val combined = "$l $id"

            val valueToFill = when {
                combined.contains("first name") || combined.contains("fname") -> profile.firstName.ifEmpty { profile.fullName.split(" ").firstOrNull().orEmpty() }
                combined.contains("last name") || combined.contains("lname") -> profile.lastName.ifEmpty { profile.fullName.split(" ").drop(1).joinToString(" ") }
                combined.contains("full name") || combined.contains("your name") || (combined.contains("name") && !combined.contains("user")) -> profile.fullName
                combined.contains("email") || combined.contains("e-mail") -> profile.email
                combined.contains("phone") || combined.contains("mobile") || combined.contains("tel") -> profile.phone
                combined.contains("street") || combined.contains("address") -> profile.address
                combined.contains("city") || combined.contains("town") -> profile.city
                combined.contains("state") || combined.contains("province") -> profile.state
                combined.contains("zip") || combined.contains("postal") || combined.contains("pincode") -> profile.zipCode
                combined.contains("country") -> profile.country
                combined.contains("company") || combined.contains("organization") -> profile.company
                else -> ""
            }

            if (valueToFill.isNotBlank()) {
                ScreenAutomation.tap(el.eid)
                android.os.SystemClock.sleep(220)
                val ok = ScreenAutomation.typeTextUnique(valueToFill, clearFirst = true)
                if (ok.success) {
                    filledCount++
                }
                android.os.SystemClock.sleep(200)
            }
        }
        return filledCount
    }
}
