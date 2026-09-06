package com.elmadanilabs.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
    val error: String? = null
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
    private val githubUsername: String by lazy {
        runCatching {
            context.assets.open("apps.json").bufferedReader().use { JSONObject(it.readText()).optString("githubUsername") }
        }.getOrDefault("samielmadani").ifBlank { "samielmadani" }
    }
    private val selfRepository = "Elmadani-Labs"

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
                ?: return null to "Unable to check for updates."
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
        if (repositories == null) return cached() to "Couldn't reach GitHub. Showing cached apps."
        val results = repositories.mapNotNull { repository -> fetchLatestApk(repository, includePrereleases) }
        save(results)
        return results to if (results.size < repositories.size) "Some repositories have no eligible APK release." else null
    }

    private fun discoverRepositories(): List<JSONObject>? {
        val repositories = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val pageData = getJsonArray("https://api.github.com/users/$githubUsername/repos?per_page=100&page=$page", "repos-$page") ?: return null
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
            iconUrl = repository.optJSONObject("owner")?.optString("avatar_url").orEmpty().ifBlank { null },
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
        return ReleaseApp(config, release.optString("tag_name"), release.optString("tag_name"),
            release.optString("body"), release.optString("published_at"), apk.getString("name"),
            apk.optLong("size"), apk.getString("browser_download_url"),
            "https://github.com/${config.owner}/${config.repo}")
    }

    private fun getJsonArray(url: String, cacheKey: String): JSONArray? {
        val etagKey = "etag-$cacheKey"
        val bodyKey = "body-$cacheKey"
        val requestBuilder = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
        preferences.getString(etagKey, null)?.let { requestBuilder.header("If-None-Match", it) }
        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 304) return JSONArray(preferences.getString(bodyKey, "[]"))
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                response.header("ETag")?.let { preferences.edit().putString(etagKey, it).apply() }
                preferences.edit().putString(bodyKey, body).apply()
                JSONArray(body)
            }
        }.getOrNull()
    }

    private fun releaseToJson(app: ReleaseApp) = JSONObject().apply {
        put("owner", app.config.owner); put("repo", app.config.repo); put("name", app.config.name)
        put("description", app.config.description); put("category", app.config.category)
        put("packageName", app.config.packageName); put("iconUrl", app.config.iconUrl); put("topics", JSONArray(app.config.topics)); put("version", app.version); put("tag", app.tag)
        put("notes", app.notes); put("publishedAt", app.publishedAt); put("assetName", app.assetName)
        put("assetSize", app.assetSize); put("downloadUrl", app.downloadUrl); put("repositoryUrl", app.repositoryUrl); put("discoveredAt", app.discoveredAt)
    }

    private fun releaseFromJson(item: JSONObject): ReleaseApp {
        val topics = item.optJSONArray("topics")?.let { (0 until it.length()).map { index -> it.optString(index) } } ?: emptyList()
        val config = AppConfig(item.optString("owner"), item.optString("repo"), item.optString("name"), item.optString("description"), item.optString("category", "Other"), item.optString("packageName").ifBlank { null }, item.optString("iconUrl").ifBlank { null }, topics)
        return ReleaseApp(config, item.optString("version"), item.optString("tag"),
        item.optString("notes"), item.optString("publishedAt"), item.optString("assetName"), item.optLong("assetSize"),
        item.optString("downloadUrl"), item.optString("repositoryUrl"), item.optLong("discoveredAt", System.currentTimeMillis()))
    }
}

