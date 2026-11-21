package com.haitham.notes.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.haitham.notes.BuildConfig
import com.haitham.notes.data.Repo
import com.haitham.notes.sync.GoogleAuth
import com.haitham.notes.sync.SyncManager
import com.haitham.notes.sync.SyncScheduler
import com.haitham.notes.sync.SyncState
import com.haitham.notes.ui.LocalNav
import com.haitham.notes.ui.Screen
import com.haitham.notes.ui.UiState
import com.haitham.notes.ui.components.DeveloperCredit
import com.haitham.notes.ui.components.Logo
import com.haitham.notes.ui.components.SectionTitle
import com.haitham.notes.util.AppLock
import com.haitham.notes.util.Fmt
import com.haitham.notes.util.Share
import com.haitham.notes.util.Updater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSecureChanged: (Boolean) -> Unit) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val scope = rememberCoroutineScope()
    val prefs = Repo.prefs
    val sync by SyncManager.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var email by remember { mutableStateOf(prefs.accountEmail) }
    var autoSync by remember { mutableStateOf(prefs.autoSync) }
    var lockOn by remember { mutableStateOf(AppLock.enabled(prefs)) }
    var bio by remember { mutableStateOf(prefs.biometric) }
    var secure by remember { mutableStateOf(prefs.secureScreen) }
    var setPin by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf<String?>(null) } // "export" أو "import"
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            backupBusy = true
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.haitham.notes.data.LocalBackup.export(it, backupPassword) } }
            }
            backupBusy = false; backupPassword = ""
            r.onSuccess { snackbar.showSnackbar("تم حفظ النسخة: ${it.notes} ملاحظة، ${it.images} صورة") }
                .onFailure { snackbar.showSnackbar("فشل إنشاء النسخة: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; askPassword = "import" }
    }
    fun runImport(password: String) {
        val uri = pendingUri ?: return
        scope.launch {
            backupBusy = true
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { com.haitham.notes.data.LocalBackup.import(it, password) } }
            }
            backupBusy = false; pendingUri = null
            r.onSuccess { snackbar.showSnackbar("تمت الاستعادة: ${it.notes} ملاحظة في ${it.categories} قسم") }
                .onFailure { snackbar.showSnackbar(it.message ?: "فشلت الاستعادة") }
        }
    }

    suspend fun afterAuth(r: AuthorizationResult) {
        val token = r.accessToken
        if (token == null) { signingIn = false; snackbar.showSnackbar("تعذّر الحصول على صلاحية Google Drive"); return }
        runCatching { r.toGoogleSignInAccount()?.email }.getOrNull()?.let { prefs.accountEmail = it }
        if (prefs.accountEmail == null) prefs.accountEmail = "" // يُحدَّث من Drive أثناء المزامنة
        val res = SyncManager.sync(ctx, token)
        signingIn = false
        email = prefs.accountEmail?.ifBlank { null }
        if (res.isSuccess) {
            SyncScheduler.scheduleDaily(ctx)
            snackbar.showSnackbar("تم تسجيل الدخول والمزامنة مع مجلد «${BuildConfig.DRIVE_FOLDER}»")
        } else snackbar.showSnackbar(prefs.lastError ?: "فشلت المزامنة")
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) scope.launch {
            runCatching { GoogleAuth.fromIntent(ctx, res.data) }.onSuccess { afterAuth(it) }
                .onFailure { signingIn = false; snackbar.showSnackbar("فشل تسجيل الدخول: ${it.message}") }
        } else signingIn = false
    }

    fun signIn() {
        signingIn = true
        scope.launch {
            runCatching { GoogleAuth.authorize(ctx, null) }
                .onSuccess { r ->
                    if (r.hasResolution()) authLauncher.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
                    else afterAuth(r)
                }
                .onFailure { signingIn = false; snackbar.showSnackbar("فشل تسجيل الدخول: ${it.message}") }
        }
    }

    fun syncNow() = scope.launch {
        val e = prefs.accountEmail ?: return@launch
        val r = runCatching { GoogleAuth.authorize(ctx, e.ifBlank { null }) }.getOrNull()
        when {
            r == null -> snackbar.showSnackbar("لا يوجد اتصال بالإنترنت")
            r.hasResolution() -> authLauncher.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
            else -> afterAuth(r)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("المزامنة والإعدادات") },
                navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionTitle("حساب جوجل والمزامنة")
            ElevatedCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (email == null && !signingIn && prefs.accountEmail == null) {
                        Text("سجّل الدخول بحساب جوجل لحفظ نسخة مشفرة من كل ملاحظاتك وصورك في Google Drive داخل مجلد «${BuildConfig.DRIVE_FOLDER}»، ومزامنتها تلقائياً كل يوم وبعد كل تعديل وبين أجهزتك.",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(14.dp))
                        Button(::signIn, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                            elevation = ButtonDefaults.buttonElevation(2.dp)) {
                            GoogleG(); Spacer(Modifier.width(10.dp)); Text("تسجيل الدخول بحساب Google", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text((prefs.accountName ?: email ?: "G").take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(prefs.accountName ?: "حساب Google", style = MaterialTheme.typography.titleMedium)
                                Text(email ?: prefs.accountEmail ?: "…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val (icon, text, color) = when (val s = sync) {
                            SyncState.Running -> Triple(Icons.Outlined.Sync, "جارٍ المزامنة…", MaterialTheme.colorScheme.primary)
                            is SyncState.Failed -> Triple(Icons.Outlined.SyncProblem, s.message, MaterialTheme.colorScheme.error)
                            else -> if (prefs.lastError != null) Triple(Icons.Outlined.SyncProblem, prefs.lastError!!, MaterialTheme.colorScheme.error)
                            else Triple(Icons.Outlined.CloudDone, "آخر مزامنة: ${Fmt.full(prefs.lastSync)}", MaterialTheme.colorScheme.tertiary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sync == SyncState.Running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(text, color = color, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("المجلد في Drive: ${BuildConfig.DRIVE_FOLDER}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button({ syncNow() }, enabled = sync != SyncState.Running && !signingIn, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Outlined.Sync, null); Spacer(Modifier.width(6.dp)); Text("مزامنة الآن")
                            }
                            OutlinedButton({ confirmLogout = true }, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(6.dp)); Text("خروج")
                            }
                        }
                    }
                    if (signingIn) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            }
            SwitchRow(Icons.Outlined.Schedule, "مزامنة تلقائية", "يومياً وبعد كل تعديل (عند توفر الإنترنت)", autoSync) {
                autoSync = it; prefs.autoSync = it
                if (it && prefs.accountEmail != null) SyncScheduler.scheduleDaily(ctx) else SyncScheduler.cancelAll(ctx)
            }

            SectionTitle("نسخة احتياطية على الجوال (بدون إنترنت)")
            ListItem(
                headlineContent = { Text("إنشاء نسخة احتياطية") },
                supportingContent = { Text("ملف واحد فيه كل الأقسام والملاحظات والصور، محمي بكلمة مرور. احفظه في الجوال أو الذاكرة أو أرسله لنفسك") },
                leadingContent = { Icon(Icons.Outlined.SaveAlt, null) },
                modifier = Modifier.clickable(enabled = !backupBusy) { askPassword = "export" },
            )
            ListItem(
                headlineContent = { Text("استعادة من ملف") },
                supportingContent = { Text("تُدمج مع ملاحظاتك الحالية ولا يُحذف شيء") },
                leadingContent = { Icon(Icons.Outlined.Restore, null) },
                modifier = Modifier.clickable(enabled = !backupBusy) { importLauncher.launch(arrayOf("*/*")) },
            )
            if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))

            SectionTitle("الحماية والتشفير")
            InfoRow(Icons.Outlined.EnhancedEncryption, "كل الملاحظات والصور مشفرة", "AES-256 على الجهاز بمفتاح Android Keystore، والنسخ في Drive مشفرة أيضاً ولا يقرأها غير التطبيق")
            SwitchRow(Icons.Outlined.Lock, "قفل التطبيق برمز PIN", "يُطلب الرمز عند فتح التطبيق", lockOn) {
                if (it) setPin = true else { AppLock.setPin(prefs, null); lockOn = false; bio = false }
            }
            if (lockOn && activity != null && AppLock.biometricAvailable(activity)) SwitchRow(Icons.Outlined.Fingerprint, "فتح بالبصمة", "استخدم البصمة بدلاً من الرمز", bio) {
                bio = it; prefs.biometric = it
            }
            SwitchRow(Icons.Outlined.ScreenLockPortrait, "منع لقطات الشاشة", "إخفاء محتوى التطبيق من لقطات الشاشة وقائمة التطبيقات الأخيرة", secure) {
                secure = it; prefs.secureScreen = it; onSecureChanged(it)
            }

            SectionTitle("المظهر")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "تلقائي", 1 to "فاتح", 2 to "داكن").forEach { (m, l) ->
                    FilterChip(UiState.themeMode == m, { UiState.themeMode = m; prefs.themeMode = m }, label = { Text(l) })
                }
            }

            SectionTitle("التطبيق")
            ListItem(
                headlineContent = { Text("البحث عن تحديث") }, supportingContent = { Text("الإصدار الحالي ${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val u = Updater.check()
                        if (u == null) snackbar.showSnackbar("أنت تستخدم آخر إصدار")
                        else { snackbar.showSnackbar("يتوفر الإصدار ${u.version}"); Share.open(ctx, u.apkUrl.ifBlank { u.pageUrl }) }
                    }
                },
            )
            ListItem(headlineContent = { Text("حول التطبيق") }, leadingContent = { Icon(Icons.Outlined.Info, null) },
                modifier = Modifier.clickable { nav.go(Screen.About) })
            Spacer(Modifier.height(16.dp))
            DeveloperCredit(modifier = Modifier.fillMaxWidth())
        }
    }

    askPassword?.let { mode ->
        BackupPasswordDialog(confirm = mode == "export", onDismiss = { askPassword = null; pendingUri = null }) { pw ->
            askPassword = null
            if (mode == "export") {
                backupPassword = pw
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                exportLauncher.launch("HaithamNotes-$date.hnbak")
            } else runImport(pw)
        }
    }
    if (setPin) PinSetupDialog({ setPin = false }) { pin -> AppLock.setPin(prefs, pin); lockOn = true; setPin = false }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("تسجيل الخروج؟") },
        text = { Text("ستبقى ملاحظاتك على هذا الجهاز وفي Google Drive، لكن ستتوقف المزامنة.") },
        confirmButton = {
            TextButton({
                prefs.accountEmail = null; prefs.accountName = null; prefs.lastError = null
                SyncScheduler.cancelAll(ctx); email = null; confirmLogout = false
                SyncManager.state.value = SyncState.Idle
            }) { Text("خروج") }
        },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("إلغاء") } },
    )
}


