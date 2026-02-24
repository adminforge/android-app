package de.adminforge.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import org.jsoup.Jsoup
import kotlin.concurrent.thread

class DonateActivity : AppCompatActivity(), StatusPoller.Listener, NewsPoller.Listener {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)

        prefs = getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupBottomNavigation()

        val cachedHtml = prefs.getString("cached_donate_html", null)
        if (cachedHtml != null) {
            renderDonate(cachedHtml)
        }

        thread {
            try {
                val doc = try {
                    Jsoup.connect("https://adminforge.de/unterstuetzen/").get()
                } catch (e: Exception) {
                    null
                }

                var finalHtml = ""
                if (doc != null) {
                    var startNode = doc.select("h1, h2, h3").find { it.text().contains("unterstützen", ignoreCase = true) }
                    if (startNode == null) {
                        startNode = doc.select("div.et_pb_module_inner").find { it.text().contains("PayPal", ignoreCase = true) }
                    }

                    if (startNode != null) {
                        val builder = StringBuilder()
                        var current: org.jsoup.nodes.Node? = startNode
                        var capture = true
                        while (current != null && capture) {
                            if (current is org.jsoup.nodes.Element) {
                                val text = current.text().lowercase()
                                if (current.tagName().matches(Regex("h[1-6]")) && 
                                    (text.contains("popular posts") || text.contains("presse") || text.contains("logos"))) {
                                    capture = false
                                    break
                                }
                                builder.append(current.outerHtml())
                            } else if (current is org.jsoup.nodes.TextNode) {
                                builder.append(current.outerHtml())
                            }
                            current = current.nextSibling()
                        }
                        finalHtml = builder.toString()
                    }
                }

                if (finalHtml.length < 100) {
                    finalHtml = "FORCE_FALLBACK"
                }

                prefs.edit().putString("cached_donate_html", finalHtml).apply()
                // Goal fetching removed as requested, but keeping cache logic for content if needed later
                runOnUiThread { renderDonate(finalHtml) }
            } catch (e: Exception) {
                Log.e("DonateActivity", "Error in fetch thread", e)
            }
        }
        
        UpdateChecker.checkOnStartup(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionItem = menu.findItem(R.id.action_version)
            versionItem?.title = "v" + pInfo.versionName
        } catch (e: Exception) { e.printStackTrace() }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("FOCUS_SEARCH", true)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent)
                finish()
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
            R.id.action_update -> {
                UpdateChecker.checkForUpdateInteractive(this)
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

    private fun renderDonate(html: String) {
        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
        
        // Setup click listeners for redesign links
        findViewById<View>(R.id.card_paypal).setOnClickListener { openUrlExternal("https://paypal.me/giebelstefan") }
        findViewById<View>(R.id.card_wero).setOnClickListener { openUrlExternal("https://share.weropay.eu/p/1/c/VQFAeK41rQ") }
        findViewById<View>(R.id.card_bitcoin).setOnClickListener { openUrlExternal("bitcoin:32MMkt7PvuJGvodKRXuJ6kTxvxHFmLhsBT?label=adminForge.de") }
        findViewById<View>(R.id.card_liberapay).setOnClickListener { openUrlExternal("https://liberapay.com/adminForge.de") }
        findViewById<View>(R.id.card_patreon).setOnClickListener { openUrlExternal("https://patreon.com/user?u=112395184") }

        // Bank info copy listeners
        findViewById<View>(R.id.row_holder).setOnClickListener { copyToClipboard("Inhaber", "Stefan Giebel") }
        findViewById<View>(R.id.row_iban).setOnClickListener { copyToClipboard("IBAN", "DE17370502991311036199") }
        findViewById<View>(R.id.row_bic).setOnClickListener { copyToClipboard("BIC", "COKSDE33XXX") }
        findViewById<View>(R.id.row_bank).setOnClickListener { copyToClipboard("Bank", "Kreissparkasse Köln") }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun openUrlExternal(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        StatusPoller.addListener(this)
        StatusPoller.start(this)
        NewsPoller.addListener(this)
        NewsPoller.start(this)
        updateStatusBadge()
        updateNewsBadge() 
        
        // Ensure Donate is highlighted when returning from WebActivity
        val imgHome = findViewById<ImageView>(R.id.img_home)
        val imgNews = findViewById<ImageView>(R.id.img_news)
        val imgStatus = findViewById<ImageView>(R.id.img_status)
        val imgDonate = findViewById<ImageView>(R.id.img_donate)

        imgHome.setImageResource(R.drawable.ic_home_outline)
        imgNews.setImageResource(R.drawable.ic_news_outline)
        imgStatus.setImageResource(R.drawable.ic_status_outline)
        imgDonate.setImageResource(R.drawable.ic_donate)
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
        runOnUiThread { updateNewsBadge() }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.btn_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_news).setOnClickListener {
            startActivity(Intent(this, NewsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_status).setOnClickListener {
            startActivity(Intent(this, StatusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
        }
        findViewById<LinearLayout>(R.id.btn_donate).setOnClickListener {
            findViewById<android.widget.ScrollView>(R.id.scrollView)?.smoothScrollTo(0, 0)
        }
        
        findViewById<ImageView>(R.id.img_home).setImageResource(R.drawable.ic_home_outline)
        findViewById<ImageView>(R.id.img_news).setImageResource(R.drawable.ic_news_outline)
        findViewById<ImageView>(R.id.img_status).setImageResource(R.drawable.ic_status_outline)
        findViewById<ImageView>(R.id.img_donate).setImageResource(R.drawable.ic_donate)

        updateNewsBadge()
        updateStatusBadge()
    }

    private fun updateNewsBadge() {
        val unreadNewsCount = prefs.getInt("unread_news_count", 0)
        val newsBadge = findViewById<TextView>(R.id.news_badge) ?: return
        if (unreadNewsCount > 0) {
            newsBadge.visibility = View.VISIBLE
            newsBadge.text = if (unreadNewsCount > 99) "99+" else unreadNewsCount.toString()
        } else {
            newsBadge.visibility = View.GONE
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
}