class CatalogueViewModel(private val repository: CatalogueRepository, private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(CatalogueState(apps = withInstalled(repository.cached()), loading = false))
    val state: StateFlow<CatalogueState> = _state.asStateFlow()
    private val _selfUpdate = MutableStateFlow(SelfUpdateState())
    val selfUpdate: StateFlow<SelfUpdateState> = _selfUpdate.asStateFlow()

    init { refresh(false) }

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
                val response = OkHttpClient().newCall(Request.Builder().url(app.downloadUrl).build()).execute()
                response.use { body ->
                    require(body.isSuccessful && body.body != null)
                    body.body!!.byteStream().use { input -> file.outputStream().use { output ->
                        val total = body.body!!.contentLength(); val buffer = ByteArray(8192); var read: Int; var count = 0L
                        while (input.read(buffer).also { read = it } != -1) { output.write(buffer, 0, read); count += read; if (total > 0) onProgress((count * 100 / total).toInt()) }
                    }}
                }
                withContext(Dispatchers.Main) { onReady(file) }
            }
        }
    }

    private fun withInstalled(apps: List<ReleaseApp>) = apps.map { app ->
        val packageName = app.config.packageName ?: return@map app
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            ReleaseApp(app.config, app.version, app.tag, app.notes, app.publishedAt, app.assetName, app.assetSize,
                app.downloadUrl, app.repositoryUrl, app.discoveredAt, info.versionName,
                if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
        }.getOrDefault(app)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CatalogueViewModel
    private var pendingInstall: File? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>) = CatalogueViewModel(CatalogueRepository(applicationContext), applicationContext) as T
        })[CatalogueViewModel::class.java]
        setContent { ElmadaniLabsApp(viewModel) }
    }

    fun launchInstaller(file: File) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(this, "com.elmadanilabs.app.fileprovider", file)
        pendingInstall = file
        startActivityForResult(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, 42)
    }

    @Deprecated("Android returns to the app after the user closes the package installer")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) {
            pendingInstall?.delete()
            pendingInstall = null
            viewModel.refreshInstalled()
        }
    }
}

@Composable
fun ElmadaniLabsApp(viewModel: CatalogueViewModel) {
    val state by viewModel.state.collectAsState()
    val selfUpdate by viewModel.selfUpdate.collectAsState()
    var selected by remember { mutableStateOf<ReleaseApp?>(null) }
    var showSelfUpdates by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val filtered = state.apps.filter { it.config.name.contains(query, true) || it.config.description.contains(query, true) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9CCBFF), background = Color(0xFF0B1017), surface = Color(0xFF151D27))) {
        Scaffold(containerColor = Color(0xFF0B1017), bottomBar = {
            NavigationBar(containerColor = Color(0xFF101821)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Catalogue") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Updates") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }) { padding ->
            when {
                selected != null -> DetailScreen(selected!!, viewModel, Modifier.padding(padding)) { selected = null }
                showSelfUpdates -> SelfUpdateScreen(selfUpdate, viewModel, Modifier.padding(padding)) { showSelfUpdates = false }
                tab == 2 -> SettingsScreen(state, viewModel, Modifier.padding(padding)) { showSelfUpdates = true }
                else -> HomeScreen(if (tab == 1) filtered.filter { it.updateAvailable } else filtered, state, query, { query = it }, { viewModel.refresh(false) }, { selected = it }, Modifier.padding(padding), tab == 1)
            }
        }
    }
}

@Composable
private fun HomeScreen(apps: List<ReleaseApp>, state: CatalogueState, query: String, onQuery: (String) -> Unit, onRefresh: () -> Unit, onSelect: (ReleaseApp) -> Unit, modifier: Modifier, updates: Boolean) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("ELMADANI LABS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(if (updates) "Updates" else "Your software hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Spacer(Modifier.height(16.dp)); SearchField(query, onQuery); Spacer(Modifier.height(20.dp))
        if (state.refreshing) LinearStatus("Checking GitHub releases...")
        state.message?.let { Text(it, color = Color(0xFFFFC878), modifier = Modifier.padding(vertical = 8.dp)) }
        if (!state.loading && apps.isEmpty()) EmptyState(if (updates) "You're up to date" else "No APK releases available")
        if (!updates && apps.isNotEmpty()) {
            Text("All Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Text("Categories", style = MaterialTheme.typography.labelLarge, color = Color(0xFF9BA9B8), modifier = Modifier.padding(bottom = 8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(apps, key = { it.config.repo }) { AppCard(it, onSelect) } }
    }
}

@Composable private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search apps") }, shape = RoundedCornerShape(14.dp))
}

