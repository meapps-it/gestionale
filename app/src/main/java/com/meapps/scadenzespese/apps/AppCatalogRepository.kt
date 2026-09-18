package com.meapps.scadenzespese.apps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.meapps.scadenzespese.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class CatalogApp(
    val id: String,
    val slug: String,
    val name: String,
    val packageName: String,
    val shortDescription: String,
    val description: String,
    val iconPath: String?,
    val isPublished: Boolean,
    val updatedAt: String?
)

class AppCatalogRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("me_apps_catalog", Context.MODE_PRIVATE)

    fun hasSession(): Boolean = !prefs.getString("access_token", null).isNullOrBlank()
    fun savedEmail(): String = prefs.getString("email", "") ?: ""

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
    }

    fun logout() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }

    suspend fun listApps(): List<CatalogApp> {
        val body = request(
            "GET",
            "/rest/v1/me_apps?select=*&order=updated_at.desc"
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
            .put("package_name", packageName.trim().ifBlank { JSONObject.NULL })
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

    suspend fun uploadApk(
        appId: String,
        uri: Uri,
        versionName: String,
        versionCode: Int?,
        changelog: String
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
            .toString()
        request(
            "POST",
            "/rest/v1/me_app_releases",
            body = payload,
            prefer = "return=minimal"
        )
    }

    private suspend fun uploadObject(appId: String, folder: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val original = queryFileName(uri).ifBlank { "file" }
        val safeName = original.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = "$appId/$folder/${System.currentTimeMillis()}_$safeName"
        var token = accessToken() ?: throw IllegalStateException("Accesso richiesto")
        var result = rawUpload(path, uri, token)
        if (result.code == 401 && refreshSessionInternal()) {
            token = accessToken() ?: throw IllegalStateException("Sessione scaduta")
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
        var token = accessToken() ?: throw IllegalStateException("Accesso richiesto")
        var result = rawRequest(method, path, body, token, "application/json", prefer)
        if (result.code == 401 && refreshSessionInternal()) {
            token = accessToken() ?: throw IllegalStateException("Sessione scaduta")
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
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
            if (body != null) doOutput = true
        }
        if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection.useResult()
    }

    private fun rawUpload(path: String, uri: Uri, token: String): HttpResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
            ?: if (path.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/octet-stream"
        val connection = (URL("${BuildConfig.SUPABASE_URL}/storage/v1/object/me-apps/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setChunkedStreamingMode(1024 * 1024)
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "false")
        }
        resolver.openInputStream(uri)?.use { input ->
            connection.outputStream.use { output -> input.copyTo(output, 1024 * 1024) }
        } ?: throw IllegalStateException("Impossibile leggere il file selezionato")
        return connection.useResult()
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

    private fun accessToken(): String? = prefs.getString("access_token", null)

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

    private fun parseApp(json: JSONObject) = CatalogApp(
        id = json.getString("id"),
        slug = json.getString("slug"),
        name = json.getString("name"),
        packageName = json.optString("package_name", ""),
        shortDescription = json.optString("short_description", ""),
        description = json.optString("description", ""),
        iconPath = if (json.isNull("icon_path")) null else json.optString("icon_path"),
        isPublished = json.optBoolean("is_published", false),
        updatedAt = if (json.isNull("updated_at")) null else json.optString("updated_at")
    )

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

    private fun HttpURLConnection.useResult(): HttpResult {
        return try {
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult(code, body)
        } finally {
            disconnect()
        }
    }
}
