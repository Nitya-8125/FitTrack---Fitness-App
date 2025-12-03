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

        // Initialize FirebaseAuth early so we can check persistent sign-in
        auth = FirebaseAuth.getInstance()

        // --- PERSISTENT LOGIN: if already signed-in, skip login screen ---
        val current = auth.currentUser
        if (current != null) {
            Log.d(TAG, "User already signed in (uid=${current.uid}) — routing to MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        try {
            setContentView(R.layout.activity_login)
        } catch (ex: Exception) {
            Log.e(TAG, "setContentView(activity_login) failed: ${ex.message}", ex)
            // if layout inflate fails, just finish to avoid crash loop
            finish()
            return
        }

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
     * After FirebaseAuth sign-in, ensure user's profile exists in Firestore.
     * If it exists -> MainActivity
     * If missing -> create profile from FirebaseUser (Google) then -> MainActivity
     */
    /**
     * Fast-start flow:
     * 1) Save a minimal session from FirebaseUser and immediately open MainActivity (fast UI).
     * 2) In the background, verify/create the Firestore profile and update the session (async).
     */
    // inside LoginActivity.kt (replace existing handlePostSignIn)
    private fun handlePostSignIn(uid: String) {
        try {
            val currentUser = auth.currentUser
            val email = currentUser?.email ?: ""
            val displayName = currentUser?.displayName ?: ""
            val (firstGuess, lastGuess) = parseName(displayName)

            val safeFirst = if (firstGuess.isNotEmpty()) firstGuess else "User"
            val safeLast = lastGuess
            val defaultAge = 25
            val defaultHeight = 170
            val defaultUserType = "Fitness Member"

            // Save a minimal session so UI in MainActivity can show something immediately
            val localSession = SessionManager(this)
            localSession.saveLoginSession(safeFirst, safeLast, email, defaultAge, defaultHeight, defaultUserType)

            val helper = FirestoreDatabaseHelper()

            // Attempt to migrate legacy email-doc -> uid doc BEFORE launching MainActivity.
            // This is asynchronous but we wait for its completion (it should be fast: few reads/writes).
            helper.migrateEmailDocToUid(email, uid) { migrated ->
                Log.d(TAG, "migrateEmailDocToUid returned migrated=$migrated for email=$email uid=$uid")

                // After migration attempt (whether migrated or not), run normal profile/sync flow.
                // Start MainActivity on UI thread.
                runOnUiThread {
                    val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                        putExtra("firstName", safeFirst)
                        putExtra("lastName", safeLast)
                        putExtra("userType", defaultUserType)
                        putExtra("email_from_login", email)
                    }
                    startActivity(intent)
                    // finish login activity so user can't go back
                    finish()
                }

                // Background: ensure Firestore profile exists / update local session with authoritative data
                Thread {
                    try {
                        helper.getUserDetails(email) { existingData ->
                            try {
                                val appCtx = applicationContext
                                val sessionForBg = SessionManager(appCtx)
                                if (existingData != null) {
                                    val first = (existingData["firstName"] as? String) ?: safeFirst
                                    val last = (existingData["lastName"] as? String) ?: safeLast
                                    val age = ((existingData["age"] as? Number)?.toInt()) ?: defaultAge
                                    val height = ((existingData["height"] as? Number)?.toInt()) ?: defaultHeight
                                    val userType = (existingData["userType"] as? String) ?: defaultUserType
                                    val emailField = (existingData["email"] as? String) ?: email

                                    sessionForBg.saveLoginSession(first, last, emailField, age, height, userType)
                                } else {
                                    helper.registerUser(
                                        email = email,
                                        password = "",
                                        firstName = safeFirst,
                                        lastName = safeLast,
                                        age = defaultAge,
                                        gender = "Prefer not to say",
                                        height = defaultHeight.toDouble(),
                                        weight = 70.0
                                    ) { created ->
                                        if (created) {
                                            helper.getUserDetails(email) { newData ->
                                                if (newData != null) {
                                                    val first = (newData["firstName"] as? String) ?: safeFirst
                                                    val last = (newData["lastName"] as? String) ?: safeLast
                                                    val age = ((newData["age"] as? Number)?.toInt()) ?: defaultAge
                                                    val height = ((newData["height"] as? Number)?.toInt()) ?: defaultHeight
                                                    val userType = (newData["userType"] as? String) ?: defaultUserType
                                                    val emailField = (newData["email"] as? String) ?: email
                                                    sessionForBg.saveLoginSession(first, last, emailField, age, height, userType)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                // swallow background exceptions
                            }
                        }
                    } catch (_: Exception) {
                        // ignore background failures
                    } finally {
                        // clear per-device step prefs so new account starts fresh on this device
                        try {
                            applicationContext.getSharedPreferences("step_prefs", MODE_PRIVATE).edit().clear().apply()
                        } catch (_: Exception) {}
                    }
                }.start()
            }
        } catch (ex: Exception) {
            try { SessionManager(this).clearSession() } catch (_: Exception) {}
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
    }




    /** Helper: split displayName into first & last name (best-effort) */
    private fun parseName(displayName: String?): Pair<String, String> {
        if (displayName == null || displayName.isBlank()) return Pair("", "")
        val parts = displayName.trim().split("\\s+".toRegex())
        return if (parts.size == 1) {
            Pair(parts[0], "")
        } else {
            val first = parts.first()
            val last = parts.subList(1, parts.size).joinToString(" ")
            Pair(first, last)
        }
    }

}
