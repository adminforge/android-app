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

    suspend fun performFetchSuspend(context: Context? = appContext): Boolean = withContext(Dispatchers.IO) {
        val ctx = context?.applicationContext ?: return@withContext false
        appContext = ctx
        
        var inputStream: InputStream? = null
        try {
            val url = URL("https://adminforge.de/feed")
            inputStream = url.openStream()
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
                val totalProcessed = prefs.getInt("total_news_items", 0)
                val currentUnread = prefs.getInt("unread_news_count", 0)
                val fetchedCount = items.size

                if (totalProcessed == 0 && fetchedCount > 0) {
                    prefs.edit().putInt("total_news_items", fetchedCount).apply()
                } else if (fetchedCount > totalProcessed) {
                    val newUnread = fetchedCount - totalProcessed
                    prefs.edit()
                        .putInt("unread_news_count", currentUnread + newUnread)
                        .putInt("total_news_items", fetchedCount)
                        .apply()
                }

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
                val url = URL("https://adminforge.de/feed")
                inputStream = url.openStream()
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
                    val totalProcessed = prefs.getInt("total_news_items", 0)
                    val currentUnread = prefs.getInt("unread_news_count", 0)
                    val fetchedCount = items.size

                    if (totalProcessed == 0 && fetchedCount > 0) {
                        prefs.edit().putInt("total_news_items", fetchedCount).apply()
                    } else if (fetchedCount > totalProcessed) {
                        val newUnread = fetchedCount - totalProcessed
                        prefs.edit()
                            .putInt("unread_news_count", currentUnread + newUnread)
                            .putInt("total_news_items", fetchedCount)
                            .apply()
                    }

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
