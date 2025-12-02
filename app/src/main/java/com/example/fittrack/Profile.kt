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
import com.example.fittrack.FirestoreDatabaseHelper
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

        // Get current user email (fallback to session)
        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()

        // Load user data from Firestore (fills fields)
        loadUserData()

        // Button actions
        btnUpdateGoals.setOnClickListener { updateGoals() }
        btnLogout.setOnClickListener { logoutUser() }

        return view
    }

    // ---------------- LOAD USER DATA ----------------
    private fun loadUserData() {
        if (email.isEmpty()) {
            if (isAdded) Toast.makeText(context, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val helper = FirestoreDatabaseHelper()
        helper.getUserDetails(email) { data ->
            if (!isAdded) return@getUserDetails

            if (data != null) {
                val firstName = (data["firstName"] as? String) ?: "Guest"
                val lastName = (data["lastName"] as? String) ?: ""
                val fullName = "$firstName $lastName"
                val stepsGoal = ((data["daily_steps_goal"] as? Number)?.toInt() ?: 10000)
                val caloriesGoal = ((data["daily_calories_goal"] as? Number)?.toInt() ?: 2000)
                val targetWeight = (data["target_weight"] as? Number)?.toDouble() ?: (data["weightToday"] as? Number)?.toDouble() ?: 70.0

                tvName.text = fullName
                tvEmail.text = email
                etStepsGoal.setText(stepsGoal.toString())
                etCaloriesGoal.setText(caloriesGoal.toString())
                etTargetWeight.setText(targetWeight.toString())
            } else {
                if (isAdded) Toast.makeText(context, "User data not found", Toast.LENGTH_SHORT).show()
            }
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

        // Use helper so doc-id resolution (UID vs email) is handled
        val helper = FirestoreDatabaseHelper()
        btnUpdateGoals.isEnabled = false
        helper.updateGoals(email, steps, calories, targetWeight) { success ->
            if (!isAdded) return@updateGoals
            btnUpdateGoals.isEnabled = true

            if (success) {
                Toast.makeText(requireContext(), "Goals updated successfully!", Toast.LENGTH_SHORT).show()

                // Optionally update session or local values if you store goals in session (not required)
                // session.setUserGoals(steps, calories, targetWeight) // if you implement

                // Navigate to Home and ensure HomeFragment refreshes data immediately
                activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.fragment_container, com.example.fittrack.ui.home.HomeFragment())
                    ?.commit()

            } else {
                Toast.makeText(requireContext(), "Failed to update goals!", Toast.LENGTH_SHORT).show()
            }
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
