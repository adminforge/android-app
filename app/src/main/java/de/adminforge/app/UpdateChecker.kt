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
    private const val VERSION_JSON_URL = "https://git.adminforge.de/adminforge/android-app/raw/branch/main/version.json"
    private const val PREFS_NAME = "adminforge_prefs"
    
    // session flag to prevent redundant checks on every activity navigation
    private var hasCheckedThisSession = false
    
    // Background check
    fun checkSilently(context: Context) {
        thread {
            try {
                val jsonStr = fetchUrl(VERSION_JSON_URL) ?: return@thread
                val json = org.json.JSONObject(jsonStr)
                val latestCode = json.optInt("version_code", 0)
                
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
                val jsonStr = fetchUrl(VERSION_JSON_URL) ?: return@thread
                val json = org.json.JSONObject(jsonStr)
                val latestCode = json.optInt("version_code", 0)
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

    // Interactive check when user clicks "Auf Updates prüfen"
    fun checkForUpdateInteractive(context: Context) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Prüfe auf Updates...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        thread {
            try {
                val jsonStr = fetchUrl(VERSION_JSON_URL)
                
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    if (jsonStr != null) {
                        val json = org.json.JSONObject(jsonStr)
                        val latestCode = json.optInt("version_code", 0)
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
        val versionName = json.optString("version_name", "Unbekannt")
        AlertDialog.Builder(context)
            .setTitle("Neues Update verfügbar ($versionName)")
            .setMessage("Eine neue Version der adminForge App wurde gefunden. Möchtest du sie jetzt herunterladen und installieren?")
            .setPositiveButton("Aktualisieren") { _, _ ->
                downloadAndInstall(context, json)
            }
            .setNegativeButton("Später", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, json: org.json.JSONObject) {
        val downloadUrl = json.optString("download_url", "")
        if (downloadUrl.isEmpty()) return

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Lade Update herunter...")
        progressDialog.isIndeterminate = false
        progressDialog.setMax(100)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setCancelable(false)
        progressDialog.show()

        thread {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.connect()
                
                val fileLength = connection.contentLength
                
                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                
                val apkFile = File(updateDir, "adminforge-latest.apk")
                if (apkFile.exists()) apkFile.delete()
                
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)
                
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                
                while (inputStream.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        Handler(Looper.getMainLooper()).post {
                            progressDialog.progress = progress
                        }
                    }
                    outputStream.write(data, 0, count)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    installApk(context, apkFile)
                }
                
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Download failed", e)
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Fehler beim Herunterladen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Install failed", e)
            Toast.makeText(context, "Fehler beim Starten der Installation", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
