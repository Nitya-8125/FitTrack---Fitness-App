package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class LoadingActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var footerText: TextView
    private lateinit var stepIcons: List<ImageView>
    private lateinit var centerIcon: ImageView

    private var progress = 0
    private var stepIndex = 0

    private val loadingSteps = listOf(
        "Initializing FitTrack",
        "Loading your profile",
        "Preparing dashboard"
    )

    private val stepIconsRes = listOf(
        R.drawable.ic_dumbbell,   // Step 1
        R.drawable.ic_profile,    // Step 2
        R.drawable.ic_dashboard   // Step 3
    )

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        progressBar = findViewById(R.id.progressBar)
        progressLabel = findViewById(R.id.progressLabel)
        footerText = findViewById(R.id.footerText)
        centerIcon = findViewById(R.id.centerIcon)

        stepIcons = listOf(
            findViewById(R.id.stepIcon1),
            findViewById(R.id.stepIcon2),
            findViewById(R.id.stepIcon3)
        )

        startLoading()
    }

    private fun startLoading() {
        val progressRunnable = object : Runnable {
            override fun run() {
                if (progress <= 100) {
                    progressBar.progress = progress
                    progressLabel.text = "${loadingSteps[stepIndex]} ($progress%)"
                    progress += 2

                    // Every ~33% move to next step
                    if (progress % 34 == 0 && stepIndex < loadingSteps.size - 1) {
                        // Mark previous step as done
                        stepIcons[stepIndex].setBackgroundResource(R.drawable.bg_step_done)

                        // Move to next step
                        stepIndex++
                        stepIcons[stepIndex].setBackgroundResource(R.drawable.bg_step_active)

                        // Update center icon with animation
                        updateCenterIcon(stepIconsRes[stepIndex])
                    }

                    handler.postDelayed(this, 80)
                } else {
                    // Mark last step done
                    stepIcons[stepIndex].setBackgroundResource(R.drawable.bg_step_done)
                    footerText.text = "Ready!"
                    updateCenterIcon(R.drawable.ic_dashboard)

                    // ✅ Auto navigation after short delay
                    handler.postDelayed({
                        val intent = Intent(this@LoadingActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }, 1000) // 1s wait for smooth finish
                }
            }
        }

        // First step active
        stepIcons[0].setBackgroundResource(R.drawable.bg_step_active)
        updateCenterIcon(stepIconsRes[0])

        handler.post(progressRunnable)
    }

    private fun updateCenterIcon(resId: Int) {
        centerIcon.setImageResource(resId)

        // Scale (pulse) animation
        val pulse = ScaleAnimation(
            0.8f, 1.2f,   // from X, to X
            0.8f, 1.2f,   // from Y, to Y
            Animation.RELATIVE_TO_SELF, 0.5f, // pivot X center
            Animation.RELATIVE_TO_SELF, 0.5f  // pivot Y center
        )
        pulse.duration = 400
        pulse.repeatCount = 1
        pulse.repeatMode = Animation.REVERSE
        centerIcon.startAnimation(pulse)
    }
}
