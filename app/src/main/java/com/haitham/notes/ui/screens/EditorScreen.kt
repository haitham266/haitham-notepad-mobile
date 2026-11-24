package com.haitham.notes.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.haitham.notes.data.*
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.Screen
import com.haitham.notes.ui.UiState
import com.haitham.notes.ui.components.ColorPalette
import com.haitham.notes.ui.components.categoryIcon
import com.haitham.notes.ui.theme.noteBg
import com.haitham.notes.ui.theme.noteColor
import com.haitham.notes.util.Fmt
import com.haitham.notes.util.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(noteId: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cats by Repo.categories.collectAsState()
    val initial = remember(noteId) { Repo.note(noteId) ?: UiState.drafts[noteId] }
    if (initial == null) { LaunchedEffect(Unit) { nav.back() }; return }

    var note by remember(noteId) { mutableStateOf(initial) }
    var lastSaved by remember(noteId) { mutableStateOf(if (Repo.note(noteId) != null) initial else null) }
    var focused by remember { mutableIntStateOf(-1) }
    var showColors by remember { mutableStateOf(false) }
    var pickCat by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    fun persist() {
        val n = note
        if (n === lastSaved) return
        if (n.isEmpty && lastSaved == null) return
        Repo.saveNote(n)
        lastSaved = n
        UiState.drafts.remove(n.id)
    }
    // حفظ تلقائي أثناء الكتابة
    LaunchedEffect(note) { delay(700); persist() }
    DisposableEffect(noteId) {
        onDispose {
            val n = note
            if (n.isEmpty) { if (lastSaved != null) Repo.trash(n.id) } else persist()
            UiState.drafts.remove(n.id)
        }
    }

    fun insertImages(ids: List<String>) {
        if (ids.isEmpty()) return
        val blocks = note.blocks.toMutableList()
        if (note.type == NoteType.CHECKLIST) {
            ids.forEach { blocks.add(Block.Image(it)) }
        } else {
            var at = if (focused in blocks.indices) focused + 1 else blocks.size
            ids.forEach { blocks.add(at++, Block.Image(it)) }
            if (at >= blocks.size || blocks[at] !is Block.Text) blocks.add(at, Block.Text(""))
        }
        note = note.copy(blocks = blocks)
    }
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importing = true
        scope.launch {
            val ids = withContext(Dispatchers.IO) { uris.mapNotNull { Repo.images.import(it) } }
            importing = false
            insertImages(ids)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { importUris(it) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) importUris(listOf(u))
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.haitham.notes.util.PdfExport.exportNote(note, it) } }.isSuccess }
            android.widget.Toast.makeText(ctx, if (ok) "تم حفظ ملف PDF" else "تعذّر إنشاء PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun sharePdf() = scope.launch {
        val f = withContext(Dispatchers.IO) { runCatching { com.haitham.notes.util.PdfExport.shareFile(ctx, note.title) { com.haitham.notes.util.PdfExport.exportNote(note, it) } }.getOrNull() }
            ?: return@launch
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(android.content.Intent.EXTRA_STREAM, u).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(android.content.Intent.createChooser(i, "إرسال PDF"))
    }

    fun openCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        cameraUri = u
        runCatching { camera.launch(u) }
    }

    fun removeImage(index: Int) {
        val b = note.blocks.toMutableList()
        b.removeAt(index)
        // دمج النصين المتجاورين بعد حذف الصورة
        if (index in 1 until b.size && b[index - 1] is Block.Text && b[index] is Block.Text) {
            val a = b[index - 1] as Block.Text; val c = b[index] as Block.Text
            b[index - 1] = a.copy(text = listOf(a.text, c.text).filter { it.isNotEmpty() }.joinToString("\n"))
            b.removeAt(index)
        }
        if (b.none { it is Block.Text }) b.add(Block.Text(""))
        note = note.copy(blocks = b)
    }

    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun pickReminder() {
        val c = java.util.Calendar.getInstance()
        if (note.reminderAt > System.currentTimeMillis()) c.timeInMillis = note.reminderAt else c.add(java.util.Calendar.HOUR_OF_DAY, 1)
        android.app.DatePickerDialog(ctx, { _, y, mo, d ->
            android.app.TimePickerDialog(ctx, { _, h, mi ->
                val t = java.util.Calendar.getInstance().apply { set(y, mo, d, h, mi, 0); set(java.util.Calendar.MILLISECOND, 0) }
                if (t.timeInMillis > System.currentTimeMillis()) {
                    note = note.copy(reminderAt = t.timeInMillis)
                    if (android.os.Build.VERSION.SDK_INT >= 33 && !com.haitham.notes.util.Reminders.canNotify(ctx)) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), false).show()
        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    val bg = noteBg(note.color)
    val accent = noteColor(note.color).accent
    val cat = cats.firstOrNull { it.id == note.categoryId } ?: cats.firstOrNull()

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg),
                navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                title = {},
                actions = {
                    IconButton({ note = note.copy(pinned = !note.pinned) }) {
                        Icon(if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "تثبيت", tint = if (note.pinned) accent else LocalContentColor.current)
                    }
                    IconButton({ pickReminder() }) {
                        Icon(if (note.reminderAt > System.currentTimeMillis()) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd, "تذكير",
                            tint = if (note.reminderAt > System.currentTimeMillis()) accent else LocalContentColor.current)
                    }
                    IconButton({ showColors = !showColors }) { Icon(Icons.Outlined.Palette, "اللون") }
                    IconButton({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Outlined.Image, "إضافة صورة") }
                    IconButton({ persist(); Share.note(ctx, note) }) { Icon(Icons.Outlined.Share, "إرسال") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                        DropdownMenu(menu, { menu = false }) {
                            if (note.reminderAt > System.currentTimeMillis())
                                DropdownMenuItem(text = { Text("إلغاء التذكير") }, leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                                    onClick = { menu = false; note = note.copy(reminderAt = 0) })
                            DropdownMenuItem(text = { Text("التقاط صورة بالكاميرا") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) }, onClick = { menu = false; openCamera() })
                            DropdownMenuItem(text = { Text("نقل إلى قسم") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; pickCat = true })
                            DropdownMenuItem(
                                text = { Text(if (note.type == NoteType.TEXT) "تحويل إلى قائمة مهام" else "تحويل إلى نص") },
                                leadingIcon = { Icon(if (note.type == NoteType.TEXT) Icons.Outlined.Checklist else Icons.Outlined.Notes, null) },
                                onClick = { menu = false; note = convert(note) },
                            )
                            DropdownMenuItem(text = { Text("حفظ كملف PDF") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                onClick = { menu = false; persist(); savePdf.launch(com.haitham.notes.util.PdfExport.fileName(note.title)) })
                            DropdownMenuItem(text = { Text("إرسال كملف PDF") }, leadingIcon = { Icon(Icons.Outlined.Send, null) },
                                onClick = { menu = false; persist(); sharePdf() })
                            DropdownMenuItem(text = { Text("نسخ النص") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; Share.copy(ctx, note) })
                            DropdownMenuItem(
                                text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    if (lastSaved != null) { persist(); Repo.trash(note.id) }
                                    note = Note(id = note.id, categoryId = note.categoryId) // فارغة فلا تُحفظ مجدداً
                                    lastSaved = null
                                    nav.back()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (showColors) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { ColorPalette(note.color, { note = note.copy(color = it) }) }

            // القسم الذي تُحفظ فيه الملاحظة
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cat != null) {
                    val ca = noteColor(cat.color).accent
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(ca.copy(.16f)).clickable { pickCat = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(categoryIcon(cat.icon), null, Modifier.size(16.dp), tint = ca)
                        Spacer(Modifier.width(6.dp))
                        Text("محفوظة في: ${cat.name}", color = ca, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Icon(Icons.Filled.ArrowDropDown, null, tint = ca, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(if (lastSaved == null) "جديدة" else Fmt.full(Repo.note(note.id)?.updatedAt ?: note.updatedAt),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))

            val ruleH = 30.sp
            val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = ruleH,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None))
            val ruleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
            val ruleStep = with(androidx.compose.ui.platform.LocalDensity.current) { ruleH.toPx() }
            val cursor = SolidColor(MaterialTheme.colorScheme.primary)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp)) {
                item(key = "title") {
                    BasicTextField(
                        note.title, { note = note.copy(title = it) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = cursor, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        decorationBox = { inner ->
                            if (note.title.isEmpty()) Text("العنوان", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(.35f))
                            inner()
                        },
                    )
                }
                if (note.type == NoteType.TEXT) {
                    itemsIndexed(note.blocks, key = { _, b -> b.key }) { i, b ->
                        when (b) {
                            is Block.Text -> BasicTextField(
                                b.text,
                                { v -> note = note.copy(blocks = note.blocks.toMutableList().also { it[i] = b.copy(text = v) }) },
                                textStyle = textStyle, cursorBrush = cursor,
                                modifier = Modifier.fillMaxWidth().then(if (i == note.blocks.lastIndex) Modifier.heightIn(min = 420.dp) else Modifier)
                                    .drawBehind {
                                        var y = ruleStep
                                        while (y <= size.height + 1f) { drawLine(ruleColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.5f); y += ruleStep }
                                    }.onFocusChanged { if (it.isFocused) focused = i },
                                decorationBox = { inner ->
                                    if (b.text.isEmpty() && i == 0) Text("اكتب ملاحظتك هنا…", style = textStyle, color = MaterialTheme.colorScheme.onSurface.copy(.35f))
                                    inner()
                                },
                            )
                            is Block.Image -> NoteImage(b.imageId, { nav.go(Screen.Image(b.imageId)) }) { removeImage(i) }
                        }
                    }
                } else {
                    note.blocks.forEachIndexed { i, b ->
                        if (b is Block.Image) item(key = b.key) { NoteImage(b.imageId, { nav.go(Screen.Image(b.imageId)) }) { removeImage(i) } }
                    }
                    item(key = "checklist") { ChecklistEditor(note.items, accent) { note = note.copy(items = it) } }
                }
            }
        }
    }

    if (pickCat) CategoryPickerSheet(cats, note.categoryId, { pickCat = false }) { note = note.copy(categoryId = it); pickCat = false }
}

private fun convert(n: Note): Note = if (n.type == NoteType.TEXT) {
    val lines = n.blocks.filterIsInstance<Block.Text>().flatMap { it.text.split("\n") }.filter { it.isNotBlank() }
    n.copy(type = NoteType.CHECKLIST, items = lines.map { CheckItem(it) }.ifEmpty { listOf(CheckItem("")) },
        blocks = n.blocks.filterIsInstance<Block.Image>() + Block.Text(""))
} else {
    val text = n.items.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
    n.copy(type = NoteType.TEXT, items = emptyList(), blocks = listOf(Block.Text(text)) + n.blocks.filterIsInstance<Block.Image>().flatMap { listOf(it, Block.Text("")) })
}

@Composable
private fun NoteImage(id: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val bmp = remember(id) { Repo.images.bitmap(id) }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (bmp != null) Image(
            bmp.asImageBitmap(), null,
            Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
            contentScale = ContentScale.FillWidth,
        ) else Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(.06f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onRemove, Modifier.align(Alignment.TopStart).padding(6.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(.5f))) {
            Icon(Icons.Filled.Close, "حذف الصورة", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ChecklistEditor(items: List<CheckItem>, accent: Color, onChange: (List<CheckItem>) -> Unit) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusKey) { focusKey?.let { delay(60); runCatching { requesters[it]?.requestFocus() } } }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val open = items.filter { !it.checked }
    val done = items.filter { it.checked }

    @Composable
    fun Row(item: CheckItem) {
        val fr = requesters.getOrPut(item.key) { FocusRequester() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.checked, { c -> onChange(items.map { if (it.key == item.key) it.copy(checked = c) else it }) },
                colors = CheckboxDefaults.colors(checkedColor = accent))
            BasicTextField(
                item.text,
                { v ->
                    if (v.contains('\n')) {
                        val parts = v.split('\n')
                        val idx = items.indexOfFirst { it.key == item.key }
                        val newItems = items.toMutableList()
                        newItems[idx] = item.copy(text = parts.first())
                        val extra = parts.drop(1).map { CheckItem(it) }
                        newItems.addAll(idx + 1, extra)
                        onChange(newItems)
                        focusKey = extra.last().key
                    } else onChange(items.map { if (it.key == item.key) it.copy(text = v) else it })
                },
                textStyle = textStyle.copy(
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = textStyle.color.copy(alpha = if (item.checked) .5f else 1f),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).focusRequester(fr),
                decorationBox = { inner ->
                    if (item.text.isEmpty()) Text("عنصر", style = textStyle, color = MaterialTheme.colorScheme.onSurface.copy(.35f))
                    inner()
                },
            )
            IconButton({ onChange(items.filter { it.key != item.key }) }, Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "حذف", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Column {
        open.forEach { key(it.key) { Row(it) } }
        TextButton({ val n = CheckItem(""); onChange(items + n); focusKey = n.key }) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة عنصر")
        }
        if (done.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("منجزة (${done.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
            done.forEach { key(it.key) { Row(it) } }
        }
    }
}
