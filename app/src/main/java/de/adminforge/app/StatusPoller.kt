package de.adminforge.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.concurrent.thread

object StatusPoller {
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
        fun onStatusUpdated()
        fun onStatusUpdateFailed() {}
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (activeActivities > 0) {
                performFetch()
                handler.postDelayed(this, 10 * 60 * 1000) // Poll every 10 minutes
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
            // Pass context to performFetch for SharedPreferences
            performFetch(context.applicationContext)
            handler.postDelayed(pollRunnable, 10 * 60 * 1000)
        }
    }

    fun stop() {
        activeActivities--
        if (activeActivities <= 0) {
            activeActivities = 0
            handler.postDelayed(stopRunnable, 500)
        }
    }

    fun forceFetch(context: Context) {
        performFetch(context.applicationContext)
    }

    fun getOfflineCount(): Int {
        val prefs = appContext?.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE) ?: return 0
        return prefs.getInt("offline_status_count", 0)
    }

    /**
     * Group name fallback for pinned services whose monitors are host/port/SMTP checks with
     * no public URL, so a domain match against the monitor list isn't possible.
     */
    private val GROUP_NAME_FALLBACK = mapOf(
        "mail.adminforge.de" to "adminForge Mail",
        "chat.adminforge.de" to "Chatmail für Delta Chat"
    )

    private fun parseSchemaGroups(ctx: Context): List<Map<String, Any>> {
        val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val schemaJson = prefs.getString("cached_status_groups", null) ?: return emptyList()
        return try {
            val schema: Map<String, Any> = gson.fromJson(schemaJson, object : TypeToken<Map<String, Any>>() {}.type)
            @Suppress("UNCHECKED_CAST")
            schema["publicGroupList"] as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves pinned favorite service links to Uptime Kuma monitor IDs, so status-page
     * outages can be scoped to favorites only. Matches by the pinned link's host against a
     * monitor's URL; falls back to [GROUP_NAME_FALLBACK] for services whose monitors don't
     * expose a public URL.
     */
    fun getFavoriteMonitorIds(ctx: Context): Set<Int> {
        val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val pinnedLinks = prefs.getStringSet("pinned_services", emptySet()) ?: emptySet()
        if (pinnedLinks.isEmpty()) return emptySet()

        val groups = parseSchemaGroups(ctx)
        val domainMap = mutableMapOf<String, MutableSet<Int>>()
        val groupMap = mutableMapOf<String, MutableSet<Int>>()
        for (group in groups) {
            val groupName = group["name"] as? String
            @Suppress("UNCHECKED_CAST")
            val monitorList = group["monitorList"] as? List<Map<String, Any>> ?: continue
            for (monitor in monitorList) {
                val id = (monitor["id"] as? Double)?.toInt() ?: continue
                if (groupName != null) groupMap.getOrPut(groupName) { mutableSetOf() }.add(id)
                val url = monitor["url"] as? String ?: continue
                val host = try { java.net.URI(url).host } catch (e: Exception) { null } ?: continue
                domainMap.getOrPut(host) { mutableSetOf() }.add(id)
            }
        }

        val result = mutableSetOf<Int>()
        for (link in pinnedLinks) {
            val host = try { java.net.URI(link).host } catch (e: Exception) { null } ?: continue
            val byDomain = domainMap[host]
            if (byDomain != null) {
                result.addAll(byDomain)
            } else {
                GROUP_NAME_FALLBACK[host]?.let { groupName -> groupMap[groupName]?.let { result.addAll(it) } }
            }
        }
        return result
    }

    /** Counts offline/pending monitors in [heartbeatJson], optionally restricted to [restrictToIds]. */
    private fun countOffline(heartbeatJson: String, restrictToIds: Set<Int>?): Int {
        var offlineCount = 0
        try {
            val heartbeatData: Map<String, Any> = gson.fromJson(heartbeatJson, object : TypeToken<Map<String, Any>>() {}.type)
            val list = heartbeatData["heartbeatList"] as? Map<String, List<Map<String, Any>>>
            list?.forEach { (monitorIdStr, heartbeats) ->
                if (restrictToIds != null && monitorIdStr.toIntOrNull() !in restrictToIds) return@forEach
                if (heartbeats.isNotEmpty()) {
                    val latest = heartbeats.last()
                    val status = (latest["status"] as? Double)?.toInt() ?: 3
                    if (status == 0 || status == 2) offlineCount++
                }
            }
        } catch (e: Exception) {}
        return offlineCount
    }

    private fun computeOfflineCount(ctx: Context, heartbeatJson: String): Int {
        val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val favoritesOnly = prefs.getBoolean("notify_favorites_only", false)
        val restrictToIds = if (favoritesOnly) getFavoriteMonitorIds(ctx) else null
        return countOffline(heartbeatJson, restrictToIds)
    }

    private var appContext: Context? = null

    suspend fun performFetchSuspend(context: Context? = appContext): Boolean = withContext(Dispatchers.IO) {
        val ctx = context?.applicationContext ?: return@withContext false
        appContext = ctx
        
        try {
            val schemaJson = URL("https://status.adminforge.de/api/status-page/adminforge").openStream().bufferedReader().use { it.readText() }
            // Heartbeat check is CRITICAL. If this fails, we can't determine status, so we must abort/retry.
            val heartbeatJson = URL("https://status.adminforge.de/api/status-page/heartbeat/adminforge").openStream().bufferedReader().use { it.readText() }

            val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("cached_status_groups", schemaJson)
            editor.putString("cached_status_heartbeats", heartbeatJson)
            editor.putInt("offline_status_count", computeOfflineCount(ctx, heartbeatJson))
            editor.apply()

            withContext(Dispatchers.Main) {
                listeners.forEach { it.onStatusUpdated() }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("StatusPoller", "Error fetching status (Suspend)", e)
            withContext(Dispatchers.Main) {
                listeners.forEach { it.onStatusUpdateFailed() }
            }
            return@withContext false
        }
    }

    private fun performFetch(context: Context? = appContext) {
        val ctx = context?.applicationContext ?: return
        appContext = ctx
        
        thread {
            try {
                val schemaJson = URL("https://status.adminforge.de/api/status-page/adminforge").openStream().bufferedReader().use { it.readText() }
                val heartbeatJson = URL("https://status.adminforge.de/api/status-page/heartbeat/adminforge").openStream().bufferedReader().use { it.readText() }

                val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("cached_status_groups", schemaJson)
                editor.putString("cached_status_heartbeats", heartbeatJson)
                editor.putInt("offline_status_count", computeOfflineCount(ctx, heartbeatJson))
                editor.apply()

                handler.post {
                    listeners.forEach { it.onStatusUpdated() }
                }
            } catch (e: Exception) {
                Log.e("StatusPoller", "Error fetching status", e)
                handler.post {
                    listeners.forEach { it.onStatusUpdateFailed() }
                }
            }
        }
    }
}