@Composable
private fun GoogleG() {
    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text("G", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF4285F4))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) }, trailingContent = { Switch(value, onChange) },
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, sub: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary) })
}

@Composable
private fun BackupPasswordDialog(confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length >= 4 && (!confirm || a == b)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text(if (confirm) "كلمة مرور النسخة" else "أدخل كلمة مرور النسخة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(a, { a = it }, label = { Text("كلمة المرور (4 أحرف على الأقل)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (confirm) {
                    OutlinedTextField(b, { b = it }, label = { Text("تأكيد كلمة المرور") }, singleLine = true, isError = b.isNotEmpty() && a != b,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Text("احفظ كلمة المرور جيداً؛ بدونها لا يمكن فتح النسخة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton({ onOk(a) }, enabled = valid) { Text(if (confirm) "متابعة" else "استعادة") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length >= 4 && a == b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز القفل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(a, { if (it.length <= 8 && it.all(Char::isDigit)) a = it }, label = { Text("الرمز (4-8 أرقام)") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                OutlinedTextField(b, { if (it.length <= 8 && it.all(Char::isDigit)) b = it }, label = { Text("تأكيد الرمز") },
                    isError = b.isNotEmpty() && a != b,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                Text("تنبيه: إذا نسيت الرمز يجب مسح بيانات التطبيق، ويمكنك استعادة ملاحظاتك من Google Drive.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onSet(a) }, enabled = valid) { Text("حفظ") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("حول التطبيق") }, navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(110.dp)
            Spacer(Modifier.height(14.dp))
            Text("Haitham Notes", style = MaterialTheme.typography.headlineSmall)
            Text("الإصدار ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "📁 أقسام تنشئها بنفسك (كمبيوتر، موبايل، عمل…) والملاحظات داخل كل قسم",
                        "📝 ملاحظات نصية وقوائم مهام بألوان مختلفة",
                        "🖼️ صور داخل الملاحظة نفسها من المعرض أو الكاميرا",
                        "☁️ مزامنة يومية تلقائية مع Google Drive في مجلد «${BuildConfig.DRIVE_FOLDER}»",
                        "🔐 تشفير كامل AES-256 وقفل برمز أو بصمة",
                        "📤 إرسال الملاحظات مع صورها لأي تطبيق",
                        "🔄 تحديثات تصل تلقائياً لكل المستخدمين",
                    ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("برمجة وتصميم", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(BuildConfig.DEVELOPER_NAME, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ Share.open(ctx, "https://wa.me/${BuildConfig.WHATSAPP}") }, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))) {
                    Icon(Icons.Outlined.Chat, null); Spacer(Modifier.width(6.dp)); Text("واتساب")
                }
                OutlinedButton({ Share.open(ctx, "tel:${BuildConfig.DEVELOPER_PHONE}") }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(6.dp)); Text(BuildConfig.DEVELOPER_PHONE)
                }
            }
        }
    }
}
