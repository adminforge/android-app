package de.adminforge.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import com.google.gson.Gson
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object NewsPoller {
    private val handler = Handler(Looper.getMainLooper())
    private var activeActivities = 0
    private val listeners = mutableListOf<Listener>()
    private var isPolling = false
    private val gson = Gson()
    private val stopRunnable = Runnable {
        if (activeActivities <= 0) {
            isPolling = false
            handler.removeCallbacks(pollRunnable)
        }
    }

    interface Listener {
        fun onNewsUpdated()
        fun onNewsUpdateFailed() {}
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (activeActivities > 0) {
                performFetch()
                handler.postDelayed(this, 10 * 60 * 1000) // 10 minutes
            }
        }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun start(context: Context) {
        activeActivities++
        handler.removeCallbacks(stopRunnable)
        if (!isPolling) {
            isPolling = true
            appContext = context.applicationContext
            performFetch()
            handler.postDelayed(pollRunnable, 10 * 60 * 1000)
        }
    }

    fun stop() {
        activeActivities--
        if (activeActivities <= 0) {
            activeActivities = 0
            // Delay the stop by 500ms to survive activity transitions (tab switches)
            handler.postDelayed(stopRunnable, 500)
        }
    }

    fun forceFetch(context: Context) {
        appContext = context.applicationContext
        performFetch()
    }

    private var appContext: Context? = null

    /**
     * Bumps the unread badge by counting items newer than the last-seen link, instead of by
     * feed length: adminforge.de/feed serves a fixed-size window (WordPress default), so once
     * the feed has more items than that window, its length never grows and the old count-based
     * comparison stopped detecting new articles at all.
     */
    private fun updateUnreadCount(prefs: android.content.SharedPreferences, items: List<NewsItem>) {
        val newestLink = items.first().link
        val lastSeenLink = prefs.getString("last_seen_news_link", null)
        if (lastSeenLink == null) {
            // First fetch ever (or after this migration): establish the baseline without
            // retroactively counting the whole feed as unread.
            prefs.edit().putString("last_seen_news_link", newestLink).apply()
            return
        }
        if (newestLink == lastSeenLink) return

        // Count items ahead of the last-seen one. If it has scrolled out of the fetched window
        // entirely (more articles published than the window holds since the last check), this
        // naturally falls back to the full window size as a safe lower bound.
        val newCount = items.takeWhile { it.link != lastSeenLink }.size
        if (newCount > 0) {
            val currentUnread = prefs.getInt("unread_news_count", 0)
            prefs.edit()
                .putInt("unread_news_count", currentUnread + newCount)
                .putString("last_seen_news_link", newestLink)
                .apply()
        }
    }

    suspend fun performFetchSuspend(context: Context? = appContext): Boolean = withContext(Dispatchers.IO) {
        val ctx = context?.applicationContext ?: return@withContext false
        appContext = ctx
        
        var inputStream: InputStream? = null
        try {
            val connection = URL("https://adminforge.de/feed").openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            inputStream = connection.inputStream
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            var insideItem = false
            var currentTitle = ""
            var currentLink = ""
            var currentDesc = ""
            var currentPubDate = ""
            var currentContentEncoded = ""
            val items = mutableListOf<NewsItem>()
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.name.lowercase()
                    if (name == "item") {
                        insideItem = true
                        currentTitle = ""; currentLink = ""; currentDesc = ""; currentPubDate = ""; currentContentEncoded = ""
                    } else if (insideItem) {
                        try {
                            when (name) {
                                "title" -> currentTitle = parser.nextText()
                                "link" -> currentLink = parser.nextText()
                                "description" -> currentDesc = parser.nextText()
                                "encoded" -> currentContentEncoded = parser.nextText()
                                "pubdate" -> currentPubDate = parser.nextText()
                            }
                        } catch (e: Exception) {}
                    }
                } else if (eventType == XmlPullParser.END_TAG && parser.name.lowercase() == "item") {
                    insideItem = false
                    var descToUse = if (currentDesc.isBlank()) currentContentEncoded else currentDesc
                    descToUse = Html.fromHtml(descToUse, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    items.add(NewsItem(currentTitle, currentPubDate, descToUse, currentLink))
                }
                eventType = parser.next()
            }

            if (items.isNotEmpty()) {
                val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
                updateUnreadCount(prefs, items)
                prefs.edit().putString("cached_news", gson.toJson(items)).apply()
                withContext(Dispatchers.Main) {
                    listeners.forEach { it.onNewsUpdated() }
                }
                return@withContext true
            } else {
                withContext(Dispatchers.Main) { listeners.forEach { it.onNewsUpdateFailed() } }
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("NewsPoller", "Error fetching news", e)
            withContext(Dispatchers.Main) { listeners.forEach { it.onNewsUpdateFailed() } }
            return@withContext false
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    private fun performFetch() {
        val ctx = appContext ?: return
        
        thread {
            var inputStream: InputStream? = null
            try {
                val connection = URL("https://adminforge.de/feed").openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                inputStream = connection.inputStream
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(inputStream, null)
                var eventType = parser.eventType
                var insideItem = false
                var currentTitle = ""
                var currentLink = ""
                var currentDesc = ""
                var currentPubDate = ""
                var currentContentEncoded = ""
                val items = mutableListOf<NewsItem>()
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val name = parser.name.lowercase()
                        if (name == "item") {
                            insideItem = true
                            currentTitle = ""; currentLink = ""; currentDesc = ""; currentPubDate = ""; currentContentEncoded = ""
                        } else if (insideItem) {
                            try {
                                when (name) {
                                    "title" -> currentTitle = parser.nextText()
                                    "link" -> currentLink = parser.nextText()
                                    "description" -> currentDesc = parser.nextText()
                                    "encoded" -> currentContentEncoded = parser.nextText()
                                    "pubdate" -> currentPubDate = parser.nextText()
                                }
                            } catch (e: Exception) {}
                        }
                    } else if (eventType == XmlPullParser.END_TAG && parser.name.lowercase() == "item") {
                        insideItem = false
                        var descToUse = if (currentDesc.isBlank()) currentContentEncoded else currentDesc
                        descToUse = Html.fromHtml(descToUse, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                        items.add(NewsItem(currentTitle, currentPubDate, descToUse, currentLink))
                    }
                    eventType = parser.next()
                }

                if (items.isNotEmpty()) {
                    val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
                    updateUnreadCount(prefs, items)
                    prefs.edit().putString("cached_news", gson.toJson(items)).apply()
                    handler.post {
                        listeners.forEach { it.onNewsUpdated() }
                    }
                } else {
                    handler.post { listeners.forEach { it.onNewsUpdateFailed() } }
                }

            } catch (e: Exception) {
                Log.e("NewsPoller", "Error fetching news", e)
                handler.post { listeners.forEach { it.onNewsUpdateFailed() } }
            } finally {
                try { inputStream?.close() } catch (e: Exception) {}
            }
        }
    }
}
