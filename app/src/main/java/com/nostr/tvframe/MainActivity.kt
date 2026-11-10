package com.nostr.tvframe

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.nostr.tvframe.ui.MainViewModel
import com.nostr.tvframe.ui.screens.MainTvScreen
import com.nostr.tvframe.ui.theme.TVFrameTheme

/**
 * Main Android TV Leanback Launcher Activity (`android.intent.category.LEANBACK_LAUNCHER`).
 * Integrates Android TV 12 Storage Access Framework (`OpenDocumentTree`), All-Files Access
 * permission intent for USB drives, and system Screensaver settings launcher.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val usbTreePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            }
            viewModel.onUsbSafTreeSelected(treeUri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TVFrameTheme {
                MainTvScreen(
                    viewModel = viewModel,
                    onRequestUsbSafPicker = {
                        runCatching { usbTreePickerLauncher.launch(null) }
                    },
                    onRequestAllFilesPermission = {
                        openAllFilesAccessSettings()
                    },
                    onOpenSystemDreamSettings = {
                        openAndroidTvDreamSettings()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUsbAndCacheMetrics()
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }.onFailure {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }

    private fun openAndroidTvDreamSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}
