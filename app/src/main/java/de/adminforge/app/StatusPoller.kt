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
        val heartbeatJson = prefs.getString("cached_status_heartbeats", null) ?: return 0
        
        var offlineCount = 0
        try {
            val heartbeatData: Map<String, Any> = gson.fromJson(heartbeatJson, object : TypeToken<Map<String, Any>>() {}.type)
            val list = heartbeatData["heartbeatList"] as? Map<String, List<Map<String, Any>>>
            list?.forEach { (_, heartbeats) ->
                if (heartbeats.isNotEmpty()) {
                    val latest = heartbeats.last()
                    val status = (latest["status"] as? Double)?.toInt() ?: 3
                    if (status == 0 || status == 2) offlineCount++
                }
            }
        } catch (e: Exception) {}
        return offlineCount
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

            var offlineCount = 0
            val heartbeatData: Map<String, Any> = gson.fromJson(heartbeatJson, object : TypeToken<Map<String, Any>>() {}.type)
            val list = heartbeatData["heartbeatList"] as? Map<String, List<Map<String, Any>>>
            list?.forEach { (_, heartbeats) ->
                if (heartbeats.isNotEmpty()) {
                    val latest = heartbeats.last()
                    val status = (latest["status"] as? Double)?.toInt() ?: 3
                    if (status == 0 || status == 2) offlineCount++
                }
            }
            
            editor.putInt("offline_status_count", offlineCount)
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

                var offlineCount = 0
                val heartbeatData: Map<String, Any> = gson.fromJson(heartbeatJson, object : TypeToken<Map<String, Any>>() {}.type)
                val list = heartbeatData["heartbeatList"] as? Map<String, List<Map<String, Any>>>
                list?.forEach { (_, heartbeats) ->
                    if (heartbeats.isNotEmpty()) {
                        val latest = heartbeats.last()
                        val status = (latest["status"] as? Double)?.toInt() ?: 3
                        if (status == 0 || status == 2) offlineCount++
                    }
                }
                
                editor.putInt("offline_status_count", offlineCount)
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
