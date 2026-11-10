package com.nostr.tvframe.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed EncryptedSharedPreferences for storing SMB / NAS Username, Password,
 * and Domain credentials on Android TV 12+.
 */
data class SmbCredentials(
    val domain: String = "",
    val username: String = "",
    val password: String = ""
)

class SmbCredentialStore(context: Context) {

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            "smb_encrypted_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(domain: String, username: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_DOMAIN, domain.trim())
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getCredentials(): SmbCredentials {
        return SmbCredentials(
            domain = encryptedPrefs.getString(KEY_DOMAIN, "") ?: "",
            username = encryptedPrefs.getString(KEY_USERNAME, "") ?: "",
            password = encryptedPrefs.getString(KEY_PASSWORD, "") ?: ""
        )
    }

    fun clearCredentials() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DOMAIN = "smb_domain"
        private const val KEY_USERNAME = "smb_username"
        private const val KEY_PASSWORD = "smb_password"
    }
}
