package com.example.fittrack

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    companion object {
        private const val PREF_NAME = "fittrack_session"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_FIRST_NAME = "firstName"
        private const val KEY_LAST_NAME = "lastName"
        private const val KEY_EMAIL = "email"
        private const val KEY_AGE = "age"
        private const val KEY_HEIGHT = "height"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()

    fun saveLoginSession(firstName: String, lastName: String, email: String, age: Int, height: Int) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_FIRST_NAME, firstName)
        editor.putString(KEY_LAST_NAME, lastName)
        editor.putString(KEY_EMAIL, email)
        editor.putInt(KEY_AGE, age)
        editor.putInt(KEY_HEIGHT, height)
        editor.apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getFirstName(): String? = prefs.getString(KEY_FIRST_NAME, null)
    fun getLastName(): String? = prefs.getString(KEY_LAST_NAME, null)
    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getUserAge(): Int = prefs.getInt(KEY_AGE, 0)
    fun getUserHeight(): Int = prefs.getInt(KEY_HEIGHT, 0)

    fun getFullName(): String {
        val first = getFirstName() ?: ""
        val last = getLastName() ?: ""
        return "$first $last".trim()
    }

    fun logout() {
        editor.clear()
        editor.apply()
    }
}

//package com.example.fittrack
//
//import android.content.Context
//import android.content.SharedPreferences
//
//class SessionManager(context: Context) {
//
//    companion object {
//        private const val PREF_NAME = "fittrack_session"
//        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
//        private const val KEY_FIRST_NAME = "firstName"
//        private const val KEY_LAST_NAME = "lastName"
//        private const val KEY_EMAIL = "email"
//        private const val KEY_AGE = "age"
//        private const val KEY_HEIGHT = "height"
//    }
//
//    private val prefs: SharedPreferences =
//        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//
//    private val editor: SharedPreferences.Editor = prefs.edit()
//
//    // ✅ Save user session (first + last name separately)
//    fun saveLoginSession(
//        firstName: String,
//        lastName: String,
//        email: String,
//        age: Int,
//        height: Int
//    ) {
//        editor.putBoolean(KEY_IS_LOGGED_IN, true)
//        editor.putString(KEY_FIRST_NAME, firstName)
//        editor.putString(KEY_LAST_NAME, lastName)
//        editor.putString(KEY_EMAIL, email)
//        editor.putInt(KEY_AGE, age)
//        editor.putInt(KEY_HEIGHT, height)
//        editor.apply()
//    }
//
//    // ✅ Check if logged in
//    fun isLoggedIn(): Boolean {
//        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
//    }
//
//    // ✅ Getters
//    fun getFirstName(): String? = prefs.getString(KEY_FIRST_NAME, null)
//    fun getLastName(): String? = prefs.getString(KEY_LAST_NAME, null)
//    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)
//    fun getUserAge(): Int = prefs.getInt(KEY_AGE, 0)
//    fun getUserHeight(): Int = prefs.getInt(KEY_HEIGHT, 0)
//
//    // ✅ Full Name
//    fun getFullName(): String {
//        val first = getFirstName() ?: ""
//        val last = getLastName() ?: ""
//        return "$first $last".trim()
//    }
//
//    // ✅ Logout (clear session)
//    fun logout() {
//        editor.clear()
//        editor.apply()
//    }
//}
//
//
////package com.example.fittrack
////
////import android.content.Context
////import android.content.SharedPreferences
////
////class SessionManager(context: Context) {
////
////    companion object {
////        private const val PREF_NAME = "fittrack_session"
////        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
////        private const val KEY_NAME = "fullName"
////        private const val KEY_EMAIL = "email"
////        private const val KEY_AGE = "age"
////        private const val KEY_HEIGHT = "height"
////    }
////
////    private val prefs: SharedPreferences =
////        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
////
////    private val editor: SharedPreferences.Editor = prefs.edit()
////
////    // ✅ Save user session
////    fun saveLoginSession(fullName: String, email: String, age: Int, height: Int) {
////        editor.putBoolean(KEY_IS_LOGGED_IN, true)
////        editor.putString(KEY_NAME, fullName)
////        editor.putString(KEY_EMAIL, email)
////        editor.putInt(KEY_AGE, age)
////        editor.putInt(KEY_HEIGHT, height)
////        editor.apply()
////    }
////
////
////    // ✅ Check if logged in
////    fun isLoggedIn(): Boolean {
////        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
////    }
////
////    // ✅ Getters
////    fun getUserName(): String? = prefs.getString(KEY_NAME, null)
////    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)
////    fun getUserAge(): Int = prefs.getInt(KEY_AGE, 0)
////    fun getUserHeight(): Int = prefs.getInt(KEY_HEIGHT, 0)
////
////    // ✅ Logout (clear session)
////    fun logout() {
////        editor.clear()
////        editor.apply()
////    }
////}
