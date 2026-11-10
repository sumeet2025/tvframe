package com.nostr.tvframe.data.storage

import android.net.Uri
import com.nostr.tvframe.data.local.PhotoEntity
import com.nostr.tvframe.data.local.SmbCredentialStore
import com.nostr.tvframe.domain.model.PhotoSourceType
import com.nostr.tvframe.domain.model.SmbAuthMode
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

/**
 * Lightweight SMBv2/SMBv3 client powered by `jcifs-ng` supporting both:
 * - Guest / Anonymous local shares (`SmbAuthMode.GUEST_ANONYMOUS`)
 * - Authenticated Username / Password / Domain shares (`SmbAuthMode.USER_PASSWORD`)
 *
 * Syncs and caches shared NAS images over Wi-Fi/Ethernet into the active cache directory
 * so Coil and the DreamService can render smooth crossfades without network stutter.
 */
class SmbNetworkDataSource(
    private val credentialStore: SmbCredentialStore
) {
    private val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")

    private fun buildCifsContext(authMode: SmbAuthMode): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "10000")
            setProperty("jcifs.smb.client.connTimeout", "7000")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
        }
        val baseContext = BaseContext(PropertyConfiguration(props))

        return when (authMode) {
            SmbAuthMode.GUEST_ANONYMOUS -> baseContext.withGuestCrendentials()
            SmbAuthMode.USER_PASSWORD -> {
                val creds = credentialStore.getCredentials()
                if (creds.username.isBlank()) {
                    baseContext.withAnonymousCredentials()
                } else {
                    val authenticator = NtlmPasswordAuthenticator(
                        creds.domain.ifBlank { null },
                        creds.username,
                        creds.password
                    )
                    baseContext.withCredentials(authenticator)
                }
            }
        }
    }

    /**
     * Tests connectivity to the configured SMB host/share and returns the count of discovered images.
     */
    suspend fun testSmbConnection(
        hostIp: String,
        shareAndPath: String,
        authMode: SmbAuthMode
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildSmbFolderUrl(hostIp, shareAndPath)
            val context = buildCifsContext(authMode)
            SmbFile(url, context).use { rootDir ->
                val files = rootDir.listFiles() ?: emptyArray()
                files.count { file ->
                    !file.isDirectory && file.name.substringAfterLast('.', "").lowercase() in supportedExtensions
                }
            }
        }
    }

    /**
     * Scans the remote SMB share and caches photos into [targetCacheDir] for smooth TV playback.
     * Note: Local/SMB photos auto-hide the QR code (`commonsPageUrl = null`).
     */
    suspend fun syncSmbSharePhotos(
        hostIp: String,
        shareAndPath: String,
        authMode: SmbAuthMode,
        targetCacheDir: File,
        maxPhotosToSync: Int = 60
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        if (hostIp.isBlank() || shareAndPath.isBlank()) return@withContext emptyList()

        val smbCacheSubdir = File(targetCacheDir, "smb_synced_photos").apply {
            if (!exists()) mkdirs()
        }

        val cifsContext = buildCifsContext(authMode)
        val rootUrl = buildSmbFolderUrl(hostIp, shareAndPath)
        val syncedEntities = mutableListOf<PhotoEntity>()

        runCatching {
            SmbFile(rootUrl, cifsContext).use { smbRoot ->
                collectAndCacheSmbFiles(
                    smbDir = smbRoot,
                    hostLabel = "$hostIp/${shareAndPath.trim('/')}",
                    cacheDir = smbCacheSubdir,
                    collector = syncedEntities,
                    maxItems = maxPhotosToSync
                )
            }
        }

        syncedEntities
    }

    private fun collectAndCacheSmbFiles(
        smbDir: SmbFile,
        hostLabel: String,
        cacheDir: File,
        collector: MutableList<PhotoEntity>,
        maxItems: Int,
        depth: Int = 0
    ) {
        if (collector.size >= maxItems || depth > 3) return
        val children = runCatching { smbDir.listFiles() }.getOrNull() ?: return

        for (smbFile in children) {
            if (collector.size >= maxItems) break
            val cleanName = smbFile.name.trimEnd('/')
            if (cleanName.startsWith(".")) continue

            if (smbFile.isDirectory) {
                collectAndCacheSmbFiles(smbFile, hostLabel, cacheDir, collector, maxItems, depth + 1)
            } else {
                val ext = cleanName.substringAfterLast('.', "").lowercase()
                if (ext in supportedExtensions) {
                    val safeLocalName = "${smbFile.uncPath.hashCode()}_$cleanName"
                    val destFile = File(cacheDir, safeLocalName)

                    if (!destFile.exists() || destFile.length() == 0L) {
                        runCatching {
                            smbFile.inputStream.use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output, bufferSize = 64 * 1024)
                                }
                            }
                        }
                    }

                    if (destFile.exists() && destFile.length() > 0L) {
                        val folderName = smbDir.name.trim('/').ifBlank { hostLabel }
                        collector.add(
                            PhotoEntity(
                                id = "smb_${smbFile.uncPath.hashCode()}",
                                sourceType = PhotoSourceType.SMB.name,
                                imageUrlOrUri = Uri.fromFile(destFile).toString(),
                                localCachedFilePath = destFile.absolutePath,
                                photographerName = "Network Share (SMB)",
                                locationName = folderName,
                                commonsPageUrl = null, // Auto-hides QR code on SMB files
                                categoryKey = "SMB",
                                width = 1920,
                                height = 1080,
                                isFavorite = false,
                                fileSizeBytes = destFile.length()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun buildSmbFolderUrl(hostIp: String, shareAndPath: String): String {
        val cleanHost = hostIp.trim().removePrefix("smb://").trimEnd('/')
        val cleanPath = shareAndPath.trim().trim('/')
        return "smb://$cleanHost/$cleanPath/"
    }
}
