package com.haitham.notes.sync

import android.content.Context
import android.util.Base64
import com.haitham.notes.BuildConfig
import com.haitham.notes.crypto.BoxCrypto
import com.haitham.notes.data.Category
import com.haitham.notes.data.Merge
import com.haitham.notes.data.Note
import com.haitham.notes.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class SyncState {
    data object Idle : SyncState()
    data object Running : SyncState()
    data class Done(val at: Long) : SyncState()
    data class Failed(val message: String) : SyncState()
}

/**
 * المزامنة مع Google Drive داخل مجلد باسم التطبيق:
 *   Haitham Notes/notes.hnb       ← كل الأقسام والملاحظات (مضغوطة ومشفرة AES-256-GCM)
 *   Haitham Notes/img_<id>.hnb    ← الصور (مشفرة)
 *   appDataFolder/key.json        ← مفتاح التشفير (مخفي، لا يصل إليه إلا التطبيق)
 * الدمج: لكل عنصر يفوز الأحدث تعديلاً، فتعمل المزامنة بين عدة أجهزة.
 */
object SyncManager {
    val state = MutableStateFlow<SyncState>(SyncState.Idle)
    private val mutex = Mutex()
    private const val DATA_FILE = "notes.hnb"
    private const val KEY_FILE = "key.json"
    private const val IMG_PREFIX = "img_"
    private const val IMG_SUFFIX = ".hnb"

    suspend fun sync(ctx: Context, token: String): Result<Unit> = mutex.withLock {
        state.value = SyncState.Running
        val r = withContext(Dispatchers.IO) { runCatching { doSync(token) } }
        val prefs = Repo.prefs
        r.onSuccess {
            prefs.lastSync = System.currentTimeMillis(); prefs.lastError = null
            state.value = SyncState.Done(prefs.lastSync)
        }.onFailure {
            val msg = friendly(it)
            prefs.lastError = msg
            state.value = SyncState.Failed(msg)
        }
        r
    }

    private fun friendly(t: Throwable): String = when {
        t is DriveException && t.code == 401 -> "انتهت صلاحية الدخول، سجّل الدخول من جديد"
        t is DriveException && t.code == 403 -> "رفض Google Drive الطلب (تحقق من تفعيل Drive API)"
        t is javax.crypto.AEADBadTagException -> "تعذّر فك تشفير النسخة السحابية"
        t is java.io.IOException -> "لا يوجد اتصال بالإنترنت"
        else -> t.message ?: "خطأ غير معروف"
    }

    private fun doSync(token: String) {
        val drive = Drive(token)
        val startCounter = Repo.changeCounter.get()

        // الحساب
        runCatching { drive.about() }.getOrNull()?.let { (email, name) ->
            if (email.isNotBlank()) Repo.prefs.accountEmail = email
            if (name.isNotBlank()) Repo.prefs.accountName = name
        }

        val key = ensureKey(drive)
        val folder = drive.ensureFolder(BuildConfig.DRIVE_FOLDER)
        val remoteFiles = drive.filesIn(folder)
        val dataFile = remoteFiles.firstOrNull { it.name == DATA_FILE }

        // 1) تنزيل ودمج
        val (localCats, localNotes) = Repo.snapshot()
        var cats = localCats; var notes = localNotes
        if (dataFile != null) {
            val o = JSONObject(gunzip(BoxCrypto.open(key, drive.download(dataFile.id))).toString(Charsets.UTF_8))
            val rc = o.optJSONArray("categories").toList { Category.fromJson(it) }
            val rn = o.optJSONArray("notes").toList { Note.fromJson(it) }
            cats = Merge.byId(localCats, rc, { it.id }, { it.updatedAt })
            notes = Merge.byId(localNotes, rn, { it.id }, { it.updatedAt })
            Repo.applyMerged(cats, notes)
        }

        // 2) الصور: رفع الناقص وتنزيل الناقص
        val remoteImgs = remoteFiles.filter { it.name.startsWith(IMG_PREFIX) }
            .associateBy { it.name.removePrefix(IMG_PREFIX).removeSuffix(IMG_SUFFIX) }
        val needed = notes.filter { !it.deleted }.flatMap { it.imageIds }.toSet()
        for (id in needed) {
            val local = Repo.images.exists(id)
            val remote = remoteImgs[id]
            if (local && remote == null) {
                Repo.images.rawJpeg(id)?.let { drive.create("$IMG_PREFIX$id$IMG_SUFFIX", folder, BoxCrypto.seal(key, it)) }
            } else if (!local && remote != null) {
                runCatching { Repo.images.putRawJpeg(id, BoxCrypto.open(key, drive.download(remote.id))) }
            }
        }

        // 3) رفع النسخة المدمجة
        val out = JSONObject()
            .put("app", "HaithamNotes").put("version", 1).put("savedAt", System.currentTimeMillis())
            .put("categories", JSONArray().apply { cats.forEach { put(it.toJson()) } })
            .put("notes", JSONArray().apply { notes.forEach { put(it.toJson()) } })
        val box = BoxCrypto.seal(key, gzip(out.toString().toByteArray(Charsets.UTF_8)))
        if (dataFile == null) drive.create(DATA_FILE, folder, box) else drive.update(dataFile.id, box)

        // 4) تنظيف الصور غير المستخدمة (سحابياً ومحلياً)
        val usedNow = Repo.referencedImages() + needed
        remoteImgs.filterKeys { it !in usedNow }.values.forEach { drive.delete(it.id) }
        val old = System.currentTimeMillis() - 10 * 60_000
        Repo.images.allIds().filter { it !in usedNow && Repo.images.file(it).lastModified() < old }.forEach { Repo.images.delete(it) }

        if (Repo.changeCounter.get() == startCounter) Repo.prefs.dirty = false
    }

    /** مفتاح التشفير المشترك: من الجهاز، أو من appDataFolder، أو يُنشأ جديداً */
    private fun ensureKey(drive: Drive): ByteArray {
        val files = drive.appDataFiles()
        val remote = files.firstOrNull { it.name == KEY_FILE }
        if (remote != null) {
            val k = Base64.decode(JSONObject(String(drive.download(remote.id))).getString("k"), Base64.NO_WRAP)
            Repo.prefs.boxKey = k
            return k
        }
        val k = Repo.prefs.boxKey ?: BoxCrypto.newKey()
        val body = JSONObject().put("k", Base64.encodeToString(k, Base64.NO_WRAP)).put("v", 1).toString().toByteArray()
        drive.create(KEY_FILE, "appDataFolder", body, "application/json")
        Repo.prefs.boxKey = k
        return k
    }

    private fun <T> JSONArray?.toList(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { runCatching { f(getJSONObject(it)) }.getOrNull() }
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(b.inputStream()).use { it.readBytes() }
}
