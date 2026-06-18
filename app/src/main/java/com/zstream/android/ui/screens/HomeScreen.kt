package com.zstream.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.R
import com.zstream.android.Urls
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private val tmdbGenres = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
    53 to "Thriller", 10752 to "War", 37 to "Western",
)

// Rainbow gradient colors matching p-stream's discover button
private val rainbowColors = listOf(
    Color(0xFFa855f7), Color(0xFFec4899), Color(0xFFf97316),
    Color(0xFFfbbf24), Color(0xFFa855f7), Color(0xFFec4899),
)

@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val theme = LocalZStreamTheme.current
    val accountVm: AccountViewModel = hiltViewModel()
    val session by accountVm.session.collectAsState()
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showSandwichMenu by remember { mutableStateOf(false) }
    var showContinueWatching by remember { mutableStateOf(true) }
    var showBookmarks by remember { mutableStateOf(true) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(theme.colors.background.main)) {
        CosmicBackground()
        ParticleOverlay()

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.colors.global.accentA)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(state.error!!, color = theme.colors.type.danger, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = theme.colors.global.accentA)) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                val isSearching = state.searchQuery.isNotBlank()
                // Filtered search results by genre
                val displayedSearchResults = if (isSearching) {
                    if (state.selectedGenreId != null)
                        searchResults.filter { it.genreIds?.contains(state.selectedGenreId) == true }
                    else searchResults
                } else emptyList()

                // Observe persistent local data via VM state (Room)
                val bookmarksList = state.bookmarks.flatMap { it.items }
                val progressList = state.continueWatching.flatMap { it.items }

                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { TopNavBar(onLayout = { showLayoutMenu = true }, onMenu = { showSandwichMenu = true }) }
                    item { HeroSection(state.searchQuery, vm::onSearchChange, nav) }
                    item { GenrePills(state.selectedGenreId, vm::setGenre) }
                    item { Spacer(Modifier.height(16.dp)) }

                    if (isSearching) {
                        // ... (keep search results as is)
                    } else {
                        // User sections first
                        items(state.userSections.filter { section ->
                            when (section.title) {
                                "Continue Watching" -> showContinueWatching
                                "My Bookmarks" -> showBookmarks
                                else -> true
                            }
                        }) { section -> MediaCarouselSection(section, nav) }

                        item { Spacer(Modifier.height(16.dp)) }
                        item { HomeTabs(state.activeTab, vm::setTab) }

                        // Base sections second
                        items(state.baseSections.map { section ->
                            val filtered = if (state.selectedGenreId != null)
                                section.items.filter { it.genreIds?.contains(state.selectedGenreId) == true }
                            else section.items
                            section.copy(items = filtered)
                        }.filter { it.items.isNotEmpty() }) { section -> MediaCarouselSection(section, nav) }
                    }
                }
            }
        }

        if (showLayoutMenu) {
            LayoutMenuDialog(
                showContinueWatching, showBookmarks,
                onToggleContinue = { showContinueWatching = it },
                onToggleBookmarks = { showBookmarks = it },
                onDismiss = { showLayoutMenu = false },
            )
        }
        if (showSandwichMenu) {
            SandwichMenuDialog(nav = nav, session = session, accountVm = accountVm, onDismiss = { showSandwichMenu = false })
        }
    }
}

@Composable
private fun SyncedSectionHeader(title: String, theme: com.zstream.android.theme.ZStreamTheme) {
    Text(title, color = theme.colors.type.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun SearchResultsGrid(results: List<Media>, nav: NavController) {
    // Non-lazy grid — search results are already paginated by TMDB (~20 items)
    val columns = 3
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        results.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { media ->
                    Box(modifier = Modifier.weight(1f)) {
                        MediaCard(media) { nav.navigate("detail/${media.type}/${media.id}") }
                    }
                }
                // Fill empty slots in last row
                repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun rememberFlickerAlpha(): State<Float> {
    val transition = rememberInfiniteTransition(label = "flicker")
    val raw = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0; 0.15f at 200; 0.05f at 260; 0.35f at 320
                0.08f at 380; 0.85f at 500; 0.4f at 560; 1f at 700; 1f at 1600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "flickerAlpha",
    )
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(750); settled = true }
    return if (settled) remember { mutableStateOf(1f) } else raw
}

@Composable
private fun CosmicBackground() {
    val theme = LocalZStreamTheme.current
    val flickerAlpha by rememberFlickerAlpha()
    val accent = theme.colors.global.accentA
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        theme.colors.background.accentA.copy(alpha = 0.55f),
                        theme.colors.background.main.copy(alpha = 0.85f),
                        theme.colors.background.main,
                    )
                )
            )
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f * flickerAlpha), Color.Transparent),
                    center = Offset(Float.MAX_VALUE / 2, 0f), radius = 600f,
                )
            ))
        Box(modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(1.5.dp)
            .align(Alignment.TopCenter)
            .offset(y = 3.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.9f * flickerAlpha),
                        Color.Transparent,
                    )
                )
            )
        )
    }
}

