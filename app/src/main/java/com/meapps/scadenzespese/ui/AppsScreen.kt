package com.meapps.scadenzespese.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meapps.scadenzespese.apps.AppCatalogRepository
import com.meapps.scadenzespese.apps.CatalogApp
import com.meapps.scadenzespese.ui.theme.Line
import com.meapps.scadenzespese.ui.theme.Positive
import java.text.Normalizer
import kotlinx.coroutines.launch

private data class PendingApk(val app: CatalogApp, val uri: Uri)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AppCatalogRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(repo.hasSession()) }
    var apps by remember { mutableStateOf<List<CatalogApp>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CatalogApp?>(null) }
    var fileTarget by remember { mutableStateOf<CatalogApp?>(null) }
    var apkTarget by remember { mutableStateOf<CatalogApp?>(null) }
    var pendingApk by remember { mutableStateOf<PendingApk?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun reload() {
        scope.launch {
            loading = true
            runCatching { repo.listApps() }
                .onSuccess { apps = it }
                .onFailure {
                    if (it.message?.contains("Accesso richiesto", true) == true) loggedIn = false
                    toast(it.message ?: "Errore nel caricamento")
                }
            loading = false
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            loading = true
            runCatching { repo.listApps() }
                .onSuccess { apps = it }
                .onFailure {
                    loggedIn = false
                    toast(it.message ?: "Sessione non valida")
                }
            loading = false
        }
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = fileTarget
        fileTarget = null
        if (uri != null && target != null) {
            scope.launch {
                loading = true
                runCatching { repo.uploadIcon(target.id, uri) }
                    .onSuccess { toast("Icona caricata"); reload() }
                    .onFailure { toast(it.message ?: "Errore caricamento icona") }
                loading = false
            }
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = fileTarget
        fileTarget = null
        if (uri != null && target != null) {
            scope.launch {
                loading = true
                runCatching { repo.uploadScreenshot(target.id, uri) }
                    .onSuccess { toast("Screenshot archiviato") }
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
        CatalogLogin(
            savedEmail = repo.savedEmail(),
            loading = loading,
            onBack = onBack,
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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ Indietro") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    repo.logout()
                    loggedIn = false
                    apps = emptyList()
                }) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Esci")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Le mie App")
                    Text(
                        "APK, descrizioni, icone e screenshot su Supabase.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = { creating = true }, enabled = !loading) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nuova")
                }
            }
        }

        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        if (!loading && apps.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Nessuna app archiviata", fontWeight = FontWeight.Bold)
                        Text(
                            "Aggiungi la prima app. Finalmente un archivio invece della caccia all'APK disperso.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        items(apps, key = { it.id }) { app ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Android, null)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (app.packageName.isNotBlank()) {
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (app.isPublished) "Pubblicata" else "Nascosta") },
                            leadingIcon = {
                                Icon(
                                    if (app.isPublished) Icons.Default.Public else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        )
                    }

                    if (app.shortDescription.isNotBlank()) Text(app.shortDescription)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                fileTarget = app
                                iconPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/*"))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (app.iconPath == null) "Icona" else "Icona ✓")
                        }
                        OutlinedButton(
                            onClick = {
                                fileTarget = app
                                screenshotPicker.launch(arrayOf("image/*"))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Screenshot")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                apkTarget = app
                                apkPicker.launch(
                                    arrayOf(
                                        "application/vnd.android.package-archive",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Carica APK")
                        }
                        FilledTonalButton(
                            onClick = { editing = app },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Modifica")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                runCatching { repo.setPublished(app, !app.isPublished) }
                                    .onSuccess { reload() }
                                    .onFailure { toast(it.message ?: "Errore aggiornamento") }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Icon(if (app.isPublished) Icons.Default.VisibilityOff else Icons.Default.Publish, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (app.isPublished) "Nascondi dal sito" else "Pubblica sul sito")
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        AppEditDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { name, slug, packageName, shortDescription, description, published ->
                scope.launch {
                    loading = true
                    runCatching {
                        repo.saveApp(
                            existingId = editing?.id,
                            name = name,
                            slug = slug,
                            packageName = packageName,
                            shortDescription = shortDescription,
                            description = description,
                            isPublished = published
                        )
                    }.onSuccess {
                        creating = false
                        editing = null
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
                            changelog = changelog,
                            publish = false
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
}

@Composable
private fun CatalogLogin(
    savedEmail: String,
    loading: Boolean,
    onBack: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf(savedEmail) }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Indietro") }
        SectionTitle("Le mie App")
        Text("Accesso amministratore Supabase", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = { onLogin(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && email.isNotBlank() && password.isNotBlank()
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Accedi")
            }
        }
    }
}

@Composable
private fun AppEditDialog(
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
        title = { Text(if (initial == null) "Nuova app" else "Modifica app") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name,
                    {
                        name = it
                        if (!slugTouched) slug = slugify(it)
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Nome") },
                    singleLine = true
                )
                OutlinedTextField(
                    slug,
                    {
                        slug = slugify(it)
                        slugTouched = true
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Slug sito") },
                    singleLine = true
                )
                OutlinedTextField(
                    packageName,
                    { packageName = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Package Android") },
                    singleLine = true
                )
                OutlinedTextField(
                    shortDescription,
                    { shortDescription = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Descrizione breve") },
                    minLines = 2
                )
                OutlinedTextField(
                    description,
                    { description = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Descrizione completa") },
                    minLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = published, onCheckedChange = { published = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Pubblicata sul sito")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, slug, packageName, shortDescription, description, published)
                },
                enabled = name.isNotBlank() && slug.isNotBlank()
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } }
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
        title = { Text("Nuovo APK") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(appName, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    versionName,
                    { versionName = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Versione, es. 1.0.0") },
                    singleLine = true
                )
                OutlinedTextField(
                    versionCode,
                    { versionCode = it.filter(Char::isDigit) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Version code") },
                    singleLine = true
                )
                OutlinedTextField(
                    changelog,
                    { changelog = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Novità / changelog") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpload(versionName, versionCode.toIntOrNull(), changelog) },
                enabled = versionName.isNotBlank()
            ) { Text("Carica") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } }
    )
}

private fun slugify(value: String): String {
    return Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
