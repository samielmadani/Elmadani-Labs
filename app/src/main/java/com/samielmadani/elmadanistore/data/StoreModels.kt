package com.samielmadani.elmadanistore.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

 data class StoreApp(
    val owner: String,
    val repo: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val repositoryUrl: String,
    val releaseId: Long,
    val version: String,
    val releaseNotes: String,
    val publishedAt: String,
    val assetName: String,
    val assetSize: Long,
    val downloadUrl: String,
    val prerelease: Boolean,
    val packageName: String? = null,
    val releaseVersionCode: Long? = null,
    val installedVersion: String? = null,
    val installedVersionCode: Long? = null,
    val releases: List<ReleaseSummary> = emptyList()
) {
    val isInstalled get() = installedVersion != null
    val hasUpdate get() = isInstalled && (releaseVersionCode?.let { installedVersionCode?.let { installed -> it > installed } } ?: (version != installedVersion))
    val needsInstall get() = !isInstalled || hasUpdate
    val formattedSize get() = if (assetSize < 1_048_576) "${assetSize / 1024} KB" else "%.1f MB".format(assetSize / 1_048_576f)
    val lastUpdatedText: String get() = runCatching {
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(Instant.parse(publishedAt))
    }.getOrDefault(publishedAt.take(10))
    val isNew: Boolean get() = runCatching {
        val published = Instant.parse(publishedAt).toEpochMilli()
        System.currentTimeMillis() - published <= TimeUnit.DAYS.toMillis(14)
    }.getOrDefault(false)
}

data class RateLimitStatus(
    val remaining: Int? = null,
    val limit: Int? = null,
    val resetAt: Long? = null
) {
    val resetText: String
        get() = resetAt?.let {
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(it * 1000))
        } ?: "Unknown"
}

data class ReleaseSummary(
    val version: String,
    val notes: String,
    val publishedAt: String,
    val assetName: String,
    val assetSize: Long,
    val downloadUrl: String,
    val prerelease: Boolean
) {
    val formattedDate: String get() = runCatching {
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(Instant.parse(publishedAt))
    }.getOrDefault(publishedAt.take(10))
}

enum class SortMode(val label: String) { NAME("Name"), UPDATED("Recently updated"), INSTALLED("Install status") }
enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }
