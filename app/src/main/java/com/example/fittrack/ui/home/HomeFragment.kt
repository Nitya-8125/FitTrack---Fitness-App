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

class HomeFragment : Fragment(), SensorEventListener {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var session: SessionManager

    private lateinit var tvGreeting: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvCurrentSteps: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvWeight: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressCalories: ProgressBar
    private lateinit var chartDaily: LineChart

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var totalSteps: Float = 0f
    private var userWeight: Double = 70.0
    private var email: String = ""
    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeUpdater: Runnable
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
        session = SessionManager(requireContext())
        prefs = requireContext().getSharedPreferences("step_prefs", Context.MODE_PRIVATE)

        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvDate = view.findViewById(R.id.tvDate)
        tvCurrentSteps = view.findViewById(R.id.tvCurrentSteps)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvWeight = view.findViewById(R.id.tvWeight)
        progressBar = view.findViewById(R.id.progressBar)
        progressCalories = view.findViewById(R.id.progressCalories)
        chartDaily = view.findViewById(R.id.chartWeekly)

        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "User not logged in!", Toast.LENGTH_SHORT).show()
        } else {
            loadUserData()
        }

        // Initialize sensor
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        startDateTimeUpdater()
        return view
    }

    // ---------------- LOAD USER DATA ----------------
    private fun loadUserData() {
        firestore.collection("users").document(email)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val firstName = doc.getString("firstName") ?: "User"
                    val targetWeight = doc.getDouble("target_weight") ?: 70.0
                    userWeight = doc.getDouble("weightToday") ?: 70.0
                    stepsGoal = (doc.getLong("daily_steps_goal") ?: 10000L).toInt()
                    caloriesGoal = (doc.getLong("daily_calories_goal") ?: 2000L).toInt()

                    tvGreeting.text = "Welcome, $firstName!"
                    tvSteps.text = "Goal: $stepsGoal"
                    tvWeight.text = "Weight Today: $userWeight kg (Target: $targetWeight kg)"

                    progressBar.max = stepsGoal
                    progressCalories.max = caloriesGoal

                    loadDailyStats()
                    loadDailyChart()
                } else {
                    Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- LOAD DAILY STATS ----------------
    private fun loadDailyStats() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        firestore.collection("daily_stats").document("${email}_$today")
            .get()
            .addOnSuccessListener { doc ->
                val stepsToday = (doc.getLong("steps") ?: 0L).toInt()
                val caloriesToday = (doc.getLong("calories") ?: 0L).toInt()

                tvCalories.text = "$caloriesToday / $caloriesGoal"
                tvCurrentSteps.text = stepsToday.toString()
                progressBar.progress = stepsToday
                progressCalories.progress = caloriesToday
            }
    }

    // ---------------- SENSOR EVENTS ----------------
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isAdded || event == null || goalCompleted) return

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            var initialSteps = prefs.getFloat("initial_steps", -1f)
            val savedDate = prefs.getString("last_date", today)

            if (savedDate != today || initialSteps < 0) {
                initialSteps = event.values[0]
                prefs.edit().putFloat("initial_steps", initialSteps).putString("last_date", today).apply()
            }

            totalSteps = event.values[0] - initialSteps
            val stepsInt = totalSteps.toInt()
            if (userWeight <= 0) userWeight = 70.0
            val calories = calculateCalories(stepsInt, userWeight)

            tvCurrentSteps.text = stepsInt.toString()
            tvCalories.text = "$calories / $caloriesGoal"

            // Save to Firestore
            saveStatsToFirestore(today, stepsInt, calories, userWeight)

            progressBar.progress = stepsInt
            progressCalories.progress = calories
            updateChart()

            if (stepsInt >= progressBar.max) {
                goalCompleted = true
                sensorManager.unregisterListener(this)
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
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        firestore.collection("hourly_stats")
            .whereEqualTo("email", email)
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { snapshot ->
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
        val dataSet = LineDataSet(entries, "Steps Today")
        dataSet.color = resources.getColor(R.color.blue_700, requireContext().theme)
        dataSet.valueTextColor = resources.getColor(R.color.black, requireContext().theme)
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.setDrawFilled(true)
        dataSet.fillAlpha = 50

        chartDaily.data = LineData(dataSet)
        chartDaily.description.isEnabled = false
        chartDaily.axisRight.isEnabled = false
        chartDaily.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartDaily.xAxis.granularity = 1f
        chartDaily.xAxis.labelCount = 24
        chartDaily.invalidate()
    }

    // ---------------- DATE & GOAL HANDLING ----------------
    private fun startDateTimeUpdater() {
        timeUpdater = object : Runnable {
            override fun run() {
                updateDateTime()
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(timeUpdater)
    }

    private fun updateDateTime() {
        val currentDate = Calendar.getInstance().time
        val sdf = SimpleDateFormat("EEEE, MMM dd • hh:mm a", Locale.getDefault())
        tvDate.text = sdf.format(currentDate)
    }

    private fun showGoalCompletedDialog() {
        if (!isAdded) return

        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Congratulations!")
        builder.setMessage("You completed your steps goal 🎉")
        builder.setCancelable(false)
        builder.setPositiveButton("Set New Target") { dialog, _ ->
            val profileFragment = ProfileFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, profileFragment)
                .addToBackStack(null)
                .commit()
            dialog.dismiss()
        }
        builder.show()
    }

    // ---------------- LIFECYCLE HANDLERS ----------------
    override fun onResume() {
        super.onResume()
        if (!goalCompleted) {
            stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(timeUpdater)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
