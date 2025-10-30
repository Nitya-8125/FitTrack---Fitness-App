package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvSignUp: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
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

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)

        // Auto-login if user already signed in
        if (sessionManager.isLoggedIn() || auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setupGoogleSignIn()

        btnSignIn.setOnClickListener { validateAndLogin() }
        btnGoogle.setOnClickListener { launchGoogleSignIn() }
        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    // ---------------- GOOGLE SIGN-IN ----------------

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.google_sign_id))
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
        } ?: Toast.makeText(this, "Google Sign-In not initialized!", Toast.LENGTH_SHORT).show()
    }

    private fun handleGoogleSignIn(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrEmpty()) {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        val email = account.email!!
                        val fullName = account.displayName ?: ""
                        val firstName = fullName.split(" ").getOrNull(0) ?: ""
                        val lastName = fullName.split(" ").getOrNull(1) ?: ""

                        // Add user document if not exists
                        val userRef = firestore.collection("users").document(email)
                        userRef.get().addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                val newUser = hashMapOf(
                                    "email" to email,
                                    "firstName" to firstName,
                                    "lastName" to lastName,
                                    "userType" to "user",
                                    "age" to 0,
                                    "gender" to "",
                                    "height" to 0.0,
                                    "weight" to 70.0,
                                    "weightToday" to 70.0,
                                    "target_weight" to 70.0,
                                    "daily_steps_goal" to 10000,
                                    "daily_calories_goal" to 2000,
                                    "stepsToday" to 0,
                                    "caloriesToday" to 0
                                )
                                userRef.set(newUser)
                            }

                            sessionManager.saveLoginSession(firstName, lastName, email, 0, 0)
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("GoogleAuth", "Sign-in failed", e)
                        Toast.makeText(this, "Google Sign-In Failed!", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: ApiException) {
            Log.e("Google_SignIn_Error", "Sign In Failed: ${e.message}", e)
            Toast.makeText(this, "Google Sign-In Failed!", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- EMAIL / PASSWORD LOGIN ----------------

    private fun validateAndLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty()) { etEmail.error = "Email is required"; return }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.error = "Invalid email"; return }
        if (password.isEmpty()) { etPassword.error = "Password is required"; return }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Fetch user info from Firestore
                firestore.collection("users").document(email).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val firstName = document.getString("firstName") ?: ""
                            val lastName = document.getString("lastName") ?: ""
                            val age = (document.getLong("age") ?: 0L).toInt()
                            val heightDouble = (document.getDouble("height") ?: 0.0)
                            val heightInt = heightDouble.toInt()

                            sessionManager.saveLoginSession(firstName, lastName, email, age, heightInt)

                            Toast.makeText(this, "Welcome $firstName $lastName!", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            Snackbar.make(btnSignIn, "User data not found!", Snackbar.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener {
                Snackbar.make(btnSignIn, "Invalid email or password", Snackbar.LENGTH_SHORT).show()
            }
    }
}
