package com.samielmadani.elmadanistore.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.samielmadani.elmadanistore.data.SortMode
import com.samielmadani.elmadanistore.data.StoreApp
import com.samielmadani.elmadanistore.data.ThemeMode
import com.samielmadani.elmadanistore.ui.theme.ThemeSettings

private enum class TopLevelPage { Apps, Websites, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreApp(initialRepo: String? = null, storeViewModel: StoreViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TopLevelPage.entries.size })
    var selectedPage by remember { mutableIntStateOf(0) }
    var selectedApp by remember { mutableStateOf<StoreApp?>(null) }
    val apps by storeViewModel.apps.collectAsState()
    val loading by storeViewModel.loading.collectAsState()
    val error by storeViewModel.error.collectAsState()
    val progress by storeViewModel.downloadProgress.collectAsState()
    val updateNotice by storeViewModel.updateNotice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sortSheetOpen = remember { mutableStateOf(false) }
    LaunchedEffect(apps, initialRepo) {
        initialRepo?.let { repo -> apps.firstOrNull { it.repo.equals(repo, true) }?.let { selectedApp = it } }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedPage = it }
    }
    LaunchedEffect(updateNotice) {
        updateNotice?.let {
            snackbarHostState.showSnackbar(it)
            storeViewModel.dismissUpdateNotice()
        }
    }
    BackHandler(enabled = selectedApp != null || sortSheetOpen.value || selectedPage != 0) {
        when {
            selectedApp != null -> selectedApp = null
            sortSheetOpen.value -> sortSheetOpen.value = false
            else -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = selectedPage == TopLevelPage.Apps.ordinal, onClick = { scope.launch { pagerState.animateScrollToPage(TopLevelPage.Apps.ordinal) } }, icon = { Icon(Icons.Default.CloudDownload, null) }, label = { Text("Apps") })
            NavigationBarItem(selected = selectedPage == TopLevelPage.Websites.ordinal, onClick = { scope.launch { pagerState.animateScrollToPage(TopLevelPage.Websites.ordinal) } }, icon = { Icon(Icons.Default.Language, null) }, label = { Text("Websites") })
            NavigationBarItem(selected = selectedPage == TopLevelPage.Settings.ordinal, onClick = { scope.launch { pagerState.animateScrollToPage(TopLevelPage.Settings.ordinal) } }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (TopLevelPage.entries[page]) {
                    TopLevelPage.Apps -> HomePage(apps, loading, error, progress, storeViewModel, { selectedApp = it }, { sortSheetOpen.value = true }, sortSheetOpen.value) { sortSheetOpen.value = false }
                    TopLevelPage.Websites -> WebsitesPage()
                    TopLevelPage.Settings -> SettingsPage(storeViewModel)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
        }
    }
    selectedApp?.let { app ->
        DetailPage(app, progress[app.repo], storeViewModel) { selectedApp = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(apps: List<StoreApp>, loading: Boolean, error: String?, progress: Map<String, Int>, vm: StoreViewModel, openDetails: (StoreApp) -> Unit, openSort: () -> Unit, sortSheetOpen: Boolean, closeSort: () -> Unit) {
    val context = LocalContext.current
    var sort by remember { mutableStateOf(SortMode.UPDATED) }
    val shownApps = vm.sorted(sort)
    Scaffold(topBar = {
        TopAppBar(title = { Column { Text("Elmadani Store", fontWeight = FontWeight.Bold); Text("Personal app catalogue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, actions = {
            IconButton(onClick = openSort) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort apps") }
        })
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
        ModalBottomSheet(onDismissRequest = closeSort, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.label) }, onClick = { sort = mode; closeSort() })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, progress: Int?, openDetails: () -> Unit, install: () -> Unit) {
    val isInstalling = progress != null && progress < 100
    val accentColor = when {
        isInstalling -> MaterialTheme.colorScheme.tertiary
        app.hasUpdate -> MaterialTheme.colorScheme.error
        app.isInstalled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val cardColor = when {
        app.hasUpdate -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
        isInstalling -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
        app.isInstalled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = app.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(app.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(app.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.version, fontWeight = FontWeight.Medium); Text("  •  ${app.formattedSize}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f));
                StatusChip(app, isInstalling)
                Spacer(Modifier.width(8.dp))
                if (isInstalling) {
                    CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.tertiary)
                } else {
                    AppActionButton(app, onClick = install)
                }
            }
            if (isInstalling) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)) }
        }
    }
}

@Composable
private fun StatusChip(app: StoreApp, isInstalling: Boolean = false) {
    val (container, label, icon) = when {
        isInstalling -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        app.hasUpdate -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.onErrorContainer)
        app.isInstalled -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(if (isInstalling) "Installing" else if (app.hasUpdate) "Update available" else if (app.isInstalled) "Up to date" else "Ready") },
        leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = label,
            disabledLeadingIconContentColor = icon
        ),
        modifier = Modifier.height(32.dp)
    )
}

