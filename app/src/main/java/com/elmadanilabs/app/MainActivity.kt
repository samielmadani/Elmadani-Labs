package com.elmadanilabs.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class AppConfig(
    val owner: String,
    val repo: String,
    val name: String,
    val description: String,
    val category: String,
    val packageName: String?,
    val iconUrl: String?,
    val topics: List<String> = emptyList()
)

data class ReleaseApp(
    val config: AppConfig,
    val version: String,
    val tag: String,
    val notes: String,
    val publishedAt: String,
    val assetName: String,
    val assetSize: Long,
    val downloadUrl: String,
    val repositoryUrl: String,
    val discoveredAt: Long = System.currentTimeMillis(),
    val installedVersion: String? = null,
    val installedVersionCode: Long? = null,
    val error: String? = null,
    val assetApiUrl: String? = null
) {
    val isInstalled: Boolean get() = installedVersion != null
    val updateAvailable: Boolean get() = isInstalled && compareVersions(version, installedVersion.orEmpty()) > 0
}

data class CatalogueState(
    val apps: List<ReleaseApp> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: String? = null,
    val lastRefresh: String? = null
)

data class SelfUpdateState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val latest: ReleaseApp? = null,
    val latestVersionCode: Int? = null,
    val checking: Boolean = false,
    val message: String? = null,
    val downloadProgress: Int? = null
) {
    val updateAvailable: Boolean
        get() = latest != null && (latestVersionCode?.let { it > currentVersionCode }
            ?: (compareVersions(latest.version, currentVersion) > 0))
}

class CatalogueRepository(private val context: Context) {
    private val client = OkHttpClient()
    private val preferences = context.getSharedPreferences("catalogue", Context.MODE_PRIVATE)
    private val securePreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "github_auth", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private var lastApiStatus = 0
    private val githubUsername: String by lazy {
        runCatching {
            context.assets.open("apps.json").bufferedReader().use { JSONObject(it.readText()).optString("githubUsername") }
        }.getOrDefault("samielmadani").ifBlank { "samielmadani" }
    }
    private val selfRepository = "Elmadani-Labs"

    fun hasGithubToken(): Boolean = !securePreferences.getString("token", null).isNullOrBlank()
    fun setGithubToken(token: String) {
        if (token.isBlank()) securePreferences.edit().remove("token").apply()
        else securePreferences.edit().putString("token", token.trim()).apply()
    }

    fun authenticatedRequest(url: String): Request.Builder = Request.Builder().url(url).apply {
        header("User-Agent", "Elmadani-Labs")
        securePreferences.getString("token", null)?.takeIf { it.isNotBlank() }?.let {
            header("Authorization", "Bearer ${it.trim()}")
        }
    }

