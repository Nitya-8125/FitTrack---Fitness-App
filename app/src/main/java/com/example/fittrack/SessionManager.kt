package com.example.fittrack

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "fittrack_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_AGE = "age"
        private const val KEY_HEIGHT = "height"
        private const val KEY_USER_TYPE = "user_type"    // <- added
    }

    /**
     * Save login session.
     * `userType` is optional and defaults to "Fitness Member" to preserve existing calls.
     */
    fun saveLoginSession(
        firstName: String,
        lastName: String,
        email: String,
        age: Int,
        height: Int,
        userType: String = "Fitness Member"
    ) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_FIRST_NAME, firstName)
        editor.putString(KEY_LAST_NAME, lastName)
        editor.putString(KEY_EMAIL, email)
        editor.putInt(KEY_AGE, age)
        editor.putInt(KEY_HEIGHT, height)
        editor.putString(KEY_USER_TYPE, userType)
        editor.apply()
    }

    // Convenience setter for only user type (useful when profile updates userType separately)
    fun setUserType(userType: String) {
        prefs.edit().putString(KEY_USER_TYPE, userType).apply()
    }

    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // Get user email
    fun getUserEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    // Get first name
    fun getFirstName(): String? {
        return prefs.getString(KEY_FIRST_NAME, null)
    }

    // Get last name
    fun getLastName(): String? {
        return prefs.getString(KEY_LAST_NAME, null)
    }

    // Get full name convenience
    fun getFullName(): String {
        val f = getFirstName().orEmpty()
        val l = getLastName().orEmpty()
        return listOf(f, l).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "User" }
    }

    // Get age
    fun getAge(): Int {
        return prefs.getInt(KEY_AGE, 0)
    }

    // Get height
    fun getHeight(): Int {
        return prefs.getInt(KEY_HEIGHT, 0)
    }

    // Get user type (may be null)
    fun getUserType(): String? {
        return prefs.getString(KEY_USER_TYPE, null)
    }

    // Clear session (logout)
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
