package de.adminforge.app

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker {
    private const val GITEA_RELEASES_API = "https://git.adminforge.de/api/v1/repos/adminforge/android-app/releases?limit=1"
    private const val RAW_FILE_BASE = "https://git.adminforge.de/adminforge/android-app/raw/tag"
    
    // session flag to prevent redundant checks on every activity navigation
    private var hasCheckedThisSession = false
    
    fun checkSilently(context: Context) {
        thread {
            try {
                val json = fetchLatestVersionInfo() ?: return@thread
                val latestCode = json.optInt("versionCode", 0)
                val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                if (latestCode > currentCode) {
                    Log.d("UpdateChecker", "New update detected silently: Code $latestCode")
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Silent check failed", e)
            }
        }
    }

    fun checkOnStartup(context: Context) {
        if (hasCheckedThisSession) return
        hasCheckedThisSession = true
        
        thread {
            try {
                val json = fetchLatestVersionInfo() ?: return@thread
                val latestCode = json.optInt("versionCode", 0)
                val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                
                if (latestCode > currentCode) {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            showUpdateDialog(context, json)
                        } catch (e: Exception) {
                            Log.e("UpdateChecker", "Could not show update dialog on startup", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Startup check failed", e)
            }
        }
    }

    fun checkForUpdateInteractive(context: Context) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Prüfe auf Updates...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        thread {
            try {
                val json = fetchLatestVersionInfo()
                
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    if (json != null) {
                        val latestCode = json.optInt("versionCode", 0)
                        val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                        
                        if (latestCode > currentCode) {
                            showUpdateDialog(context, json)
                        } else {
                            Toast.makeText(context, "Du bist bereits auf dem neuesten Stand.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Fehler bei der Update-Prüfung", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Interactive check failed", e)
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Fehler bei der Update-Prüfung", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchLatestVersionInfo(): org.json.JSONObject? {
        return try {
            // 1. Get latest release from API
            val releasesJsonStr = fetchUrl(GITEA_RELEASES_API) ?: return null
            val releases = org.json.JSONArray(releasesJsonStr)
            if (releases.length() == 0) return null
            
            val latestRelease = releases.getJSONObject(0)
            val tagName = latestRelease.getString("tag_name")
            
            // 2. Fetch version.json from that specific tag
            val versionJsonUrl = "$RAW_FILE_BASE/$tagName/version.json"
            val versionJsonStr = fetchUrl(versionJsonUrl) ?: return null
            org.json.JSONObject(versionJsonStr)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error fetching version info", e)
            null
        }
    }

    private fun fetchUrl(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun showUpdateDialog(context: Context, json: org.json.JSONObject) {
        val versionName = json.optString("versionName", "Unbekannt")
        val downloadUrl = json.optString("downloadUrl", "")

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Neues Update verfügbar ($versionName)")
            .setMessage("Eine neue Version der adminForge App wurde gefunden. Möchtest du sie jetzt installieren?")
            .setPositiveButton("Installieren") { _, _ ->
                if (downloadUrl.isNotEmpty()) {
                    downloadAndInstall(context, downloadUrl)
                } else {
                    val releaseUrl = "https://git.adminforge.de/adminforge/android-app/releases"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                    context.startActivity(intent)
                }
            }
            .setNegativeButton("Später", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, downloadUrl: String) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Lade Update herunter...")
        progressDialog.setIndeterminate(false)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.max = 100
        progressDialog.setCancelable(false)
        progressDialog.show()

        thread {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val updateDir = File(context.getExternalFilesDir(null), "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                val apkFile = File(updateDir, "adminforge-update.apk")
                val output = FileOutputStream(apkFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        Handler(Looper.getMainLooper()).post {
                            progressDialog.progress = (total * 100 / fileLength).toInt()
                        }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Download failed", e)
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Download fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Install failed", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Installation fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
