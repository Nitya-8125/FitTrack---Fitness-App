package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private var currentStep = 0
    private lateinit var progressBar: ProgressBar
    private lateinit var stepContainer: FrameLayout
    private lateinit var btnNext: Button
    private lateinit var btnPrevious: Button
    private lateinit var txtLoginRedirect: TextView

    private lateinit var userData: MutableMap<String, String>

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        progressBar = findViewById(R.id.progressSignup)
        stepContainer = findViewById(R.id.stepContainer)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        txtLoginRedirect = findViewById(R.id.txtLoginRedirect)

        userData = mutableMapOf()

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

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
        val stepView: View? = when (step) {

            // Step 1: Account Info
            0 -> inflater.inflate(R.layout.layout_step_account, stepContainer, false).apply {
                val etEmail: EditText = findViewById(R.id.inputEmail)
                val etPassword: EditText = findViewById(R.id.inputPassword)
                val etConfirmPassword: EditText = findViewById(R.id.inputConfirmPassword)

                etEmail.setText(userData["email"] ?: "")
                etPassword.setText(userData["password"] ?: "")
                etConfirmPassword.setText(userData["confirmPassword"] ?: "")

                btnNext.text = "Next"
            }

            // Step 2: Personal Info
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
            }

            else -> null
        }

        stepContainer.removeAllViews()
        stepView?.let { stepContainer.addView(it) }

        progressBar.progress = ((step + 1) * 100 / 2)
        btnPrevious.visibility = if (step == 0) View.GONE else View.VISIBLE

        btnNext.setOnClickListener {
            when (currentStep) {

                // Step 1 validation
                0 -> {
                    val etEmail: EditText = stepView!!.findViewById(R.id.inputEmail)
                    val etPassword: EditText = stepView.findViewById(R.id.inputPassword)
                    val etConfirmPassword: EditText = stepView.findViewById(R.id.inputConfirmPassword)

                    val email = etEmail.text.toString().trim()
                    val password = etPassword.text.toString().trim()
                    val confirmPassword = etConfirmPassword.text.toString().trim()

                    if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                        Snackbar.make(stepContainer, "All fields are required", Snackbar.LENGTH_SHORT).show()
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

                // Step 2 validation + Firebase Registration
                1 -> {
                    val etFirstName: EditText = stepView!!.findViewById(R.id.inputFirstName)
                    val etLastName: EditText = stepView.findViewById(R.id.inputLastName)
                    val etAge: EditText = stepView.findViewById(R.id.inputAge)
                    val etWeight: EditText = stepView.findViewById(R.id.inputWeight)
                    val etHeight: EditText = stepView.findViewById(R.id.inputHeight)
                    val spinnerGender: Spinner = stepView.findViewById(R.id.spinnerGender)

                    val firstName = etFirstName.text.toString().trim()
                    val lastName = etLastName.text.toString().trim()
                    val ageStr = etAge.text.toString().trim()
                    val weightStr = etWeight.text.toString().trim()
                    val heightStr = etHeight.text.toString().trim()
                    val gender = spinnerGender.selectedItem.toString()

                    if (firstName.isEmpty() || lastName.isEmpty() || ageStr.isEmpty() ||
                        weightStr.isEmpty() || heightStr.isEmpty() || gender.isEmpty()
                    ) {
                        Snackbar.make(stepContainer, "All fields are required", Snackbar.LENGTH_SHORT).show()
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

                    // ✅ Create user in Firebase Authentication
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                                val userMap = hashMapOf(
                                    "firstName" to firstName,
                                    "lastName" to lastName,
                                    "email" to email,
                                    "age" to age,
                                    "weight" to weight,
                                    "height" to height,
                                    "gender" to gender,
                                    "createdAt" to System.currentTimeMillis()
                                )

                                // ✅ Store user data in Firestore
                                firestore.collection("users").document(userId)
                                    .set(userMap)
                                    .addOnSuccessListener {
                                        Snackbar.make(stepContainer, "Account Created Successfully!", Snackbar.LENGTH_LONG).show()
                                        startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                                        finish()
                                    }
                                    .addOnFailureListener {
                                        Snackbar.make(stepContainer, "Failed to save user data: ${it.message}", Snackbar.LENGTH_LONG).show()
                                    }
                            } else {
                                Snackbar.make(stepContainer, "Registration failed: ${task.exception?.message}", Snackbar.LENGTH_LONG).show()
                            }
                        }
                }
            }
        }
    }
}
