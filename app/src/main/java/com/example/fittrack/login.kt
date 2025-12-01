package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var googleActivityLauncher: ActivityResultLauncher<Intent>

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvSignUp: TextView   // signup link

    // Firestore instance
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Wire UI (IDs from your activity_login.xml)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnGoogle = findViewById(R.id.btnGoogle)
        tvSignUp = findViewById(R.id.tvSignUp)    // <-- bind signup textview

        // Set signup click listener to open SignupActivity
        tvSignUp.setOnClickListener {
            try {
                startActivity(Intent(this@LoginActivity, SignupActivity::class.java))
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch SignupActivity: ${ex.message}", ex)
                Snackbar.make(etEmail, "Unable to open signup screen", Snackbar.LENGTH_LONG).show()
            }
        }

        setupGoogleSignIn()

        btnGoogle.setOnClickListener {
            googleSignInClient?.signInIntent?.let { intent ->
                googleActivityLauncher.launch(intent)
            } ?: run {
                Snackbar.make(etEmail, "Google Sign-In not configured", Snackbar.LENGTH_LONG).show()
                Log.w(TAG, "googleSignInClient is null")
            }
        }

        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid Email"
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                etPassword.error = "Enter password"
                return@setOnClickListener
            }

            // Email/password sign-in
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Signed in — now check for profile in Firestore
                        val user = auth.currentUser
                        if (user == null) {
                            // Unexpected — but handle safely
                            Snackbar.make(etEmail, "Login succeeded but user is null", Snackbar.LENGTH_LONG).show()
                            return@addOnCompleteListener
                        }
                        handlePostSignIn(user.uid)
                    } else {
                        Snackbar.make(etEmail, "Login failed: ${task.exception?.message}", Snackbar.LENGTH_LONG).show()
                        Log.w(TAG, "Email sign-in failed", task.exception)
                    }
                }
        }
    }

    /** ---------------- GOOGLE SIGN IN SETUP ---------------- */
    private fun setupGoogleSignIn() {
        val webClientId = try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            ""
        }

        Log.d(TAG, "Using Web Client ID: $webClientId")

        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        if (webClientId.isNotEmpty()) gsoBuilder.requestIdToken(webClientId)
        else Log.w(TAG, "default_web_client_id missing — Google token may be null")

        val gso = gsoBuilder.build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(TAG, "Google result code = ${result.resultCode}")

            if (result.resultCode == RESULT_CANCELED) {
                Snackbar.make(etEmail, "Google Sign-In Cancelled!", Snackbar.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken == null) {
                    Snackbar.make(etEmail, "Google Token is NULL! Check Web Client ID", Snackbar.LENGTH_LONG).show()
                    return@registerForActivityResult
                }

                // Exchange token with Firebase
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { firebaseTask ->
                        if (firebaseTask.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null) handlePostSignIn(user.uid)
                            else {
                                Snackbar.make(etEmail, "Google login succeeded but user null", Snackbar.LENGTH_LONG).show()
                            }
                        } else {
                            Snackbar.make(etEmail, "Firebase auth failed: ${firebaseTask.exception?.message}", Snackbar.LENGTH_LONG).show()
                            Log.w(TAG, "Firebase credential sign-in failed", firebaseTask.exception)
                        }
                    }

            } catch (e: ApiException) {
                Snackbar.make(etEmail, "Google Sign-In Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                Log.w(TAG, "GoogleSignIn ApiException: code=${e.statusCode} msg=${e.message}")
            } catch (e: Exception) {
                Snackbar.make(etEmail, "Google Sign-In unexpected error: ${e.message}", Snackbar.LENGTH_LONG).show()
                Log.e(TAG, "GoogleSignIn unexpected", e)
            }
        }
    }

    /**
     * After FirebaseAuth sign-in, check whether user's profile exists in Firestore.
     * We expect profile to be at collection "users" with document id = uid.
     * - If exists -> go to MainActivity
     * - If missing -> go to SignupActivity (or Profile creation)
     */
    private fun handlePostSignIn(uid: String) {
        try {
            val usersColl = firestore.collection("users")
            val docRef = usersColl.document(uid)
            docRef.get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "User profile found for uid=$uid")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Log.d(TAG, "User profile NOT found for uid=$uid -> redirecting to Signup/Profile creation")
                        val i = Intent(this@LoginActivity, SignupActivity::class.java)
                        auth.currentUser?.email?.let { i.putExtra("email_from_login", it) }
                        i.putExtra("uid_from_login", uid)
                        startActivity(i)
                        finish()
                    }
                }
                .addOnFailureListener { ex ->
                    Log.e(TAG, "Failed to read user profile for uid=$uid: ${ex.message}", ex)
                    Snackbar.make(etEmail, "Failed to verify profile: ${ex.message}", Snackbar.LENGTH_LONG).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
        } catch (ex: Exception) {
            Log.e(TAG, "handlePostSignIn fatal: ${ex.message}", ex)
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
    }
}