    fun cached(): List<ReleaseApp> = runCatching {
        val array = JSONArray(preferences.getString("apps", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            releaseFromJson(item)
        }
    }.getOrDefault(emptyList())

    fun save(apps: List<ReleaseApp>) {
        val array = JSONArray().apply { apps.forEach { put(releaseToJson(it)) } }
        preferences.edit().putString("apps", array.toString()).putString("refreshed", System.currentTimeMillis().toString()).apply()
    }

    fun lastRefresh(): String? = preferences.getString("refreshed", null)
    fun clearCache() = preferences.edit().clear().apply()

    fun checkSelfUpdate(includePrereleases: Boolean): Pair<ReleaseApp?, String?> {
        var page = 1
        while (true) {
            val releases = getJsonArray("https://api.github.com/repos/$githubUsername/$selfRepository/releases?per_page=100&page=$page", "self-releases-$page")
                ?: return null to when (lastApiStatus) {
                    401, 403 -> "GitHub rejected the token or its permissions."
                    404 -> "Elmadani Labs is not reachable with this GitHub account."
                    else -> "Unable to check for updates."
                }
            val release = (0 until releases.length()).asSequence()
                .map { releases.getJSONObject(it) }
                .firstOrNull { !it.optBoolean("draft") && (includePrereleases || !it.optBoolean("prerelease")) && hasApk(it) }
            if (release != null) {
                val config = AppConfig(
                    owner = githubUsername,
                    repo = selfRepository,
                    name = "Elmadani Labs",
                    description = "Personal software distribution platform",
                    category = "System",
                    packageName = BuildConfig.APPLICATION_ID,
                    iconUrl = null
                )
                return releaseToApp(config, release) to null
            }
            if (releases.length() < 100) return null to "You're up to date"
            page++
        }
    }

    fun refresh(includePrereleases: Boolean): Pair<List<ReleaseApp>, String?> {
        val repositories = discoverRepositories()
        if (repositories == null) return cached() to when (lastApiStatus) {
            401, 403 -> "GitHub rejected the token or its permissions. Showing cached apps."
            else -> "Couldn't reach GitHub. Showing cached apps."
        }
        if (repositories.isEmpty()) return emptyList<ReleaseApp>() to if (hasGithubToken()) "No repositories found for this GitHub account." else "No public repositories found. Add a GitHub token for private repositories."
        val results = repositories.mapNotNull { repository -> fetchLatestApk(repository, includePrereleases) }
        save(results)
        return results to null
    }

    private fun discoverRepositories(): List<JSONObject>? {
        val repositories = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val endpoint = if (hasGithubToken()) {
                "https://api.github.com/user/repos?visibility=all&affiliation=owner&per_page=100&page=$page"
            } else {
                "https://api.github.com/users/$githubUsername/repos?per_page=100&page=$page"
            }
            val pageData = getJsonArray(endpoint, "repos-$page") ?: return null
            for (index in 0 until pageData.length()) {
                val repository = pageData.getJSONObject(index)
                val owner = repository.optJSONObject("owner")?.optString("login").orEmpty()
                if (owner.equals(githubUsername, ignoreCase = true)) repositories += repository
            }
            if (pageData.length() < 100) return repositories
            page++
        }
    }

    private fun fetchLatestApk(repository: JSONObject, includePrereleases: Boolean): ReleaseApp? {
        val owner = repository.optJSONObject("owner")?.optString("login").orEmpty().ifBlank { githubUsername }
        val repo = repository.optString("name")
        if (owner.equals(githubUsername, ignoreCase = true) && repo.equals(selfRepository, ignoreCase = true)) return null
        val config = AppConfig(
            owner = owner,
            repo = repo,
            name = repository.optString("name", repo),
            description = repository.optString("description").ifBlank { "Android application from $owner" },
            category = repository.optJSONArray("topics")?.optString(0)?.replaceFirstChar { it.uppercase() } ?: "Other",
            packageName = null,
            iconUrl = null,
            topics = repository.optJSONArray("topics")?.let { topics -> (0 until topics.length()).map { topics.optString(it) } } ?: emptyList()
        )
        var page = 1
        while (true) {
            val releases = getJsonArray("https://api.github.com/repos/$owner/$repo/releases?per_page=100&page=$page", "releases-$owner-$repo-$page") ?: return null
            val release = (0 until releases.length()).asSequence()
                .map { releases.getJSONObject(it) }
                .firstOrNull { !it.optBoolean("draft") && (includePrereleases || !it.optBoolean("prerelease")) && hasApk(it) }
            if (release != null) return releaseToApp(config, release)
            if (releases.length() < 100) return null
            page++
        }
    }

    private fun hasApk(release: JSONObject): Boolean {
        val assets = release.optJSONArray("assets") ?: return false
        return (0 until assets.length()).any { assets.getJSONObject(it).optString("name").lowercase(Locale.US).endsWith(".apk") }
    }

