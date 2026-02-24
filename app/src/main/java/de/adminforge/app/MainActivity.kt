package de.adminforge.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.json.JSONArray
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.content.Context
import android.content.SharedPreferences
import android.app.NotificationManager

import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), StatusPoller.Listener, NewsPoller.Listener {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)

        // Initialize Background Notifications
        NotificationHelper.createNotificationChannel(this)
        if (prefs.getBoolean("notifications_enabled", false)) {
            NotificationWorker.schedule(this)
        }

        // Immediate sync of status and news
        NotificationWorker.runOnce(this)
        
        // Setup background worker for updates (daily)
        val updateWorkRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AppUpdateSync",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWorkRequest
        )

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val imgHome = findViewById<ImageView>(R.id.img_home)
        val imgNews = findViewById<ImageView>(R.id.img_news)
        val imgStatus = findViewById<ImageView>(R.id.img_status)
        val imgDonate = findViewById<ImageView>(R.id.img_donate)

        fun updateBottomNavIcons(activeKey: String) {
            // Reset all to OUTLINE
            imgHome.setImageResource(R.drawable.ic_home_outline)
            imgNews.setImageResource(R.drawable.ic_news_outline)
            imgStatus.setImageResource(R.drawable.ic_status_outline)
            imgDonate.setImageResource(R.drawable.ic_donate_outline)

            when (activeKey) {
                "home" -> imgHome.setImageResource(R.drawable.ic_home)
                "news" -> imgNews.setImageResource(R.drawable.ic_news)
                "status" -> imgStatus.setImageResource(R.drawable.ic_status)
                "donate" -> imgDonate.setImageResource(R.drawable.ic_donate)
            }
        }

        val container = findViewById<LinearLayout>(R.id.items_container)
        val inflater = LayoutInflater.from(this)
        val allServices = loadServices()

        fun renderServices(items: List<ListItem>) {
            container.removeAllViews()

            items.forEach { item ->
                when (item) {
                    is ListItem.CategoryItem -> {
                        val view = inflater.inflate(R.layout.item_category, container, false)
                        view.findViewById<TextView>(R.id.category_name).text = item.category.name
                        container.addView(view)
                    }
                    is ListItem.ServiceItem -> {
                        val view = inflater.inflate(R.layout.item_service, container, false)
                        val service = item.service
                        
                        view.findViewById<TextView>(R.id.service_name).text = service.name
                        view.findViewById<TextView>(R.id.service_description).text = service.description
                        val iconView = view.findViewById<ImageView>(R.id.service_icon)
                        
                        val cacheDir = java.io.File(filesDir, "icons")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val fileName = service.iconUrl.substringAfterLast("/")
                        val localFile = java.io.File(cacheDir, fileName)

                        thread {
                            try {
                                if (localFile.exists()) {
                                    val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                                    runOnUiThread { iconView.setImageBitmap(bitmap) }
                                } else {
                                    val stream = URL(service.iconUrl).openStream()
                                    val bytes = stream.readBytes()
                                    localFile.writeBytes(bytes)
                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    runOnUiThread { iconView.setImageBitmap(bitmap) }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }

                        view.setOnClickListener {
                            val intent = Intent(this, WebActivity::class.java)
                            intent.putExtra("URL", service.link)
                            intent.putExtra("FROM_HOME_SERVICE", true)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                        }
                        container.addView(view)
                    }
                }
            }
        }

        val searchBox = findViewById<android.widget.EditText>(R.id.search_box)
        
        // Let's load categories and services separately once to make filtering easier
        val allCategories = mutableListOf<Category>()
        try {
            val jsonString = resources.openRawResource(R.raw.services).bufferedReader().use { it.readText() }
            val categoriesJson = JSONArray(jsonString)
            for (i in 0 until categoriesJson.length()) {
                val categoryObj = categoriesJson.getJSONObject(i)
                val servicesArr = categoryObj.getJSONArray("services")
                val servicesList = mutableListOf<Service>()
                for (j in 0 until servicesArr.length()) {
                    val serviceObj = servicesArr.getJSONObject(j)
                    servicesList.add(Service(
                        name = serviceObj.getString("name"),
                        iconUrl = serviceObj.getString("icon"),
                        description = serviceObj.getString("description"),
                        link = serviceObj.getString("link"),
                        infoLink = serviceObj.optString("info_link", "")
                    ))
                }
                allCategories.add(Category(categoryObj.getString("name"), servicesList))
            }
        } catch (e: Exception) { e.printStackTrace() }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                val filteredItems = mutableListOf<ListItem>()

                if (query.isEmpty()) {
                    allCategories.forEach { cat ->
                        filteredItems.add(ListItem.CategoryItem(cat))
                        cat.services.forEach { filteredItems.add(ListItem.ServiceItem(it)) }
                    }
                } else {
                    allCategories.forEach { cat ->
                        val matchingServices = cat.services.filter { 
                            it.name.lowercase().contains(query) || 
                            it.description.lowercase().contains(query) 
                        }
                        if (matchingServices.isNotEmpty()) {
                            filteredItems.add(ListItem.CategoryItem(cat))
                            matchingServices.forEach { filteredItems.add(ListItem.ServiceItem(it)) }
                        }
                    }
                }
                renderServices(filteredItems)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Initial render
        val initialItems = mutableListOf<ListItem>()
        allCategories.forEach { cat ->
            initialItems.add(ListItem.CategoryItem(cat))
            cat.services.forEach { initialItems.add(ListItem.ServiceItem(it)) }
        }
        renderServices(initialItems)

        val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollView) ?: null

        findViewById<android.widget.LinearLayout>(R.id.btn_home).setOnClickListener {
            updateBottomNavIcons("home")
            scrollView?.smoothScrollTo(0, 0)
        }
        findViewById<LinearLayout>(R.id.btn_news).setOnClickListener {
            updateBottomNavIcons("news")
            
            // Clear unread count when clicking news
            prefs.edit().putInt("unread_news_count", 0).apply()
            
            // Clear Notification Badge
            try {
                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(100)
            } catch (e: Exception) {}
            
            updateNewsBadge()
            
            val intent = Intent(this, NewsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }
        findViewById<LinearLayout>(R.id.btn_status).setOnClickListener {
            updateBottomNavIcons("status")
            val intent = Intent(this, StatusActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }
        findViewById<LinearLayout>(R.id.btn_donate).setOnClickListener {
            updateBottomNavIcons("donate")
            val intent = Intent(this, DonateActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }
        // Initial state
        updateBottomNavIcons("home")

        if (intent.getBooleanExtra("FOCUS_SEARCH", false)) {
            searchBox.post {
                searchBox.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(searchBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }

        UpdateChecker.checkOnStartup(this)
    }

    override fun onStatusUpdated() {
        runOnUiThread { updateStatusBadge() }
    }

    override fun onNewsUpdated() {
        runOnUiThread { updateNewsBadge() }
    }

    private fun updateNewsBadge() {
        val badge = findViewById<TextView>(R.id.news_badge)
        val unreadCount = prefs.getInt("unread_news_count", 0)
        if (unreadCount > 0) {
            badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            badge.visibility = android.view.View.VISIBLE
        } else {
            badge.visibility = android.view.View.GONE
        }
    }

    private fun updateStatusBadge() {
        val badge = findViewById<TextView>(R.id.status_badge) ?: return
        val offlineCount = prefs.getInt("offline_status_count", 0)
        if (offlineCount > 0) {
            badge.text = if (offlineCount > 99) "99+" else offlineCount.toString()
            badge.visibility = android.view.View.VISIBLE
        } else {
            badge.visibility = android.view.View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        StatusPoller.addListener(this)
        StatusPoller.start(this)
        NewsPoller.addListener(this)
        NewsPoller.start(this)
        updateNewsBadge()
        updateStatusBadge()
        
        // Ensure Home is highlighted when returning from WebActivity
        val imgHome = findViewById<ImageView>(R.id.img_home)
        val imgNews = findViewById<ImageView>(R.id.img_news)
        val imgStatus = findViewById<ImageView>(R.id.img_status)
        val imgDonate = findViewById<ImageView>(R.id.img_donate)

        imgHome.setImageResource(R.drawable.ic_home)
        imgNews.setImageResource(R.drawable.ic_news_outline)
        imgStatus.setImageResource(R.drawable.ic_status_outline)
        imgDonate.setImageResource(R.drawable.ic_donate_outline)
    }

    override fun onPause() {
        super.onPause()
        StatusPoller.removeListener(this)
        StatusPoller.stop()
        NewsPoller.removeListener(this)
        NewsPoller.stop()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionItem = menu.findItem(R.id.action_version)
            versionItem?.title = "v" + pInfo.versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                val searchBox = findViewById<android.widget.EditText>(R.id.search_box)
                searchBox.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(searchBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollView)
                scrollView?.smoothScrollTo(0, 0)
                true
            }
            R.id.action_update -> {
                UpdateChecker.checkForUpdateInteractive(this)
                true
            }
            R.id.action_forum -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://community.adminforge.de"))
                startActivity(intent)
                true
            }
            R.id.action_website -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://adminforge.de"))
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
            R.id.action_changelog -> {
                val intent = Intent(this, ChangelogActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_version -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadServices(): List<ListItem> {
        return try {
            val inputStream = resources.openRawResource(R.raw.services)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val categoriesJson = JSONArray(jsonString)
            
            val listItems = mutableListOf<ListItem>()
            for (i in 0 until categoriesJson.length()) {
                val categoryObj = categoriesJson.getJSONObject(i)
                val categoryName = categoryObj.getString("name")
                val servicesArr = categoryObj.getJSONArray("services")
                
                val servicesList = mutableListOf<Service>()
                for (j in 0 until servicesArr.length()) {
                    val serviceObj = servicesArr.getJSONObject(j)
                    val service = Service(
                        name = serviceObj.getString("name"),
                        iconUrl = serviceObj.getString("icon"),
                        description = serviceObj.getString("description"),
                        link = serviceObj.getString("link"),
                        infoLink = serviceObj.getString("info_link")
                    )
                    servicesList.add(service)
                    listItems.add(ListItem.ServiceItem(service))
                }
                val category = Category(categoryName, servicesList)
                listItems.add(listItems.size - servicesList.size, ListItem.CategoryItem(category))
            }
            listItems
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
