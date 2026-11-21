package com.haitham.notes.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.haitham.notes.data.Repo
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.components.DeveloperCredit
import com.haitham.notes.ui.components.Logo
import com.haitham.notes.ui.theme.Brand
import com.haitham.notes.util.AppLock
import kotlinx.coroutines.delay

@Composable
fun LockScreen(activity: FragmentActivity, onUnlock: () -> Unit) {
    val prefs = Repo.prefs
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val bio = prefs.biometric && AppLock.biometricAvailable(activity)
    LaunchedEffect(Unit) { if (bio) AppLock.promptBiometric(activity, onUnlock) }
    LaunchedEffect(error) { if (error) { delay(900); error = false; pin = "" } }

    fun press(d: String) {
        if (error) return
        pin += d
        if (pin.length >= 4 && AppLock.check(prefs, pin)) onUnlock()
        else if (pin.length >= 8) error = true
    }

    Box(Modifier.fillMaxSize().background(Brand.Dark).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Logo(80.dp)
            Spacer(Modifier.height(16.dp))
            Text("أدخل رمز القفل", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(
                        when { error -> Brand.Red; i < pin.length -> Brand.Gold; else -> Color.White.copy(.25f) }))
                }
            }
            if (error) Text("رمز غير صحيح", color = Brand.Red, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        Box(Modifier.size(72.dp).clip(CircleShape).background(if (k == "bio" && !bio) Color.Transparent else Color.White.copy(.08f))
                            .then(if (k == "bio" && !bio) Modifier else Modifier.clickable {
                                when (k) { "del" -> pin = pin.dropLast(1); "bio" -> AppLock.promptBiometric(activity, onUnlock); else -> press(k) }
                            }),
                            contentAlignment = Alignment.Center) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "حذف", tint = Color.White)
                                "bio" -> if (bio) Icon(Icons.Outlined.Fingerprint, "البصمة", tint = Brand.Gold, modifier = Modifier.size(32.dp))
                                else -> Text(k, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            DeveloperCredit(light = true)
        }
    }
}


@Composable
fun ImageViewerScreen(imageId: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val bmp = remember(imageId) { Repo.images.bitmap(imageId) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bmp != null) Image(
            bmp.asImageBitmap(), null,
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            contentScale = ContentScale.Fit,
        )
        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth()) {
            IconButton({ nav.back() }) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton({
                Repo.images.exportForShare(imageId)?.let { f ->
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/jpeg")
                        .putExtra(android.content.Intent.EXTRA_STREAM, u).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(android.content.Intent.createChooser(i, "إرسال الصورة"))
                }
            }) { Icon(Icons.Outlined.Share, "إرسال", tint = Color.White) }
        }
    }
}
