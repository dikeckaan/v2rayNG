package com.v2ray.ang.ui.main.compact

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.circularStrictSafeArea
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel

/** Screens of the compact round-screen mode. Deliberately a flat, tiny state machine. */
enum class CompactRoute { Home, Profiles, Menu }

/**
 * Compact replacement for [com.v2ray.ang.ui.main.MainScreen].
 *
 * The phone layout spends roughly 80dp on a top bar, bottom bar and drawer handle,
 * which is a third of a 240dp screen. This screen drops all three and navigates
 * between three full-screen destinations instead.
 */
@Composable
fun CompactMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(CompactRoute.Home) }

    BackHandler(enabled = route != CompactRoute.Home) { route = CompactRoute.Home }

    val selectedGuid = uiState.selectedGuid
    val profileName = remember(selectedGuid) {
        selectedGuid?.let { MmkvManager.decodeServerConfig(it)?.remarks }
    }

    when (route) {
        CompactRoute.Home -> CompactHome(
            statusText = uiState.statusText,
            isRunning = uiState.isRunning,
            profileName = profileName,
            onToggle = { onAction(MainAction.ToggleService) },
            onOpenProfiles = { route = CompactRoute.Profiles },
            onOpenMenu = { route = CompactRoute.Menu },
        )

        CompactRoute.Menu -> CompactMenuScreen(
            onAction = onAction,
            onNavigate = onNavigate,
            onClose = { route = CompactRoute.Home },
        )

        CompactRoute.Profiles -> CompactProfileList(
            mainViewModel = mainViewModel,
            onAction = onAction,
            onClose = { route = CompactRoute.Home },
        )
    }
}

@Composable
private fun CompactHome(
    statusText: String,
    isRunning: Boolean,
    profileName: String?,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .circularStrictSafeArea(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isRunning) {
                    MaterialTheme.colorScheme.onTertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = if (isRunning) {
                            painterResource(R.drawable.ic_stop_24dp)
                        } else {
                            painterResource(R.drawable.ic_play_24dp)
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Text(
                text = profileName ?: stringResource(R.string.title_file_chooser),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenProfiles)
                    .padding(vertical = 2.dp),
            )

            Icon(
                painter = painterResource(R.drawable.ic_menu_24dp),
                contentDescription = stringResource(R.string.title_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onOpenMenu),
            )
        }
    }
}