private data class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

@Composable
private fun ParticleOverlay() {
    val particles = remember {
        List(55) { Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.2f + 0.3f,
            Random.nextFloat() * 0.25f + 0.08f, Random.nextFloat() * 360f) }
    }
    // tickSecs advances in real seconds; read only inside Canvas so only the Canvas redraws
    val tickSecs = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastMs = 0L
        while (true) {
            withFrameMillis { ms ->
                if (lastMs != 0L) tickSecs.floatValue += (ms - lastMs) / 1000f
                lastMs = ms
            }
        }
    }
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)) {
        val t = tickSecs.floatValue  // state read inside Canvas — only invalidates this node
        val h = size.height; val w = size.width
        particles.forEach { p ->
            val cy = ((p.y + t * p.speed) % 1f) * h
            val cx = p.x * w + sin(p.phase + t).toFloat() * 6f
            val fadeStart = h * 0.7f
            val alpha = if (cy > fadeStart) 0.7f * (1f - (cy - fadeStart) / (h - fadeStart)) else 0.7f
            drawCircle(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), p.r, Offset(cx, cy))
        }
    }
}

@Composable
private fun TopNavBar(onLayout: () -> Unit, onMenu: () -> Unit) {
    val theme = LocalZStreamTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
//            .padding(horizontal = 16.dp, vertical = 6.dp,),
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Logo pill: icon + "Z-Stream" text
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            coil.compose.AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape),
            )
            Text(
                "Z-Stream",
                color = theme.colors.type.emphasis,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Layout button — dark pill matching sandwich (just grid icon, no text)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        theme.colors.type.divider.copy(alpha = 0.3f),
                        RoundedCornerShape(50)
                    )
                    .clickable(onClick = onLayout)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.GridView, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp))
            }
            // Sandwich menu
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        theme.colors.type.divider.copy(alpha = 0.3f),
                        RoundedCornerShape(50)
                    )
                    .clickable(onClick = onMenu)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.Menu, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp))
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroSection(searchQuery: String, onSearch: (String) -> Unit, nav: NavController) {
    val theme = LocalZStreamTheme.current
    // Animated gradient offset for discover button border
    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "rainbowOffset",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "What would you like\nto watch?",
            color = theme.colors.type.emphasis, fontSize = 22.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp,
            modifier = Modifier.padding(bottom = 20.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Search field — no border, background only, cursor visible
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(theme.colors.search.background),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Search, null, tint = theme.colors.search.icon, modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("What do you want to watch?", color = theme.colors.search.placeholder, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearch,
                            singleLine = true,
                            textStyle = TextStyle(color = theme.colors.search.text, fontSize = 13.sp),
                            cursorBrush = SolidColor(theme.colors.global.accentA),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Discover button — rainbow animated border, dark fill, wand icon
            val DISCOVER_BUTTON_CORNER_RADIUS = 48.dp;
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(DISCOVER_BUTTON_CORNER_RADIUS))
                    .drawBehind {
                        // Smooth right-to-left moving rainbow — shift start X by animated offset
                        val w = size.width * 3f  // wide gradient so colors flow smoothly
                        val shift = gradientOffset * w
                        val brush = Brush.linearGradient(
                            colors = rainbowColors + rainbowColors, // double for seamless loop
                            start = Offset(w - shift, 0f),
                            end = Offset(w * 2 - shift, size.height),
                        )
                        drawRoundRect(
                            brush = brush,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(DISCOVER_BUTTON_CORNER_RADIUS.toPx())
                        )
                    }
                    .padding(2.dp)
                    .clip(RoundedCornerShape(DISCOVER_BUTTON_CORNER_RADIUS))
                    .background(theme.colors.search.background)
                    .clickable { nav.navigate("search") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = theme.colors.type.secondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GenrePills(selectedGenreId: Int?, onSelect: (Int?) -> Unit) {
    val theme = LocalZStreamTheme.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tmdbGenres.entries.toList()) { (id, name) ->
            val selected = selectedGenreId == id
            Text(
                text = name,
                color = if (selected) theme.colors.type.emphasis else theme.colors.type.dimmed,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) theme.colors.global.accentA else theme.colors.background.secondary.copy(
                            alpha = 0.5f
                        )
                    )
                    .border(
                        1.dp,
                        if (selected) Color.Transparent else theme.colors.type.divider.copy(alpha = 0.25f),
                        RoundedCornerShape(50)
                    )
                    .clickable { onSelect(if (selected) null else id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun HomeTabs(activeTab: HomeTab, onTab: (HomeTab) -> Unit) {
    val theme = LocalZStreamTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            val label = when (tab) { HomeTab.MOVIES -> "Movies"; HomeTab.TV -> "TV Shows"; HomeTab.EDITOR -> "Editor Picks" }
            val active = tab == activeTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTab(tab) }
                    .padding(horizontal = 12.dp),
            ) {
                Text(label, color = if (active) theme.colors.type.emphasis else theme.colors.type.dimmed,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Box(Modifier
                    .size(if (active) 6.dp else 0.dp)
                    .clip(CircleShape)
                    .background(theme.colors.global.accentA))
            }
        }
    }
}

@Composable
private fun MediaCarouselSection(section: MediaSection, nav: NavController) {
    val theme = LocalZStreamTheme.current
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(section.title, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 16.sp)
//            Text("View more →", color = theme.colors.type.dimmed, fontSize = 11.sp) // TODO: add ability to click this
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(section.items) { media ->
                MediaCard(media) { nav.navigate("detail/${media.type}/${media.id}") }
            }
        }
    }
}

