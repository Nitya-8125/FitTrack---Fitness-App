package com.example.fittrack

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.fittrack.ui.analysis.AnalysisFragment
import com.example.fittrack.ui.home.HomeFragment
import com.example.fittrack.ui.settings.SettingsFragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var session: SessionManager
    private var loggedInUser: String = "User"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Load dark mode BEFORE super.onCreate
        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SessionManager
        session = SessionManager(this)
        loggedInUser = session.getFullName().ifBlank { "User" }

        // Setup toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        // --- Add footer programmatically ---
        val footerView = layoutInflater.inflate(R.layout.nav_footer_main, navView, false)
        navView.addView(footerView, navView.childCount)

        val darkModeSwitch: Switch = footerView.findViewById(R.id.switch_dark_mode)
        val darkModeLabel: TextView = footerView.findViewById(R.id.tv_dark_mode)
        val profileName: TextView = footerView.findViewById(R.id.tv_user_name)
        val profileType: TextView = footerView.findViewById(R.id.tv_user_type)
        val profileImage: ImageView = footerView.findViewById(R.id.img_profile)

        profileName.text = loggedInUser
        profileType.text = "Premium Member"
        profileImage.setImageResource(R.drawable.ic_profile)

        // Sync dark mode
        darkModeSwitch.isChecked = isDarkMode
        darkModeLabel.text = if (isDarkMode) "Dark Mode" else "Light Mode"
        darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            darkModeLabel.text = if (checked) "Dark Mode" else "Light Mode"
            sharedPrefs.edit().putBoolean("dark_mode", checked).apply()
        }

        // 👉 Default fragment = HomeFragment with full name
        if (savedInstanceState == null) {
            openHomeFragment()
            navView.setCheckedItem(R.id.nav_home)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> openHomeFragment()
            R.id.nav_analysis -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AnalysisFragment()).commit()
            R.id.nav_settings -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment()).commit()
            R.id.nav_profile -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment()).commit()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openHomeFragment() {
        val homeFragment = HomeFragment()
        val bundle = Bundle()
        bundle.putString("userName", loggedInUser) // Pass full name to fragment
        homeFragment.arguments = bundle

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, homeFragment)
            .commit()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else super.onBackPressed()
    }
}


