package com.example.fittrack

import android.os.Bundle
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var session: SessionManager
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var currentFragmentTag: String = "HOME"

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

        // Inflate footer
        val footerView: View = layoutInflater.inflate(R.layout.nav_footer_main, navView, false)
        navView.addView(footerView)

        val tvUserName = footerView.findViewById<TextView>(R.id.tv_user_name)
        val tvUserType = footerView.findViewById<TextView>(R.id.tv_user_type)
        val imgProfile = footerView.findViewById<ImageView>(R.id.img_profile)
        imgProfile.setImageResource(R.drawable.ic_profile)

        // ---------------- LOAD USER DATA FROM FIRESTORE ----------------
        val currentUser = auth.currentUser
        val email = currentUser?.email ?: session.getUserEmail()

        if (!email.isNullOrEmpty()) {
            firestore.collection("users").document(email)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val firstName = document.getString("firstName") ?: "Guest"
                        val lastName = document.getString("lastName") ?: "User"
                        val userType = document.getString("userType") ?: "Fitness Member"

                        tvUserName.text = "$firstName $lastName"
                        tvUserType.text = userType
                    } else {
                        tvUserName.text = "Guest User"
                        tvUserType.text = "Welcome"
                    }
                }
                .addOnFailureListener {
                    tvUserName.text = "Guest User"
                    tvUserType.text = "Welcome"
                }
        } else {
            tvUserName.text = "Guest User"
            tvUserType.text = "Welcome"
        }

        // ---------------- LOAD DEFAULT FRAGMENT ----------------
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
            navView.setCheckedItem(R.id.nav_home)
            currentFragmentTag = "HOME"
        }
    }

    // ---------------- NAVIGATION HANDLER ----------------
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                if (currentFragmentTag != "HOME") {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()
                    currentFragmentTag = "HOME"
                }
            }
            R.id.nav_profile -> {
                if (currentFragmentTag != "PROFILE") {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ProfileFragment())
                        .commit()
                    currentFragmentTag = "PROFILE"
                }
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
