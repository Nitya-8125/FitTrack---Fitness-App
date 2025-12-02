package com.example.fittrack.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fittrack.R
import com.example.fittrack.SessionManager
import com.example.fittrack.ui.profile.ProfileFragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.fittrack.FirestoreDatabaseHelper

class HomeFragment : Fragment(), SensorEventListener {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var session: SessionManager

    private var tvGreeting: TextView? = null
    private var tvDate: TextView? = null
    private var tvCurrentSteps: TextView? = null
    private var tvSteps: TextView? = null
    private var tvCalories: TextView? = null
    private var tvWeight: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressCalories: ProgressBar? = null
    private var chartDaily: LineChart? = null

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var totalSteps: Float = 0f
    private var userWeight: Double = 70.0
    private var email: String = ""
    private var prefs: android.content.SharedPreferences? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeUpdater: Runnable? = null
    private var goalCompleted = false

    private var stepsGoal = 10000
    private var caloriesGoal = 2000

    // --- auth state listener and last known email to detect changes ---
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var lastKnownEmail: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Use context-safe initialization for session/prefs/sensors
        context?.let { ctx ->
            session = SessionManager(ctx)
            prefs = ctx.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
            sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }

        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvDate = view.findViewById(R.id.tvDate)
        tvCurrentSteps = view.findViewById(R.id.tvCurrentSteps)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvWeight = view.findViewById(R.id.tvWeight)
        progressBar = view.findViewById(R.id.progressBar)
        progressCalories = view.findViewById(R.id.progressCalories)
        chartDaily = view.findViewById(R.id.chartWeekly)

        // Use email from Firebase if available, else from SessionManager (safe)
        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        lastKnownEmail = email

