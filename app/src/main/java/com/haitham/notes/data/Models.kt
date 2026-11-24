package com.haitham.notes.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "")

data class Category(
    val id: String = newId(),
    val name: String,
    val color: Int = 0,
    val icon: String = "folder",
    val order: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** قفل القسم: بصمة الرمز (PBKDF2) والملح، وليس الرمز نفسه */
    val pinHash: String? = null,
    val pinSalt: String? = null,
) {
    val locked: Boolean get() = pinHash != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("color", color).put("icon", icon)
        .put("order", order).put("updatedAt", updatedAt).put("deleted", deleted)
        .apply { if (pinHash != null) put("pinHash", pinHash).put("pinSalt", pinSalt) }

    companion object {
        const val GENERAL = "general"
        fun fromJson(o: JSONObject) = Category(
            id = o.getString("id"), name = o.optString("name"), color = o.optInt("color"),
            icon = o.optString("icon", "folder"), order = o.optInt("order"),
            updatedAt = o.optLong("updatedAt"), deleted = o.optBoolean("deleted"),
            pinHash = o.optString("pinHash").ifBlank { null }, pinSalt = o.optString("pinSalt").ifBlank { null },
        )
    }
}

/** محتوى الملاحظة: نصوص وصور متتابعة داخل الملاحظة نفسها */
sealed class Block {
    abstract val key: String
    data class Text(val text: String, override val key: String = newId()) : Block()
    data class Image(val imageId: String, override val key: String = newId()) : Block()
}

data class CheckItem(val text: String, val checked: Boolean = false, val key: String = newId())

enum class NoteType { TEXT, CHECKLIST }

data class Note(
    val id: String = newId(),
    val categoryId: String = Category.GENERAL,
    val type: NoteType = NoteType.TEXT,
    val title: String = "",
    val blocks: List<Block> = listOf(Block.Text("")),
    val items: List<CheckItem> = emptyList(),
    val color: Int = 0,
    val pinned: Boolean = false,
    val trashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** وقت التذكير بالمللي ثانية، 0 = بدون تذكير */
    val reminderAt: Long = 0,
) {
    val plainText: String
        get() = when (type) {
            NoteType.TEXT -> blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }.trim()
            NoteType.CHECKLIST -> items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
        }
    val imageIds: List<String> get() = blocks.filterIsInstance<Block.Image>().map { it.imageId }
    val isEmpty: Boolean
        get() = title.isBlank() && imageIds.isEmpty() &&
            (if (type == NoteType.TEXT) plainText.isBlank() else items.all { it.text.isBlank() })

    fun toJson(): JSONObject {
        val b = JSONArray()
        blocks.forEach {
            when (it) {
                is Block.Text -> b.put(JSONObject().put("t", "text").put("v", it.text))
                is Block.Image -> b.put(JSONObject().put("t", "img").put("v", it.imageId))
            }
        }
        val it2 = JSONArray()
        items.forEach { it2.put(JSONObject().put("v", it.text).put("c", it.checked)) }
        return JSONObject()
            .put("id", id).put("categoryId", categoryId).put("type", type.name).put("title", title)
            .put("blocks", b).put("items", it2).put("color", color).put("pinned", pinned)
            .put("trashed", trashed).put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)
            .put("reminderAt", reminderAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Note {
            val blocks = mutableListOf<Block>()
            o.optJSONArray("blocks")?.let { a ->
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    if (x.optString("t") == "img") blocks += Block.Image(x.optString("v"))
                    else blocks += Block.Text(x.optString("v"))
                }
            }
            if (blocks.isEmpty()) blocks += Block.Text("")
            val items = mutableListOf<CheckItem>()
            o.optJSONArray("items")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { items += CheckItem(it.optString("v"), it.optBoolean("c")) }
            }
            return Note(
                id = o.getString("id"), categoryId = o.optString("categoryId", Category.GENERAL),
                type = runCatching { NoteType.valueOf(o.optString("type")) }.getOrDefault(NoteType.TEXT),
                title = o.optString("title"), blocks = blocks, items = items, color = o.optInt("color"),
                pinned = o.optBoolean("pinned"), trashed = o.optBoolean("trashed"),
                createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"), deleted = o.optBoolean("deleted"),
                reminderAt = o.optLong("reminderAt"),
            )
        }
    }
}

/** دمج نسختين (محلية وسحابية): الأحدث تعديلاً يفوز، والمحذوف يبقى كشاهد حذف. */
object Merge {
    fun <T> byId(local: List<T>, remote: List<T>, id: (T) -> String, time: (T) -> Long): List<T> {
        val map = LinkedHashMap<String, T>()
        local.forEach { map[id(it)] = it }
        remote.forEach { r ->
            val l = map[id(r)]
            if (l == null || time(r) > time(l)) map[id(r)] = r
        }
        return map.values.toList()
    }
}
