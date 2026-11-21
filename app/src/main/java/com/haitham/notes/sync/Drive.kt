package com.haitham.notes.sync

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DriveException(val code: Int, msg: String) : Exception(msg)

data class DriveFile(val id: String, val name: String)

/** عميل Google Drive REST v3 خفيف (بدون مكتبات ضخمة). */
class Drive(private val token: String) {
    private val api = "https://www.googleapis.com/drive/v3"
    private val upload = "https://www.googleapis.com/upload/drive/v3"

    private fun call(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 60000
        if (method == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") }
        else c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        if (code !in 200..299) throw DriveException(code, "Drive $code: ${String(bytes).take(300)}")
        return bytes
    }

    private fun json(b: ByteArray) = JSONObject(String(b, Charsets.UTF_8))
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun about(): Pair<String, String> {
        val u = json(call("GET", "$api/about?fields=user(displayName,emailAddress)")).getJSONObject("user")
        return u.optString("emailAddress") to u.optString("displayName")
    }

    fun list(q: String, space: String = "drive"): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "$api/files?spaces=$space&pageSize=1000&fields=nextPageToken,files(id,name)&q=${enc(q)}" +
                (page?.let { "&pageToken=${enc(it)}" } ?: "")
            val o = json(call("GET", url))
            val a = o.optJSONArray("files")
            if (a != null) for (i in 0 until a.length()) a.getJSONObject(i).let { out += DriveFile(it.getString("id"), it.getString("name")) }
            page = o.optString("nextPageToken").ifBlank { null }
        } while (page != null)
        return out
    }

    fun ensureFolder(name: String): String {
        val safe = name.replace("'", "\\'")
        list("name = '$safe' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and 'root' in parents")
            .firstOrNull()?.let { return it.id }
        val meta = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
        return json(call("POST", "$api/files?fields=id", meta.toString().toByteArray(), "application/json")).getString("id")
    }

    fun filesIn(folderId: String) = list("'$folderId' in parents and trashed = false")
    fun appDataFiles() = list("trashed = false", space = "appDataFolder")

    fun download(id: String): ByteArray = call("GET", "$api/files/$id?alt=media")

    fun create(name: String, parent: String, data: ByteArray, mime: String = "application/octet-stream"): String {
        val boundary = "hn" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(parent))
        val out = ByteArrayOutputStream()
        out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
        out.write(data)
        out.write("\r\n--$boundary--".toByteArray())
        return json(call("POST", "$upload/files?uploadType=multipart&fields=id", out.toByteArray(), "multipart/related; boundary=$boundary")).getString("id")
    }

    fun update(id: String, data: ByteArray) { call("PATCH", "$upload/files/$id?uploadType=media", data) }

    fun delete(id: String) { runCatching { call("DELETE", "$api/files/$id") } }
}
