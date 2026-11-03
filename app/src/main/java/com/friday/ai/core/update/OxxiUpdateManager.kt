package com.friday.ai.core.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases and installs the signed APK without making the owner hunt for it. */
object OxxiUpdateManager {
    private const val RELEASES_URL = "https://api.github.com/repos/ivanduharte23-svg/Oxxi-mobile/releases/latest"
    private const val APK_NAME = "Oxxi.apk"

    data class UpdateInfo(val versionName: String, val downloadUrl: String, val releaseUrl: String)

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Oxxi-Android")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").removePrefix("v").trim()
                if (compareVersions(tag, currentVersionName(context)) <= 0) return@withContext null
                val asset = json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").equals(APK_NAME, ignoreCase = true) }
                } ?: return@withContext null
                val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                UpdateInfo(tag, url, json.optString("html_url"))
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Downloads the release inside Oxxi and opens Android's trusted package installer. */
    suspend fun downloadAndInstall(context: Context, update: UpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    error("Permite a Oxxi instalar actualizaciones y vuelve a intentarlo.")
                }

                val manager = context.getSystemService(DownloadManager::class.java)
                    ?: error("El gestor de descargas de Android no está disponible.")
                val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                    .setTitle("Actualizando Oxxi")
                    .setDescription("Descargando Oxxi ${update.versionName}")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(
                        context, Environment.DIRECTORY_DOWNLOADS, "Oxxi-update-${update.versionName}.apk"
                    )
                val id = manager.enqueue(request)
                val uri = waitForDownload(manager, id)
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(install)
            }
        }

    private suspend fun waitForDownload(manager: DownloadManager, id: Long): Uri {
        repeat(240) {
            val cursor = manager.query(DownloadManager.Query().setFilterById(id))
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL ->
                            return manager.getUriForDownloadedFile(id)
                                ?: error("Android no encontró el archivo descargado.")
                        DownloadManager.STATUS_FAILED -> error("Android no pudo descargar la actualización.")
                    }
                }
            }
            delay(500)
        }
        error("La descarga de Oxxi tardó demasiado.")
    }

    private fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfoCompat(context.packageName).versionName.orEmpty()

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun PackageManager.getPackageInfoCompat(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, 0)
        }
}
