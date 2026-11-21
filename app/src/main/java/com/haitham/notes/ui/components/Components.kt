package com.haitham.notes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haitham.notes.BuildConfig
import com.haitham.notes.R
import com.haitham.notes.data.Category
import com.haitham.notes.ui.theme.Brand
import com.haitham.notes.ui.theme.NoteColors
import com.haitham.notes.ui.theme.noteColor
import com.haitham.notes.util.Share

@Composable
fun Logo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Brand.gradient),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_logo_foreground), null, Modifier.fillMaxSize())
    }
}

/** «برمجة هيثم العريفي» مع رقم التواصل */
@Composable
fun DeveloperCredit(light: Boolean = false, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = if (light) Color.White.copy(alpha = .85f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("برمجة: ${BuildConfig.DEVELOPER_NAME}", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { Share.open(ctx, "https://wa.me/${BuildConfig.WHATSAPP}") }
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Call, null, tint = if (light) Brand.Gold else Brand.Teal, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(BuildConfig.DEVELOPER_PHONE, color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

val CategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "folder" to Icons.Outlined.Folder,
    "inbox" to Icons.Outlined.Inbox,
    "computer" to Icons.Outlined.Computer,
    "phone" to Icons.Outlined.PhoneAndroid,
    "work" to Icons.Outlined.Work,
    "school" to Icons.Outlined.School,
    "home" to Icons.Outlined.Home,
    "star" to Icons.Outlined.StarOutline,
    "heart" to Icons.Outlined.FavoriteBorder,
    "cart" to Icons.Outlined.ShoppingCart,
    "money" to Icons.Outlined.Payments,
    "code" to Icons.Outlined.Code,
    "book" to Icons.Outlined.MenuBook,
    "idea" to Icons.Outlined.Lightbulb,
    "travel" to Icons.Outlined.Flight,
    "health" to Icons.Outlined.LocalHospital,
    "car" to Icons.Outlined.DirectionsCar,
    "wifi" to Icons.Outlined.Wifi,
    "lock" to Icons.Outlined.Lock,
    "person" to Icons.Outlined.Person,
)

fun categoryIcon(name: String): ImageVector = CategoryIcons.firstOrNull { it.first == name }?.second ?: Icons.Outlined.Folder

@Composable
fun CategoryBadge(c: Category, size: Dp = 36.dp) {
    val accent = noteColor(c.color).accent
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(c.icon), null, tint = accent, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun ColorPalette(selected: Int, onSelect: (Int) -> Unit, useAccent: Boolean = false) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NoteColors.forEachIndexed { i, c ->
            val fill = if (useAccent) c.accent else c.light
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(fill)
                    .border(if (i == selected) 3.dp else 1.dp, if (i == selected) MaterialTheme.colorScheme.primary else Color.Black.copy(.12f), CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) Icon(Icons.Filled.Check, null, tint = if (useAccent) Color.White else Color.Black.copy(.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** إنشاء/تعديل قسم: الاسم واللون والأيقونة */
@Composable
fun CategoryDialog(initial: Category?, onDismiss: () -> Unit, onSave: (name: String, color: Int, icon: String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: 5) }
    var icon by remember { mutableStateOf(initial?.icon ?: "folder") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "قسم جديد" else "تعديل القسم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("اسم القسم") }, singleLine = true,
                    placeholder = { Text("مثال: كمبيوتر، موبايل، عمل…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
                )
                Text("اللون", style = MaterialTheme.typography.labelLarge)
                ColorPalette(color, { color = it }, useAccent = true)
                Text("الأيقونة", style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CategoryIcons) { (key, vec) ->
                        val sel = key == icon
                        val accent = noteColor(color).accent
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) accent.copy(.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (sel) 2.dp else 0.dp, if (sel) accent else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vec, null, tint = if (sel) accent else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color, icon) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
