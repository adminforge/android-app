package de.adminforge.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.app.NotificationManager

class WebActivity : AppCompatActivity(), StatusPoller.Listener, NewsPoller.Listener {

    private lateinit var prefs: SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val url = intent.getStringExtra("URL") ?: "https://adminforge.de/"
        val webView = findViewById<WebView>(R.id.webview)

        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        
        // Security Polish: Sandbox WebView
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        // Performance Polish: Ensure smooth experience
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = false

        val imgHome = findViewById<ImageView>(R.id.img_home)
        val imgNews = findViewById<ImageView>(R.id.img_news)
        val imgStatus = findViewById<ImageView>(R.id.img_status)
        val imgDonate = findViewById<ImageView>(R.id.img_donate)

        fun updateNewsBadge() {
            val badge = findViewById<TextView>(R.id.news_badge)
            val unreadCount = prefs.getInt("unread_news_count", 0)
            if (unreadCount > 0) {
                badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                badge.visibility = android.view.View.VISIBLE
            } else {
                badge.visibility = android.view.View.GONE
            }
        }

        fun updateStatusBadge() {
            val badge = findViewById<TextView>(R.id.status_badge) ?: return
            val offlineCount = prefs.getInt("offline_status_count", 0)
            if (offlineCount > 0) {
                badge.text = if (offlineCount > 99) "99+" else offlineCount.toString()
                badge.visibility = android.view.View.VISIBLE
            } else {
                badge.visibility = android.view.View.GONE
            }
        }

        fun updateBottomNavIcons(activeUrl: String?) {
            // Reset all to OUTLINE by default
            imgHome.setImageResource(R.drawable.ic_home_outline)
            imgNews.setImageResource(R.drawable.ic_news_outline)
            imgStatus.setImageResource(R.drawable.ic_status_outline)
            imgDonate.setImageResource(R.drawable.ic_donate_outline)

            val fromNews = intent.getBooleanExtra("FROM_NEWS", false)
            val fromHomeService = intent.getBooleanExtra("FROM_HOME_SERVICE", false)
            
            if (fromNews) {
                imgNews.setImageResource(R.drawable.ic_news)
            } else if (fromHomeService) {
                imgHome.setImageResource(R.drawable.ic_home)
            } else {
                activeUrl?.lowercase()?.let { urlL ->
                    when {
                        urlL.contains("/blog") || urlL.contains("news") -> imgNews.setImageResource(R.drawable.ic_news)
                        urlL.contains("status") -> imgStatus.setImageResource(R.drawable.ic_status)
                        urlL.contains("unterstuetzen") || urlL.contains("spenden") || urlL.contains("donate") -> imgDonate.setImageResource(R.drawable.ic_donate)
                        urlL.contains("home") -> imgHome.setImageResource(R.drawable.ic_home)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java", ReplaceWith("true"))
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url)
                    updateBottomNavIcons(url)
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        view.context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        // Log or handle missing app for intent
                    }
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateBottomNavIcons(url ?: view?.url)
            }
        }
        
        webView.isNestedScrollingEnabled = true
        webView.loadUrl(url)
        updateBottomNavIcons(url)
        updateNewsBadge()
        updateStatusBadge()

        findViewById<LinearLayout>(R.id.btn_home).setOnClickListener {
            updateBottomNavIcons("home")
            finish()
            overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.btn_news).setOnClickListener {
            val intent = Intent(this, NewsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_status).setOnClickListener {
            val intent = Intent(this, StatusActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_donate).setOnClickListener {
            val intent = Intent(this, DonateActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            finish()
        }

    }

    override fun onResume() {
        super.onResume()
        StatusPoller.addListener(this)
        StatusPoller.start(this)
        NewsPoller.addListener(this)
        NewsPoller.start(this)
        // Try to update badges if resumed
        try {
            updateNewsBadge()
            updateStatusBadge()
        } catch (e: Exception) {}
    }

    private fun updateNewsBadge() {
        val newsBadge = findViewById<TextView>(R.id.news_badge) ?: return
        val unreadCount = prefs.getInt("unread_news_count", 0)
        if (unreadCount > 0) {
            newsBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            newsBadge.visibility = android.view.View.VISIBLE
        } else {
            newsBadge.visibility = android.view.View.GONE
        }
    }

    private fun updateStatusBadge() {
        val statusBadge = findViewById<TextView>(R.id.status_badge) ?: return
        val offlineCount = prefs.getInt("offline_status_count", 0)
        if (offlineCount > 0) {
            statusBadge.text = if (offlineCount > 99) "99+" else offlineCount.toString()
            statusBadge.visibility = android.view.View.VISIBLE
        } else {
            statusBadge.visibility = android.view.View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        StatusPoller.removeListener(this)
        StatusPoller.stop()
        NewsPoller.removeListener(this)
        NewsPoller.stop()
    }

    override fun onStatusUpdated() {
        runOnUiThread {
            try {
                updateStatusBadge()
            } catch (e: Exception) {}
        }
    }

    override fun onNewsUpdated() {
        runOnUiThread {
            try {
                updateNewsBadge()
            } catch (e: Exception) {}
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionItem = menu?.findItem(R.id.action_version)
            versionItem?.title = "v" + pInfo.versionName

            // Show 'Open in Browser' icon only if in WebActivity
            menu?.findItem(R.id.action_open_browser)?.isVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_open_browser -> {
                val webView = findViewById<android.webkit.WebView>(R.id.webview)
                val currentUrl = webView.url ?: intent.getStringExtra("URL")
                if (currentUrl != null) {
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(currentUrl))
                    startActivity(browserIntent)
                }
                true
            }
            R.id.action_website -> {
                val browserIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://adminforge.de")
                )
                startActivity(browserIntent)
                true
            }
            R.id.action_forum -> {
                val browserIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://community.adminforge.de")
                )
                startActivity(browserIntent)
                true
            }
            R.id.action_contact -> {
                val browserIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://adminforge.de/kontakt/")
                )
                startActivity(browserIntent)
                true
            }
            R.id.action_update -> {
                UpdateChecker.checkForUpdateInteractive(this)
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
}
