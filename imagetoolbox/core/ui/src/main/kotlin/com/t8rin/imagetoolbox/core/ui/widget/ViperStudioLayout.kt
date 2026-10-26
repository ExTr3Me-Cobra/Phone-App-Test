// Viper Vision: the layout Image Toolbox's tool screens use inside Viper Vision's Studio — black,
// a compact top bar, the picture as large as the space allows, the tool's settings scrolling
// underneath it and its buttons at the bottom.

package com.t8rin.imagetoolbox.core.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.resources.icons.ArrowBack
import com.t8rin.imagetoolbox.core.ui.widget.dialogs.ExitBackHandler
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedLoadingIndicator

@Composable
internal fun ViperStudioLayout(
    title: @Composable () -> Unit,
    onGoBack: () -> Unit,
    shouldDisableBackHandler: Boolean,
    actions: @Composable RowScope.() -> Unit,
    topAppBarPersistentActions: @Composable RowScope.() -> Unit,
    imagePreview: (@Composable () -> Unit)?,
    controls: (@Composable ColumnScope.(LazyListState) -> Unit)?,
    buttons: @Composable (actions: @Composable RowScope.() -> Unit) -> Unit,
    noDataControls: @Composable () -> Unit,
    canShowScreenData: Boolean,
    underTopAppBarContent: (@Composable ColumnScope.() -> Unit)?,
    isLoading: Boolean,
    listState: LazyListState
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnhancedIconButton(onClick = onGoBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(R.string.exit), tint = Color.White)
                }
                Box(Modifier.weight(1f)) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                if (canShowScreenData) actions()
                topAppBarPersistentActions()
            }
            underTopAppBarContent?.invoke(this)

            if (!canShowScreenData) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) EnhancedLoadingIndicator() else noDataControls()
                }
                return@Column
            }

            if (imagePreview != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { imagePreview() }
            }
            if (controls != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (imagePreview != null) Modifier.heightIn(max = screenHeight * 0.42f) else Modifier.weight(1f))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { controls(listState) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) { buttons {} }
        }
    }
    ExitBackHandler(enabled = !shouldDisableBackHandler, onBack = onGoBack)
}
