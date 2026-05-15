package com.project.gramaurja

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pref = getSharedPreferences("GramaUrjaPrefs", Context.MODE_PRIVATE)
        // Auto-login if data exists
        if (pref.contains("farmer_name")) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_setup)

        val etName = findViewById<EditText>(R.id.etFarmerName)
        val spinner = findViewById<Spinner>(R.id.setupZoneSpinner)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)

        val villages = listOf("Zone_A", "Zone_B", "Zone_C")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, villages)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                pref.edit()
                    .putString("farmer_name", name)
                    .putString("farmer_zone", spinner.selectedItem.toString())
                    .apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
    }
}