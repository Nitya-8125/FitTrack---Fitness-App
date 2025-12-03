package com.example.fittrack

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.emptyMap

// NEW: For chart + today stats with reinstall support
data class DailyStat(
    val date: String = "",
    val steps: Long = 0L,
    val distance: Double = 0.0,
    val calories: Double = 0.0
)

class FirestoreDatabaseHelper {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirestoreDB"
    }

    /** return current firebase uid if available */
    private fun currentUid(): String? = auth.currentUser?.uid

    // NEW: shortcut to current user document (users/{uid})
    private fun userDoc() = currentUid()?.let { uid ->
        db.collection("users").document(uid)
    }

    // NEW: helper for today's date string
    private fun todayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Resolve the document id for a user given their email.
     * Tries (in order):
     *  - current UID (if exists as user doc)
     *  - email-as-doc-id (legacy)
     *  - query users where email == provided email (returns first match)
     */
    fun getDocIdByEmail(email: String, onResult: (String?) -> Unit) {
        if (email.isBlank()) {
            onResult(null)
            return
        }

        val uid = currentUid()
        if (!uid.isNullOrEmpty()) {
            // check uid doc
            db.collection("users").document(uid).get()
                .addOnSuccessListener { docByUid ->
                    if (docByUid.exists()) {
                        Log.d(TAG, "getDocIdByEmail: found user doc by UID=$uid")
                        onResult(uid)
                    } else {
                        // check legacy email-as-id
                        db.collection("users").document(email).get()
                            .addOnSuccessListener { docByEmailId ->
                                if (docByEmailId.exists()) {
                                    Log.d(TAG, "getDocIdByEmail: found user doc by email-as-id=$email")
                                    onResult(email)
                                } else {
                                    // fallback query by field
                                    db.collection("users")
                                        .whereEqualTo("email", email)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener { snapshot ->
                                            val docId = if (!snapshot.isEmpty) snapshot.documents[0].id else null
                                            Log.d(TAG, "getDocIdByEmail: query-by-field result docId=$docId for email=$email")
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
                    // fallback: email-as-id then query-by-field
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
            // no UID available: check email-as-id then query-by-field
            db.collection("users").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) onResult(email)
                    else {
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

    // ---------------- USERS ----------------

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
        val uid = currentUid() ?: email
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

    fun getUserDetails(email: String, onResult: (Map<String, Any>?) -> Unit) {
        getDocIdByEmail(email) { docId ->
            if (docId == null) { onResult(null); return@getDocIdByEmail }
            db.collection("users").document(docId)
                .get()
                .addOnSuccessListener { document -> onResult(document.data) }
                .addOnFailureListener { e -> Log.e(TAG, "getUserDetails failed", e); onResult(null) }
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
        getDocIdByEmail(email) { docId ->
            if (docId == null) { onComplete(false); return@getDocIdByEmail }
            val updates = mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "age" to age,
                "height" to height,
                "gender" to gender
            )
            db.collection("users").document(docId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e -> Log.e(TAG, "updateUserProfile failed", e); onComplete(false) }
        }
    }

    // ---------------- DAILY / HOURLY STATS (existing top-level collections) ----------------

    fun saveDailyStats(email: String, steps: Int, calories: Int, weight: Double) {

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 1) Calculate distance (for chart)
        val stepLength = 0.762
        val distanceKm = steps * stepLength / 1000.0

        // 2) Write into UID-based dailyStats (CHART READS THIS)
        saveTodayStats(
            steps = steps.toLong(),
            distance = distanceKm,
            calories = calories.toDouble()
        )

        // 3) (Optional) Keep legacy daily_stats for old features
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            val stats = mapOf(
                "user_email" to email,
                "date" to date,
                "steps" to steps,
                "calories" to calories,
                "weight" to weight,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("daily_stats").document("${id}_$date")
                .set(stats, SetOptions.merge())
        }
    }



    fun getDailyStats(email: String, date: String, onResult: (Map<String, Any>?) -> Unit) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            db.collection("daily_stats").document("${id}_$date")
                .get()
                .addOnSuccessListener { document -> if (document.exists()) onResult(document.data) else onResult(null) }
                .addOnFailureListener { e -> Log.e(TAG, "getDailyStats failed", e); onResult(null) }
        }
    }

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
                .addOnSuccessListener { }
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
                .addOnFailureListener { e -> Log.e(TAG, "getTodayHourlyStats failed", e); onResult(emptyList()) }
        }
    }

    // ---------------- NEW: UID-based stats for reinstall + chart ----------------
    // These are the functions your new HomeFragment will call:
    //  - saveTodayStats(...)
    //  - getTodayStats(...)
    //  - getLastNDaysStats(...)

    /**
     * Save or update today's stats for the **currently logged-in Firebase user**.
     * Stored at: users/{uid}/dailyStats/{yyyy-MM-dd}
     */
    fun saveTodayStats(
        steps: Long,
        distance: Double,
        calories: Double,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val userDoc = userDoc()
        if (userDoc == null) {
            onComplete?.invoke(false)
            return
        }

        val date = todayString()
        val data = hashMapOf(
            "date" to date,
            "steps" to steps,
            "distance" to distance,
            "calories" to calories
        )

        userDoc.collection("dailyStats")
            .document(date)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "saveTodayStats: success for uid=${currentUid()} date=$date")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "saveTodayStats: failure", e)
                onComplete?.invoke(false)
            }
    }

    /**
     * Get today's stats for the **currently logged-in Firebase user**.
     * Used on app open / reinstall to continue from old data.
     * Returns null if no data for today.
     */
    fun getTodayStats(onResult: (DailyStat?) -> Unit) {
        val userDoc = userDoc()
        if (userDoc == null) {
            onResult(null)
            return
        }

        val date = todayString()
        userDoc.collection("dailyStats")
            .document(date)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val steps = doc.getLong("steps") ?: 0L
                    val distance = doc.getDouble("distance") ?: 0.0
                    val calories = doc.getDouble("calories") ?: 0.0
                    onResult(DailyStat(date, steps, distance, calories))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getTodayStats: failure", e)
                onResult(null)
            }
    }

    /**
     * Get last [days] stats for the chart for the **currently logged-in user**.
     * Reads from users/{uid}/dailyStats collection.
     */
    fun getLastNDaysStats(days: Int, onResult: (List<DailyStat>) -> Unit) {
        val userDoc = userDoc()
        if (userDoc == null) {
            onResult(emptyList())
            return
        }

        userDoc.collection("dailyStats")
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    val date = doc.getString("date") ?: return@mapNotNull null
                    val steps = doc.getLong("steps") ?: 0L
                    val distance = doc.getDouble("distance") ?: 0.0
                    val calories = doc.getDouble("calories") ?: 0.0
                    DailyStat(date, steps, distance, calories)
                }.sortedBy { it.date }

                val result = if (list.size > days) list.takeLast(days) else list
                onResult(result)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getLastNDaysStats: failure", e)
                onResult(emptyList())
            }
    }

    // ---------------- MIGRATION: email-as-doc-id -> uid ----------------

    /**
     * If the app previously stored user documents keyed by email (email-as-doc-id),
     * and the user now signs in with FirebaseAuth (UID), copy/merge the profile
     * + today's daily + hourly stats into the UID document so future reads succeed.
     *
     * Non-blocking; onComplete(true) if migration completed or wasn't needed; false on error.
     */
    fun migrateEmailDocToUid(email: String, uid: String, onComplete: (Boolean) -> Unit) {
        if (email.isBlank() || uid.isBlank() || email == uid) {
            onComplete(true)
            return
        }
        val usersCol = db.collection("users")
        val dailyCol = db.collection("daily_stats")
        val hourlyCol = db.collection("hourly_stats")

        usersCol.document(email).get()
            .addOnSuccessListener { emailDoc ->
                if (!emailDoc.exists()) {
                    // nothing to migrate
                    onComplete(true)
                    return@addOnSuccessListener
                }

                // explicit typed emptyMap to avoid ambiguity
                val emailData = emailDoc.data ?: emptyMap<String, Any>()

                // merge profile into uid doc
                usersCol.document(uid)
                    .set(emailData + mapOf("email" to email), SetOptions.merge())
                    .addOnSuccessListener {
                        // copy today's daily_stats if present
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val emailDailyId = "${email}_$today"
                        val uidDailyId = "${uid}_$today"

                        dailyCol.document(emailDailyId).get()
                            .addOnSuccessListener { dailyDoc ->
                                if (dailyDoc.exists()) {
                                    // make emptyMap typed explicitly to match expected Map<String, Any>
                                    dailyCol.document(uidDailyId)
                                        .set(dailyDoc.data ?: emptyMap<String, Any>(), SetOptions.merge())
                                }
                                // copy hourly docs for today (0..23)
                                val tasks = mutableListOf<com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot>>()
                                for (h in 0..23) {
                                    tasks.add(hourlyCol.document("${email}_${today}_$h").get())
                                }
                                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks)
                                    .addOnSuccessListener { results ->
                                        for (snap in results) {
                                            if (snap != null && snap.exists()) {
                                                val data = snap.data ?: emptyMap<String, Any>()
                                                val dataWithEmail = data.toMutableMap()
                                                dataWithEmail["email"] = email
                                                hourlyCol.document("${uid}_${today}_${snap.getLong("hour")?.toInt() ?: 0}")
                                                    .set(dataWithEmail, SetOptions.merge())
                                            }
                                        }
                                        onComplete(true)
                                    }
                                    .addOnFailureListener {
                                        // hourly copy failed, but profile/daily already copied
                                        onComplete(true)
                                    }
                            }
                            .addOnFailureListener {
                                // daily copy failed but profile copied
                                onComplete(true)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "migrateEmailDocToUid: failed to merge profile to UID doc", e)
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "migrateEmailDocToUid: failed to read email doc", e)
                onComplete(false)
            }
    }

    // ---------------- OTHER (goals/weight/reset) ----------------

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
                .addOnFailureListener { e -> Log.e(TAG, "updateGoals failed", e); onComplete(false) }
        }
    }

    fun updateDailyWeight(email: String, weight: Double, onComplete: (Boolean) -> Unit) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            val updates = mapOf("weightToday" to weight)
            db.collection("users").document(id)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e -> Log.e(TAG, "updateDailyWeight failed", e); onComplete(false) }
        }
    }

    fun resetDailyProgress(email: String) {
        getDocIdByEmail(email) { docId ->
            val id = docId ?: email
            db.collection("users").document(id).get()
                .addOnSuccessListener { userDoc ->
                    val user = userDoc.data
                    val steps = (user?.get("stepsToday") as? Long)?.toInt() ?: 0
                    val calories = (user?.get("caloriesToday") as? Long)?.toInt() ?: 0
                    val weight = (user?.get("weightToday") as? Double) ?: 0.0

                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val stats = hashMapOf(
                        "user_email" to (user?.get("email") as? String ?: ""),
                        "date" to date,
                        "steps" to steps,
                        "calories" to calories,
                        "weight" to weight,
                        "archivedAt" to System.currentTimeMillis()
                    )
                    db.collection("daily_stats").document("${id}_$date").set(stats, SetOptions.merge())

                    val resetData = mapOf("stepsToday" to 0, "caloriesToday" to 0)
                    db.collection("users").document(id).set(resetData, SetOptions.merge())
                }
                .addOnFailureListener { e -> Log.e(TAG, "resetDailyProgress failed to fetch user", e) }
        }
    }

    fun fetchUsersByType(userType: String, onResult: (QuerySnapshot?) -> Unit) {
        db.collection("users")
            .whereEqualTo("userType", userType)
            .get()
            .addOnSuccessListener { snapshot -> onResult(snapshot) }
            .addOnFailureListener { e -> Log.e(TAG, "fetchUsersByType failed", e); onResult(null) }
    }
}