    private fun releaseToApp(config: AppConfig, release: JSONObject): ReleaseApp? {
        val assets = release.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").lowercase(Locale.US).endsWith(".apk") } ?: return null
        val metadataIcon = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").equals("elmadani-app-icon.png", ignoreCase = true) }
            ?.optString("browser_download_url").orEmpty().ifBlank {
                (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { asset ->
                val name = asset.optString("name").lowercase(Locale.US)
                name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                }?.optString("browser_download_url").orEmpty().ifBlank { config.iconUrl.orEmpty() }
            }
        val metadata = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").equals("elmadani-app.json", ignoreCase = true) }
            ?.let { asset -> getJsonObject(asset.optString("url"), "metadata-${config.owner}-${config.repo}-${release.optString("tag_name")}") }
        val packageName = metadata?.optString("packageName").orEmpty().ifBlank { config.packageName.orEmpty() }.ifBlank { null }
        return ReleaseApp(config.copy(packageName = packageName, iconUrl = metadataIcon.ifBlank { null }), release.optString("tag_name"), release.optString("tag_name"),
            release.optString("body"), release.optString("published_at"), apk.getString("name"),
            apk.optLong("size"), apk.getString("browser_download_url"),
            "https://github.com/${config.owner}/${config.repo}",
            assetApiUrl = apk.optString("url").ifBlank { null })
    }

    private fun getJsonArray(url: String, cacheKey: String): JSONArray? {
        val etagKey = "etag-$cacheKey"
        val bodyKey = "body-$cacheKey"
        val requestBuilder = authenticatedRequest(url).header("Accept", "application/vnd.github+json")
        preferences.getString(etagKey, null)?.let { requestBuilder.header("If-None-Match", it) }
        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                lastApiStatus = response.code
                if (response.code == 304) return JSONArray(preferences.getString(bodyKey, "[]"))
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                response.header("ETag")?.let { preferences.edit().putString(etagKey, it).apply() }
                preferences.edit().putString(bodyKey, body).apply()
                JSONArray(body)
            }
        }.getOrNull()
    }

    private fun getJsonObject(url: String, cacheKey: String): JSONObject? {
        if (url.isBlank()) return null
        val bodyKey = "body-$cacheKey"
        return runCatching {
            val request = authenticatedRequest(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                preferences.edit().putString(bodyKey, body).apply()
                JSONObject(body)
            }
        }.getOrElse {
            preferences.getString(bodyKey, null)?.let(::JSONObject)
        }
    }

    private fun releaseToJson(app: ReleaseApp) = JSONObject().apply {
        put("owner", app.config.owner); put("repo", app.config.repo); put("name", app.config.name)
        put("description", app.config.description); put("category", app.config.category)
        put("packageName", app.config.packageName); put("iconUrl", app.config.iconUrl); put("topics", JSONArray(app.config.topics)); put("version", app.version); put("tag", app.tag)
        put("notes", app.notes); put("publishedAt", app.publishedAt); put("assetName", app.assetName)
        put("assetSize", app.assetSize); put("downloadUrl", app.downloadUrl); put("assetApiUrl", app.assetApiUrl); put("repositoryUrl", app.repositoryUrl); put("discoveredAt", app.discoveredAt)
    }

    private fun releaseFromJson(item: JSONObject): ReleaseApp {
        val topics = item.optJSONArray("topics")?.let { (0 until it.length()).map { index -> it.optString(index) } } ?: emptyList()
        val config = AppConfig(item.optString("owner"), item.optString("repo"), item.optString("name"), item.optString("description"), item.optString("category", "Other"), item.optString("packageName").ifBlank { null }, item.optString("iconUrl").ifBlank { null }, topics)
        return ReleaseApp(config, item.optString("version"), item.optString("tag"),
        item.optString("notes"), item.optString("publishedAt"), item.optString("assetName"), item.optLong("assetSize"),
        item.optString("downloadUrl"), item.optString("repositoryUrl"), item.optLong("discoveredAt", System.currentTimeMillis()), assetApiUrl = item.optString("assetApiUrl").ifBlank { null })
    }
}

