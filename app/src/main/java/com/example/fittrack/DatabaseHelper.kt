package com.example.fittrack

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class FirestoreDatabaseHelper {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirestoreDB"
    }

    /**
     * Helper: return the current user's UID if available, otherwise null.
     */
    private fun currentUid(): String? = auth.currentUser?.uid

    /**
     * Helper: find the document ID for a user by email.
     * If a document exists with ID == email, it returns email immediately (backwards compatibility).
     * Otherwise it queries `users` collection for a document whose "email" field matches.
     */
    // Replace the existing getDocIdByEmail(...) with this implementation
    fun getDocIdByEmail(email: String, onResult: (String?) -> Unit) {
        if (email.isBlank()) {
            onResult(null)
            return
        }

        // 1) Fast path: if current Firebase UID exists and a user doc with that UID exists, use it
        val uid = currentUid()
        if (!uid.isNullOrEmpty()) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { docByUid ->
                    if (docByUid.exists()) {
                        onResult(uid)
                    } else {
                        // 2) Next fast path: maybe app used email as document id historically
                        db.collection("users").document(email).get()
                            .addOnSuccessListener { docByEmailId ->
                                if (docByEmailId.exists()) {
                                    onResult(email)
                                } else {
                                    // 3) Fallback: query by field "email"
                                    db.collection("users")
                                        .whereEqualTo("email", email)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener { snapshot ->
                                            val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                                            onResult(docId)
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG, "getDocIdByEmail: query-by-field failed", e)
                                            onResult(null)
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "getDocIdByEmail: direct doc(email) check failed", e)
                                // try query-by-field as fallback
                                db.collection("users")
                                    .whereEqualTo("email", email)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                                        onResult(docId)
                                    }
                                    .addOnFailureListener {
                                        onResult(null)
                                    }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "getDocIdByEmail: direct doc(uid) check failed", e)
                    // If uid path failed for some reason, fallback to existing logic:
                    db.collection("users").document(email).get()
                        .addOnSuccessListener { docByEmailId ->
                            if (docByEmailId.exists()) {
                                onResult(email)
                            } else {
                                db.collection("users")
                                    .whereEqualTo("email", email)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                                        onResult(docId)
                                    }
                                    .addOnFailureListener {
                                        onResult(null)
                                    }
                            }
                        }
                        .addOnFailureListener {
                            // last resort: query-by-field
                            db.collection("users")
                                .whereEqualTo("email", email)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { snapshot ->
                                    val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                                    onResult(docId)
                                }
                                .addOnFailureListener {
                                    onResult(null)
                                }
                        }
                }
        } else {
            // If no UID (edge case), keep original behavior: check doc id == email then query by field
            db.collection("users").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        onResult(email)
                    } else {
                        db.collection("users")
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { snapshot ->
                                val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                                onResult(docId)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "getDocIdByEmail: query failure", e)
                                onResult(null)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "getDocIdByEmail: direct doc check failure", e)
                    db.collection("users")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                            onResult(docId)
                        }
                        .addOnFailureListener {
                            onResult(null)
                        }
                }
        }
    }

    // ---------------- USERS COLLECTION ----------------

    /**
     * Register user in Firestore.
     *
     * Note: This method accepts a `password` parameter for API parity with existing code,
     * but we DO NOT store passwords in Firestore. Passwords should be handled only by FirebaseAuth.
     *
     * This method will attempt to use the current FirebaseAuth UID as the document id.
     * If UID is not available, it will use the email as the document id.
     */
    fun registerUser(
        email: String,
        password: String, // accepted for compatibility but not stored
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        height: Double,
        weight: Double,
        userType: String = "user",
        onComplete: (Boolean) -> Unit
    ) {
        val uid = currentUid() ?: email // prefer UID, fallback to email

        val user = hashMapOf(
            "email" to email,
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
            "caloriesToday" to 0,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .set(user, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding user", e)
                onComplete(false)
            }
    }

    /**
     * Get user details by email. Returns the document data or null.
     * This method will locate the doc using the helper (supports UID docs or email-id docs).
     */
    fun getUserDetails(email: String, onResult: (Map<String, Any>?) -> Unit) {
        getDocIdByEmail(email) { docId ->
            if (docId == null) {
                onResult(null)
                return@getDocIdByEmail
            }
            db.collection("users").document(docId)
                .get()
                .addOnSuccessListener { document ->
                    onResult(document.data)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "getUserDetails failed", e)
                    onResult(null)
                }
        }
    }

    /**
     * Update user profile: merges provided fields into the user document.
     */
    fun updateUserProfile(
        email: String,
        firstName: String,
        lastName: String,
        age: Int,
        height: Double,
        gender: String,
        password: String, // kept in signature for parity — not stored
        onComplete: (Boolean) -> Unit
    ) {
        getDocIdByEmail(email) { docId ->
            if (docId == null) {
                onComplete(false)
                return@getDocIdByEmail
            }
            val updates = mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "age" to age,
                "height" to height,
                "gender" to gender
                // password intentionally omitted from Firestore
            )
            db.collection("users").document(docId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "updateUserProfile failed", e)
                    onComplete(false)
                }
        }
    }

    // ---------------- DAILY STATS ----------------

    /**
     * Save daily stats. Document id is {docId}_{yyyy-MM-dd} ensuring one-per-day per user.
     * If docId is not found by email, method will fallback to using email as id.
     */
    fun saveDailyStats(email: String, steps: Int, calories: Int, weight: Double) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            val stats = hashMapOf(
                "user_email" to email,
                "date" to date,
                "steps" to steps,
                "calories" to calories,
                "weight" to weight,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("daily_stats").document("${id}_$date")
                .set(stats, SetOptions.merge())
                .addOnSuccessListener { /* optional success log */ }
                .addOnFailureListener { e -> Log.e(TAG, "saveDailyStats failed", e) }
        }
    }

    fun getDailyStats(email: String, date: String, onResult: (Map<String, Any>?) -> Unit) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            db.collection("daily_stats").document("${id}_$date")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) onResult(document.data) else onResult(null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "getDailyStats failed", e)
                    onResult(null)
                }
        }
    }

    // ---------------- HOURLY STATS ----------------

    fun saveHourlyStats(email: String, hour: Int, steps: Int, calories: Int = 0, weight: Double = 0.0) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            val hourlyData = hashMapOf(
                "email" to email,
                "date" to date,
                "hour" to hour,
                "steps" to steps,
                "calories" to calories,
                "weight" to weight,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("hourly_stats").document("${id}_${date}_$hour")
                .set(hourlyData, SetOptions.merge())
                .addOnSuccessListener { /* ok */ }
                .addOnFailureListener { e -> Log.e(TAG, "saveHourlyStats failed", e) }
        }
    }

    fun getTodayHourlyStats(email: String, onResult: (List<Pair<Int, Int>>) -> Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
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
                .addOnFailureListener { e ->
                    Log.e(TAG, "getTodayHourlyStats failed", e)
                    onResult(emptyList())
                }
        }
    }



    // ---------------- GOAL UPDATES ----------------

    fun updateGoals(email: String, steps: Int, calories: Int, targetWeight: Double, onComplete: (Boolean) -> Unit) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            val updates = mapOf(
                "daily_steps_goal" to steps,
                "daily_calories_goal" to calories,
                "target_weight" to targetWeight
            )
            db.collection("users").document(id)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "updateGoals failed", e)
                    onComplete(false)
                }
        }
    }

    // ---------------- WEIGHT UPDATE ----------------

    fun updateDailyWeight(email: String, weight: Double, onComplete: (Boolean) -> Unit) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            val updates = mapOf("weightToday" to weight)
            db.collection("users").document(id)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "updateDailyWeight failed", e)
                    onComplete(false)
                }
        }
    }

    // ---------------- RESET PROGRESS ----------------

    fun resetDailyProgress(email: String) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            // first fetch current user data (to archive into daily_stats)
            db.collection("users").document(id).get()
                .addOnSuccessListener { userDoc ->
                    val user = userDoc.data
                    val steps = (user?.get("stepsToday") as? Long)?.toInt() ?: 0
                    val calories = (user?.get("caloriesToday") as? Long)?.toInt() ?: 0
                    val weight = (user?.get("weightToday") as? Double) ?: 0.0

                    // Save a daily_stats record
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val stats = hashMapOf(
                        "user_email" to (user?.get("email") as? String ?: ""),
                        "date" to date,
                        "steps" to steps,
                        "calories" to calories,
                        "weight" to weight,
                        "archivedAt" to System.currentTimeMillis()
                    )
                    db.collection("daily_stats").document("${id}_$date")
                        .set(stats, SetOptions.merge())

                    // reset counters
                    val resetData = mapOf("stepsToday" to 0, "caloriesToday" to 0)
                    db.collection("users").document(id)
                        .set(resetData, SetOptions.merge())
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "resetDailyProgress failed to fetch user", e)
                }
        }
    }

    // ---------------- EXTRA: fetch multiple users (example) ----------------
    fun fetchUsersByType(userType: String, onResult: (QuerySnapshot?) -> Unit) {
        db.collection("users")
            .whereEqualTo("userType", userType)
            .get()
            .addOnSuccessListener { snapshot -> onResult(snapshot) }
            .addOnFailureListener { e ->
                Log.e(TAG, "fetchUsersByType failed", e)
                onResult(null)
            }
    }
}
