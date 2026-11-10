package com.nostr.tvframe

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.nostr.tvframe.data.local.AppDatabase
import com.nostr.tvframe.data.local.SmbCredentialStore
import com.nostr.tvframe.data.local.UserPreferencesRepository
import com.nostr.tvframe.data.remote.WikimediaClient
import com.nostr.tvframe.data.repository.PhotoRepository
import com.nostr.tvframe.data.storage.SmartCacheManager
import com.nostr.tvframe.data.storage.SmbNetworkDataSource
import com.nostr.tvframe.data.storage.UsbStorageDataSource
import com.nostr.tvframe.data.worker.PrefetchWorker
import com.nostr.tvframe.receiver.UsbHotPlugReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Application singleton managing the dependency container, dynamic Coil DiskCache
 * (supporting hot-swappable USB cache expansion), and periodic WorkManager pre-fetching.
 */
class TVFrameApp : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var smbCredentialStore: SmbCredentialStore
        private set

    lateinit var usbStorageDataSource: UsbStorageDataSource
        private set

    lateinit var smbNetworkDataSource: SmbNetworkDataSource
        private set

    lateinit var smartCacheManager: SmartCacheManager
        private set

    lateinit var photoRepository: PhotoRepository
        private set

    private var usbHotPlugReceiver: UsbHotPlugReceiver? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferencesRepository = UserPreferencesRepository(this)
        smbCredentialStore = SmbCredentialStore(this)
        usbStorageDataSource = UsbStorageDataSource(this, preferencesRepository)
        smbNetworkDataSource = SmbNetworkDataSource(smbCredentialStore)
        smartCacheManager = SmartCacheManager(
            context = this,
            photoDao = database.photoDao(),
            preferencesRepository = preferencesRepository,
            usbStorageDataSource = usbStorageDataSource
        )
        photoRepository = PhotoRepository(
            context = this,
            photoDao = database.photoDao(),
            wikimediaApi = WikimediaClient.apiService,
            usbStorageDataSource = usbStorageDataSource,
            smbNetworkDataSource = smbNetworkDataSource,
            smartCacheManager = smartCacheManager,
            preferencesRepository = preferencesRepository
        )

        registerUsbHotPlugMonitor()
        PrefetchWorker.schedulePeriodicPrefetch(this)

        // Verify mounted USB path on startup and prune expired cache entries
        applicationScope.launch {
            usbStorageDataSource.verifyUsbMountOrFallback()
            smartCacheManager.enforceStorageQuota()
        }
    }

    /**
     * Dynamically configures Coil's DiskCache to use the external USB cache directory
     * when available and mounted, or seamlessly falls back to internal TV flash cache.
     */
    override fun newImageLoader(): ImageLoader {
        val prefs = runBlocking { preferencesRepository.userPreferencesFlow.first() }
        val activeCacheDir: File = smartCacheManager.resolveActiveCacheDirectory()
        val maxQuotaBytes = prefs.cacheQuotaMb.toLong() * 1024L * 1024L

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "TVFrame/1.0 (Android TV; https://primal.net/s; s@primal.net)"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(activeCacheDir, "coil_image_cache"))
                    .maxSizeBytes(maxQuotaBytes)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    private fun registerUsbHotPlugMonitor() {
        val receiver = UsbHotPlugReceiver()
        usbHotPlugReceiver = receiver
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            mediaFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            receiver,
            usbFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    companion object {
        lateinit var instance: TVFrameApp
            private set
    }
}