class CatalogueViewModel(private val repository: CatalogueRepository, private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(CatalogueState(apps = withInstalled(repository.cached()), loading = false))
    val state: StateFlow<CatalogueState> = _state.asStateFlow()
    private val _selfUpdate = MutableStateFlow(SelfUpdateState())
    val selfUpdate: StateFlow<SelfUpdateState> = _selfUpdate.asStateFlow()

    init {
        refresh(false)
        checkSelfUpdate(false)
    }

    fun refresh(includePrereleases: Boolean) {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.refresh(includePrereleases)
            _state.value = _state.value.copy(apps = withInstalled(result.first), loading = false, refreshing = false,
                message = result.second ?: if (result.first.isEmpty()) "No published APK releases found." else null,
                lastRefresh = repository.lastRefresh())
        }
    }

    fun clearCache() { repository.clearCache(); _state.value = CatalogueState(loading = false) }
    fun refreshInstalled() { _state.value = _state.value.copy(apps = withInstalled(_state.value.apps)) }
    fun hasGithubToken(): Boolean = repository.hasGithubToken()
    fun saveGithubToken(token: String) {
        repository.setGithubToken(token)
        refresh(false)
    }

    fun checkSelfUpdate(includePrereleases: Boolean = false) {
        _selfUpdate.value = _selfUpdate.value.copy(checking = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            val (latest, error) = repository.checkSelfUpdate(includePrereleases)
            val message = when {
                error != null && latest == null -> error
                latest != null && (extractVersionCode(latest.notes)?.let { it > BuildConfig.VERSION_CODE }
                    ?: (compareVersions(latest.version, BuildConfig.VERSION_NAME) > 0)) -> "Update available"
                else -> "You're up to date"
            }
            _selfUpdate.value = _selfUpdate.value.copy(latest = latest, latestVersionCode = latest?.let { extractVersionCode(it.notes) }, checking = false, message = message)
        }
    }

    fun downloadSelfUpdate(onProgress: (Int) -> Unit, onReady: (File) -> Unit) {
        val latest = _selfUpdate.value.latest ?: return
        install(latest, { progress ->
            _selfUpdate.value = _selfUpdate.value.copy(downloadProgress = progress)
            onProgress(progress)
        }, onReady)
    }

    fun rememberPackage(app: ReleaseApp, file: File) {
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return
        val updated = _state.value.apps.map { current ->
            if (current.config.owner == app.config.owner && current.config.repo == app.config.repo) {
                current.copy(config = current.config.copy(packageName = packageInfo.packageName))
            } else current
        }
        _state.value = _state.value.copy(apps = updated)
        repository.save(updated)
    }

    fun install(app: ReleaseApp, onProgress: (Int) -> Unit, onReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.cacheDir, app.assetName)
            runCatching {
                val request = repository.authenticatedRequest(app.assetApiUrl ?: app.downloadUrl).apply {
                    if (app.assetApiUrl != null) header("Accept", "application/octet-stream")
                }.build()
                val response = OkHttpClient().newCall(request).execute()
                response.use { body ->
                    require(body.isSuccessful && body.body != null)
                    body.body!!.byteStream().use { input -> file.outputStream().use { output ->
                        val total = body.body!!.contentLength(); val buffer = ByteArray(8192); var read: Int; var count = 0L
                        while (input.read(buffer).also { read = it } != -1) { output.write(buffer, 0, read); count += read; if (total > 0) onProgress((count * 100 / total).toInt()) }
                    }}
                }
                require(file.exists() && file.length() > 0) { "The downloaded APK is empty." }
                withContext(Dispatchers.Main) { onReady(file) }
            }.onFailure { error -> withContext(Dispatchers.Main) { onProgress(-1) } }
        }
    }

    private fun withInstalled(apps: List<ReleaseApp>) = apps.map { app ->
        val packageName = app.config.packageName ?: findInstalledPackage(app) ?: return@map app
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            ReleaseApp(app.config.copy(packageName = packageName), app.version, app.tag, app.notes, app.publishedAt, app.assetName, app.assetSize,
                app.downloadUrl, app.repositoryUrl, app.discoveredAt, info.versionName,
                if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), assetApiUrl = app.assetApiUrl)
        }.getOrDefault(app)
    }

    private fun findInstalledPackage(app: ReleaseApp): String? = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        .firstOrNull { info ->
            val label = context.packageManager.getApplicationLabel(info).toString()
            label.equals(app.config.name, ignoreCase = true) || info.packageName.substringAfterLast('.').equals(app.config.repo, ignoreCase = true)
        }?.packageName
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CatalogueViewModel
    private var pendingInstall: File? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>) = CatalogueViewModel(CatalogueRepository(applicationContext), applicationContext) as T
        })[CatalogueViewModel::class.java]
        setContent { ElmadaniLabsApp(viewModel) }
    }

    fun launchInstaller(file: File) {
        pendingInstall = file
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")), 43)
            return
        }
        openInstaller(file)
    }

    private fun openInstaller(file: File) {
        val uri = FileProvider.getUriForFile(this, "com.elmadanilabs.app.fileprovider", file)
        startActivityForResult(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("APK", uri)
        }, 42)
    }

    @Deprecated("Android returns to the app after the user closes the package installer")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 43) {
            pendingInstall?.let { file ->
                if (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) openInstaller(file)
                else { file.delete(); pendingInstall = null; viewModel.refreshInstalled() }
            }
        } else if (requestCode == 42) {
            pendingInstall?.delete()
            pendingInstall = null
            viewModel.refreshInstalled()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElmadaniLabsApp(viewModel: CatalogueViewModel) {
    val state by viewModel.state.collectAsState()
    val selfUpdate by viewModel.selfUpdate.collectAsState()
    var selected by remember { mutableStateOf<ReleaseApp?>(null) }
    var showSelfUpdates by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val filtered = state.apps.filter { it.config.name.contains(query, true) || it.config.description.contains(query, true) }
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(primary = Color(0xFFFFB86B), secondary = Color(0xFF9AD9C2), background = Color(0xFF101413), surface = Color(0xFF19201E))
    } else {
        androidx.compose.material3.lightColorScheme(primary = Color(0xFF9A4D00), secondary = Color(0xFF2E6B59), background = Color(0xFFF8F9F6), surface = Color.White)
    }
    MaterialTheme(colorScheme = colors) {
        val refreshState = rememberPullToRefreshState()
        val pagerState = rememberPagerState(initialPage = tab, pageCount = { 2 })
        LaunchedEffect(tab) { if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab) }
        LaunchedEffect(pagerState.currentPage) { tab = pagerState.currentPage }
        Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0), bottomBar = {
            FloatingDock(tab) { destination -> selected = null; showSelfUpdates = false; tab = destination }
        }) { padding ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { viewModel.refresh(false) }, state = refreshState, modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = padding.calculateBottomPadding())) {
                if (showSelfUpdates) {
                    SelfUpdateScreen(selfUpdate, viewModel, Modifier.fillMaxSize().padding(horizontal = 20.dp)) { showSelfUpdates = false }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 1) SettingsScreen(state, viewModel, Modifier.fillMaxSize().padding(horizontal = 20.dp)) { showSelfUpdates = true }
                        else HomeScreen(filtered, state, query, { query = it }, { viewModel.refresh(false) }, { selected = it }, Modifier.fillMaxSize().padding(horizontal = 20.dp))
                    }
                }
            }
        }
        selected?.let { app ->
            ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                DetailScreen(app, viewModel, Modifier.padding(horizontal = 20.dp)) { selected = null }
            }
        }
    }
}

