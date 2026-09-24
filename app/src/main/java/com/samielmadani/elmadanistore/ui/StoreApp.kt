package com.samielmadani.elmadanistore.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.samielmadani.elmadanistore.data.SortMode
import com.samielmadani.elmadanistore.data.StoreApp
import com.samielmadani.elmadanistore.data.ThemeMode
import com.samielmadani.elmadanistore.ui.theme.ThemeSettings

private sealed interface Page {
    data object Home : Page
    data class Details(val app: StoreApp) : Page
    data object Settings : Page
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreApp(initialRepo: String? = null, storeViewModel: StoreViewModel = viewModel()) {
    var page by remember { mutableStateOf<Page>(Page.Home) }
    val apps by storeViewModel.apps.collectAsState()
    val loading by storeViewModel.loading.collectAsState()
    val error by storeViewModel.error.collectAsState()
    val progress by storeViewModel.downloadProgress.collectAsState()
    LaunchedEffect(apps, initialRepo) {
        initialRepo?.let { repo -> apps.firstOrNull { it.repo.equals(repo, true) }?.let { page = Page.Details(it) } }
    }
    when (val current = page) {
        Page.Home -> HomePage(apps, loading, error, progress, storeViewModel, { page = Page.Details(it) }, { page = Page.Settings })
        is Page.Details -> DetailPage(current.app, progress[current.app.repo], storeViewModel) { page = Page.Home }
        Page.Settings -> SettingsPage(storeViewModel) { page = Page.Home }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(apps: List<StoreApp>, loading: Boolean, error: String?, progress: Map<String, Int>, vm: StoreViewModel, openDetails: (StoreApp) -> Unit, openSettings: () -> Unit) {
    val context = LocalContext.current
    var sort by remember { mutableStateOf(SortMode.UPDATED) }
    var sortSheetOpen by remember { mutableStateOf(false) }
    val shownApps = vm.sorted(sort)
    Scaffold(topBar = {
        TopAppBar(title = { Column { Text("Elmadani Store", fontWeight = FontWeight.Bold); Text("Personal app catalogue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, actions = {
            IconButton(onClick = { sortSheetOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort apps") }
        })
    }, bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.CloudDownload, null) }, label = { Text("Apps") })
            NavigationBarItem(selected = false, onClick = openSettings, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
        }
    }) { padding ->
        PullToRefreshBox(isRefreshing = loading, onRefresh = vm::refresh, state = rememberPullToRefreshState(), modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && apps.isEmpty() -> LoadingList()
                error != null && apps.isEmpty() -> EmptyState("Could not load apps", error ?: "Try again", Icons.Default.Refresh, vm::refresh)
                apps.isEmpty() -> EmptyState("No releases yet", "Public repositories with APK releases will appear here.", Icons.Default.CloudDownload, vm::refresh)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                    item { Text("Available apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Install directly from GitHub Releases", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(shownApps, key = { it.repo }) { app -> AppCard(app, progress[app.repo], { openDetails(app) }) { vm.download(app) { file -> context.startActivity(vm.install(file)) } } }
                }
            }
        }
    }
    if (sortSheetOpen) {
        ModalBottomSheet(onDismissRequest = { sortSheetOpen = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.label) }, onClick = { sort = mode; sortSheetOpen = false })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, progress: Int?, openDetails: () -> Unit, install: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = app.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(app.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(app.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.version, fontWeight = FontWeight.Medium); Text("  •  ${app.formattedSize}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f));
                StatusChip(app)
                Spacer(Modifier.width(8.dp))
                if (progress != null && progress < 100) { CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp) } else { Button(onClick = install, enabled = !app.isInstalled || app.hasUpdate, shape = RoundedCornerShape(14.dp)) { Text(if (app.hasUpdate) "Update" else if (app.isInstalled) "Up to date" else "Install") } }
            }
            if (progress != null && progress < 100) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun StatusChip(app: StoreApp) {
    val color = if (app.hasUpdate) Color(0xFFC24D38) else Color(0xFF2E7D62)
    AssistChip(onClick = {}, enabled = false, label = { Text(if (app.hasUpdate) "Update available" else if (app.isInstalled) "Up to date" else "Ready") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(disabledContainerColor = color.copy(alpha = .15f), disabledLabelColor = color, disabledLeadingIconContentColor = color))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPage(app: StoreApp, progress: Int?, vm: StoreViewModel, back: () -> Unit) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf(vm.overrideName(app.repo).orEmpty()) }
    Scaffold(topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(padding)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = app.iconUrl, contentDescription = null, modifier = Modifier.size(82.dp).clip(RoundedCornerShape(22.dp))); Spacer(Modifier.width(18.dp)); Column { Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Latest ${app.version}", color = MaterialTheme.colorScheme.primary); Text(app.formattedSize, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Button(onClick = { vm.download(app) { file -> context.startActivity(vm.install(file)) } }, enabled = !app.isInstalled || app.hasUpdate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text(if (app.hasUpdate) "Update app" else if (app.isInstalled) "Up to date" else "Install app") } }
            if (progress != null && progress < 100) item { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) }
            item { Section("About this app") { Text(app.description, style = MaterialTheme.typography.bodyLarge) } }
            item { Section("Release notes") { Text(app.releaseNotes.ifBlank { "No release notes provided." }, style = MaterialTheme.typography.bodyMedium) } }
            item { Section("Version history") { app.releases.forEach { release -> Text("${release.version}  •  ${release.formattedDate}", fontWeight = FontWeight.Medium); Text(release.assetName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Section("Personalize") { OutlinedTextField(customName, { customName = it }, label = { Text("Display name override") }, modifier = Modifier.fillMaxWidth(), singleLine = true); TextButton(onClick = { vm.saveOverrideName(app.repo, customName) }) { Text("Save name") } } }
            item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Advanced", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide" else "Show") } }; AnimatedVisibility(advanced) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Repository: ${app.owner}/${app.repo}", style = MaterialTheme.typography.bodySmall); Text("APK asset: ${app.assetName}", style = MaterialTheme.typography.bodySmall); Text("Pre-release: ${if (app.prerelease) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall); OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.repositoryUrl))) }) { Text("View release on GitHub") } } } } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(vm: StoreViewModel, back: () -> Unit) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var ignoredRepos by remember { mutableStateOf(vm.ignoredRepos().joinToString("\n")) }
    var ignoredSaved by remember { mutableStateOf(false) }
    val rateLimit by vm.rateLimit.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("GitHub access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("A personal access token increases API limits and enables private repositories. It is stored only on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(token, { token = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Personal access token") }, singleLine = true)
            Button(onClick = { vm.saveToken(token); saved = true; vm.refresh() }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Saved" else "Save token") }
            Text("API status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${rateLimit.remaining?.toString() ?: "Unknown"} requests remaining${rateLimit.limit?.let { " of $it" } ?: ""}. Reset: ${rateLimit.resetText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ignored repositories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("One owner/repo per line. Ignored repositories are never fetched or shown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(ignoredRepos, { ignoredRepos = it; ignoredSaved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("owner/repo") }, minLines = 2)
            OutlinedButton(onClick = { vm.saveIgnoredRepos(ignoredRepos); ignoredSaved = true; vm.refresh() }, modifier = Modifier.fillMaxWidth()) { Text(if (ignoredSaved) "Ignored list saved" else "Save ignored list") }
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { ThemeMode.entries.forEach { mode -> TextButton(onClick = { ThemeSettings.setMode(mode) }) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) } } }
            Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { listOf(Color(0xFF315F90), Color(0xFF006B5E), Color(0xFF8B4A60), Color(0xFF745900)).forEach { color -> Box(Modifier.size(34.dp).clip(CircleShape).background(color).clickable { ThemeSettings.setAccent(color) }) } }
            Text("Storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = vm::clearDownloads, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp)); Text("Clear cached APKs") }
            OutlinedButton(onClick = { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "Elmadani Store debug export\nPackage: ${context.packageName}\nAndroid: ${android.os.Build.VERSION.RELEASE}"), "Export logs")) }, modifier = Modifier.fillMaxWidth()) { Text("Export debug logs") }
            Text("Updates are checked when the store refreshes. Background checks will notify you when a newer release is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }

@Composable
private fun LoadingList() { LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { items(4) { Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(150.dp)) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) } } } }

@Composable
private fun EmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, retry: () -> Unit) { Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = retry) { Text("Try again") } } } }