//package com.example.fittrack
//
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.view.MenuItem
//import android.widget.ImageView
//import android.widget.Switch
//import android.widget.TextView
//import androidx.appcompat.app.ActionBarDrawerToggle
//import androidx.appcompat.app.AppCompatActivity
//import androidx.appcompat.app.AppCompatDelegate
//import androidx.core.view.GravityCompat
//import androidx.drawerlayout.widget.DrawerLayout
//import com.example.fittrack.ui.analysis.AnalysisFragment
//import com.example.fittrack.ui.home.HomeFragment
//import com.example.fittrack.ui.settings.SettingsFragment
//import com.google.android.material.navigation.NavigationView
//
//class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
//
//    private lateinit var drawerLayout: DrawerLayout
//    private lateinit var navView: NavigationView
//    private lateinit var sharedPrefs: SharedPreferences
//    private lateinit var sessionManager: SessionManager
//    private var loggedInUser: String = "User"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        // Load saved dark mode setting BEFORE super.onCreate
//        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
//        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
//        if (isDarkMode) {
//            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
//        } else {
//            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
//        }
//
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        // Initialize session manager and fetch full name
//        sessionManager = SessionManager(this)
//        loggedInUser = sessionManager.getFullName().ifEmpty { "User" }
//
//        // Setup toolbar
//        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
//        setSupportActionBar(toolbar)
//
//        drawerLayout = findViewById(R.id.drawer_layout)
//        navView = findViewById(R.id.nav_view)
//
//        val toggle = ActionBarDrawerToggle(
//            this, drawerLayout, toolbar,
//            R.string.navigation_drawer_open,
//            R.string.navigation_drawer_close
//        )
//        drawerLayout.addDrawerListener(toggle)
//        toggle.syncState()
//
//        navView.setNavigationItemSelectedListener(this)
//
//        // --- Add footer programmatically ---
//        val footerView = layoutInflater.inflate(R.layout.nav_footer_main, navView, false)
//        navView.addView(footerView, navView.childCount)
//
//        val darkModeSwitch: Switch = footerView.findViewById(R.id.switch_dark_mode)
//        val darkModeLabel: TextView = footerView.findViewById(R.id.tv_dark_mode)
//        val profileName: TextView = footerView.findViewById(R.id.tv_user_name)
//        val profileType: TextView = footerView.findViewById(R.id.tv_user_type)
//        val profileImage: ImageView = footerView.findViewById(R.id.img_profile)
//
//        profileName.text = loggedInUser
//        profileType.text = "Premium Member"
//        profileImage.setImageResource(R.drawable.ic_profile)
//
//        // Sync dark mode state
//        darkModeSwitch.isChecked = isDarkMode
//        darkModeLabel.text = if (isDarkMode) "Dark Mode" else "Light Mode"
//
//        darkModeSwitch.setOnCheckedChangeListener { _, checked ->
//            if (checked) {
//                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
//                darkModeLabel.text = "Dark Mode"
//                sharedPrefs.edit().putBoolean("dark_mode", true).apply()
//            } else {
//                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
//                darkModeLabel.text = "Light Mode"
//                sharedPrefs.edit().putBoolean("dark_mode", false).apply()
//            }
//        }
//
//        // 👉 Default fragment = HomeFragment
//        if (savedInstanceState == null) {
//            openHomeFragment()
//            navView.setCheckedItem(R.id.nav_home)
//        }
//    }
//
//    override fun onNavigationItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.nav_home -> openHomeFragment()
//            R.id.nav_analysis -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.fragment_container, AnalysisFragment())
//                    .commit()
//            }
//            R.id.nav_settings -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.fragment_container, SettingsFragment())
//                    .commit()
//            }
//            R.id.nav_profile -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.fragment_container, Profile())
//                    .commit()
//            }
//        }
//        drawerLayout.closeDrawer(GravityCompat.START)
//        return true
//    }
//
//    private fun openHomeFragment() {
//        val homeFragment = HomeFragment()
//        // No need to pass username via bundle anymore; HomeFragment reads from SessionManager
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.fragment_container, homeFragment)
//            .commit()
//    }
//
//    override fun onBackPressed() {
//        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            drawerLayout.closeDrawer(GravityCompat.START)
//        } else {
//            super.onBackPressed()
//        }
//    }
//}
//
////package com.example.fittrack
////
////import android.content.SharedPreferences
////import android.os.Bundle
////import android.view.MenuItem
////import android.widget.ImageView
////import android.widget.Switch
////import android.widget.TextView
////import androidx.appcompat.app.ActionBarDrawerToggle
////import androidx.appcompat.app.AppCompatActivity
////import androidx.appcompat.app.AppCompatDelegate
////import androidx.core.view.GravityCompat
////import androidx.drawerlayout.widget.DrawerLayout
////import com.example.fittrack.ui.analysis.AnalysisFragment
////import com.example.fittrack.ui.home.HomeFragment
////import com.example.fittrack.ui.settings.SettingsFragment
////import com.google.android.material.navigation.NavigationView
////
////class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
////
////    private lateinit var drawerLayout: DrawerLayout
////    private lateinit var navView: NavigationView
////    private lateinit var sharedPrefs: SharedPreferences
////    private var loggedInUser: String = "User"
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        // Load saved dark mode setting BEFORE super.onCreate
////        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
////        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
////        if (isDarkMode) {
////            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
////        } else {
////            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
////        }
////
////        super.onCreate(savedInstanceState)
////        setContentView(R.layout.activity_main)
////
////        // Get the userName passed from LoginActivity
////        loggedInUser = intent.getStringExtra("fullName") ?: "User"
////
////        // Setup toolbar
////        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
////        setSupportActionBar(toolbar)
////
////        drawerLayout = findViewById(R.id.drawer_layout)
////        navView = findViewById(R.id.nav_view)
////
////        val toggle = ActionBarDrawerToggle(
////            this, drawerLayout, toolbar,
////            R.string.navigation_drawer_open,
////            R.string.navigation_drawer_close
////        )
////        drawerLayout.addDrawerListener(toggle)
////        toggle.syncState()
////
////        navView.setNavigationItemSelectedListener(this)
////
////        // --- Add footer programmatically ---
////        val footerView = layoutInflater.inflate(R.layout.nav_footer_main, navView, false)
////        navView.addView(footerView, navView.childCount)
////
////        val darkModeSwitch: Switch = footerView.findViewById(R.id.switch_dark_mode)
////        val darkModeLabel: TextView = footerView.findViewById(R.id.tv_dark_mode)
////        val profileName: TextView = footerView.findViewById(R.id.tv_user_name)
////        val profileType: TextView = footerView.findViewById(R.id.tv_user_type)
////        val profileImage: ImageView = footerView.findViewById(R.id.img_profile)
////
////        profileName.text = loggedInUser
////        profileType.text = "Premium Member"
////        profileImage.setImageResource(R.drawable.ic_profile)
////
////        // Sync dark mode state
////        darkModeSwitch.isChecked = isDarkMode
////        darkModeLabel.text = if (isDarkMode) "Dark Mode" else "Light Mode"
////
////        darkModeSwitch.setOnCheckedChangeListener { _, checked ->
////            if (checked) {
////                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
////                darkModeLabel.text = "Dark Mode"
////                sharedPrefs.edit().putBoolean("dark_mode", true).apply()
////            } else {
////                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
////                darkModeLabel.text = "Light Mode"
////                sharedPrefs.edit().putBoolean("dark_mode", false).apply()
////            }
////        }
////
////        // 👉 Default fragment = HomeFragment with username
////        if (savedInstanceState == null) {
////            openHomeFragment()
////            navView.setCheckedItem(R.id.nav_home)
////        }
////    }
////
////    override fun onNavigationItemSelected(item: MenuItem): Boolean {
////        when (item.itemId) {
////            R.id.nav_home -> {
////                openHomeFragment()
////            }
////            R.id.nav_analysis -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, AnalysisFragment())
////                    .commit()
////            }
////            R.id.nav_settings -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, SettingsFragment())
////                    .commit()
////            }
////            R.id.nav_profile -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, Profile())
////                    .commit()
////            }
////        }
////        drawerLayout.closeDrawer(GravityCompat.START)
////        return true
////    }
////
////    private fun openHomeFragment() {
////        val homeFragment = HomeFragment()
////        val bundle = Bundle()
////        bundle.putString("userName", loggedInUser)
////        homeFragment.arguments = bundle
////
////        supportFragmentManager.beginTransaction()
////            .replace(R.id.fragment_container, homeFragment)
////            .commit()
////    }
////
////    override fun onBackPressed() {
////        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
////            drawerLayout.closeDrawer(GravityCompat.START)
////        } else {
////            super.onBackPressed()
////        }
////    }
////}
//
////package com.example.fittrack
////
////import android.content.SharedPreferences
////import android.icu.util.Calendar
////import android.os.Bundle
////import android.view.MenuItem
////import android.widget.ImageView
////import android.widget.Switch
////import android.widget.TextView
////import androidx.appcompat.app.ActionBarDrawerToggle
////import androidx.appcompat.app.AppCompatActivity
////import androidx.appcompat.app.AppCompatDelegate
////import androidx.core.view.GravityCompat
////import androidx.drawerlayout.widget.DrawerLayout
////import com.example.fittrack.ui.analysis.AnalysisFragment
////import com.example.fittrack.ui.home.HomeFragment
////import com.example.fittrack.ui.settings.SettingsFragment
////import com.google.android.material.navigation.NavigationView
////
////class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
////
////    private lateinit var drawerLayout: DrawerLayout
////    private lateinit var navView: NavigationView
////    private lateinit var sharedPrefs: SharedPreferences
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        // Load saved dark mode setting BEFORE super.onCreate
////        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
////        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
////        if (isDarkMode) {
////            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
////        } else {
////            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
////        }
////
////        super.onCreate(savedInstanceState)
////        setContentView(R.layout.activity_main)
////
////        // Get the userName passed from LoginActivity
////        val userName = intent.getStringExtra("userName") ?: "User"
////
////        // Setup toolbar
////        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
////        setSupportActionBar(toolbar)
////
////        drawerLayout = findViewById(R.id.drawer_layout)
////        navView = findViewById(R.id.nav_view)
////
////        val toggle = ActionBarDrawerToggle(
////            this, drawerLayout, toolbar,
////            R.string.navigation_drawer_open,
////            R.string.navigation_drawer_close
////        )
////        drawerLayout.addDrawerListener(toggle)
////        toggle.syncState()
////
////        navView.setNavigationItemSelectedListener(this)
////
////        // --- Update Navigation Header with logged-in user ---
//////        val homeView = navView.getHeaderView(0)
//////        val tvUserName = homeView.findViewById<TextView>(R.id.tvGreeting)
//////        tvUserName?.text = "Welcome, $userName"
////
////
////        // --- Add footer programmatically ---
////        val footerView = layoutInflater.inflate(R.layout.nav_footer_main, navView, false)
////        navView.addView(footerView, navView.childCount)
////
////        val darkModeSwitch: Switch = footerView.findViewById(R.id.switch_dark_mode)
////        val darkModeLabel: TextView = footerView.findViewById(R.id.tv_dark_mode)
////        val profileName: TextView = footerView.findViewById(R.id.tv_user_name)
////        val profileType: TextView = footerView.findViewById(R.id.tv_user_type)
////        val profileImage: ImageView = footerView.findViewById(R.id.img_profile)
////
////        profileName.text = userName
////        profileType.text = "Premium Member"
////        profileImage.setImageResource(R.drawable.ic_profile)
////
////        // Sync dark mode state
////        darkModeSwitch.isChecked = isDarkMode
////        darkModeLabel.text = if (isDarkMode) "Dark Mode" else "Light Mode"
////
////        darkModeSwitch.setOnCheckedChangeListener { _, checked ->
////            if (checked) {
////                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
////                darkModeLabel.text = "Dark Mode"
////                sharedPrefs.edit().putBoolean("dark_mode", true).apply()
////            } else {
////                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
////                darkModeLabel.text = "Light Mode"
////                sharedPrefs.edit().putBoolean("dark_mode", false).apply()
////            }
////        }
////
////        // 👉 Default fragment when app opens after login = HomeFragment
////        if (savedInstanceState == null) {
////            supportFragmentManager.beginTransaction()
////                .replace(R.id.fragment_container, HomeFragment())
////                .commit()
////            navView.setCheckedItem(R.id.nav_home)
////        }
////    }
////
////    override fun onNavigationItemSelected(item: MenuItem): Boolean {
////        when (item.itemId) {
////            R.id.nav_home -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, HomeFragment())
////                    .commit()
////            }
////            R.id.nav_analysis -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, AnalysisFragment())
////                    .commit()
////            }
////            R.id.nav_settings -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, SettingsFragment())
////                    .commit()
////            }
////            R.id.nav_profile -> {
////                supportFragmentManager.beginTransaction()
////                    .replace(R.id.fragment_container, Profile())
////                    .commit()
////            }
////        }
////        drawerLayout.closeDrawer(GravityCompat.START)
////        return true
////    }
////
////    override fun onBackPressed() {
////        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
////            drawerLayout.closeDrawer(GravityCompat.START)
////        } else {
////            super.onBackPressed()
////        }
////    }
////}
