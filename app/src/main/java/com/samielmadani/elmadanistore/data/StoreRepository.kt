package com.samielmadani.elmadanistore.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class StoreRepository(private val context: Context) {
    private val client = OkHttpClient()
    private val preferences = context.getSharedPreferences("store", Context.MODE_PRIVATE)
    private val trackingStore = TrackingStore(context)
    private val username = "samielmadani"
    private val selfRepo = "Elmadani-Labs"

    suspend fun loadApps(): List<StoreApp> = withContext(Dispatchers.IO) {
        val repositories = getJsonArray("https://api.github.com/users/$username/repos?type=owner&per_page=100") ?: return@withContext emptyList()
        val repoObjects = (0 until repositories.length()).map { repositories.getJSONObject(it) }.toMutableList()
        if (repoObjects.none { it.optString("name").equals(selfRepo, true) }) {
            getJson("https://api.github.com/repos/$username/$selfRepo")?.let(repoObjects::add)
        }
        val apps = repoObjects.mapNotNull { repo ->
            val owner = repo.optJSONObject("owner")?.optString("login") ?: username
            val name = repo.optString("name")
            val release = getJson("https://api.github.com/repos/$owner/$name/releases/latest") ?: return@mapNotNull null
            val assets = release.optJSONArray("assets") ?: return@mapNotNull null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return@mapNotNull null
            val metadata = assets.metadataAsset()?.let { getJson(it.optString("browser_download_url")) }
            val packageName = metadata?.optString("packageName").orEmpty().ifBlank { if (name.equals(selfRepo, true)) "com.samielmadani.elmadanistore" else null }
            val releaseVersionCode = metadata?.optLong("versionCode")?.takeIf { it > 0L }
            val history = getJsonArray("https://api.github.com/repos/$owner/$name/releases?per_page=20")?.let { releases ->
                (0 until releases.length()).mapNotNull { index ->
                    val item = releases.optJSONObject(index) ?: return@mapNotNull null
                    val itemAssets = item.optJSONArray("assets") ?: return@mapNotNull null
                    val itemApk = (0 until itemAssets.length()).map { itemAssets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return@mapNotNull null
                    ReleaseSummary(item.optString("tag_name"), item.optString("body"), item.optString("published_at"), itemApk.optString("name"), itemApk.optLong("size"), itemApk.optString("browser_download_url"), item.optBoolean("prerelease"))
                }
            }.orEmpty()
            val app = StoreApp(owner = owner, repo = name, name = name, description = repo.optString("description").ifBlank { "Android app from $owner" }, iconUrl = iconFor(owner, name), repositoryUrl = "https://github.com/$owner/$name", releaseId = release.optLong("id"), version = release.optString("tag_name"), releaseNotes = release.optString("body"), publishedAt = release.optString("published_at"), assetName = apk.optString("name"), assetSize = apk.optLong("size"), downloadUrl = apk.optString("browser_download_url"), prerelease = release.optBoolean("prerelease"), packageName = packageName, releaseVersionCode = releaseVersionCode, releases = history)
            val tracked = trackingStore.recordLatest(app)
            val reconciled = trackingStore.reconcileInstalled(app)
            app.copy(installedVersion = reconciled?.installedVersionName ?: tracked.installedVersionName, installedVersionCode = reconciled?.installedVersionCode ?: tracked.installedVersionCode)
        }
        preferences.edit().putString("apps_cache", apps.joinToString("\n") { "${it.owner}|${it.repo}|${it.version}|${it.description}" }).apply()
        apps
    }

    suspend fun download(app: StoreApp, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "${app.repo}-${app.version}.apk")
        val request = Request.Builder().url(app.downloadUrl).header("User-Agent", "Elmadani-Store").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: ${response.code}" }
            val body = response.body ?: error("Empty download")
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input -> FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    read += count
                    if (total > 0) onProgress((read * 100 / total).toInt())
                }
            } }
        }
        preferences.edit().putString("installed_${app.repo}", app.version).apply()
        target
    }

    fun install(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun clearDownloads() { context.cacheDir.listFiles()?.filter { it.extension == "apk" }?.forEach(File::delete) }
    fun token(): String = preferences.getString("github_token", "").orEmpty()
    fun saveToken(value: String) { preferences.edit().putString("github_token", value.trim()).apply() }
    fun overrideName(repo: String): String? = preferences.getString("name_$repo", null)
    fun saveOverrideName(repo: String, name: String) { preferences.edit().putString("name_$repo", name.trim()).apply() }

    private fun getJson(url: String): JSONObject? = runCatching {
        client.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "Elmadani-Store").apply { if (token().isNotBlank()) header("Authorization", "Bearer ${token()}") }.build()).execute().use { response -> if (response.isSuccessful) JSONObject(response.body?.string().orEmpty()) else null }
    }.getOrNull()

    private fun getJsonArray(url: String): JSONArray? = runCatching {
        client.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "Elmadani-Store").apply { if (token().isNotBlank()) header("Authorization", "Bearer ${token()}") }.build()).execute().use { response -> if (response.isSuccessful) JSONArray(response.body?.string().orEmpty()) else null }
    }.getOrNull()

    private fun iconFor(owner: String, repo: String) = "https://raw.githubusercontent.com/$owner/$repo/main/public/icon-512.png"

    private fun JSONArray.metadataAsset(): JSONObject? = (0 until length()).map { getJSONObject(it) }.firstOrNull { it.optString("name").equals("elmadani-app.json", true) }
}

private fun JSONArray.mapNotNull(transform: (JSONObject) -> StoreApp?): List<StoreApp> = (0 until length()).mapNotNull { transform(getJSONObject(it)) }
