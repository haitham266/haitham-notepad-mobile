package com.haitham.notes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.haitham.notes.data.Block
import com.haitham.notes.data.Note
import com.haitham.notes.data.Repo
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.Screen
import com.haitham.notes.ui.UiState
import com.haitham.notes.ui.components.DeveloperCredit
import com.haitham.notes.ui.components.Logo
import com.haitham.notes.ui.screens.*
import com.haitham.notes.ui.theme.Brand
import com.haitham.notes.ui.theme.HnTheme
import com.haitham.notes.util.AppLock
import com.haitham.notes.util.Share
import com.haitham.notes.util.UpdateInfo
import com.haitham.notes.util.Updater
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    private var locked = mutableStateOf(false)
    private var stoppedAt = 0L
    private val nav get() = UiState.nav

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Repo.prefs
        UiState.themeMode = prefs.themeMode
        applySecure(prefs.secureScreen)
        locked.value = AppLock.enabled(prefs)

        // قفل التطبيق عند العودة بعد 30 ثانية في الخلفية
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { stoppedAt = SystemClock.elapsedRealtime() }
            override fun onStart(owner: LifecycleOwner) {
                if (stoppedAt > 0 && AppLock.enabled(Repo.prefs) && SystemClock.elapsedRealtime() - stoppedAt > 30_000) locked.value = true
            }
        })

        if (savedInstanceState == null) handleShareIntent(intent)

        setContent {
            var splash by remember { mutableStateOf(savedInstanceState == null) }
            var update by remember { mutableStateOf<UpdateInfo?>(null) }
            LaunchedEffect(Unit) { delay(800); splash = false }
            LaunchedEffect(Unit) { update = Updater.check() }
            HnTheme(UiState.themeMode) {
                when {
                    splash -> Splash()
                    locked.value -> LockScreen(this@MainActivity) { locked.value = false }
                    else -> CompositionLocalProvider(LocalNav provides nav) {
                        update?.let { u -> UpdateDialog(u) { update = null } }
                        BackHandler(enabled = nav.stack.size > 1) { nav.back() }
                        AnimatedContent(nav.current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { s ->
                            when (s) {
                                Screen.Home -> HomeScreen()
                                is Screen.Editor -> EditorScreen(s.noteId)
                                Screen.Trash -> TrashScreen()
                                Screen.Categories -> CategoriesScreen()
                                Screen.Settings -> SettingsScreen(onSecureChanged = ::applySecure)
                                Screen.About -> AboutScreen()
                                is Screen.Image -> ImageViewerScreen(s.imageId)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    fun applySecure(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** نص أو صورة مُرسلة من تطبيق آخر ← ملاحظة جديدة */
    private fun handleShareIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        @Suppress("DEPRECATION")
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
        val blocks = mutableListOf<Block>()
        uri?.let { Repo.images.import(it) }?.let { blocks += Block.Image(it) }
        blocks += Block.Text(text)
        val n = Note(categoryId = UiState.selectedCategory ?: com.haitham.notes.data.Category.GENERAL, title = subject, blocks = blocks)
        UiState.drafts[n.id] = n
        nav.home(); nav.go(Screen.Editor(n.id))
        i.action = null
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Brand.Dark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(112.dp)
            Spacer(Modifier.height(18.dp))
            Text("Haitham Notes", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("ملاحظاتك مرتبة في أقسام، آمنة ومتزامنة", color = Color.White.copy(.7f), fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = Brand.Gold, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        DeveloperCredit(light = true, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp))
    }
}

@Composable
private fun UpdateDialog(u: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديث جديد متوفر: ${u.version}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("الإصدار الحالي: ${BuildConfig.VERSION_NAME}")
                if (u.notes.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(u.notes, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { Share.open(ctx, u.apkUrl.ifBlank { u.pageUrl }); onDismiss() }) { Text("تحديث الآن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لاحقاً") } },
    )
}
