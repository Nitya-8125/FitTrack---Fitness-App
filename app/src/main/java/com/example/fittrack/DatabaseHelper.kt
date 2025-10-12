package com.example.fittrack

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ftusers.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_USERS = "users"
        private const val TABLE_DAILY_STATS = "daily_stats"
        private const val TABLE_HOURLY_STATS = "daily_hourly_stats"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE,
                password TEXT,
                firstName TEXT,
                lastName TEXT,
                userType TEXT DEFAULT 'user',
                age INTEGER,
                gender TEXT,
                height REAL,
                weight REAL,
                daily_steps_goal INTEGER DEFAULT 10000,
                daily_calories_goal INTEGER DEFAULT 2000,
                target_weight REAL DEFAULT 70,
                stepsToday INTEGER DEFAULT 0,
                caloriesToday INTEGER DEFAULT 0,
                weightToday REAL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createUsersTable)

        val createDailyStatsTable = """
            CREATE TABLE $TABLE_DAILY_STATS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_email TEXT,
                date TEXT,
                steps INTEGER DEFAULT 0,
                calories INTEGER DEFAULT 0,
                weight REAL DEFAULT 0,
                UNIQUE(user_email, date)
            )
        """.trimIndent()
        db.execSQL(createDailyStatsTable)

        val createHourlyStatsTable = """
            CREATE TABLE $TABLE_HOURLY_STATS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT,
                date TEXT,
                hour INTEGER,
                steps INTEGER DEFAULT 0,
                calories INTEGER DEFAULT 0,
                weight REAL DEFAULT 0,
                UNIQUE(email, date, hour)
            )
        """.trimIndent()
        db.execSQL(createHourlyStatsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DAILY_STATS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HOURLY_STATS")
        onCreate(db)
    }

    // ---------------- User Functions ----------------
    fun registerUser(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        height: Double,
        weight: Double,
        userType: String = "user"
    ): Boolean {
        if (isEmailTaken(email)) return false
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("email", email)
            put("password", password)
            put("firstName", firstName)
            put("lastName", lastName)
            put("userType", userType)
            put("age", age)
            put("gender", gender)
            put("height", height)
            put("weight", weight)
            put("weightToday", weight)
            put("target_weight", weight)
            put("daily_steps_goal", 10000)
            put("daily_calories_goal", 2000)
        }
        val result = db.insert(TABLE_USERS, null, cv)
        db.close()
        return result != -1L
    }

    fun isEmailTaken(email: String): Boolean {
        val cursor = getUserByEmail(email)
        val exists = cursor?.count ?: 0 > 0
        cursor?.close()
        return exists
    }

    fun getUserByEmail(email: String): Cursor? {
        val db = readableDatabase
        return db.query(TABLE_USERS, null, "email = ?", arrayOf(email), null, null, null)
    }

    // Get full name safely
    fun getFullName(email: String): String {
        val cursor = getUserByEmail(email)
        val name = if (cursor != null && cursor.moveToFirst()) {
            val first = cursor.getString(cursor.getColumnIndexOrThrow("firstName"))
            val last = cursor.getString(cursor.getColumnIndexOrThrow("lastName"))
            "$first $last"
        } else {
            "Guest User"
        }
        cursor?.close()
        return name
    }

    fun updateDailyWeight(email: String, weight: Double): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply { put("weightToday", weight) }
        val result = db.update(TABLE_USERS, cv, "email = ?", arrayOf(email))
        db.close()
        return result > 0
    }

    fun updateGoals(email: String, steps: Int, calories: Int, targetWeight: Double): Boolean {
        resetDailyProgress(email)
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("daily_steps_goal", steps)
            put("daily_calories_goal", calories)
            put("target_weight", targetWeight)
        }
        val result = db.update(TABLE_USERS, cv, "email = ?", arrayOf(email))
        db.close()
        return result > 0
    }

    fun updateUserProfile(
        email: String,
        firstName: String,
        lastName: String,
        age: Int,
        height: Double,
        gender: String,
        password: String
    ): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("firstName", firstName)
            put("lastName", lastName)
            put("age", age)
            put("height", height)
            put("gender", gender)
            put("password", password)
        }
        val result = db.update(TABLE_USERS, cv, "email = ?", arrayOf(email))
        db.close()
        return result > 0
    }

    // ---------------- Daily Stats Functions ----------------
    fun saveDailyStats(email: String, date: String, steps: Int, calories: Int, weight: Double) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("user_email", email)
            put("date", date)
            put("steps", steps)
            put("calories", calories)
            put("weight", weight)
        }
        db.insertWithOnConflict(TABLE_DAILY_STATS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getDailyStats(email: String, date: String): Cursor? {
        val db = readableDatabase
        return db.query(
            TABLE_DAILY_STATS,
            arrayOf("steps", "calories", "weight"),
            "user_email=? AND date=?",
            arrayOf(email, date),
            null, null, null
        )
    }

    // ---------------- Hourly Stats Functions ----------------
    fun saveHourlyStats(
        email: String,
        date: String,
        hour: Int,
        steps: Int,
        calories: Int = 0,
        weight: Double = 0.0
    ) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("email", email)
            put("date", date)
            put("hour", hour)
            put("steps", steps)
            put("calories", calories)
            put("weight", weight)
        }
        db.insertWithOnConflict(TABLE_HOURLY_STATS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getTodayHourlyStats(email: String): List<Pair<Int, Int>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val list = mutableListOf<Pair<Int, Int>>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT hour, steps FROM $TABLE_HOURLY_STATS WHERE email=? AND date=? ORDER BY hour ASC",
            arrayOf(email, today)
        )
        if (cursor.moveToFirst()) {
            do {
                val hour = cursor.getInt(cursor.getColumnIndexOrThrow("hour"))
                val steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps"))
                list.add(Pair(hour, steps))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // ---------------- Reset Daily Progress ----------------
    fun resetDailyProgress(email: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cursor = getUserByEmail(email)
        if (cursor != null && cursor.moveToFirst()) {
            val oldSteps = cursor.getInt(cursor.getColumnIndexOrThrow("stepsToday"))
            val oldCalories = cursor.getInt(cursor.getColumnIndexOrThrow("caloriesToday"))
            val weightToday = cursor.getDouble(cursor.getColumnIndexOrThrow("weightToday"))
            saveDailyStats(email, today, oldSteps, oldCalories, weightToday)
        }
        cursor?.close()

        val db = writableDatabase
        val cv = ContentValues().apply {
            put("stepsToday", 0)
            put("caloriesToday", 0)
        }
        db.update(TABLE_USERS, cv, "email=?", arrayOf(email))
        db.close()
    }

    fun registerGoogleUser(email: String, firstName: String, lastName: String): Boolean {
        if (isEmailTaken(email)) return false
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("email", email)
            put("password", "") // No password for Google Sign-In
            put("firstName", firstName)
            put("lastName", lastName)
            put("userType", "user")
            put("age", 0)
            put("gender", "")
            put("height", 0.0)
            put("weight", 70.0)
            put("weightToday", 70.0)
            put("target_weight", 70.0)
            put("daily_steps_goal", 10000)
            put("daily_calories_goal", 2000)
            put("stepsToday", 0)
            put("caloriesToday", 0)
        }
        val result = db.insert(TABLE_USERS, null, cv)
        db.close()
        return result != -1L
    }

    fun getUserDetails(email: String): Cursor? {
        val db = readableDatabase
        return db.query(
            TABLE_USERS,
            null,
            "email = ?",
            arrayOf(email),
            null,
            null,
            null
        )
    }
}
