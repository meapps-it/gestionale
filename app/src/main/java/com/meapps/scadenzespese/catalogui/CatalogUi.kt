package com.meapps.scadenzespese.catalogui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meapps.scadenzespese.apps.AppCatalogRepository
import com.meapps.scadenzespese.apps.AppRelease
import com.meapps.scadenzespese.apps.CatalogApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Navy = Color(0xFF0F172A)
private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF697386)
private val Bg = Color(0xFFF8FAFC)
private val Line = Color(0xFFCAD2DE)
private val Blue = Color(0xFF2563EB)
private val Cyan = Color(0xFF10A9DF)
private val Green = Color(0xFF16A34A)
private val Gold = Color(0xFFF59E0B)
private val Purple = Color(0xFF7C3AED)
private val Danger = Color(0xFFFF4048)
private val SoftBlue = Color(0xFFEEF3FF)
private val Cream = Color(0xFFFFF6DD)
private val SoftGreen = Color(0xFFF0FDF4)

enum class CatalogSection(val label: String) {
    DASHBOARD("Dashboard"),
    APPS("App"),
    RELEASES("Versioni"),
    SITE("Sito")
}

data class PendingApk(val app: CatalogApp, val uri: Uri)

@Composable
fun CatalogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Cyan,
            background = Bg,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
            error = Danger
        ),
        content = content
    )
}

