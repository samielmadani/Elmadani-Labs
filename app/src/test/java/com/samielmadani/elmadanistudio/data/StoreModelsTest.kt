package com.samielmadani.elmadanistudio.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreModelsTest {
    @Test
    fun matchingSelfUpdateReleaseIsNotAnUpdate() {
        assertFalse(app(installedCode = 12L, latestCode = 12L).hasUpdate)
    }

    @Test
    fun newerReleaseCodeIsAnUpdate() {
        assertTrue(app(installedCode = 12L, latestCode = 13L, version = "v1.0.12").hasUpdate)
    }

    @Test
    fun olderReleaseCodeIsNotAnUpdate() {
        assertFalse(app(installedCode = 12L, latestCode = 11L, version = "v1.0.10").hasUpdate)
    }

    @Test
    fun matchingVersionNamesIgnoreTagPrefix() {
        assertFalse(app(installedCode = null, latestCode = null).hasUpdate)
    }

    private fun app(
        installedCode: Long?,
        latestCode: Long?,
        version: String = "v1.0.11"
    ) = StoreApp(
        owner = "test-owner",
        repo = "test-app",
        name = "Test app",
        description = "",
        iconUrl = null,
        repositoryUrl = "",
        releaseId = 1L,
        version = version,
        releaseNotes = "",
        publishedAt = "",
        assetName = "app.apk",
        assetSize = 0L,
        downloadUrl = "",
        prerelease = false,
        releaseVersionCode = latestCode,
        installedVersion = "1.0.11",
        installedVersionCode = installedCode
    )
}