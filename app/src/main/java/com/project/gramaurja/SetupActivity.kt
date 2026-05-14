package com.project.gramaurja

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize SharedPreferences
        val sharedPref = getSharedPreferences("GramaUrjaPrefs", Context.MODE_PRIVATE)

        /*
           NOTE: We removed the "Auto-Skip" logic here.
           Now, every time the app opens, it will stay on this page
           so you can enter your name.
        */

        setContentView(R.layout.activity_setup)

        val etName = findViewById<EditText>(R.id.etFarmerName)
        val spinner = findViewById<Spinner>(R.id.setupZoneSpinner)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)

        // Populate Spinner with Villages
        val villages = listOf("Zone_A", "Zone_B", "Zone_C")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, villages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // 2. Save Button Logic
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val zone = spinner.selectedItem.toString()

            if (name.isNotEmpty()) {
                val editor = sharedPref.edit()
                editor.putString("farmer_name", name)
                editor.putString("farmer_zone", zone)
                editor.commit() // Using commit for immediate saving

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
    }
}