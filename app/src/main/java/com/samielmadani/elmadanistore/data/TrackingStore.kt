package com.samielmadani.elmadanistore.data

import android.content.Context
import android.content.pm.PackageInfo
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

val Context.trackingDataStore by preferencesDataStore(name = "tracked_apps")

data class TrackedAppEntity(
    val repo: String,
    val packageName: String?,
    val installedVersionCode: Long?,
    val installedVersionName: String?,
    val lastCheckedAt: Long,
    val latestVersion: String?,
    val latestVersionCode: Long?,
    val latestAssetName: String?,
    val latestAssetSize: Long?,
    val latestDownloadUrl: String?
)

class TrackingStore(private val context: Context) {
    private val key = stringPreferencesKey("entries")

    suspend fun all(): List<TrackedAppEntity> = decode(context.trackingDataStore.data.first()[key])

    suspend fun recordLatest(app: StoreApp): TrackedAppEntity {
        val current = all().associateBy { it.repo }.toMutableMap()
        val existing = current[app.repo]
        val entity = TrackedAppEntity(
            repo = app.repo,
            packageName = app.packageName ?: existing?.packageName,
            installedVersionCode = existing?.installedVersionCode,
            installedVersionName = existing?.installedVersionName,
            lastCheckedAt = System.currentTimeMillis(),
            latestVersion = app.version,
            latestVersionCode = app.releaseVersionCode,
            latestAssetName = app.assetName,
            latestAssetSize = app.assetSize,
            latestDownloadUrl = app.downloadUrl
        )
        current[app.repo] = entity
        save(current.values)
        return entity
    }

    suspend fun reconcileInstalled(app: StoreApp): TrackedAppEntity? {
        val current = all().associateBy { it.repo }.toMutableMap()
        val existing = current[app.repo] ?: return null
        val packageName = existing.packageName ?: app.packageName ?: return existing
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        val entity = existing.copy(
            packageName = packageName,
            installedVersionCode = installed?.let(::versionCode),
            installedVersionName = installed?.versionName
        )
        current[app.repo] = entity
        save(current.values)
        return entity
    }

    suspend fun updatePackageName(repo: String, packageName: String) {
        val current = all().associateBy { it.repo }.toMutableMap()
        current[repo]?.let { current[repo] = it.copy(packageName = packageName) }
        save(current.values)
    }

    private suspend fun save(entries: Collection<TrackedAppEntity>) {
        context.trackingDataStore.edit { it[key] = encode(entries) }
    }

    private fun encode(entries: Collection<TrackedAppEntity>) = JSONArray().apply {
        entries.forEach { entry -> put(JSONObject().apply {
            put("repo", entry.repo)
            put("packageName", entry.packageName)
            put("installedVersionCode", entry.installedVersionCode)
            put("installedVersionName", entry.installedVersionName)
            put("lastCheckedAt", entry.lastCheckedAt)
            put("latestVersion", entry.latestVersion)
            put("latestVersionCode", entry.latestVersionCode)
            put("latestAssetName", entry.latestAssetName)
            put("latestAssetSize", entry.latestAssetSize)
            put("latestDownloadUrl", entry.latestDownloadUrl)
        }) }
    }.toString()

    private fun decode(raw: String?): List<TrackedAppEntity> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TrackedAppEntity(
                repo = item.getString("repo"),
                packageName = item.optString("packageName").ifBlank { null },
                installedVersionCode = item.optLong("installedVersionCode").takeUnless { it == 0L },
                installedVersionName = item.optString("installedVersionName").ifBlank { null },
                lastCheckedAt = item.optLong("lastCheckedAt"),
                latestVersion = item.optString("latestVersion").ifBlank { null },
                latestVersionCode = item.optLong("latestVersionCode").takeUnless { it == 0L },
                latestAssetName = item.optString("latestAssetName").ifBlank { null },
                latestAssetSize = item.optLong("latestAssetSize").takeUnless { it == 0L },
                latestDownloadUrl = item.optString("latestDownloadUrl").ifBlank { null }
            )
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}
