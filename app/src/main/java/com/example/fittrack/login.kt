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
import com.example.fittrack.ui.home.HomeFragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.AuthResult
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
    private lateinit var tvSignUp: TextView

    // Firestore instance
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_login)
        } catch (ex: Exception) {
            Log.e(TAG, "setContentView(activity_login) failed: ${ex.message}", ex)
            // if layout inflate fails, just finish to avoid crash loop
            finish()
            return
        }

        auth = FirebaseAuth.getInstance()

        // Bind views — guarded so missing ids are reported
        try {
            etEmail = findViewById(R.id.etEmail)
            etPassword = findViewById(R.id.etPassword)
            btnSignIn = findViewById(R.id.btnSignIn)
            btnGoogle = findViewById(R.id.btnGoogle)
            tvSignUp = findViewById(R.id.tvSignUp)
        } catch (ex: Exception) {
            Log.e(TAG, "findViewById failed: ${ex.message}", ex)
            // if critical view missing, finish to avoid crashes later
            finish()
            return
        }

        // signup click (open SignupActivity)
        tvSignUp.setOnClickListener {
            try {
                startActivity(Intent(this@LoginActivity, SignupActivity::class.java))
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open SignupActivity: ${ex.message}", ex)
                Snackbar.make(etEmail, "Unable to open signup screen", Snackbar.LENGTH_LONG).show()
            }
        }

        setupGoogleSignIn()

        // Google button click
        btnGoogle.setOnClickListener {
            try {
                val intent = googleSignInClient?.signInIntent
                if (intent != null) {
                    googleActivityLauncher.launch(intent)
                } else {
                    Log.w(TAG, "GoogleSignIn client is null")
                    Snackbar.make(etEmail, "Google Sign-In not configured", Snackbar.LENGTH_LONG).show()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "btnGoogle click failed: ${ex.message}", ex)
                Snackbar.make(etEmail, "Google Sign-In failed to start", Snackbar.LENGTH_LONG).show()
            }
        }

        // Email/password login — defensive
        btnSignIn.setOnClickListener {
            val email = try { etEmail.text.toString().trim() } catch (t: Throwable) { "" }
            val pass = try { etPassword.text.toString() } catch (t: Throwable) { "" }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid Email"
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                etPassword.error = "Enter password"
                return@setOnClickListener
            }

            try {
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener { task ->
                        try {
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    handlePostSignIn(user.uid)
                                } else {
                                    Log.w(TAG, "signIn successful but currentUser==null")
                                    Snackbar.make(etEmail, "Login succeeded but user missing", Snackbar.LENGTH_LONG).show()
                                }
                            } else {
                                Log.w(TAG, "Email sign-in failed", task.exception)
                                Snackbar.make(etEmail, "Login failed: ${task.exception?.message}", Snackbar.LENGTH_LONG).show()
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Exception in signIn onComplete: ${ex.message}", ex)
                            Snackbar.make(etEmail, "Unexpected error during sign-in", Snackbar.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { ex ->
                        Log.e(TAG, "signInWithEmail failed (onFailure): ${ex.message}", ex)
                        Snackbar.make(etEmail, "Login failed: ${ex.message}", Snackbar.LENGTH_LONG).show()
                    }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception calling signInWithEmailAndPassword: ${ex.message}", ex)
                Snackbar.make(etEmail, "Login error: ${ex.message}", Snackbar.LENGTH_LONG).show()
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

        if (webClientId.isNotEmpty()) gsoBuilder.requestIdToken(webClientId) else Log.w(TAG, "default_web_client_id missing")

        try {
            val gso = gsoBuilder.build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to build GoogleSignInClient: ${ex.message}", ex)
        }

        googleActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            try {
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
                        Log.w(TAG, "idToken is null after GoogleSignIn")
                        Snackbar.make(etEmail, "Google Token is NULL! Check Web Client ID", Snackbar.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }

                    // Sign in to Firebase
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { firebaseTask ->
                            try {
                                if (firebaseTask.isSuccessful) {
                                    val user = auth.currentUser
                                    if (user != null) handlePostSignIn(user.uid)
                                    else {
                                        Log.w(TAG, "Firebase signInWithCredential succeeded but user is null")
                                        Snackbar.make(etEmail, "Logged in but user missing", Snackbar.LENGTH_LONG).show()
                                    }
                                } else {
                                    Log.w(TAG, "Firebase credential sign-in failed", firebaseTask.exception)
                                    Snackbar.make(etEmail, "Firebase auth failed: ${firebaseTask.exception?.message}", Snackbar.LENGTH_LONG).show()
                                }
                            } catch (ex: Exception) {
                                Log.e(TAG, "Exception in firebase credential onComplete: ${ex.message}", ex)
                                Snackbar.make(etEmail, "Unexpected Firebase auth error", Snackbar.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener { ex ->
                            Log.e(TAG, "signInWithCredential onFailure: ${ex.message}", ex)
                            Snackbar.make(etEmail, "Firebase auth failed: ${ex.message}", Snackbar.LENGTH_LONG).show()
                        }

                } catch (e: ApiException) {
                    Log.w(TAG, "GoogleSignIn ApiException: code=${e.statusCode} msg=${e.message}")
                    Snackbar.make(etEmail, "Google Sign-In Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Unhandled exception in Google launcher callback: ${ex.message}", ex)
                Snackbar.make(etEmail, "Google Sign-In failed unexpectedly", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /**
     * After FirebaseAuth sign-in, check whether user's profile exists in Firestore.
     * If profile exists -> MainActivity; else -> SignupActivity.
     */
    private fun handlePostSignIn(uid: String) {
        try {
            // Defensive: if firestore null (shouldn't be) avoid crash
            val usersColl = try { firestore.collection("users") } catch (ex: Exception) {
                Log.e(TAG, "Firestore collection access failed: ${ex.message}", ex)
                // fallback: go to MainActivity
                startActivity(Intent(this@LoginActivity, HomeFragment::class.java))
                finish()
                return
            }

            val docRef = usersColl.document(uid)
            docRef.get()
                .addOnSuccessListener { snapshot ->
                    try {
                        if (snapshot != null && snapshot.exists()) {
                            Log.d(TAG, "User profile found for uid=$uid")
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Log.d(TAG, "User profile NOT found for uid=$uid -> redirecting to Signup")
                            val i = Intent(this@LoginActivity, SignupActivity::class.java)
                            auth.currentUser?.email?.let { i.putExtra("email_from_login", it) }
                            i.putExtra("uid_from_login", uid)
                            startActivity(i)
                            finish()
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Exception in addOnSuccessListener: ${ex.message}", ex)
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
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
