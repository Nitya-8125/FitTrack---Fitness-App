package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoadingActivity : AppCompatActivity() {

    private val TAG = "LoadingActivity"

    private var progressBar: ProgressBar? = null
    private var progressLabel: TextView? = null
    private var footerText: TextView? = null
    private var centerIcon: ImageView? = null
    private var stepIcons: MutableList<ImageView> = mutableListOf()

    private var progress = 0
    private var stepIndex = 0

    private val loadingSteps = listOf(
        "Initializing FitTrack",
        "Loading your profile",
        "Preparing dashboard"
    )

    // Resources are referenced here but if they don't exist app won't compile.
    // At runtime we still guard and catch any errors.
    private val stepIconsRes = listOf(
        R.drawable.ic_dumbbell,
        R.drawable.ic_profile,
        R.drawable.ic_dashboard
    )

    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_loading)
        } catch (ex: Exception) {
            Log.e(TAG, "setContentView failed: ${ex.message}", ex)
            // If layout inflate fails, navigate forward to avoid crash loop
            safeNavigateToLogin()
            return
        }

        try {
            progressBar = findViewById(R.id.progressBar)
            progressLabel = findViewById(R.id.progressLabel)
            footerText = findViewById(R.id.footerText)
            centerIcon = findViewById(R.id.centerIcon)
        } catch (ex: Exception) {
            Log.w(TAG, "findViewById initial fetch failed: ${ex.message}", ex)
        }

        // Safe attempt to collect step icons
        try {
            val ids = listOf(R.id.stepIcon1, R.id.stepIcon2, R.id.stepIcon3)
            for (id in ids) {
                try {
                    val v = findViewById<ImageView?>(id)
                    if (v != null) stepIcons.add(v)
                } catch (inner: Exception) {
                    Log.w(TAG, "Missing or invalid step icon for id=$id: ${inner.message}")
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Error while locating step icons: ${ex.message}", ex)
        }

        // log missing views for debugging
        if (progressBar == null) Log.w(TAG, "progressBar view is missing.")
        if (progressLabel == null) Log.w(TAG, "progressLabel view is missing.")
        if (centerIcon == null) Log.w(TAG, "centerIcon view is missing.")
        if (stepIcons.isEmpty()) Log.w(TAG, "No step icons found (stepIcon1..3).")

        // Start loading (guarded)
        startLoadingSafely()
    }

    private fun startLoadingSafely() {
        if (started) return
        started = true

        progressRunnable = object : Runnable {
            override fun run() {
                try {
                    // If progressBar or progressLabel are null, still advance but don't touch UI
                    if (progressBar == null || progressLabel == null) {
                        progress += 4
                        if (progress > 100) {
                            safeNavigateToLogin()
                            return
                        }
                        handler.postDelayed(this, 80)
                        return
                    }

                    if (progress <= 100) {
                        // Update UI guarded
                        try {
                            progressBar?.progress = progress
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set progressBar.progress: ${e.message}")
                        }
                        val stepText = if (stepIndex in loadingSteps.indices) loadingSteps[stepIndex] else "Loading"
                        try {
                            progressLabel?.text = "$stepText ($progress%)"
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set progressLabel.text: ${e.message}")
                        }

                        progress += 4

                        // Move to next step on thresholds (defensive math)
                        try {
                            if (progress % 34 == 0 && stepIndex < loadingSteps.size - 1) {
                                if (stepIndex < stepIcons.size) {
                                    try {
                                        stepIcons[stepIndex].setBackgroundResource(R.drawable.bg_step_done)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to mark step done: ${e.message}")
                                    }
                                }
                                stepIndex = (stepIndex + 1).coerceAtMost(loadingSteps.size - 1)
                                if (stepIndex < stepIcons.size) {
                                    try {
                                        stepIcons[stepIndex].setBackgroundResource(R.drawable.bg_step_active)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to mark step active: ${e.message}")
                                    }
                                }
                                val resIndex = stepIndex.coerceAtMost(stepIconsRes.size - 1)
                                try {
                                    updateCenterIcon(stepIconsRes[resIndex])
                                } catch (e: Exception) {
                                    Log.w(TAG, "updateCenterIcon error: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Step update error: ${e.message}")
                        }

                        handler.postDelayed(this, 80)
                    } else {
                        // Finalize UI
                        try {
                            if (stepIndex < stepIcons.size) stepIcons[stepIndex].setBackgroundResource(R.drawable.bg_step_done)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to finalize step icon: ${e.message}")
                        }
                        try {
                            footerText?.text = "Ready!"
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set footerText: ${e.message}")
                        }
                        try {
                            updateCenterIcon(R.drawable.ic_dashboard)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update center icon final: ${e.message}")
                        }

                        handler.postDelayed({
                            safeNavigateToLogin()
                        }, 600)
                    }
                } catch (ex: Exception) {
                    // Log full stacktrace for debugging
                    Log.e(TAG, "Unhandled exception in loading runnable: ${ex.message}", ex)
                    safeNavigateToLogin()
                }
            }
        }

        // Initialize UI safely
        try {
            if (stepIcons.isNotEmpty()) {
                try { stepIcons[0].setBackgroundResource(R.drawable.bg_step_active) } catch (e: Exception) { Log.w(TAG, "init stepIcons[0] failed: ${e.message}") }
                val res = try { stepIconsRes[0] } catch (e: Exception) { R.drawable.ic_dashboard }
                try { updateCenterIcon(res) } catch (e: Exception) { Log.w(TAG, "init center icon failed: ${e.message}") }
            } else {
                try { updateCenterIcon(R.drawable.ic_dashboard) } catch (e: Exception) { Log.w(TAG, "init center icon fallback failed: ${e.message}") }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "UI init failed: ${ex.message}", ex)
        }

        progress = 0
        stepIndex = 0
        progressRunnable?.let { handler.post(it) }
    }

    private fun updateCenterIcon(resId: Int) {
        try {
            centerIcon?.setImageResource(resId)
            centerIcon?.let { icon ->
                val pulse = ScaleAnimation(
                    0.85f, 1.15f,
                    0.85f, 1.15f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                )
                pulse.duration = 350
                pulse.repeatCount = 1
                pulse.repeatMode = Animation.REVERSE
                icon.startAnimation(pulse)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "updateCenterIcon failed: ${ex.message}", ex)
        }
    }

    private fun safeNavigateToLogin() {
        // Run once and stop runnable
        try {
            progressRunnable?.let { handler.removeCallbacks(it) }
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to remove callbacks: ${ex.message}")
        }

        try {
            runOnUiThread {
                try {
                    val intent = Intent(this@LoadingActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } catch (ex: Exception) {
                    Log.e(TAG, "Navigation to LoginActivity failed: ${ex.message}", ex)
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "safeNavigateToLogin runOnUiThread failed: ${ex.message}", ex)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            progressRunnable?.let { handler.removeCallbacks(it) }
        } catch (ex: Exception) {
            Log.w(TAG, "onDestroy removeCallbacks failed: ${ex.message}")
        }
    }
}
