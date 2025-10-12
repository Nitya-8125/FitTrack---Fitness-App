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
import com.example.fittrack.DatabaseHelper
import com.example.fittrack.R
import com.example.fittrack.SessionManager
import com.example.fittrack.ui.profile.ProfileFragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), SensorEventListener {

    private lateinit var dbHelper: DatabaseHelper
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        dbHelper = DatabaseHelper(requireContext())
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

        val session = SessionManager(requireContext())
        email = session.getUserEmail() ?: ""

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

    private fun loadUserData() {
        val cursor = dbHelper.getUserByEmail(email)
        if (cursor != null && cursor.moveToFirst()) {
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow("firstName"))
            val stepsGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_steps_goal"))
            val caloriesGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_calories_goal"))
            userWeight = cursor.getDouble(cursor.getColumnIndexOrThrow("weightToday"))
            val targetWeight = cursor.getDouble(cursor.getColumnIndexOrThrow("target_weight"))

            tvGreeting.text = "Welcome, $firstName!"

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dailyCursor = dbHelper.getDailyStats(email, today)
            var stepsToday = 0
            var caloriesToday = 0
            if (dailyCursor != null && dailyCursor.moveToFirst()) {
                stepsToday = dailyCursor.getInt(dailyCursor.getColumnIndexOrThrow("steps"))
                caloriesToday = dailyCursor.getInt(dailyCursor.getColumnIndexOrThrow("calories"))
            }
            dailyCursor?.close()

            tvSteps.text = "Goal: $stepsGoal"
            tvCalories.text = "$stepsToday / $caloriesGoal"
            tvWeight.text = "Weight Today: $userWeight kg (Target: $targetWeight kg)"

            progressBar.max = stepsGoal
            progressBar.progress = stepsToday
            progressCalories.max = caloriesGoal
            progressCalories.progress = caloriesToday

            loadDailyChart()
        }
        cursor?.close()
    }

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
            tvCalories.text = "$calories / ${progressCalories.max}"

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            dbHelper.saveHourlyStats(email, today, hour, stepsInt, calories, userWeight)
            dbHelper.saveDailyStats(email, today, stepsInt, calories, userWeight)

            progressBar.progress = stepsInt
            progressCalories.progress = calories
            updateChart()

            // Check if steps goal is completed
            if (stepsInt >= progressBar.max) {
                goalCompleted = true
                sensorManager.unregisterListener(this)
                showGoalCompletedDialog()
            }
        }
    }

    private fun showGoalCompletedDialog() {
        if (!isAdded) return

        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Congratulations!")
        builder.setMessage("You completed your steps goal 🎉")
        builder.setCancelable(false)
        builder.setPositiveButton("Set New Target") { dialog, _ ->
            // Navigate to ProfileFragment using manual transaction
            val profileFragment = ProfileFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, profileFragment)
                .addToBackStack(null)
                .commit()
            dialog.dismiss()
        }
        builder.show()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateCalories(steps: Int, weightKg: Double): Int {
        val stepLengthMeters = 0.762
        val distanceKm = steps * stepLengthMeters / 1000.0
        val caloriesBurned = distanceKm * weightKg * 1.036
        return caloriesBurned.toInt()
    }

    private fun updateChart() {
        if (!isAdded) return

        val stats = dbHelper.getTodayHourlyStats(email)
        val entries = mutableListOf<Entry>()
        val hourlyMap = mutableMapOf<Int, Int>()
        for (h in 0..23) hourlyMap[h] = 0
        for ((hour, steps) in stats) hourlyMap[hour] = steps

        for ((hour, steps) in hourlyMap) {
            entries.add(Entry(hour.toFloat(), steps.toFloat()))
        }

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

    override fun onResume() {
        super.onResume()
        // Reset goalCompleted if the user changed the target
        val cursor = dbHelper.getUserByEmail(email)
        cursor?.let {
            if (it.moveToFirst()) {
                val stepsGoal = it.getInt(it.getColumnIndexOrThrow("daily_steps_goal"))
                // If current progress < new goal, allow tracking again
                if (progressBar.progress < stepsGoal) {
                    goalCompleted = false
                }
            }
            it.close()
        }

        // Reset initial steps for new tracking day or new goal
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString("last_date", today)
        if (savedDate != today || goalCompleted.not()) {
            prefs.edit().putFloat("initial_steps", -1f).apply()
        }

        // Re-register sensor if not completed
        if (!goalCompleted) {
            stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }


    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(timeUpdater)
    }

    private fun loadDailyChart() {
        val stats = dbHelper.getTodayHourlyStats(email)
        if (stats.isNotEmpty()) updateChart()
    }
}
