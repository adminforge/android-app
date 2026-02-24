package de.adminforge.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URL
import kotlin.concurrent.thread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.os.Handler
import android.os.Looper

data class NewsItem(
    val title: String,
    val date: String,
    val description: String,
    val link: String
)

class NewsActivity : AppCompatActivity(), StatusPoller.Listener, NewsPoller.Listener {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)

        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupBottomNavigation()

        findViewById<TextView>(R.id.btn_clear_news).setOnClickListener {
            prefs.edit().putInt("unread_news_count", 0).apply()
            updateUnreadBadge()
            updateStatusBadge() // Update the bottom navigation badge
            findViewById<android.widget.ScrollView>(R.id.scrollView)?.smoothScrollTo(0, 0)
        }

        val cachedNewsJson = prefs.getString("cached_news", null)
        if (cachedNewsJson != null) {
            try {
                val type = object : TypeToken<List<NewsItem>>() {}.type
                val cachedItems: List<NewsItem> = gson.fromJson(cachedNewsJson, type)
                if (cachedItems.isNotEmpty()) renderNews(cachedItems)
            } catch (e: Exception) { Log.e("NewsActivity", "Error loading cache", e) }
        }

        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            NewsPoller.forceFetch(this)
        }

    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.btn_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_news).setOnClickListener {
            findViewById<android.widget.ScrollView>(R.id.scrollView)?.smoothScrollTo(0, 0)
        }
        findViewById<LinearLayout>(R.id.btn_status).setOnClickListener {
            startActivity(Intent(this, StatusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_donate).setOnClickListener {
            startActivity(Intent(this, DonateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
        }
        
        findViewById<ImageView>(R.id.img_home).setImageResource(R.drawable.ic_home_outline)
        findViewById<ImageView>(R.id.img_news).setImageResource(R.drawable.ic_news)
        findViewById<ImageView>(R.id.img_status).setImageResource(R.drawable.ic_status_outline)
        findViewById<ImageView>(R.id.img_donate).setImageResource(R.drawable.ic_donate_outline)
        updateStatusBadge()
    }

    override fun onResume() {
        super.onResume()
        StatusPoller.addListener(this)
        StatusPoller.start(this)
        NewsPoller.addListener(this)
        NewsPoller.start(this)
        findViewById<ImageView>(R.id.img_home).setImageResource(R.drawable.ic_home_outline)
        findViewById<ImageView>(R.id.img_news).setImageResource(R.drawable.ic_news)
        findViewById<ImageView>(R.id.img_status).setImageResource(R.drawable.ic_status_outline)
        findViewById<ImageView>(R.id.img_donate).setImageResource(R.drawable.ic_donate_outline)
        updateStatusBadge()
        updateUnreadBadge()
    }

    private fun updateUnreadBadge() {
        val clearBtn = findViewById<TextView>(R.id.btn_clear_news) ?: return
        val unreadCount = prefs.getInt("unread_news_count", 0)
        if (unreadCount > 0) {
            clearBtn.visibility = View.VISIBLE
            clearBtn.text = if (unreadCount == 1) "1 neuer Artikel" else "$unreadCount neue Artikel"
        } else {
            clearBtn.visibility = View.GONE
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
        runOnUiThread { updateStatusBadge() }
    }

    override fun onNewsUpdated() {
        runOnUiThread { updateUnreadBadge() }
        val cachedNewsJson = prefs.getString("cached_news", null)
        if (cachedNewsJson != null) {
            try {
                val type = object : TypeToken<List<NewsItem>>() {}.type
                val cachedItems: List<NewsItem> = gson.fromJson(cachedNewsJson, type)
                runOnUiThread {
                    if (cachedItems.isNotEmpty()) renderNews(cachedItems)
                    findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false
                    findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
                }
            } catch (e: Exception) { Log.e("NewsActivity", "Error loading cache", e) }
        }
    }

    override fun onNewsUpdateFailed() {
        runOnUiThread {
            findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            android.widget.Toast.makeText(this@NewsActivity, "Fehler beim Laden", android.widget.Toast.LENGTH_SHORT).show()
        }
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            menu.findItem(R.id.action_version)?.title = "v" + pInfo.versionName
        } catch (e: Exception) { e.printStackTrace() }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
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

    // Moved fetchNews method to NewsPoller.kt
    private fun renderNews(items: List<NewsItem>) {
        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
        val container = findViewById<LinearLayout>(R.id.items_container)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (item in items) {
            val view = inflater.inflate(R.layout.item_news, container, false)
            view.findViewById<TextView>(R.id.news_title).text = item.title
            view.findViewById<TextView>(R.id.news_date).text = item.date.substringBefore("+0000").trim()
            view.findViewById<TextView>(R.id.news_description).text = item.description
            view.setOnClickListener {
                startActivity(Intent(this, WebActivity::class.java).putExtra("URL", item.link).putExtra("FROM_NEWS", true).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
            container.addView(view)
        }
    }
}
