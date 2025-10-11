package com.example.fittrack.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fittrack.DatabaseHelper
import com.example.fittrack.R
import com.example.fittrack.SessionManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tvGreeting: TextView
    private lateinit var tvCurrentSteps: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvWeight: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressCalories: ProgressBar
    private lateinit var chartWeekly: LineChart

    private var email: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        dbHelper = DatabaseHelper(requireContext())

        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvCurrentSteps = view.findViewById(R.id.tvCurrentSteps)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvWeight = view.findViewById(R.id.tvWeight)
        progressBar = view.findViewById(R.id.progressBar)
        progressCalories = view.findViewById(R.id.progressCalories)
        chartWeekly = view.findViewById(R.id.chartWeekly)

        val session = SessionManager(requireContext())
        email = session.getUserEmail() ?: ""

        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "Error: User not logged in!", Toast.LENGTH_SHORT).show()
        } else {
            loadUserData()
        }

        return view
    }

    private fun loadUserData() {
        val cursor = dbHelper.getUserByEmail(email)
        if (cursor != null && cursor.moveToFirst()) {
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow("firstName"))
            val stepsToday = cursor.getInt(cursor.getColumnIndexOrThrow("stepsToday"))
            val caloriesToday = cursor.getInt(cursor.getColumnIndexOrThrow("caloriesToday"))
            val weightToday = cursor.getDouble(cursor.getColumnIndexOrThrow("weightToday"))

            val stepsGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_steps_goal"))
            val caloriesGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_calories_goal"))
            val targetWeight = cursor.getDouble(cursor.getColumnIndexOrThrow("target_weight"))

            tvGreeting.text = "Welcome, $firstName!"
            tvCurrentSteps.text = stepsToday.toString()
            tvSteps.text = "Goal: $stepsGoal"
            tvCalories.text = "$caloriesToday / $caloriesGoal"
            tvWeight.text = "Weight Today: $weightToday kg (Target: $targetWeight kg)"

            progressBar.max = if (stepsGoal > 0) stepsGoal else 10000
            progressBar.progress = stepsToday

            progressCalories.max = if (caloriesGoal > 0) caloriesGoal else 2000
            progressCalories.progress = caloriesToday

            saveTodayStats(stepsToday, caloriesToday, weightToday)
            loadWeeklyChart()
        }
        cursor?.close()
    }

    private fun saveTodayStats(steps: Int, calories: Int, weight: Double) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dbHelper.saveDailyStats(email, today, steps, calories, weight)
    }

    private fun loadWeeklyChart() {
        val stats = dbHelper.getLast7DaysStats(email)
        val entries = mutableListOf<Entry>()

        for ((index, stat) in stats.withIndex()) {
            entries.add(Entry(index.toFloat(), stat.second.toFloat())) // steps
        }

        val dataSet = LineDataSet(entries, "Steps Last 7 Days")
        dataSet.color = resources.getColor(R.color.blue_700, null)
        dataSet.valueTextColor = resources.getColor(R.color.black, null)
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.setDrawFilled(true)
        dataSet.fillAlpha = 50

        val lineData = LineData(dataSet)
        chartWeekly.data = lineData
        chartWeekly.description.isEnabled = false
        chartWeekly.axisRight.isEnabled = false
        chartWeekly.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartWeekly.invalidate()
    }
}
