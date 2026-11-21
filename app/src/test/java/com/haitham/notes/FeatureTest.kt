package com.haitham.notes

import com.haitham.notes.data.*
import com.haitham.notes.util.AppLock
import com.haitham.notes.util.PdfExport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class FeatureTest {
    private fun init() = Repo.init(RuntimeEnvironment.getApplication())

    @Test fun backupRoundTripAndWrongPassword() {
        init()
        val cat = Repo.addCategory("اختبار", 2, "star")
        Repo.images.putRawJpeg("imgX", byteArrayOf(1, 2, 3))
        val n = Note(categoryId = cat.id, title = "نسخة", blocks = listOf(Block.Text("نص"), Block.Image("imgX")))
        Repo.saveNote(n)
        val out = ByteArrayOutputStream()
        val r = LocalBackup.export(out, "1234")
        assertTrue(r.notes >= 1); assertTrue(r.images >= 1)

        try { LocalBackup.import(out.toByteArray().inputStream(), "9999"); fail() } catch (_: LocalBackup.WrongPassword) {}

        Repo.deleteForever(n.id)               // نحذفها ثم نستعيد
        assertNull(Repo.note(n.id))
        Repo.images.delete("imgX")
        LocalBackup.import(out.toByteArray().inputStream(), "1234")
        // الحذف أحدث من النسخة فيبقى محذوفاً (الأحدث يفوز)
        assertNull(Repo.note(n.id))
        val n2 = Note(categoryId = cat.id, title = "جديدة")
        val out2 = ByteArrayOutputStream()
        Repo.saveNote(n2); LocalBackup.export(out2, "abcd"); Repo.trash(n2.id)
        assertTrue(Repo.note(n2.id)!!.trashed)
    }

    @Test fun categoryPinAndAppPinFallback() {
        init()
        val (h, s) = AppLock.categoryLock("4321")
        val c = Category(name = "سري", pinHash = h, pinSalt = s)
        assertTrue(c.locked)
        assertTrue(AppLock.checkCategory(c, Repo.prefs, "4321"))
        assertFalse(AppLock.checkCategory(c, Repo.prefs, "0000"))
        AppLock.setPin(Repo.prefs, "7777")
        assertTrue(AppLock.checkCategory(c, Repo.prefs, "7777")) // رمز التطبيق كاحتياطي
        AppLock.setPin(Repo.prefs, null)
        // القفل ينتقل مع المزامنة
        val back = Category.fromJson(org.json.JSONObject(c.toJson().toString()))
        assertEquals(h, back.pinHash)
    }

    @Test fun pdfLayout() {
        init()
        val img = run {
            val bmp = android.graphics.Bitmap.createBitmap(600, 300, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).drawColor(0xFF7B5CFF.toInt())
            val o = ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o)
            newId().also { Repo.images.putRawJpeg(it, o.toByteArray()) }
        }
        val c = Category(name = "كمبيوتر")
        val notes = listOf(
            Note(title = "إعدادات الراوتر", blocks = listOf(Block.Text("IP: 192.168.88.1\nالمستخدم: admin"), Block.Image(img), Block.Text("تم تغيير كلمة المرور."))),
            Note(type = NoteType.CHECKLIST, title = "مهام", items = listOf(CheckItem("تحديث ويندوز", true), CheckItem("تنظيف الجهاز"))),
            Note(title = "نص طويل", blocks = listOf(Block.Text(List(80) { "هذا سطر تجريبي رقم ${it + 1} لاختبار تقسيم الصفحات" }.joinToString("\n")))),
        )
        val pages = mutableListOf<android.graphics.Bitmap>()
        PdfExport.render(object : PdfExport.PageSink {
            override fun start(no: Int, w: Int, h: Int): android.graphics.Canvas =
                android.graphics.Canvas(android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { pages += it })
            override fun finish() {}
        }, PdfExport.categoryBody(c, notes))
        assertTrue("يجب أن ينقسم إلى أكثر من صفحة", pages.size >= 2)
        val dir = java.io.File("build/pdf-preview").apply { mkdirs() }
        pages.forEachIndexed { i, p -> java.io.File(dir, "page${i + 1}.png").outputStream().use { p.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