@Composable
private fun FloatingDock(selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DockItem(selected == 0, Icons.Default.Search, "Apps") { onSelect(0) }
                DockItem(selected == 1, Icons.Default.Settings, "Settings") { onSelect(1) }
            }
        }
    }
}

@Composable
private fun DockItem(selected: Boolean, icon: androidx.compose.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
        Icon(icon, description, Modifier.padding(horizontal = 22.dp, vertical = 12.dp).size(23.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(apps: List<ReleaseApp>, state: CatalogueState, query: String, onQuery: (String) -> Unit, onRefresh: () -> Unit, onSelect: (ReleaseApp) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(top = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Elmadani Labs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Spacer(Modifier.height(18.dp)); SearchField(query, onQuery); Spacer(Modifier.height(10.dp))
        AnimatedVisibility(state.loading, enter = fadeIn(), exit = fadeOut()) { SkeletonList() }
        AnimatedVisibility(!state.loading, enter = fadeIn(), exit = fadeOut()) {
            Column {
                FriendlyMessage(state.message, onRefresh)
                if (apps.isEmpty()) EmptyState(if (query.isBlank()) "No apps available" else "No matching apps", "Pull down to refresh and check for new applications.", onRefresh)
                if (apps.isNotEmpty()) Text("All applications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(apps, key = { it.config.repo }) { AppCard(it, onSelect) } }
            }
        }
    }
}

@Composable private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.TextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Search") }, trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "Clear search") } }, placeholder = { Text("Search apps") }, shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.TextFieldDefaults.colors(unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent))
}

@Composable private fun FriendlyMessage(message: String?, onRetry: () -> Unit) {
    if (message == null || message.contains("No published", true) || message.contains("No repositories", true)) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.onErrorContainer); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("Couldn't refresh apps", fontWeight = FontWeight.SemiBold); Text("Check your connection and try again.", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable private fun SkeletonList() {
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        repeat(4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)))
                Spacer(Modifier.width(16.dp)); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(150.dp, 16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                    Box(Modifier.size(210.dp, 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                    Box(Modifier.size(92.dp, 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@Composable private fun AppCard(app: ReleaseApp, onSelect: (ReleaseApp) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { onSelect(app) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 68); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) {
                Text(displayName(app.config.name), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp)); AppStatus(app)
            }
            Spacer(Modifier.width(8.dp)); TextButton(onClick = { onSelect(app) }) {
                Icon(if (app.updateAvailable) Icons.Default.Download else if (app.isInstalled) Icons.Default.OpenInNew else Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(if (app.updateAvailable) "Update" else if (app.isInstalled) "Open" else "Install")
            }
        }
    }
}

@Composable private fun AppStatus(app: ReleaseApp) {
    val update = app.updateAvailable
    val color = if (update) MaterialTheme.colorScheme.primary else if (app.isInstalled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (update) Icons.Default.Download else if (app.isInstalled) Icons.Default.CheckCircle else Icons.Default.Info, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(5.dp)); Text(if (update) "Update available" else if (app.isInstalled) "Installed" else "Version ${app.version.trimStart('v')}", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun DetailScreen(app: ReleaseApp, viewModel: CatalogueViewModel, modifier: Modifier, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var progress by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; AppIcon(app, 96); Spacer(Modifier.height(12.dp)); Text(displayName(app.config.name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); AppStatus(app); Text("${app.version}  •  ${formatSize(app.assetSize)}  •  ${app.publishedAt.take(10)}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)); Spacer(Modifier.height(16.dp));
        if (progress != null) {
            val currentProgress = progress!!.coerceIn(0, 100)
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(progress = { currentProgress / 100f }, Modifier.size(22.dp), strokeWidth = 3.dp); Spacer(Modifier.width(12.dp)); Text("Downloading $currentProgress%", fontWeight = FontWeight.Medium) } }
        } else Button(onClick = { progress = 0; viewModel.install(app, { value -> progress = value.takeIf { it >= 0 } }) { file -> progress = null; viewModel.rememberPackage(app, file); (context as? MainActivity)?.launchInstaller(file) } }, enabled = !app.isInstalled || app.updateAvailable, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (app.updateAvailable) "Update" else "Install") }
        if (app.isInstalled && !app.updateAvailable) TextButton(onClick = { context.startActivity(context.packageManager.getLaunchIntentForPackage(app.config.packageName.orEmpty())) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open") }
        Spacer(Modifier.height(20.dp)); TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.repositoryUrl))) }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("GitHub repository") }
    }
}

@Composable private fun SettingsScreen(state: CatalogueState, viewModel: CatalogueViewModel, modifier: Modifier, onOpenSelfUpdates: () -> Unit) {
    var includePrereleases by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 26.dp, bottom = 28.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp)); Text("GENERAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        PreferenceRow(Icons.Default.Info, "GitHub access", "Private repository access")
        androidx.compose.material3.OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("Access token") }, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
        TextButton(onClick = { viewModel.saveGithubToken(token); token = "" }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Save and refresh") }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Download, "Prereleases", tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Include prereleases"); Text("Show early releases", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; androidx.compose.material3.Switch(checked = includePrereleases, onCheckedChange = { includePrereleases = it; viewModel.refresh(it) }) }
        Spacer(Modifier.height(24.dp)); Text("STORAGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        PreferenceRow(Icons.Default.Storage, "Cached apps", "Clear saved release information") { viewModel.clearCache() }
        Spacer(Modifier.height(24.dp)); Text("ABOUT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        PreferenceRow(Icons.Default.Info, "Elmadani Labs", "Version ${BuildConfig.VERSION_NAME}")
        PreferenceRow(Icons.Default.Refresh, "Updates", "Check for a new Elmadani Labs version", onOpenSelfUpdates)
    }
}

@Composable private fun PreferenceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Surface(onClick = { onClick?.invoke() }, enabled = onClick != null, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, title, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)); Column { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }
}

