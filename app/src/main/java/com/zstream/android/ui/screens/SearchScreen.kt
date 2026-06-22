package com.zstream.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsSearchField
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavController, vm: SearchViewModel = hiltViewModel()) {
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ZsSearchField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = "Search movies & shows…",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    ZsIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        variant = ZsIconButtonVariant.Ghost,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.colors.background.main,
                    titleContentColor = theme.colors.type.emphasis,
                    navigationIconContentColor = theme.colors.type.emphasis,
                ),
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = theme.colors.global.accentA)
                error != null -> ZsStatusBanner(
                    message = error!!,
                    variant = ZsStatusBannerVariant.Error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp),
                )
                results.isEmpty() && query.isNotBlank() -> ZsStatusBanner(
                    message = "No results",
                    variant = ZsStatusBannerVariant.Info,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results) { media ->
                        MediaCard(media = media, onClick = { nav.navigate("detail/${media.type}/${media.id}") })
                    }
                }
            }
        }
    }
}
