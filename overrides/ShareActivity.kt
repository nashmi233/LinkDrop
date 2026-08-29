/*
 * Based on KDE Connect Android.
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 *
 * LinkDrop V1 modification: adds "Send to all devices".
 * Compatible with KDE Connect Android v1.35.9.
 */
package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.URLUtil
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.compose.screen.share.ShareScreen
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding

class ShareActivity : BaseActivity<ActivityShareBinding>() {

    override val binding: ActivityShareBinding by lazy { ActivityShareBinding.inflate(layoutInflater) }
    override val isScrollable: Boolean = true

    private var isRefreshing by mutableStateOf(false)
    private var uiDevices by mutableStateOf<List<DeviceUiModel>>(emptyList())
    private var intentHasUrl by mutableStateOf(false)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.refresh, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.menu_refresh) {
            refreshDevicesAction()
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    private fun refreshDevicesAction() {
        isRefreshing = true
        BackgroundService.ForceRefreshConnections(context = this)
        binding.devicesListLayout.composeView.postDelayed({
            isRefreshing = false
        }, 1500)
    }

    private fun doesIntentContainUrl(sourceIntent: Intent?): Boolean {
        val text = sourceIntent?.extras?.getString(Intent.EXTRA_TEXT)
        return URLUtil.isHttpUrl(text) || URLUtil.isHttpsUrl(text)
    }

    private fun updateDeviceList() {
        val action = intent.action
        if (Intent.ACTION_SEND != action && Intent.ACTION_SEND_MULTIPLE != action) {
            finish()
            return
        }

        val devices = KdeConnect.getInstance().devices.values
        intentHasUrl = doesIntentContainUrl(intent)
        uiDevices = devices
            .filter { device -> device.isPaired && (intentHasUrl || device.isReachable) }
            .map { it.toUiModel() }
    }

    private fun shareToDeviceAndFinish(deviceId: String, sourceIntent: Intent) {
        shareToDevice(deviceId, sourceIntent, showErrors = true)
        finish()
    }

    private fun shareToAllAndFinish(sourceIntent: Intent) {
        var accepted = 0

        uiDevices.forEach { uiDevice ->
            if (shareToDevice(uiDevice.id, Intent(sourceIntent), showErrors = false)) {
                accepted++
            }
        }

        Toast.makeText(
            this,
            getString(R.string.sent_to_all_devices, accepted),
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun shareToDevice(
        deviceId: String,
        sourceIntent: Intent,
        showErrors: Boolean
    ): Boolean {
        val device = KdeConnect.getInstance().getDevice(id = deviceId)

        if (device == null) {
            if (showErrors) {
                Toast.makeText(this, getString(R.string.unknown_device), Toast.LENGTH_LONG).show()
            }
            return false
        }

        if (!device.isReachable) {
            val url = sourceIntent.getStringExtra(Intent.EXTRA_TEXT)
            return if (doesIntentContainUrl(sourceIntent) && url != null) {
                storeUrlForFutureDelivery(device, url, showToast = showErrors)
                true
            } else {
                if (showErrors) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_not_reachable),
                        Toast.LENGTH_LONG
                    ).show()
                }
                false
            }
        }

        val plugin = KdeConnect.getInstance().getDevicePlugin(
            deviceId = device.deviceId,
            pluginClass = SharePlugin::class.java
        ) ?: return false

        plugin.share(sourceIntent)
        return true
    }

    private fun storeUrlForFutureDelivery(
        device: Device,
        url: String,
        showToast: Boolean = true
    ) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val key = KEY_UNREACHABLE_URL_LIST + device.deviceId
        val oldUrlSet = sharedPrefs.getStringSet(key, null)
        val newUrlSet = mutableSetOf(url)
        if (oldUrlSet != null) {
            newUrlSet.addAll(oldUrlSet)
        }
        sharedPrefs.edit().putStringSet(key, newUrlSet).apply()

        if (showToast) {
            Toast.makeText(
                this,
                getString(R.string.unreachable_share_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            displayOptions =
                ActionBar.DISPLAY_SHOW_HOME or
                    ActionBar.DISPLAY_SHOW_TITLE or
                    ActionBar.DISPLAY_SHOW_CUSTOM
        }

        binding.devicesListLayout.composeView.setContent {
            KdeTheme(this) {
                ShareScreen(
                    devices = uiDevices,
                    intentHasUrl = intentHasUrl,
                    isRefreshing = isRefreshing,
                    onDeviceClick = { deviceId ->
                        shareToDeviceAndFinish(deviceId, intent)
                    },
                    onSendToAll = {
                        shareToAllAndFinish(intent)
                    },
                    onRefresh = {
                        refreshDevicesAction()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        var deviceId = intent.getStringExtra("deviceId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
        }

        if (deviceId != null) {
            shareToDeviceAndFinish(deviceId, intent)
        } else {
            KdeConnect.getInstance().addDeviceListChangedCallback(key = "ShareActivity") {
                runOnUiThread { updateDeviceList() }
            }
            BackgroundService.ForceRefreshConnections(context = this)
            updateDeviceList()
        }
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback(key = "ShareActivity")
        super.onStop()
    }

    companion object {
        private const val KEY_UNREACHABLE_URL_LIST = "key_unreachable_url_list"
    }
}
