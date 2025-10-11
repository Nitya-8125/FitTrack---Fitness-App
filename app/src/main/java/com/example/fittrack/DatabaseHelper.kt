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
        private const val DATABASE_VERSION = 2
        private const val TABLE_USERS = "users"
        private const val TABLE_DAILY_STATS = "daily_stats"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE,
                password TEXT,
                firstName TEXT,
                lastName TEXT,
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DAILY_STATS")
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
        weight: Double
    ): Boolean {
        if (isEmailTaken(email)) return false
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("email", email)
            put("password", password)
            put("firstName", firstName)
            put("lastName", lastName)
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

    fun updateDailyWeight(email: String, weight: Double): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply { put("weightToday", weight) }
        val result = db.update(TABLE_USERS, cv, "email = ?", arrayOf(email))
        db.close()
        return result > 0
    }

    fun updateGoals(email: String, steps: Int, calories: Int, targetWeight: Double): Boolean {
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

    fun registerGoogleUser(email: String, firstName: String, lastName: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("email", email)
            put("firstName", firstName)
            put("lastName", lastName)
            put("age", 0)
            put("height", 0.0)
            put("weight", 0.0)
            put("daily_steps_goal", 10000)
            put("daily_calories_goal", 2000)
            put("target_weight", 70.0)
            put("stepsToday", 0)
            put("caloriesToday", 0)
            put("weightToday", 0.0)
        }
        val result = db.insert("users", null, cv)
        db.close()
        return result != -1L
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

    fun getLast7DaysStats(email: String): List<Triple<String, Int, Int>> {
        val db = readableDatabase
        val calendar = Calendar.getInstance()
        val stats = mutableListOf<Triple<String, Int, Int>>()

        for (i in 6 downTo 0) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val cursor = db.query(
                TABLE_DAILY_STATS,
                arrayOf("steps", "calories"),
                "user_email = ? AND date = ?",
                arrayOf(email, date),
                null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps"))
                val calories = cursor.getInt(cursor.getColumnIndexOrThrow("calories"))
                stats.add(Triple(date, steps, calories))
            } else {
                stats.add(Triple(date, 0, 0))
            }
            cursor?.close()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        return stats.reversed()
    }

    fun getGoals(email: String): Cursor? {
        val db = this.readableDatabase
        // Replace "users" with your table name if different
        return db.rawQuery(
            "SELECT daily_steps_goal, daily_calories_goal, target_weight FROM users WHERE email = ?",
            arrayOf(email)
        )
    }

    fun updateGoals(email: String, steps: Int, calories: Int, targetWeight: Float): Boolean {
        val db = this.writableDatabase
        val cv = ContentValues().apply {
            put("daily_steps_goal", steps)
            put("daily_calories_goal", calories)
            put("target_weight", targetWeight)
        }
        val result = db.update("users", cv, "email = ?", arrayOf(email))
        return result > 0
    }

}
