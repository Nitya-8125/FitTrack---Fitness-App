package com.example.fittrack.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.fittrack.FirestoreDatabaseHelper

class HomeFragment : Fragment(), SensorEventListener {

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

        if (email.isEmpty()) {
            // Use safe context and isAdded check
            if (isAdded) {
                context?.let { ctx -> Toast.makeText(ctx, "User not logged in!", Toast.LENGTH_SHORT).show() }
            }
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
                    val firstName = (data["firstName"] as? String) ?: "User"
                    val targetWeight = (data["target_weight"] as? Double) ?: 70.0
                    userWeight = (data["weightToday"] as? Double) ?: (data["weight"] as? Double) ?: 70.0
                    stepsGoal = ((data["daily_steps_goal"] as? Number)?.toInt() ?: 10000)
                    caloriesGoal = ((data["daily_calories_goal"] as? Number)?.toInt() ?: 2000)

                    // Update UI safely
                    tvGreeting?.text = "Welcome, $firstName!"
                    tvSteps?.text = "Goal: $stepsGoal"
                    tvWeight?.text = "Weight Today: $userWeight kg (Target: $targetWeight kg)"

                    progressBar?.max = stepsGoal
                    progressCalories?.max = caloriesGoal

                    // load other data
                    loadDailyStats()
                    loadDailyChart()
                } else {
                    // no data found
                    if (isAdded) context?.let { ctx -> Toast.makeText(ctx, "User data not found", Toast.LENGTH_SHORT).show() }
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
        firestore.collection("daily_stats").document("${email}_$today")
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
        val dailyData = mapOf(
            "user_email" to email,
            "date" to date,
            "steps" to steps,
            "calories" to calories,
            "weight" to weight
        )
        val hourlyData = mapOf(
            "email" to email,
            "date" to date,
            "hour" to hour,
            "steps" to steps,
            "calories" to calories,
            "weight" to weight
        )

        // Fire-and-forget: no UI changes here
        firestore.collection("daily_stats").document("${email}_$date").set(dailyData)
        firestore.collection("hourly_stats").document("${email}_${date}_$hour").set(hourlyData)
    }

    private fun calculateCalories(steps: Int, weightKg: Double): Int {
        val stepLengthMeters = 0.762
        val distanceKm = steps * stepLengthMeters / 1000.0
        val caloriesBurned = distanceKm * weightKg * 1.036
        return caloriesBurned.toInt()
    }

    // ---------------- CHART HANDLING ----------------
    private fun loadDailyChart() {
        if (!isAdded || email.isEmpty()) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        firestore.collection("hourly_stats")
            .whereEqualTo("email", email)
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val entries = mutableListOf<Entry>()
                val hourlyMap = mutableMapOf<Int, Int>()
                for (h in 0..23) hourlyMap[h] = 0
                for (doc in snapshot.documents) {
                    val hour = (doc.getLong("hour") ?: 0L).toInt()
                    val steps = (doc.getLong("steps") ?: 0L).toInt()
                    hourlyMap[hour] = steps
                }
                for ((hour, steps) in hourlyMap) {
                    entries.add(Entry(hour.toFloat(), steps.toFloat()))
                }
                drawChart(entries)
            }
    }

    private fun updateChart() {
        loadDailyChart()
    }

    private fun drawChart(entries: List<Entry>) {
        if (!isAdded) return
        try {
            val dataSet = LineDataSet(entries, "Steps Today")
            // Use safe context access
            val ctx = context
            if (ctx == null) return

            dataSet.color = resources.getColor(R.color.blue_700, ctx.theme)
            dataSet.valueTextColor = resources.getColor(R.color.black, ctx.theme)
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

    // ---------------- DATE & GOAL HANDLING ----------------
    private fun startDateTimeUpdater() {
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
        if (!goalCompleted) {
            stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        timeUpdater?.let { handler.removeCallbacks(it) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
