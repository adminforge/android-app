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

class MainActivity : BaseActivity(), StatusPoller.Listener, NewsPoller.Listener {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)

        // One-time: clear the legacy filename-based icon cache so migrated icon URLs are re-fetched
        if (!prefs.getBoolean("icon_cache_v2_cleared", false)) {
            java.io.File(filesDir, "icons").deleteRecursively()
            prefs.edit().putBoolean("icon_cache_v2_cleared", true).apply()
        }

        // Initialize Background Notifications
        NotificationHelper.createNotificationChannel(this)
        if (prefs.getBoolean("notifications_enabled", false)) {
            NotificationWorker.schedule(this)
        }

        // Immediate sync of status and news
        NotificationWorker.runOnce(this)
        

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
        val searchBox = findViewById<android.widget.EditText>(R.id.search_box)
        val inflater = LayoutInflater.from(this)
        val allServices = loadServices()

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
                        link = serviceObj.getString("link")
                    ))
                }
                allCategories.add(Category(categoryObj.getString("name"), servicesList))
            }
        } catch (e: Exception) { e.printStackTrace() }

        fun getPinnedLinks(): Set<String> {
            return prefs.getStringSet("pinned_services", emptySet()) ?: emptySet()
        }

        // Forward declaration trick or just move them properly
        // In Kotlin, local functions can refer to each other if they are in the same block
        // but the compiler sometimes struggles with the order.
        
        var refreshListFunc: ((String) -> Unit)? = null

        fun renderServices(items: List<ListItem>) {
            container.removeAllViews()
            val pinnedLinks = getPinnedLinks()

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
                        val pinBtn = view.findViewById<ImageView>(R.id.btn_pin)

                        val isPinned = pinnedLinks.contains(service.link)
                        pinBtn.setImageResource(if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_outline)
                        pinBtn.setColorFilter(if (isPinned) android.graphics.Color.parseColor("#FFD700") else android.graphics.Color.GRAY)
                        
                        pinBtn.setOnClickListener {
                            val pinned = getPinnedLinks().toMutableSet()
                            if (pinned.contains(service.link)) {
                                pinned.remove(service.link)
                            } else {
                                pinned.add(service.link)
                            }
                            prefs.edit().putStringSet("pinned_services", pinned).apply()
                            
                            val currentQuery = searchBox.text.toString()
                            refreshListFunc?.invoke(currentQuery)
                        }
                        
                        val cacheDir = java.io.File(filesDir, "icons")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        // Cache key is derived from the full URL, so a changed icon path/host invalidates the old cache
                        val ext = service.iconUrl.substringAfterLast('.', "png").take(4)
                        val fileName = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(service.iconUrl.toByteArray())
                            .joinToString("") { "%02x".format(it) } + "." + ext
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

        fun refreshList(query: String) {
            val lowercaseQuery = query.lowercase()
            val finalItems = mutableListOf<ListItem>()
            val pinnedLinks = getPinnedLinks()

            // 1. Handle Pinned Services
            val pinnedServices = mutableListOf<Service>()
            allCategories.forEach { cat ->
                cat.services.forEach { service ->
                    if (pinnedLinks.contains(service.link)) {
                        if (lowercaseQuery.isEmpty() || 
                            service.name.lowercase().contains(lowercaseQuery) || 
                            service.description.lowercase().contains(lowercaseQuery)) {
                            pinnedServices.add(service)
                        }
                    }
                }
            }

            if (pinnedServices.isNotEmpty()) {
                finalItems.add(ListItem.CategoryItem(Category(getString(R.string.category_favorites), pinnedServices)))
                pinnedServices.forEach { finalItems.add(ListItem.ServiceItem(it)) }
            }

            // 2. Handle Regular Categories
            if (lowercaseQuery.isEmpty()) {
                allCategories.forEach { category: Category ->
                    finalItems.add(ListItem.CategoryItem(category))
                    category.services.forEach { service: Service ->
                        finalItems.add(ListItem.ServiceItem(service))
                    }
                }
            } else {
                allCategories.forEach { category: Category ->
                    val matchingServices: List<Service> = category.services.filter { service: Service ->
                        service.name.lowercase().contains(lowercaseQuery) || 
                        service.description.lowercase().contains(lowercaseQuery) 
                    }
                    if (matchingServices.isNotEmpty()) {
                        finalItems.add(ListItem.CategoryItem(category))
                        matchingServices.forEach { service: Service ->
                            finalItems.add(ListItem.ServiceItem(service))
                        }
                    }
                }
            }
            renderServices(finalItems)
        }
        
        refreshListFunc = ::refreshList

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshList(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Initial render
        refreshList("")

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

        // Explicitly set navigation labels from resources to ensure localization
        findViewById<TextView>(R.id.txt_home)?.text = getString(R.string.nav_home)
        findViewById<TextView>(R.id.txt_news)?.text = getString(R.string.nav_news)
        findViewById<TextView>(R.id.txt_status)?.text = getString(R.string.nav_status)
        findViewById<TextView>(R.id.txt_donate)?.text = getString(R.string.nav_donate)

        if (intent.getBooleanExtra("FOCUS_SEARCH", false)) {
            searchBox.post {
                searchBox.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(searchBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }

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
        
        // Explicitly set menu titles from resources to ensure localization
        menu.findItem(R.id.action_open_browser)?.title = getString(R.string.menu_open_browser)
        menu.findItem(R.id.action_settings_page)?.title = getString(R.string.title_settings)
        menu.findItem(R.id.action_website)?.title = getString(R.string.menu_website)
        menu.findItem(R.id.action_forum)?.title = getString(R.string.menu_forum)
        menu.findItem(R.id.action_contact)?.title = getString(R.string.menu_contact)
        menu.findItem(R.id.action_git)?.title = getString(R.string.menu_git)

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
            R.id.action_git -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://git.adminforge.de/adminforge/android-app"))
                startActivity(intent)
                true
            }
            R.id.action_update -> {
                UpdateChecker.checkForUpdateInteractive(this)
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
                        link = serviceObj.getString("link")
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
