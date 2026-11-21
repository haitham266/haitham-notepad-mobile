package com.haitham.notes.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.haitham.notes.data.Category
import com.haitham.notes.data.Repo
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen() {
    val nav = LocalNav.current
    val notes by Repo.notes.collectAsState()
    val cats by Repo.categories.collectAsState()
    val trashed = notes.filter { it.trashed && !com.haitham.notes.ui.UiState.isHidden(it, cats) }.sortedByDescending { it.updatedAt }
    var confirmEmpty by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("سلة المحذوفات") },
            navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            actions = { if (trashed.isNotEmpty()) TextButton({ confirmEmpty = true }) { Text("إفراغ السلة", color = MaterialTheme.colorScheme.error) } },
        )
    }) { pad ->
        if (trashed.isEmpty()) EmptyState(Icons.Outlined.DeleteOutline, "السلة فارغة", "الملاحظات المحذوفة تظهر هنا ويمكنك استعادتها", Modifier.padding(pad).padding(top = 60.dp))
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(trashed, key = { it.id }) { n ->
                Column {
                    NoteCard(n, cats.firstOrNull { it.id == n.categoryId }, true, {}, {})
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ Repo.restore(n.id) }) { Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(6.dp)); Text("استعادة") }
                        TextButton({ Repo.deleteForever(n.id) }) {
                            Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                            Text("حذف نهائي", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("إفراغ السلة؟") }, text = { Text("سيتم حذف ${trashed.size} ملاحظة نهائياً من هذا الجهاز ومن كل الأجهزة المتزامنة.") },
        confirmButton = { TextButton({ Repo.emptyTrash(); confirmEmpty = false }) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmEmpty = false }) { Text("إلغاء") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen() {
    val nav = LocalNav.current
    val cats by Repo.categories.collectAsState()
    val notes by Repo.notes.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfCat by remember { mutableStateOf<Category?>(null) }
    val savePdf = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val c = pdfCat
        if (uri != null && c != null) scope.launch {
            val list = notes.filter { !it.trashed && it.categoryId == c.id }.sortedWith(compareByDescending<com.haitham.notes.data.Note> { it.pinned }.thenByDescending { it.updatedAt })
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.haitham.notes.util.PdfExport.exportCategory(c, list, it) } }.isSuccess
            }
            android.widget.Toast.makeText(ctx, if (ok) "تم حفظ PDF قسم «${c.name}»" else "تعذّر إنشاء PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var unlockThen by remember { mutableStateOf<Pair<Category, () -> Unit>?>(null) }
    /** الأقسام المقفلة تحتاج الرمز قبل تعديلها أو حذفها */
    fun guarded(c: Category, action: () -> Unit) {
        if (c.locked && c.id !in com.haitham.notes.ui.UiState.unlocked) unlockThen = c to action else action()
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("إدارة الأقسام") },
                navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.CreateNewFolder, null) }, text = { Text("قسم جديد") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats, key = { it.id }) { c ->
                val count = notes.count { !it.trashed && it.categoryId == c.id }
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, 44.dp)
                        if (c.locked) Icon(Icons.Outlined.Lock, "مقفل", Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (c.locked && c.id !in com.haitham.notes.ui.UiState.unlocked) "مقفل" else "$count ملاحظة") + if (c.id == Category.GENERAL) " • القسم الافتراضي" else "", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.id != Category.GENERAL) {
                            IconButton({ Repo.moveCategory(c.id, true) }) { Icon(Icons.Filled.KeyboardArrowUp, "أعلى") }
                            IconButton({ Repo.moveCategory(c.id, false) }) { Icon(Icons.Filled.KeyboardArrowDown, "أسفل") }
                        }
                        Box {
                            var m by remember { mutableStateOf(false) }
                            IconButton({ m = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                            DropdownMenu(m, { m = false }) {
                                DropdownMenuItem(text = { Text("تعديل") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = { m = false; guarded(c) { editing = c } })
                                DropdownMenuItem(text = { Text("تصدير PDF") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                    onClick = { m = false; guarded(c) { pdfCat = c; savePdf.launch(com.haitham.notes.util.PdfExport.fileName(c.name)) } })
                                if (c.id != Category.GENERAL) DropdownMenuItem(
                                    text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { m = false; guarded(c) { deleting = c } })
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) CategoryDialog(null, { adding = false }) { n, col, ic, lock ->
        Repo.addCategory(n, col, ic) { it.withLock(lock) }.also { com.haitham.notes.ui.UiState.unlocked.add(it.id) }; adding = false
    }
    editing?.let { c -> CategoryDialog(c, { editing = null }) { n, col, ic, lock -> Repo.saveCategory(c.copy(name = n, color = col, icon = ic).withLock(lock)); editing = null } }
    unlockThen?.let { (c, action) -> UnlockCategoryDialog(c, { unlockThen = null }) { unlockThen = null; action() } }
    deleting?.let { c ->
        val count = notes.count { !it.trashed && it.categoryId == c.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف قسم «${c.name}»؟") },
            text = { Text(if (count == 0) "القسم فارغ." else "يحتوي القسم على $count ملاحظة. ماذا تريد أن تفعل بها؟") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (count > 0) TextButton({ Repo.deleteCategory(c.id, moveNotes = true); deleting = null }) { Text("نقلها إلى «عام» ثم حذف القسم") }
                    TextButton({ Repo.deleteCategory(c.id, moveNotes = false); deleting = null }) {
                        Text(if (count > 0) "حذف القسم ونقل ملاحظاته للسلة" else "حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } },
        )
    }
}