@Composable private fun AppCard(app: ReleaseApp, onSelect: (ReleaseApp) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onSelect(app) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151D27)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 58); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(app.config.name, fontWeight = FontWeight.Bold); Text(app.config.description, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF9BA9B8)); Text("${app.version}  •  ${formatSize(app.assetSize)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            Text(if (app.updateAvailable) "UPDATE" else if (app.isInstalled) "OPEN" else "GET", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun DetailScreen(app: ReleaseApp, viewModel: CatalogueViewModel, modifier: Modifier, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var progress by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxSize().padding(20.dp)) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; AppIcon(app, 92); Spacer(Modifier.height(12.dp)); Text(app.config.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(app.config.description, color = Color(0xFFB6C3D1)); Spacer(Modifier.height(18.dp)); Text("${app.version}  •  ${formatSize(app.assetSize)}  •  ${app.publishedAt.take(10)}", color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp));
        Button(onClick = { progress = 0; viewModel.install(app, { progress = it }) { file -> viewModel.rememberPackage(app, file); (context as? MainActivity)?.launchInstaller(file) } }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) { Text(if (app.updateAvailable) "UPDATE" else "INSTALL") }
        progress?.let { Text("Downloading APK: $it%", modifier = Modifier.padding(vertical = 12.dp)) }; Spacer(Modifier.height(20.dp)); Text("Release notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(app.notes.ifBlank { "No release notes provided." }, modifier = Modifier.padding(top = 8.dp)); Spacer(Modifier.height(16.dp)); TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.repositoryUrl))) }) { Text("GitHub repository") }
    }
}

@Composable private fun SettingsScreen(state: CatalogueState, viewModel: CatalogueViewModel, modifier: Modifier, onOpenSelfUpdates: () -> Unit) {
    var includePrereleases by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(20.dp)) { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(24.dp)); Text("Repositories", style = MaterialTheme.typography.titleLarge); Text("GitHub repositories are discovered automatically from apps.json", color = Color(0xFF9BA9B8), modifier = Modifier.padding(top = 6.dp)); Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Include prereleases"); Text("Show prerelease GitHub releases", color = Color(0xFF9BA9B8)) }; androidx.compose.material3.Switch(checked = includePrereleases, onCheckedChange = { includePrereleases = it; viewModel.refresh(it) }) }; Divider(Modifier.padding(vertical = 20.dp)); Text("Cache", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { viewModel.clearCache() }) { Text("Clear cached release information") }; Divider(Modifier.padding(vertical = 12.dp)); Text("About", style = MaterialTheme.typography.titleLarge); Text("Elmadani Labs\nVersion ${BuildConfig.VERSION_NAME}", color = Color(0xFF9BA9B8), modifier = Modifier.padding(top = 8.dp)); TextButton(onClick = onOpenSelfUpdates) { Text("Updates") } }
}

@Composable private fun SelfUpdateScreen(state: SelfUpdateState, viewModel: CatalogueViewModel, modifier: Modifier, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.fillMaxSize().padding(20.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text("Elmadani Labs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Current version\nv${state.currentVersion}", color = Color(0xFFB6C3D1), modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = { viewModel.checkSelfUpdate() }, enabled = !state.checking, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
        if (state.checking) LinearStatus("Checking GitHub Releases...")
        state.message?.let { message -> Text(message, color = if (message == "Update available") MaterialTheme.colorScheme.primary else Color(0xFFB6C3D1), modifier = Modifier.padding(vertical = 16.dp)) }
        state.latest?.let { latest ->
            Text("New version\nv${latest.version.trimStart('v')}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Release date: ${latest.publishedAt.take(10)}", color = Color(0xFF9BA9B8), modifier = Modifier.padding(top = 8.dp))
            Text(latest.notes.ifBlank { "No release notes provided." }, modifier = Modifier.padding(top = 16.dp))
            if (state.updateAvailable) {
                var progress by remember { mutableStateOf<Int?>(null) }
                Button(onClick = { progress = 0; viewModel.downloadSelfUpdate({ progress = it }) { file -> (context as? MainActivity)?.launchInstaller(file) } }, enabled = progress == null, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Update") }
                progress?.let { Text("Downloading update: $it%", modifier = Modifier.padding(top = 10.dp)) }
            }
        }
    }
}
@Composable private fun AppIcon(app: ReleaseApp, size: Int) { if (app.config.iconUrl != null) AsyncImage(model = app.config.iconUrl, contentDescription = null, modifier = Modifier.size(size.dp)) else Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text(app.config.name.take(1), color = Color(0xFF0B1017), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } }
@Composable private fun LinearStatus(text: String) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(text, color = Color(0xFF9BA9B8)) } }
@Composable private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFF9BA9B8)) } }
private fun formatSize(bytes: Long) = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(Locale.US, bytes / 1024f / 1024f)
private fun extractVersionCode(notes: String): Int? = Regex("(?i)versionCode\\s*:\\s*(\\d+)").find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
private fun compareVersions(left: String, right: String): Int { val a = left.trimStart('v').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }; val b = right.trimStart('v').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }; for (i in 0 until maxOf(a.size, b.size)) { val result = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 }); if (result != 0) return result }; return 0 }