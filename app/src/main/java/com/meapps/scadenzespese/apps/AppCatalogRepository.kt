package com.meapps.scadenzespese.apps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.meapps.scadenzespese.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

data class AppRelease(
    val id: String,
    val versionName: String,
    val versionCode: Int?,
    val apkPath: String,
    val changelog: String,
    val fileSize: Long?,
    val isPublished: Boolean,
    val publishedAt: String?,
    val createdAt: String
)

data class AppScreenshot(
    val id: String,
    val storagePath: String,
    val caption: String,
    val sortOrder: Int,
    val createdAt: String
)

data class CatalogApp(
    val id: String,
    val slug: String,
    val name: String,
    val packageName: String,
    val shortDescription: String,
    val description: String,
    val iconPath: String?,
    val isPublished: Boolean,
    val publishedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val releases: List<AppRelease> = emptyList(),
    val screenshots: List<AppScreenshot> = emptyList()
) {
    val latestRelease: AppRelease?
        get() = releases.maxByOrNull { it.createdAt }

    val latestPublishedRelease: AppRelease?
        get() = releases.filter { it.isPublished }
            .maxByOrNull { it.publishedAt ?: it.createdAt }
}

class AppCatalogRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("me_apps_catalog", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun hasSession(): Boolean = !prefs.getString("access_token", null).isNullOrBlank()
    fun savedEmail(): String = prefs.getString("email", "") ?: ""
    fun accessToken(): String = prefs.getString("access_token", "") ?: ""

    fun storageUrl(path: String): String {
        val clean = path.trim().trimStart('/')
        return "${BuildConfig.SUPABASE_URL}/storage/v1/object/authenticated/me-apps/$clean"
    }

    fun storageHeaders(): Map<String, String> = mapOf(
        "apikey" to BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        "Authorization" to "Bearer ${accessToken()}"
    )

    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val result = rawRequest(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = payload,
            token = null,
            contentType = "application/json"
        )
        ensureSuccess(result)
        saveSession(JSONObject(result.body), email.trim())

        // Verifica subito che l'account abbia i permessi admin per il catalogo.
        val probe = rawRequest(
            method = "POST",
            path = "/rest/v1/rpc/is_me_apps_admin",
            body = "{}",
            token = accessToken(),
            contentType = "application/json"
        )
        val allowed = probe.code in 200..299 && probe.body.trim().equals("true", ignoreCase = true)
        if (!allowed) {
            logout()
            throw IllegalStateException("Questo account non ha accesso amministratore al catalogo App")
        }
    }

    fun logout() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }

    suspend fun listApps(): List<CatalogApp> {
        val select = "*,me_app_releases(*),me_app_screenshots(*)"
        val body = request(
            "GET",
            "/rest/v1/me_apps?select=$select&order=updated_at.desc"
        )
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) add(parseApp(array.getJSONObject(i)))
        }
    }

    suspend fun saveApp(
        existingId: String?,
        name: String,
        slug: String,
        packageName: String,
        shortDescription: String,
        description: String,
        isPublished: Boolean
    ): CatalogApp {
        val payload = JSONObject()
            .put("name", name.trim())
            .put("slug", slug.trim().lowercase())
            .put("package_name", if (packageName.isBlank()) JSONObject.NULL else packageName.trim())
            .put("short_description", shortDescription.trim())
            .put("description", description.trim())
            .put("is_published", isPublished)
            .put("published_at", if (isPublished) Instant.now().toString() else JSONObject.NULL)
            .toString()

        val body = if (existingId == null) {
            request(
                "POST",
                "/rest/v1/me_apps?select=*",
                body = payload,
                prefer = "return=representation"
            )
        } else {
            request(
                "PATCH",
                "/rest/v1/me_apps?id=eq.$existingId&select=*",
                body = payload,
                prefer = "return=representation"
            )
        }
        return parseApp(JSONArray(body).getJSONObject(0))
    }

    suspend fun setPublished(app: CatalogApp, published: Boolean) {
        val payload = JSONObject()
            .put("is_published", published)
            .put("published_at", if (published) Instant.now().toString() else JSONObject.NULL)
            .toString()
        request(
            "PATCH",
            "/rest/v1/me_apps?id=eq.${app.id}",
            body = payload,
            prefer = "return=minimal"
        )
    }

    suspend fun deleteApp(appId: String) {
        request(
            "DELETE",
            "/rest/v1/me_apps?id=eq.$appId",
            prefer = "return=minimal"
        )
    }

    suspend fun uploadIcon(appId: String, uri: Uri) {
        val path = uploadObject(appId, "icon", uri)
        val payload = JSONObject().put("icon_path", path).toString()
        request(
            "PATCH",
            "/rest/v1/me_apps?id=eq.$appId",
            body = payload,
            prefer = "return=minimal"
        )
    }

    suspend fun uploadScreenshot(appId: String, uri: Uri) {
        val path = uploadObject(appId, "screenshots", uri)
        val payload = JSONObject()
            .put("app_id", appId)
            .put("storage_path", path)
            .toString()
        request(
            "POST",
            "/rest/v1/me_app_screenshots",
            body = payload,
            prefer = "return=minimal"
        )
    }

    suspend fun deleteScreenshot(screenshot: AppScreenshot) {
        request(
            "DELETE",
            "/rest/v1/me_app_screenshots?id=eq.${screenshot.id}",
            prefer = "return=minimal"
        )
        deleteStorageObject(screenshot.storagePath)
    }

    suspend fun uploadApk(
        appId: String,
        uri: Uri,
        versionName: String,
        versionCode: Int?,
        changelog: String,
        publish: Boolean
    ) {
        val path = uploadObject(appId, "releases", uri)
        val fileSize = queryFileSize(uri)
        val payload = JSONObject()
            .put("app_id", appId)
            .put("version_name", versionName.trim())
            .put("version_code", versionCode ?: JSONObject.NULL)
            .put("apk_path", path)
            .put("changelog", changelog.trim())
            .put("file_size", fileSize ?: JSONObject.NULL)
            .put("is_published", publish)
            .put("published_at", if (publish) Instant.now().toString() else JSONObject.NULL)
            .toString()
        request(
            "POST",
            "/rest/v1/me_app_releases",
            body = payload,
            prefer = "return=minimal"
        )
    }

    suspend fun setReleasePublished(releaseId: String, published: Boolean) {
        val payload = JSONObject()
            .put("is_published", published)
            .put("published_at", if (published) Instant.now().toString() else JSONObject.NULL)
            .toString()
        request(
            "PATCH",
            "/rest/v1/me_app_releases?id=eq.$releaseId",
            body = payload,
            prefer = "return=minimal"
        )
    }

    private suspend fun deleteStorageObject(path: String) = withContext(Dispatchers.IO) {
        var token = accessToken()
        var result = rawRequest(
            method = "DELETE",
            path = "/storage/v1/object/me-apps/${path.trimStart('/')}",
            body = null,
            token = token,
            contentType = "application/json"
        )
        if (result.code == 401 && refreshSessionInternal()) {
            token = accessToken()
            result = rawRequest(
                method = "DELETE",
                path = "/storage/v1/object/me-apps/${path.trimStart('/')}",
                body = null,
                token = token,
                contentType = "application/json"
            )
        }
        if (result.code !in 200..299 && result.code != 404) ensureSuccess(result)
    }

    private suspend fun uploadObject(appId: String, folder: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val original = queryFileName(uri).ifBlank { "file" }
        val safeName = original.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = "$appId/$folder/${System.currentTimeMillis()}_$safeName"
        var token = accessToken().ifBlank { throw IllegalStateException("Accesso richiesto") }
        var result = rawUpload(path, uri, token)
        if (result.code == 401 && refreshSessionInternal()) {
            token = accessToken().ifBlank { throw IllegalStateException("Sessione scaduta") }
            result = rawUpload(path, uri, token)
        }
        ensureSuccess(result)
        path
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        var token = accessToken().ifBlank { throw IllegalStateException("Accesso richiesto") }
        var result = rawRequest(method, path, body, token, "application/json", prefer)
        if (result.code == 401 && refreshSessionInternal()) {
            token = accessToken().ifBlank { throw IllegalStateException("Sessione scaduta") }
            result = rawRequest(method, path, body, token, "application/json", prefer)
        }
        ensureSuccess(result)
        result.body
    }

    private fun rawRequest(
        method: String,
        path: String,
        body: String?,
        token: String?,
        contentType: String,
        prefer: String? = null
    ): HttpResult {
        val requestBody = body?.toRequestBody(contentType.toMediaTypeOrNull())
        val builder = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + path)
            .method(method, requestBody)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        if (!prefer.isNullOrBlank()) builder.header("Prefer", prefer)

        client.newCall(builder.build()).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    private fun rawUpload(path: String, uri: Uri, token: String): HttpResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
            ?: if (path.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/octet-stream"

        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull()
            override fun contentLength(): Long = queryFileSize(uri) ?: -1L
            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw IllegalStateException("Impossibile leggere il file selezionato")
                input.source().use { source -> sink.writeAll(source) }
            }
        }

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/me-apps/$path")
            .post(body)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .header("x-upsert", "false")
            .build()

        client.newCall(request).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    private fun refreshSessionInternal(): Boolean {
        val refresh = prefs.getString("refresh_token", null) ?: return false
        val payload = JSONObject().put("refresh_token", refresh).toString()
        val result = rawRequest(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            body = payload,
            token = null,
            contentType = "application/json"
        )
        if (result.code !in 200..299) {
            logout()
            return false
        }
        saveSession(JSONObject(result.body), savedEmail())
        return true
    }

    private fun saveSession(json: JSONObject, email: String) {
        prefs.edit()
            .putString("access_token", json.getString("access_token"))
            .putString("refresh_token", json.getString("refresh_token"))
            .putString("email", email)
            .apply()
    }

    private fun queryFileName(uri: Uri): String {
        var result = ""
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) result = cursor.getString(0) ?: ""
        }
        return result
    }

    private fun queryFileSize(uri: Uri): Long? {
        var result: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) result = cursor.getLong(0)
        }
        return result
    }

    private fun parseRelease(json: JSONObject) = AppRelease(
        id = json.getString("id"),
        versionName = json.optString("version_name", ""),
        versionCode = if (json.isNull("version_code")) null else json.optInt("version_code"),
        apkPath = json.optString("apk_path", ""),
        changelog = json.optString("changelog", ""),
        fileSize = if (json.isNull("file_size")) null else json.optLong("file_size"),
        isPublished = json.optBoolean("is_published", false),
        publishedAt = if (json.isNull("published_at")) null else json.optString("published_at"),
        createdAt = json.optString("created_at", "")
    )

    private fun parseScreenshot(json: JSONObject) = AppScreenshot(
        id = json.getString("id"),
        storagePath = json.optString("storage_path", ""),
        caption = json.optString("caption", ""),
        sortOrder = json.optInt("sort_order", 0),
        createdAt = json.optString("created_at", "")
    )

    private fun parseApp(json: JSONObject): CatalogApp {
        val releaseArray = json.optJSONArray("me_app_releases") ?: JSONArray()
        val screenshotArray = json.optJSONArray("me_app_screenshots") ?: JSONArray()
        val releases = buildList {
            for (i in 0 until releaseArray.length()) add(parseRelease(releaseArray.getJSONObject(i)))
        }
        val screenshots = buildList {
            for (i in 0 until screenshotArray.length()) add(parseScreenshot(screenshotArray.getJSONObject(i)))
        }

        return CatalogApp(
            id = json.getString("id"),
            slug = json.optString("slug", ""),
            name = json.optString("name", ""),
            packageName = if (json.isNull("package_name")) "" else json.optString("package_name", ""),
            shortDescription = json.optString("short_description", ""),
            description = json.optString("description", ""),
            iconPath = if (json.isNull("icon_path")) null else json.optString("icon_path"),
            isPublished = json.optBoolean("is_published", false),
            publishedAt = if (json.isNull("published_at")) null else json.optString("published_at"),
            createdAt = json.optString("created_at", ""),
            updatedAt = json.optString("updated_at", ""),
            releases = releases,
            screenshots = screenshots
        )
    }

    private fun ensureSuccess(result: HttpResult) {
        if (result.code in 200..299) return
        val message = runCatching {
            val json = JSONObject(result.body)
            json.optString("message")
                .ifBlank { json.optString("msg") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { json.optString("error") }
        }.getOrNull().orEmpty().ifBlank { "Errore ${result.code}" }
        throw IllegalStateException(message)
    }

    private data class HttpResult(val code: Int, val body: String)
}
