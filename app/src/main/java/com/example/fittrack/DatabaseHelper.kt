package com.example.fittrack

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class FirestoreDatabaseHelper {

    private val db = FirebaseFirestore.getInstance()

    // ---------------- USERS COLLECTION ----------------

    fun registerUser(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        height: Double,
        weight: Double,
        userType: String = "user",
        onComplete: (Boolean) -> Unit
    ) {
        val user = hashMapOf(
            "email" to email,
            "password" to password,
            "firstName" to firstName,
            "lastName" to lastName,
            "userType" to userType,
            "age" to age,
            "gender" to gender,
            "height" to height,
            "weight" to weight,
            "weightToday" to weight,
            "target_weight" to weight,
            "daily_steps_goal" to 10000,
            "daily_calories_goal" to 2000,
            "stepsToday" to 0,
            "caloriesToday" to 0
        )

        db.collection("users").document(email)
            .set(user)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e("FirestoreDB", "Error adding user", e)
                onComplete(false)
            }
    }

    fun getUserDetails(email: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) onResult(document.data)
                else onResult(null)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun updateUserProfile(
        email: String,
        firstName: String,
        lastName: String,
        age: Int,
        height: Double,
        gender: String,
        password: String,
        onComplete: (Boolean) -> Unit
    ) {
        val updates = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "age" to age,
            "height" to height,
            "gender" to gender,
            "password" to password
        )
        db.collection("users").document(email)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // ---------------- DAILY STATS ----------------

    fun saveDailyStats(email: String, steps: Int, calories: Int, weight: Double) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val stats = hashMapOf(
            "user_email" to email,
            "date" to date,
            "steps" to steps,
            "calories" to calories,
            "weight" to weight
        )
        db.collection("daily_stats").document("${email}_$date")
            .set(stats, SetOptions.merge())
    }

    fun getDailyStats(email: String, date: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("daily_stats").document("${email}_$date")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) onResult(document.data)
                else onResult(null)
            }
            .addOnFailureListener { onResult(null) }
    }

    // ---------------- HOURLY STATS ----------------

    fun saveHourlyStats(email: String, hour: Int, steps: Int, calories: Int = 0, weight: Double = 0.0) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hourlyData = hashMapOf(
            "email" to email,
            "date" to date,
            "hour" to hour,
            "steps" to steps,
            "calories" to calories,
            "weight" to weight
        )
        db.collection("hourly_stats").document("${email}_${date}_$hour")
            .set(hourlyData, SetOptions.merge())
    }

    fun getTodayHourlyStats(email: String, onResult: (List<Pair<Int, Int>>) -> Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("hourly_stats")
            .whereEqualTo("email", email)
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = mutableListOf<Pair<Int, Int>>()
                for (doc in snapshot.documents) {
                    val hour = (doc.getLong("hour") ?: 0L).toInt()
                    val steps = (doc.getLong("steps") ?: 0L).toInt()
                    list.add(Pair(hour, steps))
                }
                list.sortBy { it.first }
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    // ---------------- GOAL UPDATES ----------------

    fun updateGoals(email: String, steps: Int, calories: Int, targetWeight: Double, onComplete: (Boolean) -> Unit) {
        val updates = mapOf(
            "daily_steps_goal" to steps,
            "daily_calories_goal" to calories,
            "target_weight" to targetWeight
        )
        db.collection("users").document(email)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // ---------------- WEIGHT UPDATE ----------------

    fun updateDailyWeight(email: String, weight: Double, onComplete: (Boolean) -> Unit) {
        val updates = mapOf("weightToday" to weight)
        db.collection("users").document(email)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // ---------------- RESET PROGRESS ----------------

    fun resetDailyProgress(email: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        getUserDetails(email) { user ->
            user?.let {
                val steps = (it["stepsToday"] as? Long)?.toInt() ?: 0
                val calories = (it["caloriesToday"] as? Long)?.toInt() ?: 0
                val weight = (it["weightToday"] as? Double) ?: 0.0
                saveDailyStats(email, steps, calories, weight)
            }

            val resetData = mapOf("stepsToday" to 0, "caloriesToday" to 0)
            db.collection("users").document(email)
                .set(resetData, SetOptions.merge())
        }
    }
}