@Composable
fun CatalogRoot() {
    val context = LocalContext.current
    val repo = remember { AppCatalogRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var loggedIn by remember { mutableStateOf(repo.hasSession()) }
    var loading by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<CatalogApp>>(emptyList()) }
    var section by remember { mutableStateOf(CatalogSection.DASHBOARD) }
    var detailAppId by remember { mutableStateOf<String?>(null) }
    var showNewApp by remember { mutableStateOf(false) }
    var editAppId by remember { mutableStateOf<String?>(null) }
    var iconTarget by remember { mutableStateOf<CatalogApp?>(null) }
    var screenshotTarget by remember { mutableStateOf<CatalogApp?>(null) }
    var apkTarget by remember { mutableStateOf<CatalogApp?>(null) }
    var pendingApk by remember { mutableStateOf<PendingApk?>(null) }
    var deleteTarget by remember { mutableStateOf<CatalogApp?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun reload() {
        scope.launch {
            loading = true
            runCatching { repo.listApps() }
                .onSuccess { apps = it }
                .onFailure {
                    if ((it.message ?: "").contains("Accesso richiesto", ignoreCase = true)) {
                        repo.logout()
                        loggedIn = false
                    }
                    toast(it.message ?: "Errore nel caricamento")
                }
            loading = false
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) reload()
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = iconTarget
        iconTarget = null
        if (uri != null && target != null) {
            scope.launch {
                loading = true
                runCatching { repo.uploadIcon(target.id, uri) }
                    .onSuccess { toast("Icona aggiornata"); reload() }
                    .onFailure { toast(it.message ?: "Errore caricamento icona") }
                loading = false
            }
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = screenshotTarget
        screenshotTarget = null
        if (uri != null && target != null) {
            scope.launch {
                loading = true
                runCatching { repo.uploadScreenshot(target.id, uri) }
                    .onSuccess { toast("Screenshot aggiunto"); reload() }
                    .onFailure { toast(it.message ?: "Errore caricamento screenshot") }
                loading = false
            }
        }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = apkTarget
        apkTarget = null
        if (uri != null && target != null) pendingApk = PendingApk(target, uri)
    }

    if (!loggedIn) {
        LoginScreen(
            emailSaved = repo.savedEmail(),
            loading = loading,
            onLogin = { email, password ->
                scope.launch {
                    loading = true
                    runCatching { repo.login(email, password) }
                        .onSuccess { loggedIn = true }
                        .onFailure { toast(it.message ?: "Accesso non riuscito") }
                    loading = false
                }
            }
        )
        return
    }

    val detailApp = detailAppId?.let { id -> apps.firstOrNull { it.id == id } }

    Scaffold(
        containerColor = Bg,
        topBar = {
            GestionaleHeader(
                onMenu = { menuOpen = true },
                menuOpen = menuOpen,
                onDismissMenu = { menuOpen = false },
                onRefresh = {
                    menuOpen = false
                    reload()
                },
                onLogout = {
                    menuOpen = false
                    repo.logout()
                    apps = emptyList()
                    loggedIn = false
                }
            )
        },
        bottomBar = {
            if (detailApp == null) BottomPills(section, onSection = { section = it })
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Bg)
        ) {
            if (detailApp != null) {
                AppDetailScreen(
                    app = detailApp,
                    repo = repo,
                    loading = loading,
                    onBack = { detailAppId = null },
                    onEdit = { editAppId = detailApp.id },
                    onTogglePublish = {
                        scope.launch {
                            loading = true
                            runCatching { repo.setPublished(detailApp, !detailApp.isPublished) }
                                .onSuccess { reload() }
                                .onFailure { toast(it.message ?: "Errore aggiornamento") }
                            loading = false
                        }
                    },
                    onIcon = {
                        iconTarget = detailApp
                        iconPicker.launch(arrayOf("image/*"))
                    },
                    onScreenshot = {
                        screenshotTarget = detailApp
                        screenshotPicker.launch(arrayOf("image/*"))
                    },
                    onApk = {
                        apkTarget = detailApp
                        apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    },
                    onDelete = { deleteTarget = detailApp }
                )
            } else {
                when (section) {
                    CatalogSection.DASHBOARD -> DashboardScreen(
                        apps = apps,
                        loading = loading,
                        onNewApp = { showNewApp = true },
                        onApps = { section = CatalogSection.APPS },
                        onReleases = { section = CatalogSection.RELEASES }
                    )
                    CatalogSection.APPS -> AppsGridScreen(
                        apps = apps,
                        repo = repo,
                        loading = loading,
                        onOpen = { detailAppId = it.id },
                        onNew = { showNewApp = true },
                        onApk = {
                            apkTarget = it
                            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                        }
                    )
                    CatalogSection.RELEASES -> ReleasesScreen(apps)
                    CatalogSection.SITE -> SiteScreen(apps, repo, onOpen = { detailAppId = it.id })
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = Blue,
                    trackColor = Color.Transparent
                )
            }
        }
    }

    if (showNewApp || editAppId != null) {
        AppEditorDialog(
            initial = editAppId?.let { id -> apps.firstOrNull { it.id == id } },
            onDismiss = {
                showNewApp = false
                editAppId = null
            },
            onSave = { name, slug, packageName, shortDescription, description, published ->
                scope.launch {
                    loading = true
                    runCatching {
                        repo.saveApp(
                            existingId = editAppId,
                            name = name,
                            slug = slug,
                            packageName = packageName,
                            shortDescription = shortDescription,
                            description = description,
                            isPublished = published
                        )
                    }.onSuccess { saved ->
                        showNewApp = false
                        editAppId = null
                        detailAppId = saved.id
                        reload()
                    }.onFailure { toast(it.message ?: "Errore salvataggio") }
                    loading = false
                }
            }
        )
    }

    pendingApk?.let { pending ->
        ReleaseUploadDialog(
            appName = pending.app.name,
            onDismiss = { pendingApk = null },
            onUpload = { versionName, versionCode, changelog ->
                pendingApk = null
                scope.launch {
                    loading = true
                    runCatching {
                        repo.uploadApk(
                            appId = pending.app.id,
                            uri = pending.uri,
                            versionName = versionName,
                            versionCode = versionCode,
                            changelog = changelog
                        )
                    }.onSuccess {
                        toast("APK archiviato su Supabase")
                        reload()
                    }.onFailure { toast(it.message ?: "Errore caricamento APK") }
                    loading = false
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminare " + target.name + "?", fontWeight = FontWeight.Black) },
            text = { Text("La scheda verrà eliminata dal catalogo. Gli APK già caricati nello Storage restano conservati.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            loading = true
                            runCatching { repo.deleteApp(target.id) }
                                .onSuccess {
                                    detailAppId = null
                                    reload()
                                }
                                .onFailure { toast(it.message ?: "Errore eliminazione") }
                            loading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun LoginScreen(
    emailSaved: String,
    loading: Boolean,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf(emailSaved) }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Navy)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Text("Gestionale", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Archivio App", color = Color.White.copy(alpha = .82f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WhiteCard {
                Text("Accesso amministratore", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Collegamento al catalogo Supabase.", color = Muted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onLogin(email, password) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = !loading && email.isNotBlank() && password.isNotBlank()
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Accedi", fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun GestionaleHeader(
    onMenu: () -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy • HH:mm:ss", Locale.ITALY)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(Navy)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Text("Gestionale", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text(now.format(formatter), color = Color.White.copy(alpha = .82f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.align(Alignment.CenterEnd)) {
            Surface(
                modifier = Modifier.size(54.dp).clickable(onClick = onMenu),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1D263A),
                border = BorderStroke(1.dp, Color(0xFF3B465B))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Menu, "Menu", tint = Color.White, modifier = Modifier.size(31.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text("Aggiorna") },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = onRefresh
                )
                DropdownMenuItem(
                    text = { Text("Esci") },
                    leadingIcon = { Icon(Icons.Default.Logout, null) },
                    onClick = onLogout
                )
            }
        }
    }
}

@Composable
private fun BottomPills(section: CatalogSection, onSection: (CatalogSection) -> Unit) {
    Surface(color = Color.White, shadowElevation = 9.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CatalogSection.entries.forEach { item ->
                val active = item == section
                Surface(
                    modifier = Modifier.weight(1f).height(50.dp).clickable { onSection(item) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (active) Cream else Color.White,
                    border = BorderStroke(1.3.dp, if (active) Gold else Line)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    apps: List<CatalogApp>,
    loading: Boolean,
    onNewApp: () -> Unit,
    onApps: () -> Unit,
    onReleases: () -> Unit
) {
    val total = apps.size
    val published = apps.count { it.isPublished }
    val hidden = total - published
    val releaseCount = apps.sumOf { it.releases.size }
    val buckets = remember(apps) { releaseBuckets(apps) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WhiteCard {
                Text("NOVITÀ IN PRIMO PIANO", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (total == 0) "Archivio pronto" else if (hidden == 0) "Catalogo in ordine" else "Catalogo da rifinire",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                val message = when {
                    total == 0 -> "Aggiungi la prima app e partiamo puliti."
                    hidden > 0 -> hidden.toString() + if (hidden == 1) " app è nascosta." else " app sono nascoste."
                    else -> "Tutte le app archiviate risultano pubblicate."
                }
                Text(message, color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("VERSIONI ULTIMI 6 MESI", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("APK", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                MiniBarChart(buckets)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBox("APP TOTALI", total.toString(), "schede archiviate", Ink, Modifier.weight(1f))
                MetricBox("PUBBLICATE", published.toString(), "visibili online", Green, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBox("VERSIONI APK", releaseCount.toString(), "release archiviate", Ink, Modifier.weight(1f))
                MetricBox("NASCOSTE", hidden.toString(), "non pubblicate", if (hidden > 0) Gold else Green, Modifier.weight(1f))
            }
        }

        item {
            WhiteCard {
                Text("Azioni rapide", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                BigAction("Nuova app", Blue, onNewApp, enabled = !loading)
                Spacer(Modifier.height(10.dp))
                BigAction("Gestisci app", SoftBlue, onApps, textColor = Ink, enabled = !loading)
                Spacer(Modifier.height(10.dp))
                BigAction("Archivio versioni", SoftBlue, onReleases, textColor = Ink, enabled = !loading)
            }
        }
    }
}

@Composable
private fun MiniBarChart(items: List<Pair<String, Int>>) {
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEachIndexed { index, pair ->
            val fraction = pair.second.toFloat() / max
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((16 + 62 * fraction).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (index == items.lastIndex) Gold else Blue)
                )
                Spacer(Modifier.height(7.dp))
                Text(pair.first, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MetricBox(title: String, value: String, subtitle: String, valueColor: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(142.dp),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, Line),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 31.sp, fontWeight = FontWeight.Black, color = valueColor)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BigAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    textColor: Color = Color.White,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(57.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AppsGridScreen(
    apps: List<CatalogApp>,
    repo: AppCatalogRepository,
    loading: Boolean,
    onOpen: (CatalogApp) -> Unit,
    onNew: () -> Unit,
    onApk: (CatalogApp) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Tutte") }
    var filterMenu by remember { mutableStateOf(false) }

    val filtered = remember(apps, search, filter) {
        apps.filter { app ->
            val text = (app.name + " " + app.packageName + " " + app.shortDescription + " " + app.slug).lowercase()
            val queryOk = search.isBlank() || text.contains(search.lowercase().trim())
            val statusOk = when (filter) {
                "Pubblicate" -> app.isPublished
                "Nascoste" -> !app.isPublished
                else -> true
            }
            queryOk && statusOk
        }
    }

    Column(Modifier.fillMaxSize()) {
        WhiteCard(
            modifier = Modifier.fillMaxWidth().padding(18.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cerca per nome, package, descrizione...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onNew,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Nuova app", fontWeight = FontWeight.Black)
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { filterMenu = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(filter, color = Ink, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null, tint = Ink)
                    }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        listOf("Tutte", "Pubblicate", "Nascoste").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = { filter = value; filterMenu = false }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusPill("Tutte le app", filter == "Tutte", onClick = { filter = "Tutte" })
                StatusPill(
                    "Pubblicate (" + apps.count { it.isPublished } + ")",
                    filter == "Pubblicate",
                    onClick = { filter = "Pubblicate" },
                    green = true
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                Text(
                    "Totale app: " + filtered.size,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Muted,
                    fontWeight = FontWeight.Black
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gridItems(filtered, key = { it.id }) { app ->
                CatalogAppCard(
                    app = app,
                    repo = repo,
                    onOpen = { onOpen(app) },
                    onApk = { onApk(app) },
                    enabled = !loading
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean, onClick: () -> Unit, green: Boolean = false) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (green && active) SoftGreen else if (active) Cream else Color.White,
        border = BorderStroke(1.2.dp, if (green && active) Color(0xFF86EFAC) else if (active) Gold else Line)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (green && active) Green else Ink,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CatalogAppCard(
    app: CatalogApp,
    repo: AppCatalogRepository,
    onOpen: () -> Unit,
    onApk: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Line),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(10.dp)) {
            AppImage(
                app = app,
                repo = repo,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.08f).clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                app.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                app.packageName.ifBlank { app.slug },
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "v" + (app.latestRelease?.versionName ?: "—") + " • 📷 " + app.screenshots.size,
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.latestRelease?.let { formatBytes(it.fileSize) } ?: "Nessun APK",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(if (app.isPublished) Green else Danger)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = enabled,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text("Apri", fontWeight = FontWeight.Black) }
                Button(
                    onClick = onApk,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = enabled,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                ) { Text("APK", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun AppImage(app: CatalogApp, repo: AppCatalogRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = app.iconPath ?: app.screenshots.minByOrNull { it.sortOrder }?.storagePath
    if (path.isNullOrBlank()) {
        Box(modifier.background(SoftBlue), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Android, null, tint = Blue, modifier = Modifier.size(62.dp))
        }
        return
    }

    val model = remember(path, repo.accessToken()) {
        ImageRequest.Builder(context)
            .data(repo.storageUrl(path))
            .apply { repo.storageHeaders().forEach { (key, value) -> addHeader(key, value) } }
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = app.name,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun ReleasesScreen(apps: List<CatalogApp>) {
    val rows = remember(apps) {
        apps.flatMap { app -> app.releases.map { release -> app to release } }
            .sortedByDescending { it.second.createdAt }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Versioni APK", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Tutte le release archiviate su Supabase.", color = Muted, fontWeight = FontWeight.Bold)
        }
        if (rows.isEmpty()) {
            item {
                WhiteCard {
                    Text("Nessuna versione caricata", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Quando carichi il primo APK compare qui.", color = Muted)
                }
            }
        }
        items(rows, key = { it.second.id }) { pair ->
            ReleaseRow(pair.first, pair.second)
        }
    }
}

@Composable
private fun ReleaseRow(app: CatalogApp, release: AppRelease) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Line),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(15.dp), color = SoftBlue) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Android, null, tint = Blue) }
            }
            Column(Modifier.weight(1f)) {
                Text(app.name, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("Versione " + release.versionName, color = Muted, fontWeight = FontWeight.Bold)
                if (release.changelog.isNotBlank()) {
                    Text(release.changelog, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Muted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(release.fileSize), fontWeight = FontWeight.Black)
                Text(formatDate(release.createdAt), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SiteScreen(apps: List<CatalogApp>, repo: AppCatalogRepository, onOpen: (CatalogApp) -> Unit) {
    val published = apps.filter { it.isPublished }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            WhiteCard {
                Text("Catalogo pubblico", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Queste sono le app che il futuro sito mostrerà. Database e file sono già separati dalla grafica, quindi il dominio dopo non ci obbliga a rifare tutto.",
                    color = Muted,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SiteBadge(published.size.toString() + " pubblicate", Green)
                    SiteBadge((apps.size - published.size).toString() + " nascoste", Gold)
                }
            }
        }

        items(published, key = { it.id }) { app ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(app) },
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.2.dp, Line)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppImage(app, repo, Modifier.size(76.dp).clip(RoundedCornerShape(17.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(app.shortDescription.ifBlank { app.packageName }, color = Muted, maxLines = 2)
                        Text("v" + (app.latestRelease?.versionName ?: "—"), color = Blue, fontWeight = FontWeight.Black)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun SiteBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .35f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = color, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AppDetailScreen(
    app: CatalogApp,
    repo: AppCatalogRepository,
    loading: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTogglePublish: () -> Unit,
    onIcon: () -> Unit,
    onScreenshot: () -> Unit,
    onApk: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp).clickable(onClick = onBack),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Line)
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, null) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text(app.packageName.ifBlank { app.slug }, color = Muted, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (app.isPublished) SoftGreen else Color(0xFFFFF1F2),
                    border = BorderStroke(1.dp, if (app.isPublished) Color(0xFF86EFAC) else Color(0xFFFDA4AF))
                ) {
                    Text(
                        if (app.isPublished) "Pubblicata" else "Nascosta",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = if (app.isPublished) Green else Danger,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        item {
            WhiteCard {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppImage(app, repo, Modifier.size(118.dp).clip(RoundedCornerShape(22.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("Scheda app", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(app.shortDescription.ifBlank { "Nessuna descrizione breve" }, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text("Slug: " + app.slug, color = Muted, fontSize = 12.sp)
                        Text("Screenshot: " + app.screenshots.size, color = Muted, fontSize = 12.sp)
                        Text("Versioni: " + app.releases.size, color = Muted, fontSize = 12.sp)
                    }
                }
                if (app.description.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Line)
                    Spacer(Modifier.height(12.dp))
                    Text(app.description, color = Muted, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                }
            }
        }

        item {
            WhiteCard {
                Text("Gestione", fontSize = 23.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailAction("Modifica", Icons.Default.Edit, Blue, onEdit, Modifier.weight(1f), loading)
                    DetailAction("Icona", Icons.Default.Image, Purple, onIcon, Modifier.weight(1f), loading)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailAction("Screenshot", Icons.Default.Collections, Cyan, onScreenshot, Modifier.weight(1f), loading)
                    DetailAction("Carica APK", Icons.Default.UploadFile, Green, onApk, Modifier.weight(1f), loading)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onTogglePublish,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !loading,
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(if (app.isPublished) Icons.Default.VisibilityOff else Icons.Default.Public, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (app.isPublished) "Nascondi dal sito" else "Pubblica sul sito", fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            WhiteCard {
                Text("Ultima versione", fontSize = 23.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(9.dp))
                val latest = app.latestRelease
                if (latest == null) {
                    Text("Nessun APK caricato.", color = Muted)
                } else {
                    Text("v" + latest.versionName, fontSize = 27.sp, fontWeight = FontWeight.Black, color = Blue)
                    Text(formatBytes(latest.fileSize) + " • " + formatDate(latest.createdAt), color = Muted, fontWeight = FontWeight.Bold)
                    if (latest.changelog.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(latest.changelog, color = Muted)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                border = BorderStroke(1.2.dp, Danger.copy(alpha = .55f)),
                shape = RoundedCornerShape(17.dp),
                enabled = !loading
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Elimina scheda app", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun DetailAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    loading: Boolean
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = !loading,
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(7.dp))
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Line),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun AppEditorDialog(
    initial: CatalogApp?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var slug by remember(initial?.id) { mutableStateOf(initial?.slug.orEmpty()) }
    var slugTouched by remember(initial?.id) { mutableStateOf(initial != null) }
    var packageName by remember(initial?.id) { mutableStateOf(initial?.packageName.orEmpty()) }
    var shortDescription by remember(initial?.id) { mutableStateOf(initial?.shortDescription.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var published by remember(initial?.id) { mutableStateOf(initial?.isPublished ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuova app" else "Modifica app", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        name,
                        {
                            name = it
                            if (!slugTouched) slug = slugify(it)
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("Nome app") },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        slug,
                        {
                            slug = slugify(it)
                            slugTouched = true
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("Slug sito") },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        packageName,
                        { packageName = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Package Android") },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        shortDescription,
                        { shortDescription = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Descrizione breve") },
                        minLines = 2,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        description,
                        { description = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Descrizione completa") },
                        minLines = 4,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = published, onCheckedChange = { published = it })
                        Spacer(Modifier.width(10.dp))
                        Text("Pubblicata sul sito", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, slug, packageName, shortDescription, description, published) },
                enabled = name.isNotBlank() && slug.isNotBlank()
            ) { Text("Salva", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun ReleaseUploadDialog(
    appName: String,
    onDismiss: () -> Unit,
    onUpload: (String, Int?, String) -> Unit
) {
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var changelog by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo APK", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(appName, fontWeight = FontWeight.Black, color = Blue)
                OutlinedTextField(
                    versionName,
                    { versionName = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Versione, es. 1.0.0") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
                OutlinedTextField(
                    versionCode,
                    { versionCode = it.filter(Char::isDigit) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Version code") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
                OutlinedTextField(
                    changelog,
                    { changelog = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Novità / changelog") },
                    minLines = 3,
                    shape = RoundedCornerShape(15.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpload(versionName, versionCode.toIntOrNull(), changelog) },
                enabled = versionName.isNotBlank()
            ) { Text("Carica", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

private fun slugify(value: String): String {
    return Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) String.format(Locale.ITALY, "%.1f MB", mb)
    else String.format(Locale.ITALY, "%.0f KB", bytes / 1024.0)
}

private fun formatDate(value: String): String {
    if (value.length < 10) return value
    return runCatching {
        LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrElse { value.take(10) }
}

private fun releaseBuckets(apps: List<CatalogApp>): List<Pair<String, Int>> {
    val now = YearMonth.now()
    val formatter = DateTimeFormatter.ofPattern("MMM", Locale.ITALY)
    return (5 downTo 0).map { offset ->
        val month = now.minusMonths(offset.toLong())
        val count = apps.sumOf { app ->
            app.releases.count { release ->
                runCatching { YearMonth.parse(release.createdAt.take(7)) }.getOrNull() == month
            }
        }
        month.format(formatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString() } to count
    }
}
