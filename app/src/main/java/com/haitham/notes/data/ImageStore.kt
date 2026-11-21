package com.haitham.notes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.haitham.notes.crypto.LocalCrypto
import java.io.ByteArrayOutputStream
import java.io.File

/** الصور داخل الملاحظات: تُصغّر وتُضغط ثم تُحفظ مشفرة. */
class ImageStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "images").apply { mkdirs() }
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 8).toInt())) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun file(id: String) = File(dir, "$id.enc")
    fun exists(id: String) = file(id).exists()
    fun allIds(): List<String> = dir.listFiles()?.map { it.name.removeSuffix(".enc") } ?: emptyList()

    /** يستورد صورة من المعرض أو الكاميرا ويعيد معرّفها */
    fun import(uri: Uri, maxSide: Int = 1600): String? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })!!
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        val rot = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rot != 0f) m.postRotate(rot)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val id = newId()
        file(id).writeBytes(LocalCrypto.encrypt(out.toByteArray()))
        cache.put(id, bmp)
        id
    }.getOrNull()

    fun rawJpeg(id: String): ByteArray? = runCatching { LocalCrypto.decrypt(file(id).readBytes()) }.getOrNull()

    fun putRawJpeg(id: String, jpeg: ByteArray) = file(id).writeBytes(LocalCrypto.encrypt(jpeg))

    fun bitmap(id: String): Bitmap? {
        cache.get(id)?.let { return it }
        val raw = rawJpeg(id) ?: return null
        return BitmapFactory.decodeByteArray(raw, 0, raw.size)?.also { cache.put(id, it) }
    }

    fun delete(id: String) { cache.remove(id); file(id).delete() }

    /** نسخة غير مشفرة مؤقتة للمشاركة */
    fun exportForShare(id: String): File? {
        val raw = rawJpeg(id) ?: return null
        val d = File(ctx.cacheDir, "share").apply { mkdirs() }
        return File(d, "image_$id.jpg").apply { writeBytes(raw) }
    }
}
