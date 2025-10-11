package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvSignUp: TextView

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager

    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var googleActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnGoogle = findViewById(R.id.btnGoogle)
        tvSignUp = findViewById(R.id.tvSignUp)

        dbHelper = DatabaseHelper(this)
        sessionManager = SessionManager(this)

        // Auto-login
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Initialize Google Sign-In
        setupGoogleSignIn()

        btnSignIn.setOnClickListener { validateAndLogin() }

        btnGoogle.setOnClickListener { launchGoogleSignIn() }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.google_sign_id)) // Use your Web Client ID
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                handleGoogleSignIn(result.data)
            } else {
                Toast.makeText(this, "Google Sign-In Cancelled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchGoogleSignIn() {
        googleSignInClient?.signInIntent?.let {
            googleActivityLauncher.launch(it)
        } ?: run {
            Toast.makeText(this, "Google Sign-In not initialized!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGoogleSignIn(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrEmpty()) {
                val email = account.email!!
                val fullName = account.displayName ?: ""
                val nameParts = fullName.split(" ")
                val firstName = nameParts.getOrNull(0) ?: ""
                val lastName = nameParts.getOrNull(1) ?: ""

                // Save user to DB if not exists
                val db = DatabaseHelper(this)
                if (!db.isEmailTaken(email)) {
                    try {
                        db.registerGoogleUser(email, firstName, lastName)
                    } catch (e: Exception) {
                        Log.e("DB_ERROR", "Failed to register Google user: ${e.message}")
                    }
                }

                // Save session
                sessionManager.saveLoginSession(firstName, lastName, email, 0, 0)

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Google Sign-In Failed! Email not found.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.e("Google_Sign_In_Error", "Sign In Failed: ${e.message}", e)
            Toast.makeText(this, "Google Sign-In Failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Invalid email"
            return
        }
        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            return
        }
        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            return
        }

        val cursor = dbHelper.getUserByEmail(email)
        if (cursor != null && cursor.moveToFirst()) {
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow("firstName"))
            val lastName = cursor.getString(cursor.getColumnIndexOrThrow("lastName"))
            val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
            val height = cursor.getInt(cursor.getColumnIndexOrThrow("height"))

            sessionManager.saveLoginSession(firstName, lastName, email, age, height)

            Toast.makeText(this, "Welcome $firstName $lastName!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            Snackbar.make(btnSignIn, "Invalid email or password", Snackbar.LENGTH_SHORT).show()
        }
        cursor?.close()
    }
}
