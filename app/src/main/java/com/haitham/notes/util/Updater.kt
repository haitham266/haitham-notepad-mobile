package com.haitham.notes.util

import com.haitham.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

/** يفحص آخر إصدار منشور في GitHub Releases ويقارنه بالإصدار الحالي. */
object Updater {
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (BuildConfig.UPDATE_URL.isBlank()) return@withContext null
        try {
            val c = URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return@withContext null
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = o.optString("tag_name").removePrefix("v")
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null
            val assets = o.optJSONArray("assets")
            var apk = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk") && !name.contains("debug")) { apk = a.optString("browser_download_url"); break }
            }
            UpdateInfo(tag, o.optString("body"), apk, o.optString("html_url"))
        } catch (_: Exception) { null }
    }

    /** مقارنة أرقام الإصدارات مثل 1.2.10 > 1.2.9 */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote); val c = parts(current)
        if (remote.isBlank()) return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }; val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
