package com.v2ray.ang.ui.main.compact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.chordHalfWidth
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel

/** Vertical breathing room; also decides how wide rows may be (see [rowWidthDp]). */
private const val LIST_VERTICAL_PADDING_DP = 28f

/**
 * Widest row that stays inside the circle across the whole scrollable band.
 *
 * Rows never reach the extreme top or bottom of the display because of the content
 * padding, so the binding constraint is the chord at the edge of that band.
 */
private fun rowWidthDp(screenWidthDp: Int): Float {
    val radius = screenWidthDp / 2f
    val bandHalfHeight = radius - LIST_VERTICAL_PADDING_DP
    return 2f * chordHalfWidth(radius, bandHalfHeight)
}

/**
 * Full-screen profile picker. Tapping a row selects it; [MainActivity.setSelectServer]
 * restarts the service automatically when it is already running, which is what makes
 * switching profiles a single tap.
 */
@Composable
fun CompactProfileList(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onClose: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groupId = uiState.selectedGroupId

    val serverFlow = remember(groupId) { mainViewModel.serversForGroup(groupId) }
    val servers by serverFlow.collectAsStateWithLifecycle()

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val rowWidth = remember(screenWidthDp) { rowWidthDp(screenWidthDp).dp }

    val groups = uiState.groups
    val groupName = groups.firstOrNull { it.id == groupId }?.remarks

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = LIST_VERTICAL_PADDING_DP.dp),
        ) {
            if (groups.size > 1) {
                item(key = "group-header") {
                    GroupHeader(
                        name = groupName.orEmpty(),
                        width = rowWidth,
                        onClick = {
                            val index = groups.indexOfFirst { it.id == groupId }
                            val next = groups[(index + 1).mod(groups.size)]
                            onAction(MainAction.SelectGroup(next.id))
                        },
                    )
                }
            }

            if (servers.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.title_file_chooser),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(rowWidth),
                    )
                }
            }

            items(items = servers, key = { it.guid }) { server ->
                ProfileRow(
                    server = server,
                    selected = server.guid == uiState.selectedGuid,
                    width = rowWidth,
                    onClick = {
                        onAction(MainAction.SelectServer(server.guid))
                        onClose()
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun ProfileRow(
    server: ServersCache,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = server.profile.remarks,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (server.testDelayString.isNotEmpty()) {
                Text(
                    text = server.testDelayString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
