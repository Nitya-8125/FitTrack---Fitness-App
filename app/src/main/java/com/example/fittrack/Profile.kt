package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.fittrack.databinding.FragmentProfileBinding
import android.database.Cursor

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        session = SessionManager(requireContext())
        dbHelper = DatabaseHelper(requireContext())

        populateUserData()
        loadGoals()

        // Save goals button
        binding.btnSaveGoals.setOnClickListener { saveGoals() }

        // Logout button
        binding.btnLogout.setOnClickListener { confirmLogout() }

        return binding.root
    }

    /** Populate user details from session */
    private fun populateUserData() {
        val firstName = session.getFirstName() ?: ""
        val lastName = session.getLastName() ?: ""
        val email = session.getUserEmail() ?: ""
        val age = session.getUserAge()
        val height = session.getUserHeight()

        val initials = "${firstName.firstOrNull() ?: ""}${lastName.firstOrNull() ?: ""}".uppercase()
        binding.tvInitials.text = initials
        binding.tvEmail.text = email
        binding.tvName.text = "$firstName $lastName".trim()
        binding.tvAge.text = "$age years"
        binding.tvHeight.text = "$height cm"
    }

    /** Load stored goals from database */
    private fun loadGoals() {
        val email = session.getUserEmail() ?: return
        val cursor: Cursor? = dbHelper.getGoals(email)

        cursor?.use {
            if (it.moveToFirst()) {
                val steps = it.getInt(it.getColumnIndexOrThrow("daily_steps_goal"))
                val calories = it.getInt(it.getColumnIndexOrThrow("daily_calories_goal"))
                val targetWeight = it.getDouble(it.getColumnIndexOrThrow("target_weight"))

                // Ensure these are EditText in XML
                binding.tvStepsGoal.setText(steps.toString())
                binding.tvCaloriesGoal.setText(calories.toString())
                binding.tvTargetWeight.setText(targetWeight.toString())
            }
        }
    }

    /** Save updated goals */
    private fun saveGoals() {
        val email = session.getUserEmail() ?: return

        val steps = binding.tvStepsGoal.text.toString().toIntOrNull() ?: 10000
        val calories = binding.tvCaloriesGoal.text.toString().toIntOrNull() ?: 2000
        val targetWeight = binding.tvTargetWeight.text.toString().toFloatOrNull() ?: 70f

        val success = dbHelper.updateGoals(email, steps, calories, targetWeight)

        if (success) {
            Toast.makeText(requireContext(), "Goals updated successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to update goals.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Confirm before logout */
    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                session.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Refresh profile data from DB */
    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val email = session.getUserEmail() ?: return
        val cursor: Cursor? = dbHelper.getUserByEmail(email)

        cursor?.use {
            if (it.moveToFirst()) {
                val fName = it.getString(it.getColumnIndexOrThrow("firstName"))
                val lName = it.getString(it.getColumnIndexOrThrow("lastName"))
                val age = it.getInt(it.getColumnIndexOrThrow("age"))
                val height = it.getDouble(it.getColumnIndexOrThrow("height"))

                binding.tvName.text = "$fName $lName"
                binding.tvAge.text = "Age: $age"
                binding.tvHeight.text = "Height: $height cm"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ProfileFragment()
    }
}
