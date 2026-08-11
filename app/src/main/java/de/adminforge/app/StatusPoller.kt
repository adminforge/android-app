package de.adminforge.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private fun fetchWithTimeout(urlStr: String): String {
    val connection = URL(urlStr).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    return connection.inputStream.bufferedReader().use { it.readText() }
}

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

    /**
     * [schemaJsonOverride] lets callers that just fetched a fresh schema (about to be persisted,
     * or not yet applied) use it directly instead of racing the SharedPreferences write.
     */
    private fun parseSchemaGroups(ctx: Context, schemaJsonOverride: String? = null): List<Map<String, Any>> {
        val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val schemaJson = schemaJsonOverride ?: prefs.getString("cached_status_groups", null) ?: return emptyList()
        return try {
            val schema: Map<String, Any> = gson.fromJson(schemaJson, object : TypeToken<Map<String, Any>>() {}.type)
            @Suppress("UNCHECKED_CAST")
            schema["publicGroupList"] as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves pinned favorite service links to Uptime Kuma monitor IDs, keyed by the pinned
     * link so each favorite can be judged as a single service rather than a flat pile of
     * monitors: a mail service with simultaneous IMAP+SMTP checks down should count as one
     * outage, not two, matching what the user actually pinned. Matches by the pinned link's
     * host against a monitor's URL; falls back to [GROUP_NAME_FALLBACK] for services whose
     * monitors don't expose a public URL.
     */
    fun getFavoriteMonitorIdsByLink(ctx: Context, schemaJsonOverride: String? = null): Map<String, Set<Int>> {
        val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val pinnedLinks = prefs.getStringSet("pinned_services", emptySet()) ?: emptySet()
        if (pinnedLinks.isEmpty()) return emptyMap()

        // The status-page JSON shape is server-controlled; a `List` check via `as?` doesn't
        // verify element types (generics are erased), so an unexpected element still throws
        // when treated as a Map below. Guard the whole resolution, matching parseSchemaGroups
        // and countOffline, so a malformed response can't crash the caller (e.g. the Status
        // screen's search box).
        return try {
            val groups = parseSchemaGroups(ctx, schemaJsonOverride)
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

            val result = mutableMapOf<String, Set<Int>>()
            for (link in pinnedLinks) {
                val host = try { java.net.URI(link).host } catch (e: Exception) { null } ?: continue
                val ids = domainMap[host] ?: GROUP_NAME_FALLBACK[host]?.let { groupMap[it] }
                if (ids != null) result[link] = ids
            }
            result
        } catch (e: Exception) {
            Log.e("StatusPoller", "Error resolving favorite monitor ids", e)
            emptyMap()
        }
    }

    /** Extracts each monitor's current status (0=down, 1=up, 2=pending, 3=unknown) from [heartbeatJson]. */
    private fun parseHeartbeatStatuses(heartbeatJson: String): Map<Int, Int> {
        return try {
            val heartbeatData: Map<String, Any> = gson.fromJson(heartbeatJson, object : TypeToken<Map<String, Any>>() {}.type)
            val list = heartbeatData["heartbeatList"] as? Map<String, List<Map<String, Any>>> ?: return emptyMap()
            list.mapNotNull { (idStr, heartbeats) ->
                val id = idStr.toIntOrNull() ?: return@mapNotNull null
                val status = heartbeats.lastOrNull()?.let { (it["status"] as? Double)?.toInt() } ?: 3
                id to status
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Counts monitors currently offline/pending, or - when [favoriteIdsByLink] is non-null and
     * non-empty - counts distinct pinned services with at least one offline/pending monitor
     * instead (service-level, not monitor-level). A null or empty [favoriteIdsByLink] falls
     * back to the unrestricted monitor count: if favorites-only is on but nothing is pinned (or
     * nothing pinned resolves to a monitor), silently reporting 0 would suppress every outage
     * notification, including firing a false "all clear" for an outage already in progress.
     */
    private fun countOffline(statusById: Map<Int, Int>, favoriteIdsByLink: Map<String, Set<Int>>?): Int {
        fun isDown(id: Int) = statusById[id].let { it == 0 || it == 2 }
        if (favoriteIdsByLink.isNullOrEmpty()) {
            return statusById.values.count { it == 0 || it == 2 }
        }
        return favoriteIdsByLink.values.count { ids -> ids.any(::isDown) }
    }

    private fun computeOfflineCount(ctx: Context, schemaJson: String, heartbeatJson: String): Int {
        val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        val favoritesOnly = prefs.getBoolean("notify_favorites_only", false)
        val favoriteIdsByLink = if (favoritesOnly) getFavoriteMonitorIdsByLink(ctx, schemaJson) else null
        return countOffline(parseHeartbeatStatuses(heartbeatJson), favoriteIdsByLink)
    }

    private var appContext: Context? = null

    suspend fun performFetchSuspend(context: Context? = appContext): Boolean = withContext(Dispatchers.IO) {
        val ctx = context?.applicationContext ?: return@withContext false
        appContext = ctx
        
        try {
            val schemaJson = fetchWithTimeout("https://status.adminforge.de/api/status-page/adminforge")
            // Heartbeat check is CRITICAL. If this fails, we can't determine status, so we must abort/retry.
            val heartbeatJson = fetchWithTimeout("https://status.adminforge.de/api/status-page/heartbeat/adminforge")

            val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("cached_status_groups", schemaJson)
            editor.putString("cached_status_heartbeats", heartbeatJson)
            editor.putInt("offline_status_count", computeOfflineCount(ctx, schemaJson, heartbeatJson))
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
                val schemaJson = fetchWithTimeout("https://status.adminforge.de/api/status-page/adminforge")
                val heartbeatJson = fetchWithTimeout("https://status.adminforge.de/api/status-page/heartbeat/adminforge")

                val prefs = ctx.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("cached_status_groups", schemaJson)
                editor.putString("cached_status_heartbeats", heartbeatJson)
                editor.putInt("offline_status_count", computeOfflineCount(ctx, schemaJson, heartbeatJson))
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
