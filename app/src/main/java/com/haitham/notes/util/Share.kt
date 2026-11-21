package com.haitham.notes.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.haitham.notes.data.Note
import com.haitham.notes.data.Repo

object Share {
    /** إرسال الملاحظة (النص + الصور) إلى أي تطبيق: واتساب، تلجرام، بريد… */
    fun note(ctx: Context, n: Note) {
        val text = buildString {
            if (n.title.isNotBlank()) append(n.title).append("\n\n")
            append(n.plainText)
        }.trim()
        val uris = ArrayList<Uri>()
        n.imageIds.forEach { id ->
            Repo.images.exportForShare(id)?.let { uris += FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) }
        }
        val i = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
            uris.size == 1 -> Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uris[0])
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        if (text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, text)
        if (n.title.isNotBlank()) i.putExtra(Intent.EXTRA_SUBJECT, n.title)
        if (uris.isNotEmpty()) {
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        }
        try { ctx.startActivity(Intent.createChooser(i, "إرسال الملاحظة")) }
        catch (_: Exception) { Toast.makeText(ctx, "لا يوجد تطبيق للإرسال", Toast.LENGTH_SHORT).show() }
    }

    fun copy(ctx: Context, n: Note) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(n.title, (n.title + "\n" + n.plainText).trim()))
        Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
    }

    fun open(ctx: Context, url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }
}
