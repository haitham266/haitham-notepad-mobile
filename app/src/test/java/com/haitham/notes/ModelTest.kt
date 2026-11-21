package com.haitham.notes

import com.haitham.notes.data.*
import com.haitham.notes.util.Updater
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun noteJsonRoundTrip() {
        val n = Note(
            categoryId = "c1", title = "راوتر", color = 3, pinned = true,
            blocks = listOf(Block.Text("قبل"), Block.Image("img1"), Block.Text("بعد")),
        )
        val back = Note.fromJson(JSONObject(n.toJson().toString()))
        assertEquals(n.id, back.id); assertEquals("c1", back.categoryId); assertEquals("راوتر", back.title)
        assertEquals(listOf("img1"), back.imageIds)
        assertEquals("قبل\nبعد", back.plainText)
        assertTrue(back.pinned); assertEquals(3, back.color)
    }

    @Test fun checklistRoundTrip() {
        val n = Note(type = NoteType.CHECKLIST, items = listOf(CheckItem("a", true), CheckItem("b")))
        val back = Note.fromJson(JSONObject(n.toJson().toString()))
        assertEquals(NoteType.CHECKLIST, back.type)
        assertEquals(listOf("a" to true, "b" to false), back.items.map { it.text to it.checked })
    }

    @Test fun emptyNoteDetection() {
        assertTrue(Note().isEmpty)
        assertFalse(Note(blocks = listOf(Block.Image("x"))).isEmpty)
        assertFalse(Note(title = "t").isEmpty)
    }

    @Test fun mergeNewestWinsAndKeepsBoth() {
        val local = listOf(Note(id = "1", title = "old", updatedAt = 10), Note(id = "2", title = "only-local", updatedAt = 5))
        val remote = listOf(Note(id = "1", title = "new", updatedAt = 20), Note(id = "3", title = "only-remote", updatedAt = 7))
        val m = Merge.byId(local, remote, { it.id }, { it.updatedAt }).associateBy { it.id }
        assertEquals("new", m["1"]!!.title); assertEquals(3, m.size)
    }

    @Test fun mergeTombstoneBeatsOlderEdit() {
        val local = listOf(Note(id = "1", title = "edited", updatedAt = 10))
        val remote = listOf(Note(id = "1", deleted = true, updatedAt = 30))
        assertTrue(Merge.byId(local, remote, { it.id }, { it.updatedAt }).single().deleted)
        val newerLocal = listOf(Note(id = "1", title = "edited later", updatedAt = 40))
        assertFalse(Merge.byId(newerLocal, remote, { it.id }, { it.updatedAt }).single().deleted)
    }

    @Test fun categoryRoundTrip() {
        val c = Category(name = "موبايل", color = 4, icon = "phone")
        val b = Category.fromJson(JSONObject(c.toJson().toString()))
        assertEquals(c.copy(), b)
    }

    @Test fun versionCompare() {
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("", "1.0.0"))
    }
}
