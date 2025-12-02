package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.fittrack.ui.home.HomeFragment
import com.example.fittrack.ui.profile.ProfileFragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

// ADD THIS IMPORTANT IMPORT
import com.example.fittrack.FirestoreDatabaseHelper

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainActivity"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var session: SessionManager
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var currentFragmentTag: String = "HOME"

    private var tvUserName: TextView? = null
    private var tvUserType: TextView? = null
    private var imgProfile: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session = SessionManager(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        val headerView = layoutInflater.inflate(R.layout.nav_footer_main, navView, false)

        try {
            val existing = try { navView.getHeaderView(0) } catch (_: Exception) { null }
            if (existing != null) navView.removeHeaderView(existing)
        } catch (_: Exception) {}

        navView.addHeaderView(headerView)

        tvUserName = headerView.findViewById(R.id.tv_user_name)
        tvUserType = headerView.findViewById(R.id.tv_user_type)
        imgProfile = headerView.findViewById(R.id.img_profile)
        imgProfile?.setImageResource(R.drawable.ic_profile)

        val firstIntent = intent.getStringExtra("firstName")
        val lastIntent = intent.getStringExtra("lastName")
        val typeIntent = intent.getStringExtra("userType")

        if (!firstIntent.isNullOrBlank()) {
            tvUserName?.text = "$firstIntent ${lastIntent ?: ""}".trim()
            tvUserType?.text = typeIntent ?: "Fitness Member"
            saveSessionQuick(firstIntent, lastIntent ?: "", auth.currentUser?.email ?: "")
        } else {
            val sessFirst = session.getFirstName()
            val sessLast = session.getLastName()
            if (!sessFirst.isNullOrBlank()) {
                tvUserName?.text = "$sessFirst ${sessLast ?: ""}".trim()
                tvUserType?.text = session.getUserType() ?: "Fitness Member"
            } else {
                val displayName = auth.currentUser?.displayName
                tvUserName?.text = displayName ?: "Guest User"
                tvUserType?.text = "Welcome"
            }
        }

        // UID is the correct document ID — NOT email
        val uid = auth.currentUser?.uid ?: ""

        if (uid.isNotEmpty()) {
            firestore.collection("users").document(uid)
                .get(Source.CACHE)
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        applyProfileToUI(doc.data ?: emptyMap(), true)
                    }
                    fetchProfileFromServer(uid)
                }
                .addOnFailureListener {
                    fetchProfileFromServer(uid)
                }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
            navView.setCheckedItem(R.id.nav_home)
        }
    }

    private fun fetchProfileFromServer(uid: String) {
        firestore.collection("users").document(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    applyProfileToUI(doc.data ?: emptyMap(), true)
                } else {
                    val helper = FirestoreDatabaseHelper()
                    helper.getUserDetails(uid) { result ->
                        if (result != null) applyProfileToUI(result, true)
                    }
                }
            }
            .addOnFailureListener {
                val helper = FirestoreDatabaseHelper()
                helper.getUserDetails(uid) { result ->
                    if (result != null) applyProfileToUI(result, true)
                }
            }
    }

    private fun applyProfileToUI(map: Map<String, Any>, save: Boolean) {
        val first = (map["firstName"] as? String)
            ?: (map["name"] as? String)
            ?: auth.currentUser?.displayName
            ?: ""
        val last = (map["lastName"] as? String) ?: ""
        val type = (map["userType"] as? String) ?: "Fitness Member"

        runOnUiThread {
            tvUserName?.text = "$first $last".trim().ifBlank { "User" }
            tvUserType?.text = type
        }

        if (save) {
            val email = (map["email"] as? String) ?: auth.currentUser?.email ?: ""
            saveSessionQuick(first, last, email)
        }
    }

    private fun saveSessionQuick(first: String, last: String, email: String) {
        val sess = SessionManager(this)
        sess.saveLoginSession(first, last, email, sess.getAge() ?: 25, sess.getHeight() ?: 170)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment()).commit()

            R.id.nav_profile -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment()).commit()

            R.id.nav_view -> {
                auth.signOut()
                session.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
