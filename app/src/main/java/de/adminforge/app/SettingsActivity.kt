package de.adminforge.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import org.unifiedpush.android.connector.UnifiedPush

class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var textDistributor: TextView
    private lateinit var btnPickDistributor: Button
    private lateinit var btnTestNotification: Button
    private lateinit var cardUnifiedPush: View
    private lateinit var labelUnifiedPush: View
    private lateinit var textBatteryStatus: TextView
    private lateinit var btnRequestBatteryExemption: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            prefs.edit().putBoolean("notifications_enabled", true).apply()
            registerUnifiedPush()
            updateVisibility(true)
        } else {
            switchNotifications.isChecked = false
            prefs.edit().putBoolean("notifications_enabled", false).apply()
            updateVisibility(false)
            Toast.makeText(this, "Berechtigung für Benachrichtigungen erforderlich", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.mutate()?.let {
            it.setTint(android.graphics.Color.WHITE)
        }
        toolbar.setNavigationOnClickListener { finish() }

        switchNotifications = findViewById(R.id.switch_notifications)
        textDistributor = findViewById(R.id.text_distributor)
        btnPickDistributor = findViewById(R.id.btn_pick_distributor)
        btnTestNotification = findViewById(R.id.btn_test_notification)
        cardUnifiedPush = findViewById(R.id.card_unified_push)
        labelUnifiedPush = findViewById(R.id.label_unified_push)
        textBatteryStatus = findViewById(R.id.text_battery_status)
        btnRequestBatteryExemption = findViewById(R.id.btn_request_battery_exemption)

        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)
        switchNotifications.isChecked = notificationsEnabled
        updateVisibility(notificationsEnabled)

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkNotificationPermissionAndRegister()
            } else {
                prefs.edit().putBoolean("notifications_enabled", false).apply()
                updateVisibility(false)
                unregisterUnifiedPush()
            }
        }

        btnPickDistributor.setOnClickListener {
            showDistributorPicker()
        }

        btnTestNotification.setOnClickListener {
            NotificationHelper.showNotification(
                this, 
                "Test Benachrichtigung", 
                "Dies ist ein Test von adminForge. Wenn du das siehst, funktionieren Benachrichtigungen!"
            )
            Toast.makeText(this, "Test gesendet", Toast.LENGTH_SHORT).show()
        }

        btnRequestBatteryExemption.setOnClickListener {
            requestBatteryExemption()
        }

        updateStatus()
        updateBatteryStatus()
    }

    private fun updateBatteryStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        if (isIgnoring) {
            textBatteryStatus.text = "Akku-Optimierung: Ausgenommen"
            textBatteryStatus.setTextColor(android.graphics.Color.GREEN)
            btnRequestBatteryExemption.visibility = View.GONE
        } else {
            textBatteryStatus.text = "Akku-Optimierung: Aktiv (Eingeschränkt)"
            textBatteryStatus.setTextColor(android.graphics.Color.YELLOW)
            btnRequestBatteryExemption.visibility = View.VISIBLE
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general battery settings if direct request fails
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun checkNotificationPermissionAndRegister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    prefs.edit().putBoolean("notifications_enabled", true).apply()
                    updateVisibility(true)
                    registerUnifiedPush()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            prefs.edit().putBoolean("notifications_enabled", true).apply()
            updateVisibility(true)
            registerUnifiedPush()
        }
    }

    private fun updateVisibility(enabled: Boolean) {
        val visibility = if (enabled) View.VISIBLE else View.GONE
        cardUnifiedPush.visibility = visibility
        labelUnifiedPush.visibility = visibility
        btnTestNotification.visibility = visibility
    }

    private fun showDistributorPicker() {
        val distributors = UnifiedPush.getDistributors(this)
        if (distributors.isEmpty()) {
            showNoDistributorDialog()
        } else {
            val names = distributors.map { 
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
                } catch (e: Exception) {
                    it
                }
            }.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Distributor wählen")
                .setItems(names) { _, which ->
                    val selected = distributors[which]
                    
                    // Cleanup old subscription attempt
                    unregisterUnifiedPush()
                    
                    UnifiedPush.saveDistributor(this, selected)
                    
                    // Force a small delay or ensure registration is clean
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        UnifiedPush.register(this)
                        updateStatus()
                    }, 200)
                    
                    Toast.makeText(this, "Ausgewählt: ${names[which]}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Mehr Infos") { _, _ ->
                    showNoDistributorDialog()
                }
                .show()
        }
    }

    private fun showNoDistributorDialog() {
        val message = "UnifiedPush benötigt eine Distributor-App.\n\n" +
                "Empfohlene Apps:\n" +
                "• ntfy (Empfohlen, einfach zu nutzen)\n" +
                "• Gotify (Eigenes Hosting)\n" +
                "• Nextcloud (UnifiedPush App)\n\n" +
                "Links zum Download:"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Distributor finden")
            .setMessage(message)
            .setPositiveButton("ntfy (Play Store)") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=io.heckel.ntfy")
                startActivity(intent)
            }
            .setNeutralButton("ntfy (F-Droid)") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://f-droid.org/en/packages/io.heckel.ntfy/")
                startActivity(intent)
            }
            .setNegativeButton("Andere", { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://unifiedpush.org/users/distributors/")
                startActivity(intent)
            })
            .show()
    }

    private fun updateStatus() {
        val distributor = UnifiedPush.getSavedDistributor(this) ?: UnifiedPush.getAckDistributor(this)
        
        if (!distributor.isNullOrEmpty()) {
            val name = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(distributor, 0)).toString()
            } catch (e: Exception) {
                distributor
            }
            textDistributor.text = "Distributor: $name"
        } else {
            textDistributor.text = "Distributor: Nicht gewählt"
        }
    }

    private fun registerUnifiedPush() {
        UnifiedPush.register(this)
        NotificationHelper.createNotificationChannel(this)
        updateStatus()
    }

    private fun unregisterUnifiedPush() {
        UnifiedPush.unregister(this)
        prefs.edit().remove("unified_push_endpoint").apply()
        updateStatus()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "unified_push_endpoint" || key == "notifications_enabled") {
            runOnUiThread {
                updateStatus()
                updateVisibility(prefs.getBoolean("notifications_enabled", false))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        updateStatus()
        updateBatteryStatus()
        
        // Safety check: if enabled but waiting for endpoint, try re-registering
        if (switchNotifications.isChecked && prefs.getString("unified_push_endpoint", null).isNullOrEmpty()) {
            val dist = UnifiedPush.getSavedDistributor(this)
            if (!dist.isNullOrEmpty()) {
                UnifiedPush.register(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}
