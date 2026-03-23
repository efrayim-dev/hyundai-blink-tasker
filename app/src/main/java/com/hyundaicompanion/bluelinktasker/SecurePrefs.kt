package com.hyundaicompanion.bluelinktasker

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(v) = prefs.edit().putString(KEY_PASSWORD, v).apply()

    var pin: String?
        get() = prefs.getString(KEY_PIN, null)
        set(v) = prefs.edit().putString(KEY_PIN, v).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(v) = prefs.edit().putString(KEY_ACCESS, v).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(v) = prefs.edit().putString(KEY_REFRESH, v).apply()

    var tokenExpiresAtEpochSec: Long
        get() = prefs.getLong(KEY_EXPIRES, 0L)
        set(v) = prefs.edit().putLong(KEY_EXPIRES, v).apply()

    var vehicleVin: String?
        get() = prefs.getString(KEY_VIN, null)
        set(v) = prefs.edit().putString(KEY_VIN, v).apply()

    var vehicleRegId: String?
        get() = prefs.getString(KEY_REG_ID, null)
        set(v) = prefs.edit().putString(KEY_REG_ID, v).apply()

    var vehicleGeneration: String?
        get() = prefs.getString(KEY_GEN, null)
        set(v) = prefs.edit().putString(KEY_GEN, v).apply()

    var vehicleBrandIndicator: String?
        get() = prefs.getString(KEY_BRAND_IND, null)
        set(v) = prefs.edit().putString(KEY_BRAND_IND, v).apply()

    var vehicleNickname: String?
        get() = prefs.getString(KEY_NICK, null)
        set(v) = prefs.edit().putString(KEY_NICK, v).apply()

    var engineType: String?
        get() = prefs.getString(KEY_ENGINE, null)
        set(v) = prefs.edit().putString(KEY_ENGINE, v).apply()

    var taskerSecret: String?
        get() = prefs.getString(KEY_TASKER_SECRET, null)
        set(v) = prefs.edit().putString(KEY_TASKER_SECRET, v).apply()

    var requireBiometricForManualActions: Boolean
        get() = prefs.getBoolean(KEY_BIO_MANUAL, false)
        set(v) = prefs.edit().putBoolean(KEY_BIO_MANUAL, v).apply()

    var setupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(v) = prefs.edit().putBoolean(KEY_SETUP_DONE, v).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "bluelink_secure_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PIN = "pin"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "token_expires_at"
        private const val KEY_VIN = "vehicle_vin"
        private const val KEY_REG_ID = "vehicle_reg_id"
        private const val KEY_GEN = "vehicle_gen"
        private const val KEY_BRAND_IND = "vehicle_brand_ind"
        private const val KEY_NICK = "vehicle_nick"
        private const val KEY_ENGINE = "vehicle_engine"
        private const val KEY_TASKER_SECRET = "tasker_secret"
        private const val KEY_BIO_MANUAL = "bio_manual"
        private const val KEY_SETUP_DONE = "setup_done"
    }
}
