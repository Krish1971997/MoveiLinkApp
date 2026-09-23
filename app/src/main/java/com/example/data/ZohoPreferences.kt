package com.example.data

import android.content.Context
import android.content.SharedPreferences

class ZohoPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zoho_prefs", Context.MODE_PRIVATE)

    var clientId: String
        get() = prefs.getString("client_id", "") ?: ""
        set(value) = prefs.edit().putString("client_id", value).apply()

    var clientSecret: String
        get() = prefs.getString("client_secret", "") ?: ""
        set(value) = prefs.edit().putString("client_secret", value).apply()

    var refreshToken: String
        get() = prefs.getString("refresh_token", "") ?: ""
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var folderId: String
        get() = prefs.getString("folder_id", "") ?: ""
        set(value) = prefs.edit().putString("folder_id", value).apply()

    var fileName: String
        get() = prefs.getString("file_name", "movies") ?: "movies"
        set(value) = prefs.edit().putString("file_name", value).apply()

    var defaultExtension: String
        get() = prefs.getString("default_extension", "xlsx") ?: "xlsx"
        set(value) = prefs.edit().putString("default_extension", value).apply()

    var accountsServer: String
        get() = prefs.getString("accounts_server", "https://accounts.zoho.com") ?: "https://accounts.zoho.com"
        set(value) = prefs.edit().putString("accounts_server", value).apply()

    var apiServer: String
        get() = prefs.getString("api_server", "https://workdrive.zohoapis.com") ?: "https://workdrive.zohoapis.com"
        set(value) = prefs.edit().putString("api_server", value).apply()

    var clearOldDataBeforeUpload: Boolean
        get() = prefs.getBoolean("clear_old_data", true)
        set(value) = prefs.edit().putBoolean("clear_old_data", value).apply()

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean("is_app_lock_enabled", false)
        set(value) = prefs.edit().putBoolean("is_app_lock_enabled", value).apply()

    var appLockPasscode: String
        get() = prefs.getString("app_lock_passcode", "1234") ?: "1234"
        set(value) = prefs.edit().putString("app_lock_passcode", value).apply()
}
