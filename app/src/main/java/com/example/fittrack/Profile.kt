package com.example.fittrack.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fittrack.R
import com.example.fittrack.SessionManager
import com.example.fittrack.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
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

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        session = SessionManager(requireContext())

        // Get current user email
        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()

        // Load user data from Firestore
        loadUserData()

        // Button actions
        btnUpdateGoals.setOnClickListener { updateGoals() }
        btnLogout.setOnClickListener { logoutUser() }

        return view
    }

    // ---------------- LOAD USER DATA ----------------
    private fun loadUserData() {
        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users").document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val firstName = document.getString("firstName") ?: "Guest"
                    val lastName = document.getString("lastName") ?: ""
                    val fullName = "$firstName $lastName"
                    val stepsGoal = (document.getLong("daily_steps_goal") ?: 10000L).toInt()
                    val caloriesGoal = (document.getLong("daily_calories_goal") ?: 2000L).toInt()
                    val targetWeight = (document.getDouble("target_weight") ?: 70.0)

                    tvName.text = fullName
                    tvEmail.text = email
                    etStepsGoal.setText(stepsGoal.toString())
                    etCaloriesGoal.setText(caloriesGoal.toString())
                    etTargetWeight.setText(targetWeight.toString())
                } else {
                    Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- UPDATE USER GOALS ----------------
    private fun updateGoals() {
        val steps = etStepsGoal.text.toString().toIntOrNull()
        val calories = etCaloriesGoal.text.toString().toIntOrNull()
        val targetWeight = etTargetWeight.text.toString().toDoubleOrNull()

        if (steps == null || calories == null || targetWeight == null) {
            Toast.makeText(requireContext(), "Enter valid values", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "daily_steps_goal" to steps,
            "daily_calories_goal" to calories,
            "target_weight" to targetWeight
        )

        firestore.collection("users").document(email)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Goals updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update goals!", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- LOGOUT ----------------
    private fun logoutUser() {
        auth.signOut()
        session.clearSession()

        val loginIntent = Intent(requireContext(), LoginActivity::class.java)
        loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(loginIntent)
    }
}
