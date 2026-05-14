package com.project.gramaurja

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var welcomeText: TextView
    private lateinit var btnEditProfile: TextView
    private lateinit var statusCard: CardView
    private lateinit var statusText: TextView
    private lateinit var lastSeenText: TextView
    private lateinit var tvReportedBy: TextView
    private lateinit var zoneSpinner: Spinner

    private lateinit var btnPowerOn: Button
    private lateinit var btnLowVoltage: Button
    private lateinit var btnPowerOff: Button
    private lateinit var btnPumpTimer: Button

    // Firebase & State
    private lateinit var database: DatabaseReference
    private var currentZone = ""
    private var lastStatus = ""
    private var statusListener: ValueEventListener? = null
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize UI Elements
        welcomeText = findViewById(R.id.welcomeText)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        lastSeenText = findViewById(R.id.lastSeenText)
        tvReportedBy = findViewById(R.id.tvReportedBy)
        zoneSpinner = findViewById(R.id.zoneSpinner)
        btnPowerOn = findViewById(R.id.btnPowerOn)
        btnLowVoltage = findViewById(R.id.btnLowVoltage)
        btnPowerOff = findViewById(R.id.btnPowerOff)
        btnPumpTimer = findViewById(R.id.btnPumpTimer)

        // 2. Setup Preferences & Personalized Greeting
        sharedPref = getSharedPreferences("GramaUrjaPrefs", Context.MODE_PRIVATE)
        val name = sharedPref.getString("farmer_name", "Farmer")
        val savedZone = sharedPref.getString("farmer_zone", "Zone_A")
        welcomeText.text = "Namaste, $name"

        // 3. CHANGE Profile Logic (Fixed with commit)
        btnEditProfile.setOnClickListener {
            val editor = sharedPref.edit()
            editor.clear()
            editor.commit() // Forces data deletion immediately

            val intent = Intent(this, SetupActivity::class.java)
            // Clears activity stack so user can't press 'back' to return to Main
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // 4. Firebase Reference (Singapore Region)
        database = FirebaseDatabase.getInstance("https://grama-urja-project-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("zones")

        // 5. Spinner (Zone Selection) Setup
        val villages = listOf("Select Village", "Zone_A", "Zone_B", "Zone_C")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, villages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        zoneSpinner.adapter = adapter

        zoneSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) switchZone(villages[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Auto-select the zone chosen on the setup page
        if (!savedZone.isNullOrEmpty()) {
            val pos = adapter.getPosition(savedZone)
            if (pos > 0) zoneSpinner.setSelection(pos)
            switchZone(savedZone)
        }

        // 6. Report Buttons Click Listeners
        btnPowerOn.setOnClickListener { updateStatus("ON") }
        btnLowVoltage.setOnClickListener { updateStatus("LOW") }
        btnPowerOff.setOnClickListener { updateStatus("OFF") }
        btnPumpTimer.setOnClickListener { showPumpTimerDialog() }
    }

    private fun switchZone(newZone: String) {
        if (currentZone.isNotEmpty()) {
            statusListener?.let { database.child(currentZone).removeEventListener(it) }
        }
        currentZone = newZone
        lastStatus = ""
        sharedPref.edit().putString("farmer_zone", newZone).apply()

        FirebaseMessaging.getInstance().subscribeToTopic(currentZone)

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val powerData = snapshot.getValue(PowerStatus::class.java)
                if (powerData != null) {
                    if (lastStatus == "OFF" && powerData.status == "ON") {
                        triggerLocalNotification("Power is BACK!", "Power returned in $currentZone")
                    }
                    lastStatus = powerData.status
                    updateUI(powerData)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        }
        database.child(currentZone).addValueEventListener(statusListener!!)
    }

    private fun updateStatus(status: String) {
        if (currentZone.isEmpty()) {
            Toast.makeText(this, "Select a village first", Toast.LENGTH_SHORT).show()
            return
        }
        val farmerName = sharedPref.getString("farmer_name", "Anonymous")
        val update = PowerStatus(status, System.currentTimeMillis(), farmerName ?: "Anonymous")
        database.child(currentZone).setValue(update)
    }

    private fun updateUI(powerData: PowerStatus?) {
        if (powerData != null) {
            statusText.text = if(powerData.status == "LOW") "LOW VOLTAGE" else "POWER IS ${powerData.status}"

            val reporter = if (powerData.reportedBy.isNullOrEmpty()) "System" else powerData.reportedBy
            tvReportedBy.text = "Reported by: $reporter"

            when (powerData.status) {
                "ON" -> {
                    statusCard.setCardBackgroundColor(getColor(R.color.power_on_green))
                    statusText.setTextColor(Color.WHITE)
                }
                "OFF" -> {
                    statusCard.setCardBackgroundColor(getColor(R.color.power_off_red))
                    statusText.setTextColor(Color.WHITE)
                }
                "LOW" -> {
                    statusCard.setCardBackgroundColor(Color.parseColor("#FBC02D"))
                    statusText.setTextColor(Color.BLACK)
                }
            }

            val accentColor = if (powerData.status == "LOW") Color.BLACK else Color.WHITE
            lastSeenText.setTextColor(accentColor)
            tvReportedBy.setTextColor(accentColor)

            val mins = (System.currentTimeMillis() - powerData.timestamp) / 60000
            lastSeenText.text = if (mins < 1) "Updated: Just now" else "Updated: $mins mins ago"
        }
    }

    // --- ENHANCED DESCRIPTIVE AI LOGIC ---
    private fun showPumpTimerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pump_timer, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val etCrop = dialogView.findViewById<EditText>(R.id.etCropName)
        val btnAsk = dialogView.findViewById<Button>(R.id.btnAskAI)
        val tvResponse = dialogView.findViewById<TextView>(R.id.tvAiResponse)
        val loader = dialogView.findViewById<ProgressBar>(R.id.aiLoader)

        btnAsk.setOnClickListener {
            val crop = etCrop.text.toString().trim()
            if (crop.isNotEmpty()) {
                loader.visibility = View.VISIBLE
                tvResponse.text = "Consulting Grama-Urja AI..."

                val apiKey = "AIzaSyAcoHgBm5_5X5tTRS5c2zmXmFpPgAPHnwQ"
                val url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$apiKey"

                // DESCRIPTIVE PROMPT
                val prompt = "Provide a practical irrigation guide for $crop including recommended pumping hours, best time of day to water, and a soil moisture tip. Keep it under 4 sentences."
                val jsonBody = """{"contents": [{"parts":[{"text": "$prompt"}]}]}"""

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)
                val request = Request.Builder().url(url).post(requestBody).build()
                val client = OkHttpClient()

                MainScope().launch {
                    try {
                        val responseBody = withContext(Dispatchers.IO) {
                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) throw Exception("FAIL")
                                response.body?.string() ?: ""
                            }
                        }

                        if (responseBody.contains("\"text\": \"")) {
                            val start = responseBody.indexOf("\"text\": \"") + 9
                            val aiText = responseBody.substring(start, responseBody.indexOf("\"", start))
                            tvResponse.text = aiText.replace("\\n", "\n").replace("\\\"", "\"")
                        } else throw Exception("FAIL")

                    } catch (e: Exception) {
                        // DESCRIPTIVE FAIL-SAFE LOGIC
                        val fallback = when(crop.lowercase()) {
                            "wheat" -> "For Wheat, run pump for 2-3 hours in early morning. Check soil moisture by squeezing it; if it crumbles, it needs water."
                            "paddy", "rice" -> "Paddy needs 5 hours of pumping to maintain 5cm water depth. Ensure field is submerged daily during the growth stage."
                            "sugarcane" -> "Sugarcane is water-heavy. Run pump for 6-8 hours every 10 days, ensuring root zones are deeply saturated."
                            else -> "Run the pump for 3 hours for $crop. Avoid peak afternoon heat to reduce evaporation. Soil should be damp to the touch."
                        }
                        tvResponse.text = "Grama-Urja Expert Advice: $fallback"
                    } finally {
                        loader.visibility = View.GONE
                    }
                }
            }
        }
        dialog.show()
    }

    private fun triggerLocalNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "local_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(chId, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(this, chId)
            .setContentTitle(title).setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).build()
        manager.notify(2, notification)
    }
}