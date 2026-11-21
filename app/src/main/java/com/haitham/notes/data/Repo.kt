package com.haitham.notes.data

import android.content.Context
import com.haitham.notes.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/** مستودع البيانات: مصدر واحد للأقسام والملاحظات داخل التطبيق. */
object Repo {
    lateinit var prefs: Prefs; private set
    lateinit var images: ImageStore; private set
    private lateinit var db: Db
    private lateinit var appCtx: Context

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    /** كل الصفوف بما فيها شواهد الحذف (للمزامنة) */
    private var allCats = listOf<Category>()
    private var allNotes = listOf<Note>()

    val categories: StateFlow<List<Category>> get() = _categories
    val notes: StateFlow<List<Note>> get() = _notes
    val changeCounter = AtomicLong(0)

    @Synchronized
    fun init(ctx: Context) {
        if (::db.isInitialized) return
        appCtx = ctx.applicationContext
        prefs = Prefs(appCtx)
        images = ImageStore(appCtx)
        db = Db(appCtx)
        allCats = db.categories()
        allNotes = db.notes()
        if (allCats.none { it.id == Category.GENERAL }) {
            val g = Category(id = Category.GENERAL, name = "عام", color = 0, icon = "inbox", updatedAt = 1)
            db.save(g); allCats = allCats + g
        }
        if (!prefs.seeded) {
            prefs.seeded = true
            if (allCats.size == 1) {
                listOf(Category(name = "كمبيوتر", color = 5, icon = "computer", order = 1),
                    Category(name = "موبايل", color = 3, icon = "phone", order = 2)).forEach { db.save(it); allCats = allCats + it }
            }
        }
        publish()
    }

    private fun publish() {
        _categories.value = allCats.filter { !it.deleted }
            .sortedWith(compareBy<Category> { it.id != Category.GENERAL }.thenBy { it.order }.thenBy { it.name })
        _notes.value = allNotes.filter { !it.deleted }
    }

    private fun changed() {
        changeCounter.incrementAndGet()
        prefs.dirty = true
        SyncScheduler.requestSoon(appCtx)
    }

    fun note(id: String): Note? = allNotes.firstOrNull { it.id == id && !it.deleted }
    fun category(id: String): Category? = allCats.firstOrNull { it.id == id && !it.deleted }

    @Synchronized
    fun saveNote(n: Note, touch: Boolean = true) {
        val saved = if (touch) n.copy(updatedAt = System.currentTimeMillis()) else n
        db.save(saved)
        allNotes = allNotes.filter { it.id != saved.id } + saved
        publish(); changed()
    }

    fun trash(id: String) = note(id)?.let { saveNote(it.copy(trashed = true, pinned = false)) }
    fun restore(id: String) = note(id)?.let {
        val cat = if (category(it.categoryId) == null) Category.GENERAL else it.categoryId
        saveNote(it.copy(trashed = false, categoryId = cat))
    }

    /** حذف نهائي: يبقى شاهد حذف بدون محتوى حتى تعرف الأجهزة الأخرى بالحذف */
    @Synchronized
    fun deleteForever(id: String) {
        val n = allNotes.firstOrNull { it.id == id } ?: return
        n.imageIds.forEach { images.delete(it) }
        saveNote(Note(id = n.id, categoryId = n.categoryId, title = "", blocks = listOf(Block.Text("")), createdAt = n.createdAt, deleted = true))
    }

    fun emptyTrash() = _notes.value.filter { it.trashed }.forEach { deleteForever(it.id) }

    @Synchronized
    fun saveCategory(c: Category) {
        val saved = c.copy(updatedAt = System.currentTimeMillis())
        db.save(saved)
        allCats = allCats.filter { it.id != saved.id } + saved
        publish(); changed()
    }

    fun addCategory(name: String, color: Int, icon: String): Category {
        val c = Category(name = name.trim(), color = color, icon = icon, order = (allCats.maxOfOrNull { it.order } ?: 0) + 1)
        saveCategory(c); return c
    }

    /** حذف قسم: نقل ملاحظاته إلى «عام» أو إلى سلة المحذوفات */
    @Synchronized
    fun deleteCategory(id: String, moveNotes: Boolean) {
        if (id == Category.GENERAL) return
        _notes.value.filter { it.categoryId == id }.forEach {
            saveNote(if (moveNotes) it.copy(categoryId = Category.GENERAL) else it.copy(trashed = true, categoryId = Category.GENERAL))
        }
        category(id)?.let { saveCategory(it.copy(deleted = true)) }
    }

    fun moveCategory(id: String, up: Boolean) {
        val list = _categories.value.filter { it.id != Category.GENERAL }.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        java.util.Collections.swap(list, i, j)
        list.forEachIndexed { idx, c -> if (c.order != idx + 1) saveCategory(c.copy(order = idx + 1)) }
    }

    // ---------- المزامنة ----------
    @Synchronized fun snapshot(): Pair<List<Category>, List<Note>> = allCats to allNotes

    /** يطبّق نتيجة الدمج: يكتب فقط ما هو أحدث من النسخة المحلية الحالية */
    @Synchronized
    fun applyMerged(cats: List<Category>, notes: List<Note>) {
        var any = false
        db.inTx {
            cats.forEach { r ->
                val l = allCats.firstOrNull { it.id == r.id }
                if (l == null || r.updatedAt > l.updatedAt) { db.save(r); allCats = allCats.filter { it.id != r.id } + r; any = true }
            }
            notes.forEach { r ->
                val l = allNotes.firstOrNull { it.id == r.id }
                if (l == null || r.updatedAt > l.updatedAt) {
                    if (r.deleted) l?.imageIds?.forEach { images.delete(it) }
                    db.save(r); allNotes = allNotes.filter { it.id != r.id } + r; any = true
                }
            }
        }
        if (any) publish()
    }

    /** الصور المستخدمة في أي ملاحظة موجودة (بما فيها السلة) */
    fun referencedImages(): Set<String> = allNotes.filter { !it.deleted }.flatMap { it.imageIds }.toSet()

    fun cleanupImages() {
        val used = referencedImages()
        images.allIds().filter { it !in used }.forEach { images.delete(it) }
    }
}
