package com.haitham.notes.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.haitham.notes.BuildConfig
import com.haitham.notes.data.Block
import com.haitham.notes.data.Category
import com.haitham.notes.data.Note
import com.haitham.notes.data.NoteType
import com.haitham.notes.data.Repo
import java.io.OutputStream

/** تصدير ملاحظة أو قسم كامل إلى PDF (نص عربي + صور). */
object PdfExport {
    private const val W = 595; private const val H = 842; private const val M = 40f
    private val contentW get() = (W - 2 * M).toInt()

    /** وجهة الرسم: ملف PDF، أو صور في الاختبارات */
    interface PageSink {
        fun start(no: Int, w: Int, h: Int): Canvas
        fun finish()
    }

    private class PdfSink(val doc: PdfDocument) : PageSink {
        var page: PdfDocument.Page? = null
        override fun start(no: Int, w: Int, h: Int): Canvas =
            doc.startPage(PdfDocument.PageInfo.Builder(w, h, no).create()).also { page = it }.canvas
        override fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }

    class Writer(private val sink: PageSink) {
        private var c: Canvas? = null
        var y = M
        var pageNo = 0
        val bottom = H - M - 20f
        val canvas: Canvas get() = c!!
        val open get() = c != null

        fun newPage() {
            finishPage()
            pageNo++
            c = sink.start(pageNo, W, H)
            canvas.drawColor(Color.WHITE)
            y = M
        }

        fun finishPage() {
            val cv = c ?: return
            val f = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.CENTER }
            cv.drawText("Haitham Notes — برمجة ${BuildConfig.DEVELOPER_NAME} ${BuildConfig.DEVELOPER_PHONE} — $pageNo", W / 2f, H - 20f, f)
            sink.finish(); c = null
        }

        fun ensure(h: Float) { if (c == null || y + h > bottom) newPage() }

        fun text(t: String, size: Float, color: Int = Color.BLACK, bold: Boolean = false, gapAfter: Float = 6f) {
            if (t.isEmpty()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, contentW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.25f).build()
            var line = 0
            while (line < layout.lineCount) {
                ensure(layout.getLineBottom(line) - layout.getLineTop(line).toFloat())
                val top = layout.getLineTop(line)
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= bottom) last++
                val h = (layout.getLineBottom(last) - top).toFloat()
                canvas.save()
                canvas.clipRect(M, y, W - M, y + h)
                canvas.translate(M, y - top)
                layout.draw(canvas)
                canvas.restore()
                y += h
                line = last + 1
                if (line < layout.lineCount) newPage()
            }
            y += gapAfter
        }

        fun image(id: String) {
            val bmp = Repo.images.bitmap(id) ?: return
            var w = contentW.toFloat(); var h = w * bmp.height / bmp.width
            val maxH = 420f
            if (h > maxH) { w = w * maxH / h; h = maxH }
            ensure(h + 8)
            val left = W - M - w // محاذاة لليمين
            canvas.drawBitmap(bmp, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 10
        }

        fun divider() {
            ensure(16f)
            canvas.drawLine(M, y + 6, W - M, y + 6, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            y += 18
        }
    }

    private fun Writer.note(n: Note, cat: Category?) {
        ensure(40f)
        if (n.title.isNotBlank()) text(n.title, 18f, bold = true, gapAfter = 2f)
        text("${cat?.name ?: "عام"} • ${Fmt.full(n.updatedAt)}", 9f, Color.GRAY, gapAfter = 10f)
        if (n.type == NoteType.CHECKLIST) {
            n.blocks.filterIsInstance<Block.Image>().forEach { image(it.imageId) }
            n.items.filter { it.text.isNotBlank() }.forEach { text((if (it.checked) "☑ " else "☐ ") + it.text, 12f, gapAfter = 2f) }
        } else n.blocks.forEach {
            when (it) {
                is Block.Text -> text(it.text.trimEnd(), 12f)
                is Block.Image -> image(it.imageId)
            }
        }
    }

    fun exportNote(n: Note, out: OutputStream) = write(out, noteBody(n))
    fun exportCategory(c: Category?, notes: List<Note>, out: OutputStream) = write(out, categoryBody(c, notes))

    fun render(sink: PageSink, body: Writer.() -> Unit) {
        val w = Writer(sink)
        w.body()
        if (w.pageNo == 0) w.newPage()
        w.finishPage()
    }

    private fun write(out: OutputStream, body: Writer.() -> Unit) {
        val doc = PdfDocument()
        try { render(PdfSink(doc), body); doc.writeTo(out) } finally { doc.close() }
    }

    fun noteBody(n: Note): Writer.() -> Unit = { note(n, Repo.category(n.categoryId)) }
    fun categoryBody(c: Category?, notes: List<Note>): Writer.() -> Unit = {
        newPage()
        text(if (c == null) "كل الملاحظات" else "قسم: ${c.name}", 24f, bold = true, gapAfter = 2f)
        text("${notes.size} ملاحظة • ${Fmt.full(System.currentTimeMillis())}", 10f, Color.GRAY, gapAfter = 12f)
        notes.forEachIndexed { i, n -> if (i > 0) divider(); note(n, Repo.category(n.categoryId)) }
    }

    fun fileName(base: String) = (base.ifBlank { "note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40)) + ".pdf"

    /** إنشاء PDF مؤقت للمشاركة */
    fun shareFile(ctx: Context, name: String, body: (OutputStream) -> Unit): java.io.File {
        val d = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        return java.io.File(d, fileName(name)).also { f -> f.outputStream().use(body) }
    }
}
