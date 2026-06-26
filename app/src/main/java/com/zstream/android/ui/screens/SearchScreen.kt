package com.zstream.android.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.*
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun SearchScreen(nav: NavController, vm: SearchViewModel = hiltViewModel()) {
    val isTv = LocalIsTv.current
    if (isTv) {
        SearchScreenTV(nav, vm)
    } else {
        SearchScreenPhone(nav, vm)
    }
}

@Composable
fun SearchScreenTV(nav: NavController, vm: SearchViewModel) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val popular by vm.popular.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val theme = LocalZStreamTheme.current
    val hazeState = rememberHazeState()
    
    val searchBarFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        searchBarFocusRequester.requestFocus()
    }
    
    var focusedMedia by remember { mutableStateOf<Media?>(null) }
    
    val items = if (query.isBlank()) popular else results
    val title = if (query.isBlank()) "Popular now" else "Search results"

    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            focusedMedia = items.first()
        } else {
            focusedMedia = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Crossfade(targetState = focusedMedia?.backdropUrl(), label = "backdrop") { url ->
            if (url != null) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.15f)
                            .blur(10.dp)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Search UI
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(top = 64.dp, start = 64.dp, bottom = 64.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search Input
                var isSearchBarFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    visible = isSearchBarFocused,
                    shape = RoundedCornerShape(12.dp),
                    outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                    gap = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .focusRequester(searchBarFocusRequester)
                            .onFocusChanged { isSearchBarFocused = it.isFocused }
                            .clickable {
                                // Just to consume the click and handle focus activation in one go
                            }
                    ) {
                        ZsSearchField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            placeholder = "Search movies and shows here...",
                            modifier = Modifier.fillMaxSize().focusProperties { canFocus = false }
                        )
                    }
                }

                // Preview Area
                Box(Modifier.weight(1f)) {
                    focusedMedia?.let { media ->
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(
                                media.displayTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = theme.colors.type.emphasis,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    media.type.replaceFirstChar { it.uppercase() },
                                    color = theme.colors.type.secondary,
                                    fontSize = 14.sp
                                )
                                Text("•", color = theme.colors.type.secondary)
                                Text(
                                    media.displayDate.take(4).ifBlank { "—" },
                                    color = theme.colors.type.secondary,
                                    fontSize = 14.sp
                                )
                                media.voteAverage?.let {
                                    SharedTmdbRating(it, theme)
                                }
                            }
                        }
                    }
                }

                // Virtual Keyboard
                VirtualKeyboard(
                    onCharClick = { vm.query.value += it },
                    onBackspace = { if (query.isNotEmpty()) vm.query.value = query.dropLast(1) }
                )
            }

            // Right Column: Results Grid
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .padding(top = 64.dp, end = 64.dp, bottom = 0.dp)
            ) {
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.colors.global.accentA)
                    }
                } else if (error != null) {
                    ZsStatusBanner(message = error!!, variant = ZsStatusBannerVariant.Error)
                } else if (items.isEmpty() && query.isNotBlank()) {
                    ZsStatusBanner(message = "No results found for \"$query\"", variant = ZsStatusBannerVariant.Info)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 48.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                color = theme.colors.type.emphasis,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                        items(items) { media ->
                            Box(modifier = Modifier.onFocusChanged { if (it.isFocused) focusedMedia = media }) {
                                MediaCard(
                                    media = media,
                                    onClick = { nav.navigate("detail/${media.type}/${media.id}") },
                                    width = 100.dp,
                                    height = 150.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VirtualKeyboard(onCharClick: (String) -> Unit, onBackspace: () -> Unit) {
    val theme = LocalZStreamTheme.current
    var isUppercase by remember { mutableStateOf(true) }
    
    val rows = listOf(
        "ABCDEFG".map { it.toString() },
        "HIJKLMN".map { it.toString() },
        "OPQRSTU".map { it.toString() },
        "VWXYZ".map { it.toString() } + listOf("Caps"),
        listOf("Space", "⌫")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isCaps = label == "Caps"
                    val isSpace = label == "Space"
                    val isBackspace = label == "⌫"
                    
                    val weight = when {
                        isCaps -> 2f
                        isSpace -> 5f
                        isBackspace -> 2f
                        else -> 1f
                    }

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .padding(5.dp) // Consistent gap handling
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isFocused) Color.White 
                                else if (isCaps && isUppercase) theme.colors.global.accentA.copy(alpha = 0.2f)
                                else theme.colors.type.text.copy(alpha = 0.05f)
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { 
                                when {
                                    isCaps -> isUppercase = !isUppercase
                                    isSpace -> onCharClick(" ")
                                    isBackspace -> onBackspace()
                                    else -> onCharClick(if (isUppercase) label.uppercase() else label.lowercase())
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBackspace) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = if (isFocused) Color.Black else theme.colors.type.emphasis,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = when {
                                    isCaps || isSpace -> label
                                    isUppercase -> label.uppercase()
                                    else -> label.lowercase()
                                },
                                color = if (isFocused) Color.Black else theme.colors.type.emphasis,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCaps || isSpace) 12.sp else 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenPhone(nav: NavController, vm: SearchViewModel) {
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
        Box(Modifier.padding(padding).fillMaxSize().background(theme.colors.background.main)) {
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
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(results) { media ->
                        MediaCard(media = media, onClick = { nav.navigate("detail/${media.type}/${media.id}") })
                    }
                }
            }
        }
    }
}
