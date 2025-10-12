package com.example.fittrack.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fittrack.DatabaseHelper
import com.example.fittrack.R
import com.example.fittrack.SessionManager
import com.example.fittrack.LoginActivity

class ProfileFragment : Fragment() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var etStepsGoal: EditText
    private lateinit var etCaloriesGoal: EditText
    private lateinit var etTargetWeight: EditText
    private lateinit var btnUpdateGoals: Button
    private lateinit var btnLogout: Button

    private var email: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize UI components
        tvName = view.findViewById(R.id.tvName)
        tvEmail = view.findViewById(R.id.tvEmail)
        etStepsGoal = view.findViewById(R.id.etStepsGoal)
        etCaloriesGoal = view.findViewById(R.id.etCaloriesGoal)
        etTargetWeight = view.findViewById(R.id.etTargetWeight)
        btnUpdateGoals = view.findViewById(R.id.btnUpdateGoals)
        btnLogout = view.findViewById(R.id.btnLogout)

        dbHelper = DatabaseHelper(requireContext())
        session = SessionManager(requireContext())
        email = session.getUserEmail() ?: ""

        // Load user data (name, email, goals)
        loadUserData()

        // Button actions
        btnUpdateGoals.setOnClickListener { updateGoals() }
        btnLogout.setOnClickListener { logoutUser() }

        return view
    }

    private fun loadUserData() {
        val cursor = dbHelper.getUserByEmail(email)
        if (cursor != null && cursor.moveToFirst()) {
            // Display Name and Email
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow("firstName"))
            tvName.text = firstName
            tvEmail.text = email

            // Display Goals
            etStepsGoal.setText(cursor.getInt(cursor.getColumnIndexOrThrow("daily_steps_goal")).toString())
            etCaloriesGoal.setText(cursor.getInt(cursor.getColumnIndexOrThrow("daily_calories_goal")).toString())
            etTargetWeight.setText(cursor.getDouble(cursor.getColumnIndexOrThrow("target_weight")).toString())
        }
        cursor?.close()
    }

    private fun updateGoals() {
        val steps = etStepsGoal.text.toString().toIntOrNull()
        val calories = etCaloriesGoal.text.toString().toIntOrNull()
        val targetWeight = etTargetWeight.text.toString().toDoubleOrNull()

        if (steps == null || calories == null || targetWeight == null) {
            Toast.makeText(requireContext(), "Enter valid values", Toast.LENGTH_SHORT).show()
            return
        }

        val success = dbHelper.updateGoals(email, steps, calories, targetWeight)
        if (success) {
            Toast.makeText(requireContext(), "Goals updated successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to update goals!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutUser() {
        session.clearSession() // Clear saved email
        // Redirect to LoginActivity
        val loginIntent = Intent(requireContext(), LoginActivity::class.java)
        loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(loginIntent)
    }
}
