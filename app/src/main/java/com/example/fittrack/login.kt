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

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvSignUp: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var sessionManager: SessionManager
    private val firestoreHelper = FirestoreDatabaseHelper()

    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var googleActivityLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "GoogleSignInDebug"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnGoogle = findViewById(R.id.btnGoogle)
        tvSignUp = findViewById(R.id.tvSignUp)

        auth = FirebaseAuth.getInstance()
        sessionManager = SessionManager(this)

        // Auto-login if Firebase already has user
        if (auth.currentUser != null) {
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
        val webClientId = getString(R.string.google_sign_id)
        Log.d(TAG, "Using Web Client ID: $webClientId")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)         // must be Web client id
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(TAG, "Google launcher resultCode=${result.resultCode}, data=${result.data}")
            if (result.resultCode == RESULT_OK) {
                handleGoogleSignIn(result.data)
            } else {
                // show code so you know why cancelled (resultCode may be 0)
                Toast.makeText(this, "Google Sign-In Cancelled! resultCode=${result.resultCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchGoogleSignIn() {
        googleSignInClient?.signInIntent?.let {
            googleActivityLauncher.launch(it)
        } ?: Toast.makeText(this, "Google Sign-In not initialized!", Toast.LENGTH_SHORT).show()
    }

    private fun handleGoogleSignIn(data: Intent?) {
        // Log intent extras if any
        Log.d(TAG, "handleGoogleSignIn: intentData=$data")
        data?.extras?.keySet()?.forEach { key ->
            Log.d(TAG, "Intent extra: $key = ${data.extras?.get(key)}")
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Google account retrieved: id=${account?.id}, email=${account?.email}")

            val idToken = account?.idToken
            if (idToken == null) {
                Log.e(TAG, "idToken is null — check that strings.xml uses WEB client id and OAuth client exists")
                Toast.makeText(this, "Google Sign-In failed: idToken missing (check Web client id)", Toast.LENGTH_LONG).show()
                return
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    Log.d(TAG, "Firebase signInWithCredential success: uid=${auth.currentUser?.uid}")

                    val email = account.email ?: ""
                    val fullName = account.displayName ?: ""
                    val firstName = fullName.split(" ").getOrNull(0) ?: ""
                    val lastName = fullName.split(" ").getOrNull(1) ?: ""

                    // Save new Google users in Firestore using helper (helper uses UID when available)
                    firestoreHelper.registerUser(
                        email = email,
                        password = "",       // not used for Google accounts
                        firstName = firstName,
                        lastName = lastName,
                        age = 0,
                        gender = "",
                        height = 0.0,
                        weight = 70.0
                    ) { success ->
                        Log.d(TAG, "firestoreHelper.registerUser success=$success")
                    }

                    sessionManager.saveLoginSession(firstName, lastName, email, 0, 0)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firebase signInWithCredential failed: ${e.message}", e)
                    Toast.makeText(this, "Firebase auth failed: ${e.message}", Toast.LENGTH_LONG).show()
                }

        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In ApiException: statusCode=${e.statusCode}, message=${e.message}", e)
            Toast.makeText(this, "Google Sign-In failed (code=${e.statusCode})", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google Sign-In error: ${e.message}", e)
            Toast.makeText(this, "Google Sign-In unexpected error", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- EMAIL / PASSWORD LOGIN ----------------

    private fun validateAndLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty()) { etEmail.error = "Email is required"; return }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Invalid email"; return
        }
        if (password.isEmpty()) { etPassword.error = "Password is required"; return }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                loadUserFromFirestore(email)
            }
            .addOnFailureListener {
                Snackbar.make(btnSignIn, "Invalid email or password", Snackbar.LENGTH_SHORT).show()
            }
    }

    // Loads user details using FirestoreDatabaseHelper
    private fun loadUserFromFirestore(email: String) {
        firestoreHelper.getUserDetails(email) { data ->
            if (data == null) {
                Snackbar.make(btnSignIn, "User data not found!", Snackbar.LENGTH_SHORT).show()
                return@getUserDetails
            }

            val firstName = data["firstName"]?.toString() ?: ""
            val lastName = data["lastName"]?.toString() ?: ""
            val age = (data["age"] as? Long)?.toInt() ?: 0
            val height = ((data["height"] as? Double) ?: 0.0).toInt()

            sessionManager.saveLoginSession(firstName, lastName, email, age, height)

            Toast.makeText(this, "Welcome $firstName $lastName!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
