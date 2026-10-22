package com.alfredo.appextractor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.ArrayDeque
import java.util.Locale

private enum class AppScreen { APPS, DETAILS, FILES, TOOLS, SETTINGS, ABOUT, EXTRACTING, COMPLETE }
private data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val versionName: String,
    val baseSize: Long,
    val sourceDir: String,
    val splitDirs: List<String>
)
private data class SavedApk(val name: String, val size: Long, val uri: Uri)
private data class ExportResult(val apps: Int, val files: Int, val failures: Int)

class MainActivity : ComponentActivity() {
    private val pendingUninstalls = ArrayDeque<String>()
    private val uninstallHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { AppExtractorApp() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UNINSTALL_REQUEST && pendingUninstalls.isNotEmpty()) {
            uninstallHandler.postDelayed({ launchNextUninstall() }, 450)
        }
    }

    private fun launchNextUninstall() {
        if (pendingUninstalls.isEmpty()) return
        val pkg = pendingUninstalls.removeFirst()
        try {
            startActivityForResult(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)), UNINSTALL_REQUEST)
        } catch (_: Exception) {
            if (pendingUninstalls.isNotEmpty()) uninstallHandler.postDelayed({ launchNextUninstall() }, 350)
        }
    }

    @Composable
    private fun AppExtractorApp() {
        val prefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }
        var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
        var language by remember { mutableStateOf(prefs.getString("lang", "ar") ?: "ar") }
        var showSystemApps by remember { mutableStateOf(prefs.getBoolean("showSystemApps", false)) }
        var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
        var savedApks by remember { mutableStateOf<List<SavedApk>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var query by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var screen by remember { mutableStateOf(AppScreen.APPS) }
        var currentApp by remember { mutableStateOf<InstalledApp?>(null) }
        var exportResult by remember { mutableStateOf<ExportResult?>(null) }
        var uninstallConfirmation by remember { mutableStateOf<List<String>?>(null) }

        val darkScheme = darkColorScheme(
            primary = Color(0xFF3198FF), onPrimary = Color.White,
            background = Color(0xFF080D16), onBackground = Color(0xFFF2F6FF),
            surface = Color(0xFF111B2B), onSurface = Color(0xFFF2F6FF),
            surfaceVariant = Color(0xFF1A2A40), onSurfaceVariant = Color(0xFFA4B4CA),
            secondary = Color(0xFF4EC9F5), error = Color(0xFFFF5B65)
        )
        val lightScheme = lightColorScheme(
            primary = Color(0xFF176FE8), onPrimary = Color.White,
            background = Color(0xFFF4F7FC), onBackground = Color(0xFF1B273A),
            surface = Color.White, onSurface = Color(0xFF1B273A),
            surfaceVariant = Color(0xFFEAF0F8), onSurfaceVariant = Color(0xFF65758D),
            secondary = Color(0xFF087EA4), error = Color(0xFFD93843)
        )
        val scheme = if (dark) darkScheme else lightScheme
        val isArabic = language == "ar"
        val tr: (String, String) -> String = { ar, en -> if (isArabic) ar else en }

        LaunchedEffect(screen) {
            when (screen) {
                AppScreen.APPS -> {
                    loading = true
                    installedApps = withContext(Dispatchers.IO) { loadInstallableApps() }
                    loading = false
                }
                AppScreen.FILES -> savedApks = withContext(Dispatchers.IO) { loadSavedApks() }
                else -> Unit
            }
        }

        val visibleApps = installedApps.filter { app ->
            (showSystemApps || !app.isSystem) &&
                (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }

        val startExport: (List<InstalledApp>) -> Unit = { targets ->
            if (targets.isNotEmpty()) {
                exportResult = null
                screen = AppScreen.EXTRACTING
                Thread {
                    var okApps = 0
                    var okFiles = 0
                    var failedApps = 0
                    for (app in targets) {
                        try {
                            val paths = listOf(app.sourceDir) + app.splitDirs
                            paths.forEachIndexed { index, path ->
                                val suffix = if (paths.size > 1) "_part" + (index + 1) else ""
                                saveApk(path, safeFileName(app.label) + suffix + ".apk")
                                okFiles++
                            }
                            okApps++
                        } catch (_: Exception) {
                            failedApps++
                        }
                    }
                    runOnUiThread {
                        exportResult = ExportResult(okApps, okFiles, failedApps)
                        savedApks = loadSavedApks()
                        screen = AppScreen.COMPLETE
                    }
                }.start()
            }
        }

        val requestUninstall: (List<String>) -> Unit = { pkgs ->
            val list = pkgs.distinct().filter { it != packageName }
            if (list.isNotEmpty()) uninstallConfirmation = list
        }

        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                SideEffect {
                    window.statusBarColor = scheme.background.toArgb()
                    window.navigationBarColor = scheme.background.toArgb()
                    val bars = WindowInsetsControllerCompat(window, window.decorView)
                    bars.isAppearanceLightStatusBars = !dark
                    bars.isAppearanceLightNavigationBars = !dark
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    containerColor = scheme.background,
                    bottomBar = {
                        if (screen == AppScreen.APPS || screen == AppScreen.FILES || screen == AppScreen.TOOLS) {
                            NavigationBar(containerColor = scheme.surface) {
                                NavigationBarItem(
                                    selected = screen == AppScreen.APPS,
                                    onClick = { screen = AppScreen.APPS },
                                    icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                                    label = { Text(tr("التطبيقات", "Apps"), fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = screen == AppScreen.FILES,
                                    onClick = { screen = AppScreen.FILES },
                                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                    label = { Text(tr("ملفات APK", "APK files"), fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = screen == AppScreen.TOOLS,
                                    onClick = { screen = AppScreen.TOOLS },
                                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                                    label = { Text(tr("الأدوات", "Tools"), fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        when (screen) {
                            AppScreen.APPS -> AppsScreen(
                                apps = visibleApps, selected = selected, query = query, loading = loading, tr = tr,
                                onQuery = { query = it },
                                onOpen = { app ->
                                    if (selected.isNotEmpty()) selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                                    else { currentApp = app; screen = AppScreen.DETAILS }
                                },
                                onToggleSelect = { app -> selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName },
                                onSelectAll = {
                                    selected = if (visibleApps.isNotEmpty() && visibleApps.all { it.packageName in selected })
                                        selected - visibleApps.map { it.packageName }.toSet()
                                    else selected + visibleApps.map { it.packageName }
                                },
                                onClear = { selected = emptySet() },
                                onExtract = { startExport(visibleApps.filter { it.packageName in selected }) },
                                onUninstall = { requestUninstall(selected.toList()) },
                                onSettings = { screen = AppScreen.SETTINGS }
                            )
                            AppScreen.DETAILS -> {
                                val app = currentApp
                                if (app == null) screen = AppScreen.APPS
                                else DetailScreen(
                                    app = app, tr = tr, onBack = { screen = AppScreen.APPS },
                                    onExtract = { startExport(listOf(app)) },
                                    onShare = { shareExistingApk(app.label) },
                                    onUninstall = { requestUninstall(listOf(app.packageName)) },
                                    onSelect = {
                                        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                                        screen = AppScreen.APPS
                                    }
                                )
                            }
                            AppScreen.FILES -> FilesScreen(
                                files = savedApks, tr = tr,
                                onRefresh = { savedApks = loadSavedApks() },
                                onShare = { shareUri(it.uri, it.name) }
                            )
                            AppScreen.TOOLS -> ToolsScreen(
                                tr = tr,
                                onSettings = { screen = AppScreen.SETTINGS },
                                onAbout = { screen = AppScreen.ABOUT },
                                onFiles = { screen = AppScreen.FILES }
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                dark = dark, language = language, showSystemApps = showSystemApps, tr = tr,
                                onBack = { screen = AppScreen.APPS },
                                onDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                                onLanguage = { language = if (it) "ar" else "en"; prefs.edit().putString("lang", language).apply() },
                                onShowSystemApps = { showSystemApps = it; prefs.edit().putBoolean("showSystemApps", it).apply() }
                            )
                            AppScreen.ABOUT -> AboutScreen(onBack = { screen = AppScreen.TOOLS }, tr = tr)
                            AppScreen.EXTRACTING -> ProgressScreen(tr)
                            AppScreen.COMPLETE -> CompleteScreen(
                                result = exportResult, tr = tr,
                                onFiles = { screen = AppScreen.FILES },
                                onHome = { selected = emptySet(); screen = AppScreen.APPS }
                            )
                        }
                    }
                }

                uninstallConfirmation?.let { packages ->
                    AlertDialog(
                        onDismissRequest = { uninstallConfirmation = null },
                        title = { Text(tr("تأكيد إلغاء التثبيت", "Confirm uninstall")) },
                        text = { Text(tr(
                            "سيعرض أندرويد تأكيد النظام لكل تطبيق. عدد التطبيقات المحددة: " + packages.size,
                            "Android will ask for system confirmation for each app. Selected: " + packages.size
                        )) },
                        confirmButton = {
                            Button(onClick = {
                                uninstallConfirmation = null
                                pendingUninstalls.clear()
                                pendingUninstalls.addAll(packages)
                                launchNextUninstall()
                            }) { Text(tr("متابعة", "Continue")) }
                        },
                        dismissButton = {
                            TextButton(onClick = { uninstallConfirmation = null }) { Text(tr("إلغاء", "Cancel")) }
                        }
                    )
                }
            }
        }
    }

    private fun loadInstallableApps(): List<InstalledApp> {
        @Suppress("DEPRECATION")
        val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { info ->
            try {
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                val source = appInfo.sourceDir ?: return@mapNotNull null
                val base = File(source)
                if (!base.isFile || !base.canRead() || base.length() <= 0L) return@mapNotNull null
                val splits = (appInfo.splitSourceDirs ?: emptyArray()).toList()
                if (splits.any { path ->
                    val file = File(path)
                    !file.isFile || !file.canRead() || file.length() <= 0L
                }) return@mapNotNull null
                InstalledApp(
                    packageName = info.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    versionName = info.versionName ?: "—",
                    baseSize = base.length(),
                    sourceDir = source,
                    splitDirs = splits
                )
            } catch (_: Exception) { null }
        }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun saveApk(sourcePath: String, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/App Extractor")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create APK file")
        var succeeded = false
        try {
            FileInputStream(sourcePath).use { input ->
                contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                    output.flush()
                    succeeded = true
                } ?: throw IllegalStateException("Could not open output stream")
            }
        } finally {
            if (succeeded) contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) else contentResolver.delete(uri, null, null)
        }
    }

    private fun loadSavedApks(): List<SavedApk> {
        val output = mutableListOf<SavedApk>()
        var cursor: Cursor? = null
        try {
            val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE)
            cursor = contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(Environment.DIRECTORY_DOWNLOADS + "/App Extractor/"),
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (c.moveToNext()) output.add(
                    SavedApk(
                        name = c.getString(nameColumn) ?: "app.apk",
                        size = if (c.isNull(sizeColumn)) 0L else c.getLong(sizeColumn),
                        uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(idColumn))
                    )
                )
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return output
    }

    private fun shareExistingApk(appLabel: String) {
        val prefix = safeFileName(appLabel)
        val item = loadSavedApks().firstOrNull { it.name.startsWith(prefix) }
        if (item != null) shareUri(item.uri, item.name)
        else toast(getStringForLanguage("استخرج التطبيق أولاً قبل المشاركة", "Extract the app before sharing"))
    }

    private fun shareUri(uri: Uri, name: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share APK"))
        } catch (_: Exception) { toast("Could not share file") }
    }

    private fun safeFileName(value: String): String = value
        .map { ch -> if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' || ch == ' ') ch else '_' }
        .joinToString("").trim().replace(' ', '_')

    private fun toast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun getStringForLanguage(ar: String, en: String): String =
        if (getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "ar") == "ar") ar else en

    companion object { private const val UNINSTALL_REQUEST = 4312 }
}

@Composable
private fun PageTitle(
    title: String,
    tr: (String, String) -> String,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(tr("استخراج وإدارة التطبيقات", "Export and manage apps"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) action()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(
    apps: List<InstalledApp>, selected: Set<String>, query: String, loading: Boolean,
    tr: (String, String) -> String, onQuery: (String) -> Unit,
    onOpen: (InstalledApp) -> Unit, onToggleSelect: (InstalledApp) -> Unit,
    onSelectAll: () -> Unit, onClear: () -> Unit, onExtract: () -> Unit,
    onUninstall: () -> Unit, onSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("AppExtractor", tr, action = {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        })
        TextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(tr("ابحث عن تطبيق أو اسم الحزمة", "Search app or package")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("تظهر تطبيقات المستخدم القابلة للاستخراج فقط. يمكن إظهار النظام من الإعدادات.",
                "Only exportable user apps are shown. System apps can be enabled in Settings."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp
        )
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(tr("التطبيقات: " + apps.size, "Apps: " + apps.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) Text(
                tr("المحدد: " + selected.size, "Selected: " + selected.size),
                color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            apps.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(tr("ما في تطبيقات قابلة للاستخراج مطابقة للبحث", "No exportable apps match this search"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppTile(
                        app = app, checked = app.packageName in selected,
                        onClick = { onOpen(app) }, onLongClick = { onToggleSelect(app) }, onCheck = { onToggleSelect(app) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OutlinedButton(onClick = if (selected.isEmpty()) onSelectAll else onClear, modifier = Modifier.weight(1f)) {
                    Text(if (selected.isEmpty()) tr("تحديد الكل", "Select all") else tr("إلغاء التحديد", "Clear"), fontSize = 10.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onUninstall, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(2.dp))
                    Text(tr("إلغاء التثبيت", "Uninstall"), fontSize = 9.sp, maxLines = 1)
                }
                Button(onClick = onExtract, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(2.dp)); Text("APK", fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: InstalledApp, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onCheck: () -> Unit) {
    val icon = remember(app.packageName) { app.icon.toBitmap(112, 112).asImageBitmap() }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (checked) 1.5.dp else 0.7.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.size(51.dp)) {
                Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.fillMaxSize().padding(2.dp))
                if (checked) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50), modifier = Modifier.size(19.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color.White, modifier = Modifier.padding(1.dp))
                    }
                } else {
                    IconButton(onClick = onCheck, modifier = Modifier.size(19.dp)) { }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp))
            Text(app.versionName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailScreen(
    app: InstalledApp, tr: (String, String) -> String, onBack: () -> Unit,
    onExtract: () -> Unit, onShare: () -> Unit, onUninstall: () -> Unit, onSelect: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(tr("تفاصيل التطبيق", "App details"), tr, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bitmap = remember(app.packageName) { app.icon.toBitmap(160, 160).asImageBitmap() }, contentDescription = app.label, modifier = Modifier.size(82.dp))
            Spacer(Modifier.height(9.dp))
            Text(app.label, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Spacer(Modifier.height(17.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    InfoRow(tr("الإصدار", "Version"), app.versionName)
                    Divider(Modifier.padding(vertical = 9.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(tr("حجم APK الأساسي", "Base APK size"), app.baseSize.asReadable())
                    Divider(Modifier.padding(vertical = 9.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(tr("الأجزاء الإضافية", "Split APKs"), app.splitDirs.size.toString())
                    Divider(Modifier.padding(vertical = 9.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(tr("نوع التطبيق", "App type"), if (app.isSystem) tr("نظام", "System") else tr("مستخدم", "User"))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onExtract, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(tr("استخراج APK", "Extract APK"))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(47.dp)) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(tr("مشاركة APK محفوظ", "Share saved APK"))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth().height(47.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text(tr("إلغاء التثبيت", "Uninstall"))
            }
            TextButton(onClick = onSelect) { Text(tr("إضافة للتحديد المتعدد", "Add to multi-selection")) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FilesScreen(files: List<SavedApk>, onRefresh: () -> Unit, onShare: (SavedApk) -> Unit, tr: (String, String) -> String) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(tr("ملفات APK", "APK files"), tr, action = {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.FolderOpen, contentDescription = "Refresh") }
        })
        Text("Download / App Extractor", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(tr("لسه ما في ملفات محفوظة هنا", "No saved APK files yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                files.forEach { file ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(47.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("APK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                Text(file.size.asReadable(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onShare(file) }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen(onSettings: () -> Unit, onAbout: () -> Unit, onFiles: () -> Unit, tr: (String, String) -> String) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(tr("الأدوات", "Tools"), tr)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ToolCard("▤", tr("مدير ملفات APK", "APK file manager"), tr("استعراض الملفات المستخرجة ومشاركتها", "Browse and share exported files"), onFiles)
            ToolCard("⚙", tr("الإعدادات", "Settings"), tr("اللغة والوضع الليلي والتطبيقات الظاهرة", "Language, dark mode and visible apps"), onSettings)
            ToolCard("ⓘ", tr("حول التطبيق", "About"), tr("الإصدار والخصوصية", "Version and privacy"), onAbout)
        }
    }
}

@Composable
private fun ToolCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text("›", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(
    dark: Boolean, language: String, showSystemApps: Boolean, tr: (String, String) -> String,
    onBack: () -> Unit, onDark: (Boolean) -> Unit, onLanguage: (Boolean) -> Unit, onShowSystemApps: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(tr("الإعدادات", "Settings"), tr, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(tr("المظهر", "Appearance"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp, top = 5.dp))
            SettingRow(Icons.Default.DarkMode, tr("الوضع الليلي", "Dark mode"), tr("ثيم داكن بألوان زرقاء", "Dark theme with blue accents"), dark, onDark)
            Spacer(Modifier.height(8.dp))
            SettingRow(Icons.Default.Language, tr("اللغة العربية", "Arabic language"), tr("التبديل بين العربية والإنجليزية", "Switch Arabic and English"), language == "ar", onLanguage)
            Spacer(Modifier.height(15.dp))
            Text(tr("قائمة التطبيقات", "App list"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp))
            SettingRow(Icons.Default.Apps, tr("إظهار تطبيقات النظام", "Show system apps"), tr("مخفية افتراضياً؛ لا تظهر إلا إذا كانت ملفات APK قابلة للقراءة", "Hidden by default; only readable APKs are shown"), showSystemApps, onShowSystemApps)
            Spacer(Modifier.height(15.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Text(tr("الخصوصية والأمان", "Privacy and security"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        tr("لا يحتاج التطبيق إلى الإنترنت، ولا يرفع قائمة التطبيقات أو ملفات APK إلى خادم. إلغاء التثبيت يستخدم تأكيد أندرويد الرسمي.",
                            "The app needs no internet and does not upload your app list or APK files. Uninstall requests use Android's official confirmation."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("مكان الحفظ: Download/App Extractor. بعض التطبيقات تحتوي على عدة ملفات APK.", "Save location: Download/App Extractor. Some apps contain multiple APK files."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit, tr: (String, String) -> String) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(tr("حول التطبيق", "About"), tr, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp), modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            }
            Spacer(Modifier.height(13.dp))
            Text("AppExtractor", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(tr("الإصدار 1.1.0", "Version 1.1.0"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Text(tr("أداة لإدارة تطبيقات أندرويد المثبتة واستخراج ملفات APK وحفظها محلياً.", "A utility to manage installed Android apps and export APK files locally."), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text(tr("لا يتصل التطبيق بالإنترنت ولا يجمع بيانات الاستخدام. إلغاء التثبيت يتم عبر واجهة أندرويد الرسمية.", "The app does not connect to the internet or collect usage data. Uninstall requests use Android's official interface."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProgressScreen(tr: (String, String) -> String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(62.dp), strokeWidth = 5.dp)
            Spacer(Modifier.height(18.dp))
            Text(tr("جاري استخراج ملفات APK", "Exporting APK files"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(7.dp))
            Text(tr("يتم نسخ الملفات إلى Download/App Extractor", "Copying files to Download/App Extractor"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CompleteScreen(result: ExportResult?, onFiles: () -> Unit, onHome: () -> Unit, tr: (String, String) -> String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF21C987), modifier = Modifier.size(86.dp))
            Spacer(Modifier.height(13.dp))
            Text(tr("اكتمل الاستخراج", "Export complete"), fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                tr("التطبيقات الناجحة: " + (result?.apps ?: 0) + "\nالملفات المحفوظة: " + (result?.files ?: 0) + "\nتعذر استخراج: " + (result?.failures ?: 0),
                    "Apps exported: " + (result?.apps ?: 0) + "\nFiles saved: " + (result?.files ?: 0) + "\nFailed apps: " + (result?.failures ?: 0)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 24.sp
            )
            Spacer(Modifier.height(17.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                Text("Download/App Extractor", modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(17.dp))
            Button(onClick = onFiles, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(tr("فتح الملفات", "Open files")) }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(tr("العودة للتطبيقات", "Back to apps")) }
        }
    }
}

private fun Long.asReadable(): String = when {
    this < 1024L -> toString() + " B"
    this < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", this / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", this / (1024.0 * 1024.0))
}
