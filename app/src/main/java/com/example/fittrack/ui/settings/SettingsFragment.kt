package com.example.fittrack.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.fittrack.DatabaseHelper
import com.example.fittrack.SessionManager
import com.example.fittrack.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 🔹 Handle Update Profile Button
        binding.btnUpdateProfile.setOnClickListener {
            showUpdateProfileDialog()
        }

        return root
    }

    private fun showUpdateProfileDialog() {
        val context = requireContext()

        // 🔹 Create container layout for dialog
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 10)
        }

        // Input fields
        val etFirstName = EditText(context).apply { hint = "First Name" }
        val etLastName = EditText(context).apply { hint = "Last Name" }
        val etAge = EditText(context).apply {
            hint = "Age"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etHeight = EditText(context).apply {
            hint = "Height (cm)"
            inputType = android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etPassword = EditText(context).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirmPassword = EditText(context).apply {
            hint = "Confirm Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val spinnerGender = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Male", "Female", "Other")
            )
        }

        // Add views into layout
        layout.apply {
            addView(etFirstName)
            addView(etLastName)
            addView(etAge)
            addView(etHeight)
            addView(spinnerGender)
            addView(etPassword)
            addView(etConfirmPassword)
        }

        val db = DatabaseHelper(context)
        val session = SessionManager(context)
        val email = session.getUserEmail()

        // Prefill existing user data
        val cursor = db.getUserByEmail(email .toString())
        if (cursor != null && cursor.moveToFirst()) {
            etFirstName.setText(cursor.getString(cursor.getColumnIndexOrThrow("firstName")))
            etLastName.setText(cursor.getString(cursor.getColumnIndexOrThrow("lastName")))
            etAge.setText(cursor.getInt(cursor.getColumnIndexOrThrow("age")).toString())
            etHeight.setText(cursor.getDouble(cursor.getColumnIndexOrThrow("height")).toString())
            etPassword.setText(cursor.getString(cursor.getColumnIndexOrThrow("password")))
            etConfirmPassword.setText(cursor.getString(cursor.getColumnIndexOrThrow("password")))

            val gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"))
            val genders = arrayOf("Male", "Female", "Other")
            val index = genders.indexOf(gender)
            if (index >= 0) spinnerGender.setSelection(index)
        }
        cursor?.close()

        // 🔹 Build and show dialog
        AlertDialog.Builder(context)
            .setTitle("Update Profile")
            .setView(layout)
            .setPositiveButton("Save") { dialog, _ ->
                val fName = etFirstName.text.toString().trim()
                val lName = etLastName.text.toString().trim()
                val age = etAge.text.toString().toIntOrNull() ?: 0
                val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
                val gender = spinnerGender.selectedItem.toString()
                val password = etPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()

                if (fName.isEmpty() || lName.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                } else if (password != confirmPassword) {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                } else {
                    val updated = db.updateUserProfile(email.toString(), fName, lName, age, height, gender, password)
                    if (updated) {
                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

//package com.example.fittrack.ui.settings
//
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.fragment.app.Fragment
//import androidx.lifecycle.ViewModelProvider
//import com.example.fittrack.databinding.FragmentSettingsBinding
//
//class SettingsFragment : Fragment() {
//
//    private var _binding: FragmentSettingsBinding? = null
//
//    // This property is only valid between onCreateView and
//    // onDestroyView.
//    private val binding get() = _binding!!
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        val settingsViewModel =
//            ViewModelProvider(this).get(SettingsViewModel::class.java)
//
//        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
//        val root: View = binding.root
//
//        return root
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}