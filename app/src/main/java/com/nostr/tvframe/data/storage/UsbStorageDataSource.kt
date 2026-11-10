package com.nostr.tvframe.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import com.nostr.tvframe.data.local.PhotoEntity
import com.nostr.tvframe.data.local.UserPreferencesRepository
import com.nostr.tvframe.domain.model.PhotoSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Represents a detected external USB mass storage volume on Android TV / Google TV (Android 11+).
 */
data class UsbVolumeInfo(
    val displayName: String,
    val rootPath: String,
    val freeSpaceMb: Long,
    val totalSpaceMb: Long,
    val isWritableForCache: Boolean
)

/**
 * Handles external USB mass storage discovery, scanning, cache directory resolution,
 * and hot-unplug fallback on Android TV / Google TV (Android 11+).
 *
 * Supports both:
 * 1. Direct mounted USB paths under `/storage/<UUID>` (via `StorageManager.storageVolumes`
 *    or app-specific `Context.getExternalFilesDirs(null)` / `MANAGE_EXTERNAL_STORAGE`), and
 * 2. Storage Access Framework (`DocumentFile` tree URIs) if selected via a system picker.
 */
class UsbStorageDataSource(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val _isUsbMounted = MutableStateFlow(false)
    val isUsbMounted: StateFlow<Boolean> = _isUsbMounted.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")

    /**
     * Enumerates all removable USB volumes currently mounted on the Android TV device.
     */
    fun detectMountedUsbVolumes(): List<UsbVolumeInfo> {
        val volumes = mutableListOf<UsbVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        // 1. Query Android 11/12+ StorageVolume directory API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && storageManager != null) {
            for (sv in storageManager.storageVolumes) {
                if (sv.isRemovable && sv.state == Environment.MEDIA_MOUNTED) {
                    val dir = sv.directory ?: continue
                    if (dir.exists()) {
                        volumes.add(
                            UsbVolumeInfo(
                                displayName = sv.getDescription(context) ?: dir.name,
                                rootPath = dir.absolutePath,
                                freeSpaceMb = dir.usableSpace / (1024L * 1024L),
                                totalSpaceMb = dir.totalSpace / (1024L * 1024L),
                                isWritableForCache = true
                            )
                        )
                    }
                }
            }
        }

        // 2. Also inspect getExternalFilesDirs(null) which always grants app-specific write access
        // on removable USB drives on Android TV 12 without needing MANAGE_EXTERNAL_STORAGE.
        val externalAppDirs = context.getExternalFilesDirs(null).drop(1)
        for (appDir in externalAppDirs.filterNotNull()) {
            val state = Environment.getExternalStorageState(appDir)
            if (state == Environment.MEDIA_MOUNTED) {
                val rootVolumeDir = appDir.absolutePath.substringBefore("/Android/data")
                val rootFile = File(rootVolumeDir)
                val pathToUse = if (rootFile.canRead()) rootFile.absolutePath else appDir.absolutePath
                if (volumes.none { it.rootPath == pathToUse }) {
                    volumes.add(
                        UsbVolumeInfo(
                            displayName = "USB Drive (${rootFile.name})",
                            rootPath = pathToUse,
                            freeSpaceMb = appDir.usableSpace / (1024L * 1024L),
                            totalSpaceMb = appDir.totalSpace / (1024L * 1024L),
                            isWritableForCache = appDir.canWrite()
                        )
                    )
                }
            }
        }

        _isUsbMounted.value = volumes.isNotEmpty()
        return volumes
    }

    /**
     * Checks if the user has granted All-Files Access (`MANAGE_EXTERNAL_STORAGE`) on Android 11/12+
     * so the app can browse arbitrary user folders on a plugged-in USB drive.
     */
    fun hasAllFilesAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Returns an app-writable cache directory on the mounted USB drive if available,
     * or null if no USB drive is mounted.
     */
    fun getUsbWritableCacheDirectory(): File? {
        val externalAppDirs = context.getExternalFilesDirs(null).drop(1).filterNotNull()
        for (usbAppDir in externalAppDirs) {
            if (Environment.getExternalStorageState(usbAppDir) == Environment.MEDIA_MOUNTED && usbAppDir.canWrite()) {
                val cacheDir = File(usbAppDir, "tvframe_usb_cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                if (cacheDir.exists() && cacheDir.canWrite()) {
                    return cacheDir
                }
            }
        }
        return null
    }

    /**
     * Verifies if the configured USB path or SAF URI is still mounted and readable.
     * If unplugged, updates runtime state so the app gracefully falls back to internal storage.
     */
    suspend fun verifyUsbMountOrFallback(): Boolean = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val volumes = detectMountedUsbVolumes()

        val pathValid = prefs.usbDirectoryPath.isNotBlank() &&
            File(prefs.usbDirectoryPath).let { it.exists() && it.canRead() }

        val safValid = prefs.usbTreeUriString.isNotBlank() && runCatching {
            val uri = Uri.parse(prefs.usbTreeUriString)
            DocumentFile.fromTreeUri(context, uri)?.let { it.exists() && it.canRead() } == true
        }.getOrDefault(false)

        val mounted = pathValid || safValid || volumes.isNotEmpty()
        _isUsbMounted.value = mounted
        mounted
    }

    /**
     * Called by [com.nostr.tvframe.receiver.UsbHotPlugReceiver] when a USB drive is unplugged.
     */
    fun onUsbUnpluggedEvent(unmountedPath: String?) {
        _isUsbMounted.value = false
        _statusBannerMessage.value =
            "USB drive unplugged (${unmountedPath ?: "external"}). Switched seamlessly to internal TV storage."
    }

    fun onUsbMountedEvent(mountedPath: String?) {
        detectMountedUsbVolumes()
        _statusBannerMessage.value =
            "USB storage detected (${mountedPath ?: "USB"}). External storage expansion ready."
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    /**
     * Scans the selected USB directory (File path or SAF DocumentFile tree) for high-res photos.
     * Note: Per user specification, we do NOT extract verbose EXIF tags and QR codes auto-hide for local files.
     */
    suspend fun scanUsbPhotos(maxItems: Int = 500): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val results = mutableListOf<PhotoEntity>()

        // 1. Scan direct File path if configured and accessible
        val targetPath = prefs.usbDirectoryPath.ifBlank {
            detectMountedUsbVolumes().firstOrNull()?.rootPath.orEmpty()
        }
        if (targetPath.isNotBlank()) {
            val rootDir = File(targetPath)
            if (rootDir.exists() && rootDir.canRead()) {
                scanDirectoryRecursive(rootDir, results, maxItems)
            }
        }

        // 2. Scan SAF DocumentFile tree URI if configured
        if (results.isEmpty() && prefs.usbTreeUriString.isNotBlank()) {
            runCatching {
                val treeUri = Uri.parse(prefs.usbTreeUriString)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.exists() && rootDoc.canRead()) {
                    scanDocumentTreeRecursive(rootDoc, results, maxItems)
                }
            }
        }

        results
    }

    private fun scanDirectoryRecursive(
        dir: File,
        collector: MutableList<PhotoEntity>,
        maxItems: Int,
        depth: Int = 0
    ) {
        if (collector.size >= maxItems || depth > 5) return
        val children = dir.listFiles() ?: return

        for (file in children) {
            if (collector.size >= maxItems) break
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                // Skip internal Android system/cache folders when scanning for user photos
                if (!file.name.equals("tvframe_usb_cache", ignoreCase = true)) {
                    scanDirectoryRecursive(file, collector, maxItems, depth + 1)
                }
            } else if (file.isFile && file.extension.lowercase() in supportedExtensions) {
                val parentFolderName = file.parentFile?.name ?: "USB Storage"
                collector.add(
                    PhotoEntity(
                        id = "usb_${file.absolutePath.hashCode()}",
                        sourceType = PhotoSourceType.USB.name,
                        imageUrlOrUri = Uri.fromFile(file).toString(),
                        localCachedFilePath = file.absolutePath,
                        photographerName = "USB Storage",
                        locationName = parentFolderName,
                        commonsPageUrl = null, // Auto-hides QR code
                        categoryKey = "USB",
                        width = 1920,
                        height = 1080,
                        isFavorite = false,
                        fileSizeBytes = file.length()
                    )
                )
            }
        }
    }

    private fun scanDocumentTreeRecursive(
        docDir: DocumentFile,
        collector: MutableList<PhotoEntity>,
        maxItems: Int,
        depth: Int = 0
    ) {
        if (collector.size >= maxItems || depth > 5) return
        for (doc in docDir.listFiles()) {
            if (collector.size >= maxItems) break
            val name = doc.name ?: continue
            if (name.startsWith(".")) continue
            if (doc.isDirectory) {
                scanDocumentTreeRecursive(doc, collector, maxItems, depth + 1)
            } else if (doc.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supportedExtensions) {
                    val uriStr = doc.uri.toString()
                    collector.add(
                        PhotoEntity(
                            id = "usb_saf_${uriStr.hashCode()}",
                            sourceType = PhotoSourceType.USB.name,
                            imageUrlOrUri = uriStr,
                            localCachedFilePath = null,
                            photographerName = "USB Storage",
                            locationName = docDir.name ?: "External Drive",
                            commonsPageUrl = null, // Auto-hides QR code
                            categoryKey = "USB",
                            width = 1920,
                            height = 1080,
                            isFavorite = false,
                            fileSizeBytes = doc.length()
                        )
                    )
                }
            }
        }
    }
}