@Composable
private fun LayoutMenuDialog(
    showContinueWatching: Boolean, showBookmarks: Boolean,
    onToggleContinue: (Boolean) -> Unit, onToggleBookmarks: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.modal.background)
            .padding(20.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Edit Layout", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(16.dp))
                    }
                }
                LayoutToggleRow("Continue Watching...", showContinueWatching, onToggleContinue, theme)
                LayoutToggleRow("Bookmarks", showBookmarks, onToggleBookmarks, theme)
            }
        }
    }
}

@Composable
private fun LayoutToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, theme: com.zstream.android.theme.ZStreamTheme) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = theme.colors.global.accentA,
                uncheckedThumbColor = theme.colors.type.dimmed, uncheckedTrackColor = theme.colors.background.secondary,
            ))
    }
}

@Composable
private fun SandwichMenuDialog(nav: NavController, session: com.zstream.android.data.AccountSession?, accountVm: AccountViewModel, onDismiss: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier
            .width(384.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.modal.background)
            .padding(vertical = 8.dp)) {
            Column {
                if (session != null) {
                    SandwichItem(Icons.Default.CheckCircle, "Synced: ${session.nickname.ifBlank { session.userId.take(8) }}", Color(0xFF4ADE80), theme = theme) {
                        accountVm.logout(); onDismiss()
                    }
                } else {
                    SandwichItem(Icons.Default.Star, "Sync to Cloud", Color(0xFFD8C947), theme = theme) { nav.navigate("login"); onDismiss() }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))
                SandwichItem(Icons.Default.Settings, "Settings", theme = theme) { nav.navigate("settings"); onDismiss() }
                SandwichItem(Icons.Default.History, "Watch History", theme = theme) { nav.navigate("watchHistory"); onDismiss() }
                SandwichItem(Icons.Default.Help, "About and FAQ", theme = theme) { onDismiss() }
                SandwichItem(Icons.Default.Explore, "Discover", theme = theme) { nav.navigate("search"); onDismiss() }
                SandwichItem(Icons.Default.Group, "Join a Watch Party", theme = theme) { onDismiss() }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))

                val links = listOf(
                    Icons.Default.Code to Urls.GITHUB_REPO,
                    Icons.Default.Forum to Urls.DISCORD_LINK
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    links.forEach { (icon, url) ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.colors.background.secondary)
                                .clickable {
                                    uriHandler.openUri(url)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = theme.colors.type.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SandwichItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    theme: com.zstream.android.theme.ZStreamTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tint ?: theme.colors.type.secondary, modifier = Modifier.size(18.dp))
        Text(label, color = tint ?: theme.colors.type.text, fontSize = 14.sp,
            fontWeight = if (tint != null) FontWeight.SemiBold else FontWeight.Normal)
    }
}
