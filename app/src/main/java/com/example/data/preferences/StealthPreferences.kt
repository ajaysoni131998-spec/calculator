package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class StealthPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stealth_vault_prefs", Context.MODE_PRIVATE)

    var secretCode: String
        get() = prefs.getString(KEY_SECRET_CODE, "1319") ?: "1319"
        set(value) = prefs.edit().putString(KEY_SECRET_CODE, value.trim()).apply()

    var decoyCode: String
        get() = prefs.getString(KEY_DECOY_CODE, "0000") ?: "0000"
        set(value) = prefs.edit().putString(KEY_DECOY_CODE, value.trim()).apply()

    var myUserId: String
        get() {
            var id = prefs.getString(KEY_MY_USER_ID, null)
            if (id == null) {
                val randomNum = (1000..9999).random()
                id = "#GHOST-$randomNum"
                prefs.edit().putString(KEY_MY_USER_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_MY_USER_ID, value.trim()).apply()

    var myDisplayName: String
        get() {
            var name = prefs.getString(KEY_MY_DISPLAY_NAME, null)
            if (name == null) {
                name = "Stealth User"
                prefs.edit().putString(KEY_MY_DISPLAY_NAME, name).apply()
            }
            return name
        }
        set(value) = prefs.edit().putString(KEY_MY_DISPLAY_NAME, value.trim()).apply()

    var myPassword: String
        get() = prefs.getString(KEY_MY_PASSWORD, "ghost123") ?: "ghost123"
        set(value) = prefs.edit().putString(KEY_MY_PASSWORD, value).apply()

    var defaultDestructSeconds: Int
        get() = prefs.getInt(KEY_DEFAULT_DESTRUCT_SEC, 10) // 10 seconds default
        set(value) = prefs.edit().putInt(KEY_DEFAULT_DESTRUCT_SEC, value).apply()

    var lockOnMinimize: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ON_MINIMIZE, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_ON_MINIMIZE, value).apply()

    var isInitialSetupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SECRET_CODE = "key_secret_code"
        private const val KEY_DECOY_CODE = "key_decoy_code"
        private const val KEY_MY_USER_ID = "key_my_user_id"
        private const val KEY_MY_DISPLAY_NAME = "key_my_display_name"
        private const val KEY_MY_PASSWORD = "key_my_password"
        private const val KEY_DEFAULT_DESTRUCT_SEC = "key_default_destruct_sec"
        private const val KEY_LOCK_ON_MINIMIZE = "key_lock_on_minimize"
        private const val KEY_SETUP_DONE = "key_setup_done"
    }
}
