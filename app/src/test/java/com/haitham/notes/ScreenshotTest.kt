package com.haitham.notes

import android.graphics.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.haitham.notes.data.*
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.Navigator
import com.haitham.notes.ui.UiState
import com.haitham.notes.ui.screens.*
import com.haitham.notes.ui.theme.HnTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** يولّد لقطات الشاشة في docs/screenshots: ./gradlew recordRoborazziDebug */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun fakeImage(c1: Int, c2: Int): String {
        val b = Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 800f, 450f, c1, c2, Shader.TileMode.CLAMP) })
        val p = Paint().apply { color = Color.WHITE; alpha = 220; isAntiAlias = true }
        cv.drawRoundRect(RectF(250f, 120f, 550f, 330f), 30f, 30f, p)
        p.color = c1; cv.drawCircle(400f, 225f, 50f, p)
        val out = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val id = newId(); Repo.images.putRawJpeg(id, out.toByteArray()); return id
    }

    @Before fun seed() {
        Repo.init(RuntimeEnvironment.getApplication())
        if (Repo.notes.value.any { it.title == "إعدادات الراوتر" }) return
        val cats = Repo.categories.value
        val pc = cats.first { it.name == "كمبيوتر" }.id
        val mob = cats.first { it.name == "موبايل" }.id
        val work = Repo.addCategory("عمل", 1, "work").id
        val (h, salt) = com.haitham.notes.util.AppLock.categoryLock("1234")
        val secret = Repo.addCategory("كلمات المرور", 3, "lock") { it.copy(pinHash = h, pinSalt = salt) }.id
        Repo.saveNote(Note(categoryId = secret, title = "حساب البنك", blocks = listOf(Block.Text("سري"))))
        val img = fakeImage(0xFF2F80ED.toInt(), 0xFF7B5CFF.toInt())
        Repo.saveNote(Note(categoryId = pc, title = "إعدادات الراوتر", color = 5, pinned = true,
            blocks = listOf(Block.Text("IP: 192.168.88.1\nالمستخدم: admin"), Block.Image(img), Block.Text("تم تغيير كلمة مرور الواي فاي يوم السبت."))))
        Repo.saveNote(Note(categoryId = pc, title = "اختصارات ويندوز", color = 1,
            blocks = listOf(Block.Text("Win + E  فتح المستكشف\nWin + D  سطح المكتب\nCtrl + Shift + Esc  مدير المهام"))))
        Repo.saveNote(Note(categoryId = mob, type = NoteType.CHECKLIST, title = "تطبيقات للتثبيت", color = 7,
            items = listOf(CheckItem("واتساب", true), CheckItem("تلجرام", true), CheckItem("Haitham Notes"), CheckItem("محرر صور"))))
        Repo.saveNote(Note(categoryId = mob, title = "صيانة سامسونج A52", color = 3,
            blocks = listOf(Block.Text("تغيير البطارية + تنظيف منفذ الشحن. موعد الاستلام الخميس."))))
        Repo.saveNote(Note(categoryId = work, title = "أفكار المشروع", color = 4,
            blocks = listOf(Block.Text("• إضافة تقارير شهرية\n• نسخ احتياطي تلقائي\n• تصميم شعار جديد"))))
        Repo.saveNote(Note(categoryId = Category.GENERAL, title = "", color = 6,
            blocks = listOf(Block.Text("موعد مع الطبيب الأحد الساعة 4 عصراً"))))
    }

    private fun show(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        val nav = Navigator()
        rule.setContent { HnTheme(if (dark) 2 else 1) { CompositionLocalProvider(LocalNav provides nav) { content() } } }
        rule.waitForIdle()
    }

    @Test fun s01_home() { UiState.selectedCategory = null; show { HomeScreen() }; rule.onRoot().captureRoboImage("../docs/screenshots/01-home.png") }

    @Test fun s02_category() {
        UiState.selectedCategory = Repo.categories.value.first { it.name == "كمبيوتر" }.id
        show { HomeScreen() }; rule.onRoot().captureRoboImage("../docs/screenshots/02-category.png")
        UiState.selectedCategory = null
    }

    @Test fun s03_editor() {
        val n = Repo.notes.value.first { it.title == "إعدادات الراوتر" }
        show { EditorScreen(n.id) }; rule.onRoot().captureRoboImage("../docs/screenshots/03-editor.png")
    }

    @Test fun s04_newNote() {
        show { NewNoteSheet(Repo.categories.value, Repo.categories.value[1].id, {}) { _, _ -> } }
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/04-new-note.png")
    }

    @Test fun s05_checklistDark() {
        val n = Repo.notes.value.first { it.type == NoteType.CHECKLIST }
        show(dark = true) { EditorScreen(n.id) }; rule.onRoot().captureRoboImage("../docs/screenshots/05-checklist-dark.png")
    }

    @Test fun s06_categories() { show { CategoriesScreen() }; rule.onRoot().captureRoboImage("../docs/screenshots/06-categories.png") }

    @Test fun s07_settings() { show { SettingsScreen {} }; rule.onRoot().captureRoboImage("../docs/screenshots/07-settings.png") }

    @Test fun s09_lockedCategory() {
        UiState.unlocked.clear()
        UiState.selectedCategory = Repo.categories.value.first { it.locked }.id
        show { HomeScreen() }
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/09-locked-category.png")
        UiState.selectedCategory = null
    }

    @Test fun s08_homeDark() { UiState.selectedCategory = null; show(dark = true) { HomeScreen() }; rule.onRoot().captureRoboImage("../docs/screenshots/08-home-dark.png") }
}
