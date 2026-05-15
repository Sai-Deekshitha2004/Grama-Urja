package com.project.gramaurja

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {
    private lateinit var statusCard: CardView
    private lateinit var statusText: TextView
    private lateinit var lastSeenText: TextView
    private lateinit var tvReportedBy: TextView
    private lateinit var database: DatabaseReference
    private var currentZone = ""
    private var lastStatus = ""
    private var statusListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Initialization
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        lastSeenText = findViewById(R.id.lastSeenText)
        tvReportedBy = findViewById(R.id.tvReportedBy)
        val zoneSpinner = findViewById<Spinner>(R.id.zoneSpinner)
        val welcomeText = findViewById<TextView>(R.id.welcomeText)

        val pref = getSharedPreferences("GramaUrjaPrefs", Context.MODE_PRIVATE)
        val savedName = pref.getString("farmer_name", "Farmer")
        welcomeText.text = "Namaste, $savedName"

        // FIX: Change button logic
        findViewById<TextView>(R.id.btnEditProfile).setOnClickListener {
            pref.edit().clear().apply() // Reset all data
            val intent = Intent(this, SetupActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Ensure this URL is exactly what you see in the console screenshot
        database = FirebaseDatabase.getInstance("https://grama-urja-final-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("zones")

        // Zone Logic
        val villages = listOf("Select Village", "Zone_A", "Zone_B", "Zone_C")
        zoneSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, villages)

        // Auto-select zone from setup
        val initialZone = pref.getString("farmer_zone", "")
        val initialPos = villages.indexOf(initialZone)
        if(initialPos > 0) zoneSpinner.setSelection(initialPos)

        zoneSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (pos > 0) switchZone(villages[pos])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnPowerOn).setOnClickListener { updateStatus("ON") }
        findViewById<Button>(R.id.btnLowVoltage).setOnClickListener { updateStatus("LOW") }
        findViewById<Button>(R.id.btnPowerOff).setOnClickListener { updateStatus("OFF") }
        findViewById<Button>(R.id.btnPumpTimer).setOnClickListener { showPumpTimerDialog() }
    }

    private fun switchZone(newZone: String) {
        if (currentZone.isNotEmpty()) statusListener?.let { database.child(currentZone).removeEventListener(it) }
        currentZone = newZone
        lastStatus = "" // Reset tracker for new zone

        FirebaseMessaging.getInstance().subscribeToTopic(currentZone)

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(PowerStatus::class.java)
                if (data != null) {
                    // NOTIFICATION LOGIC: Trigger when status changes from OFF/LOW to ON
                    if ((lastStatus == "OFF" || lastStatus == "LOW") && data.status == "ON") {
                        triggerNotify("Power is BACK!", "Electricity has returned in $currentZone")
                    }
                    lastStatus = data.status
                    updateUI(data)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child(currentZone).addValueEventListener(statusListener!!)
    }

    private fun updateStatus(status: String) {
        if (currentZone.isEmpty()) return
        val name = getSharedPreferences("GramaUrjaPrefs", Context.MODE_PRIVATE).getString("farmer_name", "User")
        database.child(currentZone).setValue(PowerStatus(status, System.currentTimeMillis(), name!!))
    }

    private fun updateUI(data: PowerStatus) {
        statusText.text = if (data.status == "LOW") "LOW VOLTAGE" else "POWER IS ${data.status}"
        tvReportedBy.text = "By: ${data.reportedBy}"

        // Use our new professional color palette
        when (data.status) {
            "ON" -> {
                statusCard.setCardBackgroundColor(getColor(R.color.status_green))
                setCardContentColor(Color.WHITE)
            }
            "OFF" -> {
                statusCard.setCardBackgroundColor(getColor(R.color.status_red))
                setCardContentColor(Color.WHITE)
            }
            "LOW" -> {
                statusCard.setCardBackgroundColor(getColor(R.color.status_yellow))
                setCardContentColor(Color.BLACK)
            }
        }

        val mins = (System.currentTimeMillis() - data.timestamp) / 60000
        lastSeenText.text = if (mins < 1) "Just now" else "$mins mins ago"
    }

    // Helper to keep code clean
    private fun setCardContentColor(color: Int) {
        statusText.setTextColor(color)
        lastSeenText.setTextColor(color)
        tvReportedBy.setTextColor(color)
    }

    private fun showPumpTimerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pump_timer, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val tvRes = dialogView.findViewById<TextView>(R.id.tvAiResponse)
        val loader = dialogView.findViewById<ProgressBar>(R.id.aiLoader)
        val btnAsk = dialogView.findViewById<Button>(R.id.btnAskAI)
        val etCrop = dialogView.findViewById<EditText>(R.id.etCropName)

        btnAsk.setOnClickListener {
            val crop = etCrop.text.toString()
            if (crop.isEmpty()) return@setOnClickListener

            loader.visibility = View.VISIBLE
            tvRes.text = "Consulting Grama-Urja AI..."

            val key = getString(R.string.gemini_api_key)
            val url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$key"

            // IMPROVED DESCRIPTIVE PROMPT
            val prompt = "I am a farmer. The power just returned. Provide a highly descriptive irrigation guide for $crop. Include: 1. Exact pumping duration. 2. Best time of day to irrigate. 3. A soil moisture checking tip. Answer in exactly 3 detailed sentences."
            val body = """{"contents": [{"parts":[{"text": "$prompt"}]}]}"""

            MainScope().launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        OkHttpClient().newCall(Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build()).execute().body?.string() ?: ""
                    }
                    if (res.contains("text")) {
                        val aiText = res.split("\"text\": \"")[1].split("\"")[0].replace("\\n", "\n").replace("\\\"", "\"")
                        tvRes.text = aiText
                    } else throw Exception()
                } catch (e: Exception) {
                    tvRes.text = "Expert Tip: For $crop, irrigate for 3-4 hours in the early morning. Squeeze the soil; if it holds its shape but doesn't leak water, it is perfect."
                } finally {
                    loader.visibility = View.GONE
                }
            }
        }
        dialog.show()
    }

    private fun triggerNotify(t: String, m: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("ch", "Alerts", NotificationManager.IMPORTANCE_HIGH)
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, "ch")
            .setContentTitle(t)
            .setContentText(m)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}