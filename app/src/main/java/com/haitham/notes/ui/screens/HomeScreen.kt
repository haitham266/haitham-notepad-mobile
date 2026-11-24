package com.haitham.notes.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haitham.notes.data.*
import com.haitham.notes.sync.SyncManager
import com.haitham.notes.sync.SyncState
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.Screen
import com.haitham.notes.ui.UiState
import com.haitham.notes.ui.components.*
import com.haitham.notes.ui.theme.noteBg
import com.haitham.notes.ui.theme.noteColor
import com.haitham.notes.util.Fmt
import com.haitham.notes.util.Share
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val cats by Repo.categories.collectAsState()
    val allNotes by Repo.notes.collectAsState()
    val syncState by SyncManager.state.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(UiState.selectedCategory) }
    LaunchedEffect(selected) { UiState.selectedCategory = selected }
    if (selected != null && cats.none { it.id == selected }) selected = null

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(Repo.prefs.grid) }
    var sort by remember { mutableStateOf(Repo.prefs.sort) }
    var sortMenu by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Note?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var unlockFor by remember { mutableStateOf<Category?>(null) }
    fun select(id: String?) {
        selected = id
        val c = cats.firstOrNull { it.id == id }
        if (c != null && c.locked && c.id !in UiState.unlocked) unlockFor = c
    }

    val catMap = cats.associateBy { it.id }
    val active = allNotes.filter { !it.trashed }
    val selCat = selected?.let { catMap[it] }
    val selLocked = selCat != null && selCat.locked && selCat.id !in UiState.unlocked
    val visible = active
        .filter { if (selected == null) !UiState.isHidden(it, cats) else it.categoryId == selected && !selLocked }
        .filter { q -> query.isBlank() || q.title.contains(query, true) || q.plainText.contains(query, true) }
        .let { list ->
            when (sort) {
                SortMode.UPDATED -> list.sortedByDescending { it.updatedAt }
                SortMode.CREATED -> list.sortedByDescending { it.createdAt }
                SortMode.TITLE -> list.sortedBy { it.title.ifBlank { it.plainText }.lowercase() }
                SortMode.COLOR -> list.sortedWith(compareBy<Note> { it.color }.thenByDescending { it.updatedAt })
            }
        }
    val pinned = visible.filter { it.pinned }
    val others = visible.filter { !it.pinned }
    val title = selected?.let { catMap[it]?.name } ?: "كل الملاحظات"
    val savePdf = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val list = visible
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.haitham.notes.util.PdfExport.exportCategory(selCat, list, it) } }.isSuccess
            }
            android.widget.Toast.makeText(ctx, if (ok) "تم حفظ ملف PDF (${list.size} ملاحظة)" else "تعذّر إنشاء PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            HomeDrawer(
                cats = cats, counts = active.groupingBy { it.categoryId }.eachCount(), total = active.count { !UiState.isHidden(it, cats) },
                trashCount = allNotes.count { it.trashed }, selected = selected,
                onSelect = { select(it); scope.launch { drawer.close() } },
                onAddCategory = { newCategory = true },
                onNavigate = { s -> scope.launch { drawer.close() }; nav.go(s) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                if (searching) {
                    TopAppBar(
                        navigationIcon = { IconButton({ searching = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                        title = {
                            TextField(
                                query, { query = it }, placeholder = { Text("ابحث في ${title}…") }, singleLine = true,
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        actions = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "مسح") } },
                    )
                } else {
                    TopAppBar(
                        navigationIcon = { IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "القائمة") } },
                        title = {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${visible.size} ملاحظة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        actions = {
                            IconButton({ searching = true }) { Icon(Icons.Filled.Search, "بحث") }
                            IconButton({ nav.go(Screen.Settings) }) {
                                when {
                                    syncState is SyncState.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Repo.prefs.accountEmail == null -> Icon(Icons.Outlined.CloudOff, "المزامنة")
                                    syncState is SyncState.Failed -> Icon(Icons.Outlined.SyncProblem, "المزامنة", tint = MaterialTheme.colorScheme.error)
                                    else -> Icon(Icons.Outlined.CloudDone, "المزامنة", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            IconButton({ grid = !grid; Repo.prefs.grid = grid }) {
                                Icon(if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, "طريقة العرض")
                            }
                            Box {
                                IconButton({ sortMenu = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                                DropdownMenu(sortMenu, { sortMenu = false }) {
                                    Text("الترتيب", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                    listOf(SortMode.UPDATED to "آخر تعديل", SortMode.CREATED to "تاريخ الإنشاء", SortMode.TITLE to "العنوان", SortMode.COLOR to "اللون").forEach { (m, l) ->
                                        DropdownMenuItem(
                                            text = { Text(l) }, onClick = { sort = m; Repo.prefs.sort = m; sortMenu = false },
                                            leadingIcon = { RadioButton(sort == m, null) },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("تصدير «$title» إلى PDF") }, enabled = visible.isNotEmpty(),
                                        leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                        onClick = { sortMenu = false; savePdf.launch(com.haitham.notes.util.PdfExport.fileName(title)) },
                                    )
                                }
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showNew = true },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("ملاحظة جديدة") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                // شريط الأقسام
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected == null, { selected = null }, label = { Text("الكل (${active.count { !UiState.isHidden(it, cats) }})") },
                            leadingIcon = { Icon(Icons.Outlined.Notes, null, Modifier.size(18.dp)) })
                    }
                    items(cats, key = { it.id }) { c ->
                        val accent = noteColor(c.color).accent
                        FilterChip(
                            selected == c.id, { select(c.id) },
                            label = { Text(if (c.locked && c.id !in UiState.unlocked) c.name else "${c.name} (${active.count { it.categoryId == c.id }})") },
                            leadingIcon = { Icon(if (c.locked) (if (c.id in UiState.unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock) else categoryIcon(c.icon), null, Modifier.size(18.dp), tint = accent) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(.18f)),
                        )
                    }
                    item {
                        AssistChip({ newCategory = true }, label = { Text("قسم") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                    }
                }

                if (selLocked) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(Icons.Outlined.Lock, "هذا القسم مقفل", "أدخل رمز القسم لعرض ملاحظاته")
                        Button({ unlockFor = selCat }) { Icon(Icons.Outlined.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("فتح القسم") }
                    }
                } else if (visible.isEmpty()) {
                    EmptyState(
                        if (query.isBlank()) Icons.Outlined.EditNote else Icons.Outlined.SearchOff,
                        if (query.isBlank()) "لا توجد ملاحظات هنا" else "لا توجد نتائج",
                        if (query.isBlank()) "اضغط «ملاحظة جديدة» لإضافة أول ملاحظة في ${title}" else "جرّب كلمة أخرى",
                        Modifier.padding(top = 60.dp),
                    )
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(if (grid) 2 else 1),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                        verticalItemSpacing = 10.dp, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (pinned.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) { GroupLabel(Icons.Filled.PushPin, "مثبّتة") }
                            items(pinned, key = { it.id }) { n ->
                                NoteCard(n, catMap[n.categoryId], selected == null, { nav.go(Screen.Editor(n.id)) }, { actionsFor = n })
                            }
                            if (others.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { GroupLabel(Icons.Outlined.Notes, "الأخرى") }
                        }
                        items(others, key = { it.id }) { n ->
                            NoteCard(n, catMap[n.categoryId], selected == null, { nav.go(Screen.Editor(n.id)) }, { actionsFor = n })
                        }
                    }
                }
            }
        }
    }

    if (showNew) NewNoteSheet(cats, selected ?: Category.GENERAL, onDismiss = { showNew = false }) { type, catId ->
        showNew = false
        val n = Note(categoryId = catId, type = type, items = if (type == NoteType.CHECKLIST) listOf(CheckItem("")) else emptyList())
        UiState.drafts[n.id] = n
        nav.go(Screen.Editor(n.id))
    }
    actionsFor?.let { n -> NoteActionsSheet(n, cats, onDismiss = { actionsFor = null }, onShare = { Share.note(ctx, n) }) }
    if (newCategory) CategoryDialog(null, { newCategory = false }) { name, color, icon, lock ->
        val c = Repo.addCategory(name, color, icon) { it.withLock(lock) }; UiState.unlocked.add(c.id); newCategory = false; selected = c.id
    }
    unlockFor?.let { c -> UnlockCategoryDialog(c, { unlockFor = null; if (c.id !in UiState.unlocked) selected = null }) { unlockFor = null } }
}

@Composable
private fun GroupLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(n: Note, cat: Category?, showCategory: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    // صف مضغوط مثل ColorNote: شريط لون + سطر واحد + التاريخ
    val bg = noteBg(n.color)
    val accent = noteColor(n.color).accent
    val firstLine = when (n.type) {
        NoteType.TEXT -> n.plainText.lines().firstOrNull { it.isNotBlank() }.orEmpty()
        NoteType.CHECKLIST -> n.items.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
    }
    val label = n.title.ifBlank { firstLine }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(48.dp).background(if (n.color == 0) MaterialTheme.colorScheme.outlineVariant else accent))
        Spacer(Modifier.width(12.dp))
        if (n.pinned) { Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = accent); Spacer(Modifier.width(4.dp)) }
        if (n.type == NoteType.CHECKLIST) { Icon(Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(14.dp), tint = accent); Spacer(Modifier.width(4.dp)) }
        if (n.reminderAt > System.currentTimeMillis()) { Icon(Icons.Filled.NotificationsActive, null, Modifier.size(14.dp), tint = accent); Spacer(Modifier.width(4.dp)) }
        Text(label.ifBlank { "ملاحظة فارغة" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.date(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showCategory && cat != null) Text(cat.name, fontSize = 10.sp, color = noteColor(cat.color).accent, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun HomeDrawer(
    cats: List<Category>, counts: Map<String, Int>, total: Int, trashCount: Int, selected: String?,
    onSelect: (String?) -> Unit, onAddCategory: () -> Unit, onNavigate: (Screen) -> Unit,
) {
    ModalDrawerSheet(Modifier.width(310.dp)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(com.haitham.notes.ui.theme.Brand.gradient).statusBarsPadding().padding(20.dp)) {
                Column {
                    Logo(56.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Haitham Notes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(Repo.prefs.accountEmail ?: "غير متصل بحساب جوجل", color = Color.White.copy(.8f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("كل الملاحظات") }, selected = selected == null, onClick = { onSelect(null) },
                icon = { Icon(Icons.Outlined.Notes, null) }, badge = { Text("$total") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            SectionTitle("الأقسام")
            cats.forEach { c ->
                NavigationDrawerItem(
                    label = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = selected == c.id, onClick = { onSelect(c.id) },
                    icon = { CategoryBadge(c, 30.dp) },
                    badge = { if (c.locked && c.id !in UiState.unlocked) Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)) else Text("${counts[c.id] ?: 0}") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            NavigationDrawerItem(
                label = { Text("إضافة قسم جديد") }, selected = false, onClick = onAddCategory,
                icon = { Icon(Icons.Outlined.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            NavigationDrawerItem(label = { Text("إدارة الأقسام") }, selected = false, onClick = { onNavigate(Screen.Categories) },
                icon = { Icon(Icons.Outlined.FolderCopy, null) }, modifier = Modifier.padding(horizontal = 12.dp))
            NavigationDrawerItem(label = { Text("سلة المحذوفات") }, selected = false, onClick = { onNavigate(Screen.Trash) },
                icon = { Icon(Icons.Outlined.Delete, null) }, badge = { if (trashCount > 0) Text("$trashCount") },
                modifier = Modifier.padding(horizontal = 12.dp))
            NavigationDrawerItem(label = { Text("المزامنة والإعدادات") }, selected = false, onClick = { onNavigate(Screen.Settings) },
                icon = { Icon(Icons.Outlined.Settings, null) }, modifier = Modifier.padding(horizontal = 12.dp))
            NavigationDrawerItem(label = { Text("حول التطبيق") }, selected = false, onClick = { onNavigate(Screen.About) },
                icon = { Icon(Icons.Outlined.Info, null) }, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.weight(1f))
            DeveloperCredit(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).navigationBarsPadding())
        }
    }
}

/** عند إضافة ملاحظة: اختيار نوعها والقسم الذي تُحفظ فيه */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteSheet(cats: List<Category>, defaultCat: String, onDismiss: () -> Unit, onCreate: (NoteType, String) -> Unit) {
    var type by remember { mutableStateOf(NoteType.TEXT) }
    var cat by remember { mutableStateOf(defaultCat) }
    var newCat by remember { mutableStateOf(false) }
    val catName = cats.firstOrNull { it.id == cat }?.name ?: "عام"
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("ملاحظة جديدة", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeOption(Icons.Outlined.Notes, "نص", type == NoteType.TEXT, Modifier.weight(1f)) { type = NoteType.TEXT }
                TypeOption(Icons.Outlined.Checklist, "قائمة مهام", type == NoteType.CHECKLIST, Modifier.weight(1f)) { type = NoteType.CHECKLIST }
            }
            Spacer(Modifier.height(18.dp))
            Text("في أي قسم تريد حفظها؟", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                cats.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (c.id == cat) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { cat = c.id }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(c, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(c.id == cat, { cat = c.id })
                    }
                }
                TextButton({ newCat = true }) { Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("إنشاء قسم جديد") }
            }
            Spacer(Modifier.height(10.dp))
            Button({ onCreate(type, cat) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("إنشاء وحفظ في «$catName»")
            }
        }
    }
    if (newCat) CategoryDialog(null, { newCat = false }) { name, color, icon, lock -> cat = Repo.addCategory(name, color, icon) { it.withLock(lock) }.also { UiState.unlocked.add(it.id) }.id; newCat = false }
}

@Composable
private fun TypeOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) c.primaryContainer else c.surfaceVariant)
            .border(2.dp, if (sel) c.primary else Color.Transparent, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (sel) c.primary else c.onSurfaceVariant, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontWeight = FontWeight.Bold, color = if (sel) c.primary else c.onSurfaceVariant)
    }
}

/** اختيار/تغيير القسم لملاحظة موجودة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(cats: List<Category>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var newCat by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("نقل إلى قسم", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(cats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (c.id == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onPick(c.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(c, 34.dp); Spacer(Modifier.width(12.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (c.id == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                item { TextButton({ newCat = true }) { Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("إنشاء قسم جديد") } }
            }
        }
    }
    if (newCat) CategoryDialog(null, { newCat = false }) { name, color, icon, lock -> newCat = false; onPick(Repo.addCategory(name, color, icon) { it.withLock(lock) }.also { UiState.unlocked.add(it.id) }.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionsSheet(n: Note, cats: List<Category>, onDismiss: () -> Unit, onShare: () -> Unit) {
    var pickCat by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(n.title.ifBlank { n.plainText.take(40).ifBlank { "ملاحظة" } }, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                ColorPalette(n.color, { Repo.saveNote(n.copy(color = it)); onDismiss() })
            }
            ListItem(headlineContent = { Text(if (n.pinned) "إلغاء التثبيت" else "تثبيت في الأعلى") },
                leadingContent = { Icon(Icons.Outlined.PushPin, null) },
                modifier = Modifier.clickable { Repo.saveNote(n.copy(pinned = !n.pinned)); onDismiss() })
            ListItem(headlineContent = { Text("نقل إلى قسم") },
                supportingContent = { Text(cats.firstOrNull { it.id == n.categoryId }?.name ?: "عام") },
                leadingContent = { Icon(Icons.Outlined.DriveFileMove, null) }, modifier = Modifier.clickable { pickCat = true })
            ListItem(headlineContent = { Text("إرسال / مشاركة") }, leadingContent = { Icon(Icons.Outlined.Share, null) },
                modifier = Modifier.clickable { onShare(); onDismiss() })
            ListItem(headlineContent = { Text("نقل إلى سلة المحذوفات", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { Repo.trash(n.id); onDismiss() })
        }
    }
    if (pickCat) CategoryPickerSheet(cats, n.categoryId, { pickCat = false }) { Repo.saveNote(n.copy(categoryId = it)); pickCat = false; onDismiss() }
}