@Composable
private fun AppActionButton(app: StoreApp, onClick: () -> Unit) {
    val label = if (app.hasUpdate) "Update" else if (app.isInstalled) "Up to date" else "Install"
    val enabled = app.needsInstall
    when {
        app.hasUpdate || !app.isInstalled -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            ),
            modifier = Modifier.height(40.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            Text(label)
        }
        else -> FilledTonalButton(
            onClick = {},
            enabled = false,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier.height(40.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            Text(label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPage(app: StoreApp, progress: Int?, vm: StoreViewModel, back: () -> Unit) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf(vm.overrideName(app.repo).orEmpty()) }
    ModalBottomSheet(onDismissRequest = back, sheetState = rememberModalBottomSheetState()) {
        Scaffold(topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(padding)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = app.iconUrl, contentDescription = null, modifier = Modifier.size(82.dp).clip(RoundedCornerShape(22.dp))); Spacer(Modifier.width(18.dp)); Column { Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Latest ${app.version}", color = MaterialTheme.colorScheme.primary); Text(app.formattedSize, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Button(onClick = { vm.download(app) { file -> context.startActivity(vm.install(file)) } }, enabled = !app.isInstalled || app.hasUpdate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(percent = 50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text(if (app.hasUpdate) "Update app" else if (app.isInstalled) "Up to date" else "Install app") } }
            if (progress != null && progress < 100) item { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) }
            item { Section("About this app") { Text(app.description, style = MaterialTheme.typography.bodyLarge) } }
            item { Section("Release notes") { Text(app.releaseNotes.ifBlank { "No release notes provided." }, style = MaterialTheme.typography.bodyMedium) } }
            item { Section("Version history") { app.releases.forEach { release -> Text("${release.version}  •  ${release.formattedDate}", fontWeight = FontWeight.Medium); Text(release.assetName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Section("Personalize") { OutlinedTextField(customName, { customName = it }, label = { Text("Display name override") }, modifier = Modifier.fillMaxWidth(), singleLine = true); TextButton(onClick = { vm.saveOverrideName(app.repo, customName) }) { Text("Save name") } } }
            item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Advanced", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide" else "Show") } }; AnimatedVisibility(advanced) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Repository: ${app.owner}/${app.repo}", style = MaterialTheme.typography.bodySmall); Text("APK asset: ${app.assetName}", style = MaterialTheme.typography.bodySmall); Text("Pre-release: ${if (app.prerelease) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall); OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.repositoryUrl))) }) { Text("View release on GitHub") } } } } } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(vm: StoreViewModel) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var ignoredRepos by remember { mutableStateOf(vm.ignoredRepos().joinToString("\n")) }
    var ignoredSaved by remember { mutableStateOf(false) }
    val rateLimit by vm.rateLimit.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("GitHub access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("A personal access token increases API limits and enables private repositories. It is stored only on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(token, { token = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Personal access token") }, singleLine = true)
            Button(onClick = { vm.saveToken(token); saved = true; vm.refresh() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(if (saved) "Saved" else "Save token") }
            Text("API status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${rateLimit.remaining?.toString() ?: "Unknown"} requests remaining${rateLimit.limit?.let { " of $it" } ?: ""}. Reset: ${rateLimit.resetText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ignored repositories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("One owner/repo per line. Ignored repositories are never fetched or shown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(ignoredRepos, { ignoredRepos = it; ignoredSaved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("owner/repo") }, minLines = 2)
            FilledTonalButton(onClick = { vm.saveIgnoredRepos(ignoredRepos); ignoredSaved = true; vm.refresh() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50)) { Text(if (ignoredSaved) "Ignored list saved" else "Save ignored list") }
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { ThemeMode.entries.forEach { mode -> TextButton(onClick = { ThemeSettings.setMode(mode) }) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) } } }
            Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { listOf(Color(0xFF315F90), Color(0xFF006B5E), Color(0xFF8B4A60), Color(0xFF745900)).forEach { color -> Box(Modifier.size(34.dp).clip(CircleShape).background(color).clickable { ThemeSettings.setAccent(color) }) } }
            Text("Storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = vm::clearDownloads, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp)); Text("Clear cached APKs") }
            TextButton(onClick = { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "Elmadani Store debug export\nPackage: ${context.packageName}\nAndroid: ${android.os.Build.VERSION.RELEASE}"), "Export logs")) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Export debug logs") }
            Text("Updates are checked when the store refreshes. Background checks will notify you when a newer release is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebsitesPage() {
    Scaffold(topBar = { TopAppBar(title = { Text("Websites") }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            EmptyState("No websites added yet", "Websites you add will appear here.", Icons.Default.Language, null)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }

@Composable
private fun LoadingList() {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(4) { AppSkeletonCard() }
    }
}

@Composable
private fun AppSkeletonCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp), shape = RoundedCornerShape(10.dp))
                Skeleton(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp), shape = RoundedCornerShape(10.dp))
            }
        }
        Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Skeleton(modifier = Modifier.width(92.dp).height(30.dp), shape = RoundedCornerShape(50))
            Spacer(Modifier.width(8.dp))
            Skeleton(modifier = Modifier.width(74.dp).height(38.dp), shape = RoundedCornerShape(50))
        }
    }
}

@Composable
private fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), baseColor: Color = MaterialTheme.colorScheme.surfaceVariant, shimmerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer_offset"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor.copy(alpha = 0.55f))
            .drawBehind {
                val start = size.width * (shimmerOffset - 0.25f)
                val end = size.width * (shimmerOffset + 0.75f)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(end, size.height)
                    )
                )
            }
    )
}

@Composable
private fun EmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, retry: (() -> Unit)?) { Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); retry?.let { Button(onClick = it, shape = RoundedCornerShape(percent = 50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Try again") } } } } }

