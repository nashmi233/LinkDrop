/*
 * Based on KDE Connect Android.
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 *
 * LinkDrop V1 modification: adds "Send to all devices".
 */
package org.kde.kdeconnect.ui.compose.screen.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeCard
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    devices: List<DeviceUiModel>,
    intentHasUrl: Boolean,
    isRefreshing: Boolean,
    onDeviceClick: (String) -> Unit,
    onSendToAll: () -> Unit,
    onRefresh: () -> Unit
) {
    val state = rememberLazyListState()
    val sectionTitle = if (intentHasUrl) {
        "${stringResource(id = R.string.unreachable_device_url_share_text)} ${stringResource(id = R.string.share_to)}"
    } else {
        stringResource(id = R.string.share_to)
    }

    ShareScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        sectionTitle = sectionTitle,
        devices = devices,
        onDeviceClick = onDeviceClick,
        onSendToAll = onSendToAll,
        onRefresh = onRefresh
    )
}

@Composable
private fun ShareScreenContent(
    state: LazyListState,
    isRefreshing: Boolean,
    sectionTitle: String,
    devices: List<DeviceUiModel>,
    onDeviceClick: (String) -> Unit,
    onSendToAll: () -> Unit,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp, start = 8.dp, end = 8.dp),
            contentPadding = safeDrawingBottomPadding(),
            state = state
        ) {
            item {
                SectionHeader(title = sectionTitle)
            }

            if (devices.size > 1) {
                item {
                    KdeCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        content = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .padding(horizontal = 32.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.send_to_all_devices),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 18.sp
                                )
                            }
                        },
                        onClick = onSendToAll
                    )
                }
            }

            items(
                items = devices,
                key = { device -> device.id }
            ) { device ->
                KdeCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    content = { ShareScreenCardContent(device) },
                    onClick = { onDeviceClick(device.id) }
                )
            }
        }
    }
}

@Composable
private fun ShareScreenCardContent(device: DeviceUiModel) {
    Row(
        modifier = Modifier
            .wrapContentSize()
            .defaultMinSize(minHeight = 48.dp)
            .padding(all = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (device.icon > 0) {
            Image(
                imageVector = ImageVector.vectorResource(id = device.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .wrapContentHeight()
            )
        }

        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp
            )

            if (!device.isReachable) {
                Text(
                    text = stringResource(id = R.string.runcommand_notreachable),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCC2222),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun ShareScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        ShareScreenContent(
            state = rememberLazyListState(),
            isRefreshing = false,
            sectionTitle = stringResource(id = R.string.share_to),
            devices = listOf(
                DeviceUiModel(
                    id = "device-1",
                    icon = R.drawable.ic_device_laptop_32dp,
                    name = "Device 1",
                    summaryRes = 0,
                    isReachable = true,
                    isPaired = true,
                ),
                DeviceUiModel(
                    id = "device-2",
                    icon = R.drawable.ic_device_phone_32dp,
                    name = "Device 2",
                    summaryRes = 0,
                    isReachable = true,
                    isPaired = true,
                )
            ),
            onDeviceClick = {},
            onSendToAll = {},
            onRefresh = {}
        )
    }
}
