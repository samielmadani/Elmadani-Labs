package com.samielmadani.elmadanistore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samielmadani.elmadanistore.data.SortMode
import com.samielmadani.elmadanistore.data.StoreApp
import com.samielmadani.elmadanistore.data.StoreRepository
import com.samielmadani.elmadanistore.data.RateLimitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)
    private val _apps = MutableStateFlow<List<StoreApp>>(emptyList())
    val apps: StateFlow<List<StoreApp>> = _apps.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()
    private val _rateLimit = MutableStateFlow(RateLimitStatus())
    val rateLimit: StateFlow<RateLimitStatus> = _rateLimit.asStateFlow()
    private var lastManualRefresh = 0L

    init { refresh() }
    fun refresh() {
        val now = System.currentTimeMillis()
        if (repository.isManualRefreshThrottled(now) || (_apps.value.isNotEmpty() && now - lastManualRefresh < 60_000L)) return
        lastManualRefresh = now
        repository.markManualRefresh(now)
        viewModelScope.launch { _loading.value = true; _error.value = null; runCatching { repository.loadApps() }.onSuccess { _apps.value = it }.onFailure { _error.value = it.message ?: "Could not connect to GitHub" }; _rateLimit.value = repository.rateLimitStatus; _loading.value = false }
    }
    fun download(app: StoreApp, onReady: (java.io.File) -> Unit) { viewModelScope.launch { runCatching { repository.download(app) { progress -> _downloadProgress.value = _downloadProgress.value + (app.repo to progress) } }.onSuccess { onReady(it) }.onFailure { _error.value = it.message } } }
    fun install(file: java.io.File) = repository.install(file)
    fun clearDownloads() = repository.clearDownloads()
    fun saveToken(token: String) = repository.saveToken(token)
    fun ignoredRepos(): Set<String> = repository.ignoredRepos()
    fun saveIgnoredRepos(repos: String) = repository.saveIgnoredRepos(repos)
    fun overrideName(repo: String): String? = repository.overrideName(repo)
    fun saveOverrideName(repo: String, name: String) { repository.saveOverrideName(repo, name) }
    fun sorted(mode: SortMode): List<StoreApp> = when (mode) { SortMode.NAME -> _apps.value.sortedBy { it.name.lowercase() }; SortMode.UPDATED -> _apps.value.sortedByDescending { it.publishedAt }; SortMode.INSTALLED -> _apps.value.sortedByDescending { it.isInstalled } }
}
