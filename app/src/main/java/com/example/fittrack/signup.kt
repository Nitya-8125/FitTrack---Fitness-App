package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class SignupActivity : AppCompatActivity() {

    private var currentStep = 0
    private lateinit var progressBar: ProgressBar
    private lateinit var stepContainer: FrameLayout
    private lateinit var btnNext: Button
    private lateinit var btnPrevious: Button
    private lateinit var txtLoginRedirect: TextView

    private lateinit var userData: MutableMap<String, String>
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        progressBar = findViewById(R.id.progressSignup)
        stepContainer = findViewById(R.id.stepContainer)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        txtLoginRedirect = findViewById(R.id.txtLoginRedirect)

        dbHelper = DatabaseHelper(this)
        userData = mutableMapOf()

        showStep(currentStep)

        btnPrevious.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                showStep(currentStep)
            }
        }

        txtLoginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun showStep(step: Int) {
        val inflater = layoutInflater
        val stepView = when (step) {

            // Step 1: Email + Password
            0 -> inflater.inflate(R.layout.layout_step_account, stepContainer, false).apply {
                val etEmail: EditText = findViewById(R.id.inputEmail)
                val etPassword: EditText = findViewById(R.id.inputPassword)
                val etConfirmPassword: EditText = findViewById(R.id.inputConfirmPassword)

                etEmail.setText(userData["email"] ?: "")
                etPassword.setText(userData["password"] ?: "")
                etConfirmPassword.setText(userData["confirmPassword"] ?: "")

                btnNext.text = "Next"
                btnNext.setOnClickListener {
                    val email = etEmail.text.toString().trim()
                    val password = etPassword.text.toString().trim()
                    val confirmPassword = etConfirmPassword.text.toString().trim()

                    if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                        Snackbar.make(this, "All fields are required", Snackbar.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        etEmail.error = "Invalid email"
                        return@setOnClickListener
                    }
                    if (password.length < 6) {
                        etPassword.error = "Password must be at least 6 characters"
                        return@setOnClickListener
                    }
                    if (password != confirmPassword) {
                        etConfirmPassword.error = "Passwords do not match"
                        return@setOnClickListener
                    }

                    userData["email"] = email
                    userData["password"] = password
                    userData["confirmPassword"] = confirmPassword

                    currentStep++
                    showStep(currentStep)
                }
            }

            // Step 2: Personal Info + Weight/Height
            1 -> inflater.inflate(R.layout.layout_step_personal, stepContainer, false).apply {
                val etFirstName: EditText = findViewById(R.id.inputFirstName)
                val etLastName: EditText = findViewById(R.id.inputLastName)
                val etAge: EditText = findViewById(R.id.inputAge)
                val etWeight: EditText = findViewById(R.id.inputWeight)
                val etHeight: EditText = findViewById(R.id.inputHeight)
                val spinnerGender: Spinner = findViewById(R.id.spinnerGender)

                etFirstName.setText(userData["firstName"] ?: "")
                etLastName.setText(userData["lastName"] ?: "")
                etAge.setText(userData["age"] ?: "")
                etWeight.setText(userData["weight"] ?: "")
                etHeight.setText(userData["height"] ?: "")

                val genders = listOf("Male", "Female", "Other", "Prefer not to say")
                val adapter = ArrayAdapter(this@SignupActivity, android.R.layout.simple_spinner_item, genders)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerGender.adapter = adapter

                userData["gender"]?.let {
                    val index = genders.indexOf(it)
                    if (index >= 0) spinnerGender.setSelection(index)
                }

                btnNext.text = "Create Account"
                btnNext.setOnClickListener {
                    val firstName = etFirstName.text.toString().trim()
                    val lastName = etLastName.text.toString().trim()
                    val ageStr = etAge.text.toString().trim()
                    val weightStr = etWeight.text.toString().trim()
                    val heightStr = etHeight.text.toString().trim()
                    val gender = spinnerGender.selectedItem?.toString() ?: ""

                    if (firstName.isEmpty() || lastName.isEmpty() || ageStr.isEmpty() ||
                        weightStr.isEmpty() || heightStr.isEmpty() || gender.isEmpty()
                    ) {
                        Snackbar.make(this, "All fields are required", Snackbar.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val age = ageStr.toIntOrNull()
                    val weight = weightStr.toFloatOrNull()
                    val height = heightStr.toFloatOrNull()

                    if (age == null || age < 13 || age > 120) {
                        etAge.error = "Enter valid age (13–120)"
                        return@setOnClickListener
                    }
                    if (weight == null || weight <= 0) {
                        etWeight.error = "Enter valid weight"
                        return@setOnClickListener
                    }
                    if (height == null || height <= 0) {
                        etHeight.error = "Enter valid height"
                        return@setOnClickListener
                    }

                    userData["firstName"] = firstName
                    userData["lastName"] = lastName
                    userData["age"] = age.toString()
                    userData["weight"] = weight.toString()
                    userData["height"] = height.toString()
                    userData["gender"] = gender

                    val email = userData["email"] ?: ""
                    val password = userData["password"] ?: ""

                    // ✅ Correct DB registration call with default goals
                    val success = dbHelper.registerUser(
                        email, password,
                        firstName, lastName, age, gender,
                        height.toDouble(), weight.toDouble()
                    )

                    if (success) {
                        Snackbar.make(this, "Account Created Successfully!", Snackbar.LENGTH_LONG).show()
                        startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Snackbar.make(this, "Registration failed (email may exist)", Snackbar.LENGTH_LONG).show()
                    }
                }
            }

            else -> null
        }

        stepContainer.removeAllViews()
        stepView?.let { stepContainer.addView(it) }

        progressBar.progress = ((step + 1) * 100 / 2)
        btnPrevious.visibility = if (step == 0) View.GONE else View.VISIBLE
    }
}
