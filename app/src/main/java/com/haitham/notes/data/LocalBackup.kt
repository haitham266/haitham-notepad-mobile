package com.haitham.notes.data

import android.util.Base64
import com.haitham.notes.crypto.BoxCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * نسخة احتياطية على الجوال بدون إنترنت: ملف .hnbak محمي بكلمة مرور.
 * داخل الملف: meta.json (غير مشفر: الملح والتاريخ)، data.bin وصور img_<id>.bin (كل منها مشفر AES-256-GCM).
 * يمكن استعادته على أي جهاز بكلمة المرور.
 */
object LocalBackup {
    data class Result(val notes: Int, val categories: Int, val images: Int)

    private fun key(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded

    fun export(out: OutputStream, password: String): Result {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val k = key(password, salt)
        val (cats, notes) = Repo.snapshot()
        val liveNotes = notes.filter { !it.deleted }
        val images = liveNotes.flatMap { it.imageIds }.distinct().filter { Repo.images.exists(it) }
        ZipOutputStream(out).use { z ->
            fun put(name: String, data: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
            put("meta.json", JSONObject().put("app", "HaithamNotes").put("v", 1).put("createdAt", System.currentTimeMillis())
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).put("notes", liveNotes.size).toString().toByteArray())
            val data = JSONObject()
                .put("categories", JSONArray().apply { cats.filter { !it.deleted }.forEach { put(it.toJson()) } })
                .put("notes", JSONArray().apply { liveNotes.forEach { put(it.toJson()) } })
            put("data.bin", BoxCrypto.seal(k, data.toString().toByteArray(Charsets.UTF_8)))
            images.forEach { id -> Repo.images.rawJpeg(id)?.let { put("img_$id.bin", BoxCrypto.seal(k, it)) } }
        }
        return Result(liveNotes.size, cats.count { !it.deleted }, images.size)
    }

    class WrongPassword : Exception("كلمة المرور غير صحيحة")
    class BadFile : Exception("الملف ليس نسخة احتياطية من Haitham Notes")

    /** الاستعادة تدمج مع الموجود (لا تحذف شيئاً، والأحدث يفوز) */
    fun import(input: InputStream, password: String): Result {
        var k: ByteArray? = null
        var cats = listOf<Category>(); var notes = listOf<Note>(); var imgs = 0
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val bytes = z.readBytes()
                when {
                    e.name == "meta.json" -> {
                        val m = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: throw BadFile()
                        if (m.optString("app") != "HaithamNotes") throw BadFile()
                        k = key(password, Base64.decode(m.getString("salt"), Base64.NO_WRAP))
                    }
                    e.name == "data.bin" -> {
                        val kk = k ?: throw BadFile()
                        val plain = try { BoxCrypto.open(kk, bytes) } catch (_: javax.crypto.AEADBadTagException) { throw WrongPassword() }
                        val o = JSONObject(String(plain, Charsets.UTF_8))
                        val ca = o.getJSONArray("categories"); val na = o.getJSONArray("notes")
                        cats = (0 until ca.length()).map { Category.fromJson(ca.getJSONObject(it)) }
                        notes = (0 until na.length()).map { Note.fromJson(na.getJSONObject(it)) }
                    }
                    e.name.startsWith("img_") -> {
                        val kk = k ?: throw BadFile()
                        val id = e.name.removePrefix("img_").removeSuffix(".bin")
                        if (!Repo.images.exists(id)) runCatching { Repo.images.putRawJpeg(id, BoxCrypto.open(kk, bytes)); imgs++ }
                    }
                }
            }
        }
        if (k == null) throw BadFile()
        Repo.importMerged(cats, notes)
        return Result(notes.size, cats.size, imgs)
    }
}
