package com.samielmadani.elmadanistore.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
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
    private val defaultIgnoredRepos = setOf("samielmadani/elmadani-store")

    @Volatile
    var rateLimitStatus: RateLimitStatus = RateLimitStatus()
        private set

    suspend fun loadApps(): List<StoreApp> = withContext(Dispatchers.IO) {
        val repositories = getJsonArray("https://api.github.com/users/$username/repos?type=owner&per_page=100") ?: return@withContext emptyList()
        val ignored = ignoredRepos()
        val repoObjects = (0 until repositories.length()).mapNotNull { repositories.optJSONObject(it) }
            .filterNot { ignored.contains(repoKey(username, it.optString("name"))) }
        val apps = repoObjects.mapNotNull { repo ->
            val owner = repo.optJSONObject("owner")?.optString("login") ?: username
            val name = repo.optString("name")
            if (name.isBlank() || ignored.contains(repoKey(owner, name))) return@mapNotNull null
            val release = getJson("https://api.github.com/repos/$owner/$name/releases/latest") ?: return@mapNotNull null
            val assets = release.optJSONArray("assets") ?: return@mapNotNull null
            val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return@mapNotNull null
            val metadata = loadMetadata(owner, name, assets)
            val packageName = metadata?.optString("packageName").orEmpty().ifBlank { null }
            val releaseVersionCode = metadata?.optLong("versionCode")?.takeIf { it > 0L }
            val history = getJsonArray("https://api.github.com/repos/$owner/$name/releases?per_page=20")?.let { releases ->
                (0 until releases.length()).mapNotNull { index ->
                    val item = releases.optJSONObject(index) ?: return@mapNotNull null
                    val itemAssets = item.optJSONArray("assets") ?: return@mapNotNull null
                    val itemApk = (0 until itemAssets.length()).mapNotNull { itemAssets.optJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return@mapNotNull null
                    ReleaseSummary(item.optString("tag_name"), item.optString("body"), item.optString("published_at"), itemApk.optString("name"), itemApk.optLong("size"), itemApk.optString("browser_download_url"), item.optBoolean("prerelease"))
                }
            }.orEmpty()
            val displayName = overrideName(name).orEmpty().ifBlank { metadata?.optString("name").orEmpty().ifBlank { name } }
            val description = metadata?.optString("description").orEmpty().ifBlank { repo.optString("description").ifBlank { "Android app from $owner" } }
            val app = StoreApp(owner = owner, repo = name, name = displayName, description = description, iconUrl = iconFor(owner, name), repositoryUrl = "https://github.com/$owner/$name", releaseId = release.optLong("id"), version = release.optString("tag_name"), releaseNotes = release.optString("body"), publishedAt = release.optString("published_at"), assetName = apk.optString("name"), assetSize = apk.optLong("size"), downloadUrl = apk.optString("browser_download_url"), prerelease = release.optBoolean("prerelease"), packageName = packageName, releaseVersionCode = releaseVersionCode, releases = history)
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
    fun isManualRefreshThrottled(now: Long = System.currentTimeMillis()) = now - preferences.getLong("manual_refresh_at", 0L) < 60_000L
    fun markManualRefresh(now: Long = System.currentTimeMillis()) { preferences.edit().putLong("manual_refresh_at", now).apply() }
    fun ignoredRepos(): Set<String> = preferences.getStringSet("ignored_repos", defaultIgnoredRepos)?.map(::normaliseRepo)?.filter(String::isNotBlank)?.toSet().orEmpty()
    fun saveIgnoredRepos(value: String) { preferences.edit().putStringSet("ignored_repos", value.split(',', '\n').map(::normaliseRepo).filter(String::isNotBlank).toSet()).apply() }
    fun overrideName(repo: String): String? = preferences.getString("name_$repo", null)
    fun saveOverrideName(repo: String, name: String) { preferences.edit().putString("name_$repo", name.trim()).apply() }

    private fun getJson(url: String): JSONObject? = request(url)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun getJsonArray(url: String): JSONArray? = request(url)?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun request(url: String): String? = runCatching {
        val cacheKey = "cache_${url.hashCode()}"
        val cached = preferences.getString("${cacheKey}_body", null)
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "Elmadani-Store")
            .apply {
                preferences.getString("${cacheKey}_etag", null)?.let { header("If-None-Match", it) }
                token().takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
            }.build()
        client.newCall(request).execute().use { response ->
            updateRateLimit(response)
            when {
                response.code == 304 -> cached
                response.code == 403 && response.header("X-RateLimit-Remaining") == "0" -> throw RateLimitException(rateLimitStatus.resetAt)
                response.isSuccessful -> response.body?.string()?.also { body ->
                    preferences.edit().putString("${cacheKey}_body", body).apply()
                    response.header("ETag")?.let { preferences.edit().putString("${cacheKey}_etag", it).apply() }
                }
                else -> null
            }
        }
    }.getOrElse { error -> if (error is RateLimitException) throw error else null }

    private fun updateRateLimit(response: okhttp3.Response) {
        rateLimitStatus = RateLimitStatus(
            remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull(),
            limit = response.header("X-RateLimit-Limit")?.toIntOrNull(),
            resetAt = response.header("X-RateLimit-Reset")?.toLongOrNull()
        )
        preferences.edit().putInt("rate_remaining", rateLimitStatus.remaining ?: -1).putLong("rate_reset", rateLimitStatus.resetAt ?: 0L).apply()
    }

    private fun loadMetadata(owner: String, repo: String, assets: JSONArray): JSONObject? {
        listOf("store.json", "elmadani-store.json").firstNotNullOfOrNull { file ->
            getJson("https://api.github.com/repos/$owner/$repo/contents/$file")?.let { content ->
                content.optString("content").takeIf(String::isNotBlank)?.let { encoded ->
                    runCatching { JSONObject(String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT))) }.getOrNull()
                }
            }
        }?.let { return it }
        return assets.metadataAsset()?.let { getJson(it.optString("browser_download_url")) }
    }

    private fun iconFor(owner: String, repo: String) = "https://raw.githubusercontent.com/$owner/$repo/main/public/icon-512.png"

    private fun JSONArray.metadataAsset(): JSONObject? = (0 until length()).mapNotNull { optJSONObject(it) }.firstOrNull { it.optString("name").equals("elmadani-app.json", true) }

    private fun repoKey(owner: String, repo: String) = "${owner.lowercase()}/${repo.lowercase()}"
    private fun normaliseRepo(value: String) = value.trim().trim('/').lowercase()
}

class RateLimitException(resetAt: Long?) : Exception("GitHub is rate limited. Retry at ${resetAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it * 1000)) } ?: "the reset time"}.")
