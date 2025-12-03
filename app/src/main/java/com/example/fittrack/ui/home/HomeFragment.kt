package com.example.fittrack.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fittrack.R
import com.example.fittrack.SessionManager
import com.example.fittrack.ui.profile.ProfileFragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.fittrack.FirestoreDatabaseHelper
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment(), SensorEventListener {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var session: SessionManager

    private var tvGreeting: TextView? = null
    private var tvDate: TextView? = null
    private var tvCurrentSteps: TextView? = null
    private var tvSteps: TextView? = null
    private var tvCalories: TextView? = null
    private var tvWeight: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressCalories: ProgressBar? = null
    private var chartDaily: LineChart? = null

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var totalSteps: Float = 0f
    private var userWeight: Double = 70.0
    private var email: String = ""
    private var prefs: android.content.SharedPreferences? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeUpdater: Runnable? = null
    private var goalCompleted = false

    private var stepsGoal = 10000
    private var caloriesGoal = 2000

    // auth state listener and last known email to detect changes
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var lastKnownEmail: String = ""

    // NEW: PDF button
    private var btnDownloadPdf: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        context?.let { ctx ->
            session = SessionManager(ctx)
            prefs = ctx.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
            sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }

        // Bind UI
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvDate = view.findViewById(R.id.tvDate)
        tvCurrentSteps = view.findViewById(R.id.tvCurrentSteps)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvWeight = view.findViewById(R.id.tvWeight)
        progressBar = view.findViewById(R.id.progressBar)
        progressCalories = view.findViewById(R.id.progressCalories)
        chartDaily = view.findViewById(R.id.chartWeekly)
        btnDownloadPdf = view.findViewById(R.id.btnDownloadPdf)

        btnDownloadPdf?.setOnClickListener {
            generatePdfReport()
        }

        // Email from Firebase or Session
        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        lastKnownEmail = email

        // Auth state listener (for switching user)
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val newEmail = firebaseAuth.currentUser?.email ?: session.getUserEmail().orEmpty()
            if (newEmail != lastKnownEmail) {
                Log.d(TAG, "Auth changed: old=$lastKnownEmail new=$newEmail")
                lastKnownEmail = newEmail
                email = newEmail

                // Clear step prefs when account changes
                try { prefs?.edit()?.clear()?.apply() } catch (_: Exception) {}

                // Reset UI
                totalSteps = 0f
                goalCompleted = false
                try {
                    tvCurrentSteps?.text = "0"
                    tvCalories?.text = "0 / $caloriesGoal"
                    progressBar?.progress = 0
                    progressCalories?.progress = 0
                } catch (_: Exception) {}

                if (isAdded) {
                    if (email.isNotEmpty()) {
                        loadUserData()
                        loadDailyChart()
                        loadDailyStats()
                    } else {
                        drawEmptyChart()
                        tvGreeting?.text = "Welcome!"
                        tvSteps?.text = "Goal: $stepsGoal"
                        tvWeight?.text = "Weight Today: ${"%.1f".format(userWeight)} kg"
                        tvCalories?.text = "0 / $caloriesGoal"
                        tvCurrentSteps?.text = "0"
                        progressBar?.progress = 0
                        progressCalories?.progress = 0
                    }
                }
            }
        }
        authStateListener?.let { auth.addAuthStateListener(it) }

        if (email.isEmpty()) {
            if (isAdded) {
                context?.let { ctx -> Toast.makeText(ctx, "User not logged in!", Toast.LENGTH_SHORT).show() }
            }
            drawEmptyChart()
        } else {
            loadUserData()
        }

        // Initialize sensor safely
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        startDateTimeUpdater()
        return view
    }

    // ---------------- LOAD USER DATA ----------------
    private fun loadUserData() {
        if (email.isEmpty() || !isAdded) return

        val helper = FirestoreDatabaseHelper()
        helper.getUserDetails(email) { data ->
            if (!isAdded) return@getUserDetails

            try {
                if (data != null) {
                    val firstName = (data["firstName"] as? String) ?: (data["name"] as? String) ?: "User"
                    val targetWeight = (data["target_weight"] as? Number)?.toDouble() ?: 70.0
                    userWeight = (data["weightToday"] as? Number)?.toDouble()
                        ?: (data["weight"] as? Number)?.toDouble() ?: 70.0
                    stepsGoal = ((data["daily_steps_goal"] as? Number)?.toInt() ?: 10000)
                    caloriesGoal = ((data["daily_calories_goal"] as? Number)?.toInt() ?: 2000)

                    tvGreeting?.text = "Welcome, $firstName!"
                    tvSteps?.text = "Goal: $stepsGoal"
                    tvWeight?.text =
                        "Weight Today: ${"%.1f".format(userWeight)} kg (Target: ${"%.1f".format(targetWeight)} kg)"

                    progressBar?.max = stepsGoal
                    progressCalories?.max = caloriesGoal

                    loadDailyStats()
                    loadDailyChart()
                } else {
                    tvGreeting?.text = "Welcome!"
                    progressBar?.max = stepsGoal
                    progressCalories?.max = caloriesGoal
                    tvSteps?.text = "Goal: $stepsGoal"
                    tvWeight?.text = "Weight Today: ${"%.1f".format(userWeight)} kg"
                    tvCalories?.text = "0 / $caloriesGoal"
                    tvCurrentSteps?.text = "0"
                    drawEmptyChart()
                }
            } catch (ex: Exception) {
                if (isAdded) context?.let { ctx ->
                    Toast.makeText(ctx, "Error parsing user data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------------- LOAD DAILY STATS ----------------
    private fun loadDailyStats() {
        if (email.isEmpty() || !isAdded) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val helper = FirestoreDatabaseHelper()

        helper.getDocIdByEmail(email) { docId ->
            if (!isAdded) return@getDocIdByEmail

            val idToUse = docId ?: email
            firestore.collection("daily_stats").document("${idToUse}_$today")
                .get()
                .addOnSuccessListener { doc ->
                    if (!isAdded) return@addOnSuccessListener
                    try {
                        val stepsToday = (doc.getLong("steps") ?: 0L).toInt()
                        val caloriesToday = (doc.getLong("calories") ?: 0L).toInt()

                        tvCalories?.text = "$caloriesToday / $caloriesGoal"
                        tvCurrentSteps?.text = stepsToday.toString()
                        progressBar?.progress = stepsToday
                        progressCalories?.progress = caloriesToday
                    } catch (_: Exception) {}
                }
                .addOnFailureListener {
                    if (!isAdded) return@addOnFailureListener
                    try {
                        tvCalories?.text = "0 / $caloriesGoal"
                        tvCurrentSteps?.text = "0"
                        progressBar?.progress = 0
                        progressCalories?.progress = 0
                    } catch (_: Exception) {}
                }
        }
    }

    // ---------------- SENSOR EVENTS ----------------
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isAdded || event == null || goalCompleted) return

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            var initialSteps = prefs?.getFloat("initial_steps", -1f) ?: -1f
            val savedDate = prefs?.getString("last_date", today) ?: today

            if (savedDate != today || initialSteps < 0) {
                initialSteps = event.values[0]
                prefs?.edit()?.putFloat("initial_steps", initialSteps)
                    ?.putString("last_date", today)?.apply()
            }

            totalSteps = event.values[0] - initialSteps
            val stepsInt = totalSteps.toInt()
            if (userWeight <= 0) userWeight = 70.0
            val calories = calculateCalories(stepsInt, userWeight)

            if (isAdded) {
                tvCurrentSteps?.text = stepsInt.toString()
                tvCalories?.text = "$calories / $caloriesGoal"
                progressBar?.progress = stepsInt
                progressCalories?.progress = calories
                updateChart()
            }

            saveStatsToFirestore(today, stepsInt, calories, userWeight)

            if (stepsInt >= (progressBar?.max ?: stepsGoal)) {
                goalCompleted = true
                sensorManager?.unregisterListener(this)
                showGoalCompletedDialog()
            }
        }
    }

    private fun saveStatsToFirestore(date: String, steps: Int, calories: Int, weight: Double) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val helper = FirestoreDatabaseHelper()

        try {
            helper.saveDailyStats(email, steps, calories, weight)
        } catch (e: Exception) {
            try {
                firestore.collection("daily_stats").document("${email}_$date").set(
                    mapOf(
                        "user_email" to email,
                        "date" to date,
                        "steps" to steps,
                        "calories" to calories,
                        "weight" to weight,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { }
        }

        try {
            helper.saveHourlyStats(email, hour, steps, calories, weight)
        } catch (e: Exception) {
            try {
                firestore.collection("hourly_stats").document("${email}_${date}_$hour").set(
                    mapOf(
                        "email" to email,
                        "date" to date,
                        "hour" to hour,
                        "steps" to steps,
                        "calories" to calories,
                        "weight" to weight,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { }
        }
    }

    private fun calculateCalories(steps: Int, weightKg: Double): Int {
        val stepLengthMeters = 0.762
        val distanceKm = steps * stepLengthMeters / 1000.0
        val caloriesBurned = distanceKm * weightKg * 1.036
        return caloriesBurned.toInt()
    }

    // ---------------- CHART HANDLING ----------------
    private fun loadDailyChart() {
        if (!isAdded) return

        val currentEmail = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        if (currentEmail.isEmpty()) {
            drawEmptyChart()
            return
        }
        email = currentEmail

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val helper = FirestoreDatabaseHelper()

        helper.getDocIdByEmail(email) { docId ->
            if (!isAdded) return@getDocIdByEmail

            val idToUse = docId ?: email
            val tryPerDocFirst = !docId.isNullOrEmpty() && docId != email

            Log.d(TAG, "loadDailyChart: email=$email docId=$docId tryPerDocFirst=$tryPerDocFirst")

            if (tryPerDocFirst) {
                val hourlyMap = mutableMapOf<Int, Int>()
                for (h in 0..23) hourlyMap[h] = 0

                val tasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                for (h in 0..23) {
                    val docRef = firestore.collection("hourly_stats").document("${idToUse}_${today}_$h")
                    tasks.add(docRef.get())
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                    .addOnSuccessListener { results ->
                        if (!isAdded) return@addOnSuccessListener

                        var anyFound = false
                        for (snap in results) {
                            if (snap != null && snap.exists()) {
                                val hour = (snap.getLong("hour") ?: 0L).toInt()
                                val steps = (snap.getLong("steps") ?: 0L).toInt()
                                hourlyMap[hour] = steps
                                anyFound = true
                            }
                        }

                        if (anyFound) {
                            val entriesList = mutableListOf<Entry>()
                            for (h in 0..23)
                                entriesList.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
                            drawChart(entriesList)
                        } else {
                            queryHourlyByEmail(today, idToUse)
                        }
                    }
                    .addOnFailureListener {
                        queryHourlyByEmail(today, idToUse)
                    }
            } else {
                queryHourlyByEmail(today, idToUse)
            }
        }
    }

    private fun queryHourlyByEmail(today: String, idToUse: String) {
        firestore.collection("hourly_stats")
            .whereEqualTo("email", email)
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                Log.d(TAG, "queryHourlyByEmail: snapshot size=${snapshot.size()}")
                if (!snapshot.isEmpty) {
                    buildChartFromHourlySnapshot(snapshot.documents.map { it.data ?: emptyMap<String, Any>() })
                } else {
                    val hourlyMap = mutableMapOf<Int, Int>()
                    for (h in 0..23) hourlyMap[h] = 0
                    val tasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                    for (h in 0..23) {
                        val docRef = firestore.collection("hourly_stats").document("${idToUse}_${today}_$h")
                        tasks.add(docRef.get())
                    }
                    Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                        .addOnSuccessListener { results ->
                            if (!isAdded) return@addOnSuccessListener
                            var anyFound = false
                            for (snap in results) {
                                if (snap != null && snap.exists()) {
                                    val hour = (snap.getLong("hour") ?: 0L).toInt()
                                    val steps = (snap.getLong("steps") ?: 0L).toInt()
                                    hourlyMap[hour] = steps
                                    anyFound = true
                                }
                            }
                            if (anyFound) {
                                val entriesList = mutableListOf<Entry>()
                                for (h in 0..23)
                                    entriesList.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
                                drawChart(entriesList)
                            } else {
                                drawEmptyChart()
                            }
                        }
                        .addOnFailureListener {
                            drawEmptyChart()
                        }
                }
            }
            .addOnFailureListener {
                val hourlyMap = mutableMapOf<Int, Int>()
                for (h in 0..23) hourlyMap[h] = 0
                val tasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                for (h in 0..23) {
                    val docRef = firestore.collection("hourly_stats").document("${idToUse}_${today}_$h")
                    tasks.add(docRef.get())
                }
                Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                    .addOnSuccessListener { results ->
                        if (!isAdded) return@addOnSuccessListener
                        var anyFound = false
                        for (snap in results) {
                            if (snap != null && snap.exists()) {
                                val hour = (snap.getLong("hour") ?: 0L).toInt()
                                val steps = (snap.getLong("steps") ?: 0L).toInt()
                                hourlyMap[hour] = steps
                                anyFound = true
                            }
                        }
                        if (anyFound) {
                            val entriesList = mutableListOf<Entry>()
                            for (h in 0..23)
                                entriesList.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
                            drawChart(entriesList)
                        } else {
                            drawEmptyChart()
                        }
                    }
                    .addOnFailureListener {
                        drawEmptyChart()
                    }
            }
    }

    private fun buildChartFromHourlySnapshot(listOfMaps: List<Map<String, Any>>) {
        val hourlyMap = mutableMapOf<Int, Int>()
        for (h in 0..23) hourlyMap[h] = 0
        for (m in listOfMaps) {
            val hour = (m["hour"] as? Number)?.toInt() ?: 0
            val steps = (m["steps"] as? Number)?.toInt() ?: 0
            if (hour in 0..23) hourlyMap[hour] = steps
        }
        val entries = mutableListOf<Entry>()
        for (h in 0..23) entries.add(Entry(h.toFloat(), (hourlyMap[h] ?: 0).toFloat()))
        drawChart(entries)
    }

    private fun updateChart() {
        loadDailyChart()
    }

    private fun drawChart(entries: List<Entry>) {
        if (!isAdded) return
        try {
            val dataSet = LineDataSet(entries, "Steps Today")
            val ctx = context ?: return

            try {
                dataSet.color = resources.getColor(R.color.blue_700, ctx.theme)
                dataSet.valueTextColor = resources.getColor(R.color.black, ctx.theme)
            } catch (_: Exception) { }

            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.setDrawFilled(true)
            dataSet.fillAlpha = 50

            chartDaily?.data = LineData(dataSet)
            chartDaily?.description?.isEnabled = false
            chartDaily?.axisRight?.isEnabled = false
            chartDaily?.xAxis?.position = XAxis.XAxisPosition.BOTTOM
            chartDaily?.xAxis?.granularity = 1f
            chartDaily?.xAxis?.labelCount = 24
            chartDaily?.invalidate()
        } catch (_: Exception) { }
    }

    private fun drawEmptyChart() {
        val entries = mutableListOf<Entry>()
        for (h in 0..23) entries.add(Entry(h.toFloat(), 0f))
        drawChart(entries)
    }

    // ---------------- PDF GENERATION ----------------
    private fun generatePdfReport() {
        val ctx = context ?: return

        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint()

            var y = 40f

            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("Daily Fitness Report", 40f, y, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false
            y += 25f
            val todayStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generated: $todayStr", 40f, y, paint)

            y += 30f
            val stepsText = "Steps Today: ${tvCurrentSteps?.text ?: "0"}"
            val caloriesText = "Calories: ${tvCalories?.text ?: "0"}"
            val weightText = "Weight: ${tvWeight?.text ?: "-"}"

            canvas.drawText(stepsText, 40f, y, paint)
            y += 20f
            canvas.drawText(caloriesText, 40f, y, paint)
            y += 20f
            canvas.drawText(weightText, 40f, y, paint)

            y += 30f

            chartDaily?.let { chart ->
                try {
                    chart.measure(
                        View.MeasureSpec.makeMeasureSpec(chart.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(chart.height, View.MeasureSpec.EXACTLY)
                    )
                    chart.layout(chart.left, chart.top, chart.right, chart.bottom)

                    val chartBitmap: Bitmap = chart.chartBitmap
                    val availableWidth = pageWidth - 80
                    val scale = availableWidth.toFloat() / chartBitmap.width.toFloat()
                    val bmpHeight = (chartBitmap.height * scale).toInt()

                    val scaledBmp = Bitmap.createScaledBitmap(
                        chartBitmap,
                        availableWidth,
                        bmpHeight,
                        true
                    )

                    if (y + bmpHeight > pageHeight - 40) {
                        y = pageHeight - 40f - bmpHeight
                    }

                    canvas.drawBitmap(scaledBmp, 40f, y, null)
                    scaledBmp.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error drawing chart in PDF", e)
                    canvas.drawText("Chart could not be rendered in PDF.", 40f, y, paint)
                }
            } ?: run {
                canvas.drawText("No chart data available.", 40f, y, paint)
            }

            pdfDocument.finishPage(page)

            val fileName =
                "FitnessReport_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"

            savePdfToDownloads(pdfDocument, fileName)

        } catch (e: Exception) {
            Log.e(TAG, "generatePdfReport: error", e)
            Toast.makeText(ctx, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePdfToDownloads(pdfDocument: PdfDocument, fileName: String) {
        val ctx = requireContext()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri: Uri? = resolver.insert(collection, contentValues)

                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        pdfDocument.writeTo(out)
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)

                    Toast.makeText(ctx, "PDF saved to Downloads as $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "Failed to create PDF file", Toast.LENGTH_LONG).show()
                }
            } else {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { out ->
                    pdfDocument.writeTo(out)
                }

                Toast.makeText(ctx, "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "savePdfToDownloads: error", e)
            Toast.makeText(ctx, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }

    // ---------------- DATE & GOAL HANDLING ----------------
    private fun startDateTimeUpdater() {
        if (timeUpdater != null) return
        timeUpdater = object : Runnable {
            override fun run() {
                if (!isAdded) return
                updateDateTime()
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(timeUpdater!!)
    }

    private fun updateDateTime() {
        if (!isAdded) return
        val currentDate = Calendar.getInstance().time
        val sdf = SimpleDateFormat("EEEE, MMM dd • hh:mm a", Locale.getDefault())
        tvDate?.text = sdf.format(currentDate)
    }

    private fun showGoalCompletedDialog() {
        if (!isAdded) return

        val ctx = context ?: return
        val builder = android.app.AlertDialog.Builder(ctx)
        builder.setTitle("Congratulations!")
        builder.setMessage("You completed your steps goal 🎉")
        builder.setCancelable(false)
        builder.setPositiveButton("Set New Target") { dialog, _ ->
            val profileFragment = ProfileFragment()
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.fragment_container, profileFragment)
                ?.addToBackStack(null)
                ?.commit()
            dialog.dismiss()
        }
        builder.show()
    }

    // ---------------- LIFECYCLE ----------------
    override fun onResume() {
        super.onResume()
        email = auth.currentUser?.email ?: session.getUserEmail().orEmpty()
        lastKnownEmail = email
        if (email.isNotEmpty()) {
            loadUserData()
        }
        if (!goalCompleted) {
            stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        timeUpdater?.let { handler.removeCallbacks(it) }
        timeUpdater = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { authStateListener?.let { auth.removeAuthStateListener(it) } } catch (_: Exception) {}
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        timeUpdater?.let { handler.removeCallbacks(it) }
        timeUpdater = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
