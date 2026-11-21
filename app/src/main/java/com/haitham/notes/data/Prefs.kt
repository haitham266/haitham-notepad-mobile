package com.haitham.notes.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.haitham.notes.crypto.LocalCrypto

enum class SortMode { UPDATED, CREATED, TITLE, COLOR }

class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("hn_prefs", Context.MODE_PRIVATE)

    var themeMode: Int get() = p.getInt("theme", 0); set(v) = p.edit().putInt("theme", v).apply()
    var grid: Boolean get() = p.getBoolean("grid", true); set(v) = p.edit().putBoolean("grid", v).apply()
    var sort: SortMode
        get() = runCatching { SortMode.valueOf(p.getString("sort", null)!!) }.getOrDefault(SortMode.UPDATED)
        set(v) = p.edit().putString("sort", v.name).apply()

    // القفل
    var pinHash: String? get() = p.getString("pin_hash", null); set(v) = p.edit().putString("pin_hash", v).apply()
    var pinSalt: String? get() = p.getString("pin_salt", null); set(v) = p.edit().putString("pin_salt", v).apply()
    var biometric: Boolean get() = p.getBoolean("bio", false); set(v) = p.edit().putBoolean("bio", v).apply()
    var secureScreen: Boolean get() = p.getBoolean("secure", false); set(v) = p.edit().putBoolean("secure", v).apply()

    // المزامنة
    var accountEmail: String? get() = p.getString("acc_email", null); set(v) = p.edit().putString("acc_email", v).apply()
    var accountName: String? get() = p.getString("acc_name", null); set(v) = p.edit().putString("acc_name", v).apply()
    var autoSync: Boolean get() = p.getBoolean("auto_sync", true); set(v) = p.edit().putBoolean("auto_sync", v).apply()
    var lastSync: Long get() = p.getLong("last_sync", 0); set(v) = p.edit().putLong("last_sync", v).apply()
    var lastError: String? get() = p.getString("last_err", null); set(v) = p.edit().putString("last_err", v).apply()
    var dirty: Boolean get() = p.getBoolean("dirty", false); set(v) = p.edit().putBoolean("dirty", v).apply()
    var seeded: Boolean get() = p.getBoolean("seeded", false); set(v) = p.edit().putBoolean("seeded", v).apply()

    /** مفتاح تشفير النسخ السحابية، محفوظ محلياً مشفّراً بمفتاح الجهاز */
    var boxKey: ByteArray?
        get() = p.getString("box_key", null)?.let {
            runCatching { LocalCrypto.decrypt(Base64.decode(it, Base64.NO_WRAP)) }.getOrNull()
        }
        set(v) = p.edit().putString("box_key", v?.let { Base64.encodeToString(LocalCrypto.encrypt(it), Base64.NO_WRAP) }).apply()
}
