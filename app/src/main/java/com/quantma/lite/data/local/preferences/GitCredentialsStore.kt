package com.quantma.lite.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for Git PAT tokens (Phase 4 v1.3.0).
 * Uses EncryptedSharedPreferences with AES256 encryption.
 */
@Singleton
class GitCredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FILE_NAME = "git_credentials"
        private const val KEY_PAT_TOKEN = "pat_token"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getToken(): String = prefs.getString(KEY_PAT_TOKEN, "") ?: ""

    fun setToken(token: String) {
        prefs.edit().putString(KEY_PAT_TOKEN, token).apply()
    }

    fun hasToken(): Boolean = getToken().isNotBlank()
}