        // Register auth state listener so chart updates immediately when user changes
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val newEmail = firebaseAuth.currentUser?.email ?: session.getUserEmail().orEmpty()
            if (newEmail != lastKnownEmail) {
                Log.d(TAG, "Auth changed: old=$lastKnownEmail new=$newEmail")
                lastKnownEmail = newEmail
                email = newEmail

                // Clear per-device step prefs so initial_steps isn't reused for a different account
                try { prefs?.edit()?.clear()?.apply() } catch (_: Exception) {}

                // Reset in-memory counters and UI so old user's numbers are not shown
                totalSteps = 0f
                goalCompleted = false
                try {
                    tvCurrentSteps?.text = "0"
                    tvCalories?.text = "0 / $caloriesGoal"
                    progressBar?.progress = 0
                    progressCalories?.progress = 0
                } catch (_: Exception) {}

                // Reload UI for new user (or draw empty if not logged in)
                if (isAdded) {
                    if (email.isNotEmpty()) {
                        loadUserData()
                        loadDailyChart()
                        loadDailyStats()
                    } else {
                        drawEmptyChart()
                        tvGreeting?.text = "Welcome!"
                        tvSteps?.text = "Goal: $stepsGoal"
                        tvWeight?.text = "Weight Today: ${"%.1f".format(userWeight)} kg"
                        tvCalories?.text = "0 / $caloriesGoal"
                        tvCurrentSteps?.text = "0"
                        progressBar?.progress = 0
                        progressCalories?.progress = 0
                    }
                }
            }
        }
        authStateListener?.let { auth.addAuthStateListener(it) }

        if (email.isEmpty()) {
            if (isAdded) {
                context?.let { ctx -> Toast.makeText(ctx, "User not logged in!", Toast.LENGTH_SHORT).show() }
            }
            // still draw empty chart for safety
            drawEmptyChart()
        } else {
            loadUserData()
        }

        // Initialize sensor safely
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        startDateTimeUpdater()
        return view
    }

    // ---------------- LOAD USER DATA ----------------
    private fun loadUserData() {
        if (email.isEmpty() || !isAdded) return

        val helper = FirestoreDatabaseHelper()
        helper.getUserDetails(email) { data ->
            // Ensure fragment still attached
            if (!isAdded) return@getUserDetails

            try {
                if (data != null) {
                    val firstName = (data["firstName"] as? String) ?: (data["name"] as? String) ?: "User"
                    val targetWeight = (data["target_weight"] as? Number)?.toDouble() ?: 70.0
                    userWeight = (data["weightToday"] as? Number)?.toDouble()
                        ?: (data["weight"] as? Number)?.toDouble() ?: 70.0
                    stepsGoal = ((data["daily_steps_goal"] as? Number)?.toInt() ?: 10000)
                    caloriesGoal = ((data["daily_calories_goal"] as? Number)?.toInt() ?: 2000)

                    // Update UI safely
                    tvGreeting?.text = "Welcome, $firstName!"
                    tvSteps?.text = "Goal: $stepsGoal"
                    tvWeight?.text = "Weight Today: ${"%.1f".format(userWeight)} kg (Target: ${"%.1f".format(targetWeight)} kg)"

                    progressBar?.max = stepsGoal
                    progressCalories?.max = caloriesGoal

                    // load other data
                    loadDailyStats()
                    loadDailyChart()
                } else {
                    // no data found -> show zeroed UI for new user
                    tvGreeting?.text = "Welcome!"
                    progressBar?.max = stepsGoal
                    progressCalories?.max = caloriesGoal
                    tvSteps?.text = "Goal: $stepsGoal"
                    tvWeight?.text = "Weight Today: ${"%.1f".format(userWeight)} kg"
                    tvCalories?.text = "0 / $caloriesGoal"
                    tvCurrentSteps?.text = "0"
                    drawEmptyChart()
                }
            } catch (ex: Exception) {
                if (isAdded) context?.let { ctx -> Toast.makeText(ctx, "Error parsing user data", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // ---------------- LOAD DAILY STATS ----------------
    private fun loadDailyStats() {
        if (email.isEmpty() || !isAdded) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val helper = FirestoreDatabaseHelper()

        // Resolve the user's doc id first (handles UID or email doc ids)
        helper.getDocIdByEmail(email) { docId ->
            if (!isAdded) return@getDocIdByEmail

            val idToUse = docId ?: email
            firestore.collection("daily_stats").document("${idToUse}_$today")
                .get()
                .addOnSuccessListener { doc ->
                    if (!isAdded) return@addOnSuccessListener
                    try {
                        val stepsToday = (doc.getLong("steps") ?: 0L).toInt()
                        val caloriesToday = (doc.getLong("calories") ?: 0L).toInt()

                        tvCalories?.text = "$caloriesToday / $caloriesGoal"
                        tvCurrentSteps?.text = stepsToday.toString()
                        progressBar?.progress = stepsToday
                        progressCalories?.progress = caloriesToday
                    } catch (ex: Exception) {
                        // ignore UI update if fragment not ready
                    }
                }
                .addOnFailureListener {
                    // If fetching fails, fallback to showing zero
                    if (!isAdded) return@addOnFailureListener
                    try {
                        tvCalories?.text = "0 / $caloriesGoal"
                        tvCurrentSteps?.text = "0"
                        progressBar?.progress = 0
                        progressCalories?.progress = 0
                    } catch (_: Exception) {}
                }
        }
    }

    // ---------------- SENSOR EVENTS ----------------
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isAdded || event == null || goalCompleted) return

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            var initialSteps = prefs?.getFloat("initial_steps", -1f) ?: -1f
            val savedDate = prefs?.getString("last_date", today) ?: today

            if (savedDate != today || initialSteps < 0) {
                initialSteps = event.values[0]
                prefs?.edit()?.putFloat("initial_steps", initialSteps)?.putString("last_date", today)?.apply()
            }

            totalSteps = event.values[0] - initialSteps
            val stepsInt = totalSteps.toInt()
            if (userWeight <= 0) userWeight = 70.0
            val calories = calculateCalories(stepsInt, userWeight)

            // Update UI only if added
            if (isAdded) {
                tvCurrentSteps?.text = stepsInt.toString()
                tvCalories?.text = "$calories / $caloriesGoal"
                progressBar?.progress = stepsInt
                progressCalories?.progress = calories
                updateChart()
            }

            // Save to Firestore (safe, async)
            saveStatsToFirestore(today, stepsInt, calories, userWeight)

            if (stepsInt >= (progressBar?.max ?: stepsGoal)) {
                goalCompleted = true
                sensorManager?.unregisterListener(this)
                showGoalCompletedDialog()
            }
        }
    }

    private fun saveStatsToFirestore(date: String, steps: Int, calories: Int, weight: Double) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val helper = FirestoreDatabaseHelper()

        // Use helper to save daily stats (helper resolves doc id by UID or email)
        try {
            helper.saveDailyStats(email, steps, calories, weight)
        } catch (e: Exception) {
            // fallback: direct write (rare)
            try {
                firestore.collection("daily_stats").document("${email}_$date").set(
                    mapOf(
                        "user_email" to email,
                        "date" to date,
                        "steps" to steps,
                        "calories" to calories,
                        "weight" to weight,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { /* ignore write failure */ }
        }

        // Use helper to save hourly stats (keeps IDs consistent)
        try {
            helper.saveHourlyStats(email, hour, steps, calories, weight)
        } catch (e: Exception) {
            // fallback: direct write
            try {
                firestore.collection("hourly_stats").document("${email}_${date}_$hour").set(
                    mapOf(
                        "email" to email,
                        "date" to date,
                        "hour" to hour,
                        "steps" to steps,
                        "calories" to calories,
                        "weight" to weight,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun calculateCalories(steps: Int, weightKg: Double): Int {
        val stepLengthMeters = 0.762
        val distanceKm = steps * stepLengthMeters / 1000.0
        val caloriesBurned = distanceKm * weightKg * 1.036
        return caloriesBurned.toInt()
    }

    // ---------------- CHART HANDLING ----------------
    private fun loadDailyChart() {
        if (!isAdded) {
            Log.d(TAG, "loadDailyChart: fragment not added -> returning")
            return
        }

        // defensive: ensure we've got an email at least
        val currentEmail = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        if (currentEmail.isEmpty()) {
            Log.d(TAG, "loadDailyChart: no email available -> drawEmptyChart")
            drawEmptyChart()
            return
        }
        email = currentEmail

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val helper = FirestoreDatabaseHelper()

        helper.getDocIdByEmail(email) { docId ->
            if (!isAdded) return@getDocIdByEmail

            val idToUse = docId ?: email
            val tryPerDocFirst = !docId.isNullOrEmpty() && docId != email

            Log.d(TAG, "loadDailyChart: email=$email docId=$docId tryPerDocFirst=$tryPerDocFirst")

            if (tryPerDocFirst) {
                // Attempt per-doc reads first (24 calls). If none exist, fallback to query-by-email.
                val hourlyMap = mutableMapOf<Int, Int>()
                for (h in 0..23) hourlyMap[h] = 0

                val tasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                for (h in 0..23) {
                    val docRef = firestore.collection("hourly_stats").document("${idToUse}_${today}_$h")
                    tasks.add(docRef.get())
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                    .addOnSuccessListener { results ->
                        if (!isAdded) return@addOnSuccessListener

                        var anyFound = false
                        for (snap in results) {
                            if (snap != null && snap.exists()) {
                                val hour = (snap.getLong("hour") ?: 0L).toInt()
                                val steps = (snap.getLong("steps") ?: 0L).toInt()
                                hourlyMap[hour] = steps
                                anyFound = true
                            }
                        }

                        if (anyFound) {
                            val entriesList = mutableListOf<Entry>()
                            for (h in 0..23) entriesList.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
                            drawChart(entriesList)
                        } else {
                            // Fallback to query-by-email (older writes may have "email" field)
                            queryHourlyByEmail(today, idToUse)
                        }
                    }
                    .addOnFailureListener {
                        // If per-doc reads failed for some reason, fallback to query-by-email
                        queryHourlyByEmail(today, idToUse)
                    }
            } else {
                // No reliable docId / docId == email -> just query by email first (original behavior)
                queryHourlyByEmail(today, idToUse)
            }
        }
    }

    // helper: query hourly_stats by email field, then fallback to per-doc reads if needed
    private fun queryHourlyByEmail(today: String, idToUse: String) {
        firestore.collection("hourly_stats")
            .whereEqualTo("email", email)
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                Log.d(TAG, "queryHourlyByEmail: snapshot size=${snapshot.size()}")
                if (!snapshot.isEmpty) {
                    buildChartFromHourlySnapshot(snapshot.documents.map { it.data ?: emptyMap<String, Any>() })
                } else {
                    // fallback to per-doc reads (if any exist under idToUse)
                    val hourlyMap = mutableMapOf<Int, Int>()
                    for (h in 0..23) hourlyMap[h] = 0
                    val tasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                    for (h in 0..23) {
                        val docRef = firestore.collection("hourly_stats").document("${idToUse}_${today}_$h")
                        tasks.add(docRef.get())
                    }
                    Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                        .addOnSuccessListener { results ->
                            if (!isAdded) return@addOnSuccessListener
                            var anyFound = false
                            for (snap in results) {
                                if (snap != null && snap.exists()) {
                                    val hour = (snap.getLong("hour") ?: 0L).toInt()
                                    val steps = (snap.getLong("steps") ?: 0L).toInt()
                                    hourlyMap[hour] = steps
                                    anyFound = true
                                }
                            }
                            if (anyFound) {
                                val entriesList = mutableListOf<Entry>()
                                for (h in 0..23) entriesList.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
                                drawChart(entriesList)
                            } else {
                                drawEmptyChart()
                            }
                        }
                        .addOnFailureListener {
                            drawEmptyChart()
                        }
                }
            }
            .addOnFailureListener {
                // Query failed -> fallback to per-doc reads
                val hourlyMap = mutableMapOf<Int, Int>()
                for (h in 0..23) hourlyMap[h] = 0
                val tasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                for (h in 0..23) {
                    val docRef = firestore.collection("hourly_stats").document("${idToUse}_${today}_$h")
                    tasks.add(docRef.get())
                }
                Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                    .addOnSuccessListener { results ->
                        if (!isAdded) return@addOnSuccessListener
                        var anyFound = false
                        for (snap in results) {
                            if (snap != null && snap.exists()) {
                                val hour = (snap.getLong("hour") ?: 0L).toInt()
                                val steps = (snap.getLong("steps") ?: 0L).toInt()
                                hourlyMap[hour] = steps
                                anyFound = true
                            }
                        }
                        if (anyFound) {
                            val entriesList = mutableListOf<Entry>()
                            for (h in 0..23) entriesList.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
                            drawChart(entriesList)
                        } else {
                            drawEmptyChart()
                        }
                    }
                    .addOnFailureListener {
                        drawEmptyChart()
                    }
            }
    }

    private fun buildChartFromHourlySnapshot(listOfMaps: List<Map<String, Any>>) {
        val hourlyMap = mutableMapOf<Int, Int>()
        for (h in 0..23) hourlyMap[h] = 0
        for (m in listOfMaps) {
            val hour = (m["hour"] as? Number)?.toInt() ?: 0
            val steps = (m["steps"] as? Number)?.toInt() ?: 0
            if (hour in 0..23) hourlyMap[hour] = steps
        }
        val entries = mutableListOf<Entry>()
        for (h in 0..23) entries.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
        drawChart(entries)
    }

    private fun updateChart() {
        loadDailyChart()
    }

    private fun drawChart(entries: List<Entry>) {
        if (!isAdded) return
        try {
            val dataSet = LineDataSet(entries, "Steps Today")
            val ctx = context ?: return

            // Safe color fetch
            try {
                dataSet.color = resources.getColor(R.color.blue_700, ctx.theme)
                dataSet.valueTextColor = resources.getColor(R.color.black, ctx.theme)
            } catch (_: Exception) {}

            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.setDrawFilled(true)
            dataSet.fillAlpha = 50

            chartDaily?.data = LineData(dataSet)
            chartDaily?.description?.isEnabled = false
            chartDaily?.axisRight?.isEnabled = false
            chartDaily?.xAxis?.position = XAxis.XAxisPosition.BOTTOM
            chartDaily?.xAxis?.granularity = 1f
            chartDaily?.xAxis?.labelCount = 24
            chartDaily?.invalidate()
        } catch (ex: Exception) {
            // ignore UI drawing errors if fragment not ready
        }
    }

    private fun drawEmptyChart() {
        val entries = mutableListOf<Entry>()
        for (h in 0..23) entries.add(Entry(h.toFloat(), 0f))
        drawChart(entries)
    }

    // ---------------- DATE & GOAL HANDLING ----------------
    private fun startDateTimeUpdater() {
        if (timeUpdater != null) return
        timeUpdater = object : Runnable {
            override fun run() {
                if (!isAdded) return
                updateDateTime()
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(timeUpdater!!)
    }

    private fun updateDateTime() {
        if (!isAdded) return
        val currentDate = Calendar.getInstance().time
        val sdf = SimpleDateFormat("EEEE, MMM dd • hh:mm a", Locale.getDefault())
        tvDate?.text = sdf.format(currentDate)
    }

    private fun showGoalCompletedDialog() {
        if (!isAdded) return

        val ctx = context ?: return
        val builder = android.app.AlertDialog.Builder(ctx)
        builder.setTitle("Congratulations!")
        builder.setMessage("You completed your steps goal 🎉")
        builder.setCancelable(false)
        builder.setPositiveButton("Set New Target") { dialog, _ ->
            val profileFragment = ProfileFragment()
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.fragment_container, profileFragment)
                ?.addToBackStack(null)
                ?.commit()
            dialog.dismiss()
        }
        builder.show()
    }

    // ---------------- LIFECYCLE HANDLERS ----------------
    override fun onResume() {
        super.onResume()
        // in onResume() before loadUserData():
        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        lastKnownEmail = email

        // Ensure latest profile/goals are shown after returning from ProfileFragment
        if (!email.isNullOrEmpty()) {
            // Re-load user goals and stats from Firestore
            loadUserData()
        }
        // Re-register sensor if needed
        if (!goalCompleted) {
            stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        timeUpdater?.let { handler.removeCallbacks(it) }
        timeUpdater = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cleanup: remove auth listener and unregister sensor
        try { authStateListener?.let { auth.removeAuthStateListener(it) } } catch (_: Exception) {}
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        timeUpdater?.let { handler.removeCallbacks(it) }
        timeUpdater = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
