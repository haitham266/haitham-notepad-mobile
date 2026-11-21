package com.haitham.notes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.haitham.notes.crypto.LocalCrypto
import org.json.JSONObject

/** قاعدة بيانات محلية: كل صف محفوظ كـ JSON مشفّر (AES-GCM / Keystore). */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "notes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, updated INTEGER NOT NULL, data BLOB NOT NULL)")
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY, updated INTEGER NOT NULL, data BLOB NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun readAll(table: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT data FROM $table", null).use { c ->
            while (c.moveToNext()) {
                runCatching { out += JSONObject(LocalCrypto.decryptString(c.getBlob(0))) }
            }
        }
        return out
    }

    private fun write(table: String, id: String, updated: Long, json: JSONObject) {
        val v = ContentValues().apply {
            put("id", id); put("updated", updated); put("data", LocalCrypto.encryptString(json.toString()))
        }
        writableDatabase.insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun categories() = readAll("categories").map { Category.fromJson(it) }
    fun notes() = readAll("notes").map { Note.fromJson(it) }
    fun save(c: Category) = write("categories", c.id, c.updatedAt, c.toJson())
    fun save(n: Note) = write("notes", n.id, n.updatedAt, n.toJson())

    fun inTx(block: () -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { block(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}