@Composable private fun SelfUpdateScreen(state: SelfUpdateState, viewModel: CatalogueViewModel, modifier: Modifier, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        AppIcon(null, 88)
        Text("Elmadani Labs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Version ${state.currentVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp)); Button(onClick = { viewModel.checkSelfUpdate() }, enabled = !state.checking, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Check for updates") }
        if (state.checking) LinearStatus("Checking for updates")
        state.message?.let { rawMessage ->
            val hasUpdate = rawMessage == "Update available"
            val isCurrent = rawMessage == "You're up to date"
            val title = when { hasUpdate -> "Update available"; isCurrent -> "You're up to date"; else -> "Couldn't check for updates" }
            val detail = when { hasUpdate -> "A new version is ready to install."; isCurrent -> "You're running the latest version."; else -> "Check your connection and try again." }
            Surface(color = if (hasUpdate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (hasUpdate) Icons.Default.Download else if (isCurrent) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, "Update status"); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall) } } }
        }
        state.latest?.let { latest ->
            Text("${state.currentVersion}  →  ${latest.version.trimStart('v')}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
            Text("Release notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp))
            Text(latest.notes.ifBlank { "No release notes provided." }, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.updateAvailable) {
                var progress by remember { mutableStateOf<Int?>(null) }
                if (progress != null) {
                    val currentProgress = progress!!.coerceIn(0, 100)
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 20.dp).fillMaxWidth()) { Text("Downloading update: $currentProgress%", Modifier.padding(16.dp), fontWeight = FontWeight.Medium) }
                } else Button(onClick = { progress = 0; viewModel.downloadSelfUpdate({ value -> progress = value.takeIf { it >= 0 } }) { file -> progress = null; (context as? MainActivity)?.launchInstaller(file) } }, modifier = Modifier.padding(top = 20.dp).fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Update") }
            }
        }
    }
}
@Composable private fun AppIcon(app: ReleaseApp?, size: Int) {
    if (app == null) {
        androidx.compose.foundation.Image(painterResource(com.elmadanilabs.app.R.drawable.icon), "Elmadani Labs", Modifier.size(size.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Fit)
    } else if (app.config.iconUrl != null) {
        AsyncImage(model = app.config.iconUrl, contentDescription = app.config.name, contentScale = ContentScale.Fit, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text(app.config.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
    }
}
@Composable private fun LinearStatus(text: String) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(text, color = Color(0xFF9BA9B8)) } }
@Composable private fun EmptyState(title: String, subtitle: String, onRefresh: () -> Unit) { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(10.dp)); Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); TextButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh") } } } }
private fun formatSize(bytes: Long) = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(Locale.US, bytes / 1024f / 1024f)
private fun displayName(name: String) = name.split(Regex("[\\s_-]+"))
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) } }
private fun extractVersionCode(notes: String): Int? = Regex("(?i)versionCode\\s*:\\s*(\\d+)").find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
private fun compareVersions(left: String, right: String): Int { val a = left.trimStart('v').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }; val b = right.trimStart('v').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }; for (i in 0 until maxOf(a.size, b.size)) { val result = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 }); if (result != 0) return result }; return 0 }