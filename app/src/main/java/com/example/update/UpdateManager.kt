package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
    }

    // Update status states
    enum class UpdateState {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        DOWNLOADED,
        ERROR
    }

    var state by mutableStateOf(UpdateState.IDLE)
        private set

    var latestVersionName by mutableStateOf("")
        private set

    var changeLog by mutableStateOf("")
        private set

    var downloadUrl by mutableStateOf("")
        private set

    var downloadProgress by mutableStateOf(0f)
        private set

    var errorMessage by mutableStateOf("")
        private set

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Checks a GitHub repository's latest release for updates.
     */
    fun checkForUpdates(owner: String, repo: String, currentVersion: String) {
        if (state == UpdateState.CHECKING || state == UpdateState.DOWNLOADING) return

        state = UpdateState.CHECKING
        errorMessage = ""

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchLatestReleaseInfo(owner, repo)
                }

                if (result != null) {
                    val (tag, body, apkUrl) = result
                    latestVersionName = tag
                    changeLog = decodeJsonString(body)
                    downloadUrl = apkUrl

                    if (isNewerVersion(tag, currentVersion)) {
                        state = UpdateState.UPDATE_AVAILABLE
                    } else {
                        state = UpdateState.UP_TO_DATE
                    }
                } else {
                    errorMessage = "No releases found or couldn't parse release info."
                    state = UpdateState.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                errorMessage = "Failed to connect: ${e.localizedMessage}"
                state = UpdateState.ERROR
            }
        }
    }

    /**
     * Downloads and initiates the installation of the latest APK.
     */
    fun startUpdateDownload() {
        val url = downloadUrl
        if (url.isEmpty()) {
            errorMessage = "Download URL is empty"
            state = UpdateState.ERROR
            return
        }

        state = UpdateState.DOWNLOADING
        downloadProgress = 0f

        scope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApkFile(url) { progress ->
                        scope.launch {
                            downloadProgress = progress
                        }
                    }
                }

                if (apkFile != null && apkFile.exists()) {
                    state = UpdateState.DOWNLOADED
                    installApk(apkFile)
                } else {
                    errorMessage = "Failed to save update file."
                    state = UpdateState.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update", e)
                errorMessage = "Download failed: ${e.localizedMessage}"
                state = UpdateState.ERROR
            }
        }
    }

    /**
     * Initiates installation of an already downloaded APK file.
     */
    fun installApk(file: File) {
        try {
            // Check for Unknown App Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    errorMessage = "Please grant permission to install updates from this app, then try again."
                    state = UpdateState.ERROR
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            errorMessage = "Installation failed to start: ${e.localizedMessage}"
            state = UpdateState.ERROR
        }
    }

    /**
     * Resets the update manager state.
     */
    fun resetState() {
        state = UpdateState.IDLE
        downloadProgress = 0f
        errorMessage = ""
    }

    private fun fetchLatestReleaseInfo(owner: String, repo: String): Triple<String, String, String>? {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "BrezzaCarLink-Update-Manager")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Parse tag_name
                val tagMatch = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)
                val tagName = tagMatch?.groupValues?.get(1) ?: return null

                // Parse release body (changelog)
                val bodyMatch = "\"body\"\\s*:\\s*\"([^\"]+?)\"\\s*[,}]".toRegex().find(json)
                val body = bodyMatch?.groupValues?.get(1) ?: "No description provided."

                // Parse browser_download_url that targets an APK
                val apkUrlMatch = "\"browser_download_url\"\\s*:\\s*\"([^\"]+?\\.apk)\"".toRegex().find(json)
                val apkUrl = apkUrlMatch?.groupValues?.get(1) ?: ""

                if (apkUrl.isEmpty()) {
                    // Fallback to searching any browser_download_url if no .apk ending is strict
                    val fallbackMatch = "\"browser_download_url\"\\s*:\\s*\"([^\"]+?)\"".toRegex().find(json)
                    val fallbackUrl = fallbackMatch?.groupValues?.get(1) ?: ""
                    return Triple(tagName, body, fallbackUrl)
                }

                return Triple(tagName, body, apkUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch from URL: $apiUrl", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun downloadApkFile(downloadUrl: String, onProgressUpdate: (Float) -> Unit): File? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val fileLength = connection.contentLength
            val input = BufferedInputStream(connection.inputStream)
            
            // Save to internal cache directory / files directory to bypass permission issues
            val outputDir = File(context.cacheDir, "updates")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val apkFile = File(outputDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val output = FileOutputStream(apkFile)
            val data = ByteArray(1024)
            var total: Long = 0
            var count: Int
            
            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgressUpdate(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            return apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading file", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val cleanLatest = latestTag.replace(Regex("[^0-9.]"), "")
        val cleanCurrent = currentVersion.replace(Regex("[^0-9.]"), "")

        val latestParts = cleanLatest.split(".")
        val currentParts = cleanCurrent.split(".")

        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val latestVal = if (i < latestParts.size) latestParts[i].toIntOrNull() ?: 0 else 0
            val currentVal = if (i < currentParts.size) currentParts[i].toIntOrNull() ?: 0 else 0

            if (latestVal > currentVal) return true
            if (latestVal < currentVal) return false
        }
        return false
    }

    private fun decodeJsonString(encoded: String): String {
        return try {
            encoded
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        } catch (e: Exception) {
            encoded
        }
    }
}
