package com.example.rideautoclicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etMinFare = findViewById<EditText>(R.id.etMinFare)
        val etMaxDistance = findViewById<EditText>(R.id.etMaxDistance)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)
        val btnOverlayPermission = findViewById<Button>(R.id.btnOverlayPermission)
        val btnOpenAccessibility = findViewById<Button>(R.id.btnOpenAccessibility)
        val switchEnable = findViewById<Switch>(R.id.switchEnable)

        val prefs = getSharedPreferences("AutoClickerPrefs", Context.MODE_PRIVATE)

        etMinFare.setText(prefs.getInt("min_fare", 40).toString())
        etMaxDistance.setText(prefs.getFloat("max_distance", 5.0f).toString())
        switchEnable.isChecked = prefs.getBoolean("is_enabled", false)

        btnSaveSettings.setOnClickListener {
            val fareInput = etMinFare.text.toString().toIntOrNull() ?: 40
            val distInput = etMaxDistance.text.toString().toFloatOrNull() ?: 5.0f

            prefs.edit()
                .putInt("min_fare", fareInput)
                .putFloat("max_distance", distInput)
                .apply()

            Toast.makeText(
                this,
                "સેવ થયું: ઓછામાં ઓછું ₹$fareInput ભાડું અને મહત્તમ $distInput Km અંતર",
                Toast.LENGTH_SHORT
            ).show()
        }

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_enabled", isChecked).apply()
            val status = if (isChecked) "ચાલુ (ON)" else "બંધ (OFF)"
            Toast.makeText(this, "ઓટો-ક્લિકર હવે $status છે", Toast.LENGTH_SHORT).show()
        }

        btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "ફ્લોટિંગ બટન માટે પરમિશન ચાલુ કરો", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "ફ્લોટિંગ બટનની પરમિશન પહેલેથી જ આપેલી છે!", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "લિસ્ટમાંથી 'Ride Auto Clicker' શોધીને ચાલુ કરો", Toast.LENGTH_LONG).show()
        }
    }
}
