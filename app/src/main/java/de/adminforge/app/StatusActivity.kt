package de.adminforge.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
import kotlin.concurrent.thread

class StatusActivity : BaseActivity(), StatusPoller.Listener {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var allGroups: List<Map<String, Any>> = emptyList()
    private var allIncidents: List<Map<String, Any>> = emptyList()

    private var monitorStatusMap: Map<String, Int> = emptyMap()
    private var filterOfflineOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)
        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setupBottomNavigation()
        setupSearch()
        
        val cachedSchema = prefs.getString("cached_status_groups", null)
        val cachedHeartbeats = prefs.getString("cached_status_heartbeats", null)
        if (cachedSchema != null) {
            parseAndInitialRender(cachedSchema, cachedHeartbeats)
        }
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            StatusPoller.forceFetch(this)
        }
        
    }

    override fun onResume() {
        super.onResume()
        StatusPoller.addListener(this)
        StatusPoller.start(this)
        NewsPoller.start(this)
    }

    override fun onPause() {
        super.onPause()
        StatusPoller.removeListener(this)
        StatusPoller.stop()
        NewsPoller.stop()
    }

    override fun onStatusUpdated() {
        val cachedSchema = prefs.getString("cached_status_groups", null)
        val cachedHeartbeats = prefs.getString("cached_status_heartbeats", null)
        if (cachedSchema != null) {
            runOnUiThread {
                parseAndInitialRender(cachedSchema, cachedHeartbeats)
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false
                findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            }
        }
    }

    override fun onStatusUpdateFailed() {
        runOnUiThread {
            findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            android.widget.Toast.makeText(this@StatusActivity, getString(R.string.error_loading), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        val searchBox = findViewById<android.widget.EditText>(R.id.search_box)
        val btnFilterOffline = findViewById<ImageView>(R.id.btn_filter_offline)

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderStatus(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnFilterOffline.setOnClickListener {
            filterOfflineOnly = !filterOfflineOnly
            
            if (filterOfflineOnly) {
                btnFilterOffline.setColorFilter(Color.parseColor("#f44336"))
            } else {
                btnFilterOffline.clearColorFilter()
            }
            
            renderStatus(searchBox.text.toString())
        }
    }

    private fun parseAndInitialRender(schemaJson: String, heartbeatJson: String?) {
        try {
            val schemaMap: Map<String, Any> = gson.fromJson(schemaJson, object : TypeToken<Map<String, Any>>() {}.type)
            allIncidents = schemaMap["incidents"] as? List<Map<String, Any>> ?: emptyList()
            allGroups = schemaMap["publicGroupList"] as? List<Map<String, Any>> ?: emptyList()
            
            if (heartbeatJson != null) {
                val heartbeatData: Map<String, Any> = gson.fromJson(heartbeatJson, object : TypeToken<Map<String, Any>>() {}.type)
                val list = heartbeatData["heartbeatList"] as? Map<String, List<Map<String, Any>>>
                val statusMap = mutableMapOf<String, Int>()
                list?.forEach { (id, heartbeats) ->
                    if (heartbeats.isNotEmpty()) {
                        // Take the status of the last (most recent) heartbeat
                        val latest = heartbeats.last()
                        val status = (latest["status"] as? Double)?.toInt() ?: 3
                        statusMap[id] = status
                    } else {
                        statusMap[id] = 3 // Empty heartbeat list means unknown/paused
                    }
                }
                monitorStatusMap = statusMap
            }
            
            renderStatus("")
        } catch (e: Exception) { Log.e("StatusActivity", "Failed to parse JSON", e) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            menu.findItem(R.id.action_version)?.title = "v" + pInfo.versionName
        } catch (e: Exception) { e.printStackTrace() }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_website -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://adminforge.de"))
                startActivity(intent)
                true
            }
            R.id.action_forum -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://community.adminforge.de"))
                startActivity(intent)
                true
            }
            R.id.action_contact -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://adminforge.de/kontakt/"))
                startActivity(intent)
                true
            }
            R.id.action_settings_page -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_git -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://git.adminforge.de/adminforge/android-app"))
                startActivity(intent)
                true
            }
            R.id.action_version -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Removed internal performFetch() as StatusPoller handles this now.

    private fun renderStatus(query: String) {
        val q = query.lowercase().trim()
        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
        val container = findViewById<LinearLayout>(R.id.items_container)
        container.removeAllViews()
        val inflater = android.view.LayoutInflater.from(this)

        // Calculate Overall Health
        if (q.isEmpty() && !filterOfflineOnly) {
            val healthView = inflater.inflate(R.layout.item_status_health, container, false)
            val healthCard = healthView.findViewById<androidx.cardview.widget.CardView>(R.id.health_card)
            val healthIcon = healthView.findViewById<ImageView>(R.id.health_icon)
            val healthText = healthView.findViewById<TextView>(R.id.health_text)

            val downCount = monitorStatusMap.values.count { it == 0 || it == 2 }
            val unknownCount = monitorStatusMap.values.count { it == 3 }

            when {
                downCount > 0 -> {
                    healthCard.setCardBackgroundColor(Color.parseColor("#262626"))
                    healthIcon.setImageResource(R.drawable.ic_warning)
                    healthIcon.setColorFilter(Color.parseColor("#f39c12")) // Orange for "Dienstausfall"
                    healthText.text = getString(R.string.status_partial_outage)
                    healthText.setTextColor(Color.WHITE)
                }
                else -> {
                    healthCard.setCardBackgroundColor(Color.parseColor("#262626"))
                    healthIcon.setImageResource(R.drawable.ic_check_circle)
                    healthIcon.setColorFilter(Color.parseColor("#4caf50"))
                    healthText.text = getString(R.string.status_all_ok)
                    healthText.setTextColor(Color.WHITE)
                }
            }
            container.addView(healthView)
        }

        val filteredIncidents = if (q.isEmpty() && !filterOfflineOnly) allIncidents else emptyList()
        for (incident in filteredIncidents) {
            if (incident["active"] as? Boolean == false) continue
            val view = inflater.inflate(R.layout.item_status_incident, container, false)
            val titleView = view.findViewById<TextView>(R.id.incident_title)
            titleView.text = incident["title"] as? String ?: getString(R.string.status_incident)
            view.findViewById<TextView>(R.id.incident_content).text = incident["content"] as? String ?: ""
            when ((incident["style"] as? String ?: "info").lowercase()) {
                "warning", "warnung" -> { view.setBackgroundResource(R.drawable.bg_incident_warning); titleView.setTextColor(Color.parseColor("#FBC02D")) }
                "danger", "error", "gefahr" -> { view.setBackgroundResource(R.drawable.bg_incident_danger); titleView.setTextColor(Color.parseColor("#D32F2F")) }
                else -> { view.setBackgroundResource(R.drawable.bg_incident_info); titleView.setTextColor(Color.parseColor("#1976D2")) }
            }
            container.addView(view)
        }

        var count = 0
        for (group in allGroups) {
            val monitors = group["monitorList"] as? List<Map<String, Any>> ?: emptyList()
            val searched = if (q.isEmpty()) monitors else monitors.filter { (it["name"] as? String)?.lowercase()?.contains(q) == true }
            
            val filtered = if (!filterOfflineOnly) searched else searched.filter {
                val mid = (it["id"] as? Double)?.toInt()?.toString() ?: ""
                val status = monitorStatusMap[mid] ?: 3
                status != 1 // Only keep 0, 2, 3 (Offline, Pending, Unknown/Maintenance)
            }

            if (filtered.isNotEmpty()) {
                val head = inflater.inflate(R.layout.item_category, container, false)
                head.findViewById<TextView>(R.id.category_name).text = group["name"] as? String
                container.addView(head)
                for (m in filtered) {
                    val mview = inflater.inflate(R.layout.item_status_monitor, container, false)
                    mview.findViewById<TextView>(R.id.monitor_name).text = m["name"] as? String
                    
                    val mid = (m["id"] as? Double)?.toInt()?.toString() ?: ""
                    val status = monitorStatusMap[mid] ?: 3
                    
                    mview.findViewById<ImageView>(R.id.status_icon).apply {
                        when (status) {
                            1 -> {
                                setImageResource(R.drawable.ic_check_circle)
                                setColorFilter(Color.parseColor("#4caf50"))
                            }
                            3 -> {
                                // Maintenance / Unknown -> Gray Question Mark
                                setImageResource(R.drawable.ic_help)
                                setColorFilter(Color.parseColor("#9e9e9e"))
                            }
                            else -> {
                                // Down (0) or Pending (2) -> Red ic_cancel (X)
                                setImageResource(R.drawable.ic_cancel)
                                setColorFilter(Color.parseColor("#f44336"))
                            }
                        }
                    }

                    container.addView(mview); count++
                }
            }
        }
        if (count == 0 && filteredIncidents.isEmpty()) {
            val tv = TextView(this); tv.text = getString(R.string.no_services_found); tv.setPadding(32,32,32,32); tv.gravity = Gravity.CENTER; tv.setTextColor(Color.WHITE)
            container.addView(tv)
        }

        // Save offline count for the navigation badge (only count really offline services 0 and 2, ignored paused/unknown 3).
        // This list itself always shows every service; only the badge is scoped to favorites.
        val favoritesOnly = prefs.getBoolean("notify_favorites_only", false)
        val offlineCount = if (favoritesOnly) {
            val favoriteIds = StatusPoller.getFavoriteMonitorIds(this)
            monitorStatusMap.filterKeys { it.toIntOrNull() in favoriteIds }.values.count { it == 0 || it == 2 }
        } else {
            monitorStatusMap.values.count { it == 0 || it == 2 }
        }
        prefs.edit().putInt("offline_status_count", offlineCount).apply()
        updateStatusBadge()
    }

    private fun updateStatusBadge() {
        val badge = findViewById<TextView>(R.id.status_badge) ?: return
        val offlineCount = prefs.getInt("offline_status_count", 0)
        if (offlineCount > 0) {
            badge.text = if (offlineCount > 99) "99+" else offlineCount.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.btn_home).setOnClickListener { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)); finish() }
        findViewById<LinearLayout>(R.id.btn_news).setOnClickListener { startActivity(Intent(this, NewsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)); finish() }
        findViewById<LinearLayout>(R.id.btn_status).setOnClickListener { findViewById<android.widget.ScrollView>(R.id.scrollView)?.smoothScrollTo(0, 0) }
        findViewById<LinearLayout>(R.id.btn_donate).setOnClickListener { startActivity(Intent(this, DonateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)); finish() }
        
        findViewById<ImageView>(R.id.img_home).setImageResource(R.drawable.ic_home_outline)
        findViewById<ImageView>(R.id.img_news).setImageResource(R.drawable.ic_news_outline)
        findViewById<ImageView>(R.id.img_status).setImageResource(R.drawable.ic_status)
        findViewById<ImageView>(R.id.img_donate).setImageResource(R.drawable.ic_donate_outline)
        
        val unread = prefs.getInt("unread_news_count", 0)
        findViewById<TextView>(R.id.news_badge).apply { visibility = if (unread > 0) View.VISIBLE else View.GONE; text = unread.toString() }

        updateStatusBadge()
    }
}
