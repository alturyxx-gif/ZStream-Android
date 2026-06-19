package com.zstream.android.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.R
import com.zstream.android.Urls
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
private val displayDateFormat = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US)

private fun formatDate(pubDate: String): String {
    return try {
        val date = rssDateFormat.parse(pubDate) ?: return pubDate
        val display = displayDateFormat.format(date)
        val diff = System.currentTimeMillis() - date.time
        val relative = when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            diff < TimeUnit.DAYS.toMillis(30) -> "${TimeUnit.MILLISECONDS.toDays(diff) / 7} week${if (TimeUnit.MILLISECONDS.toDays(diff) / 7 > 1) "s" else ""} ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff) / 30} month${if (TimeUnit.MILLISECONDS.toDays(diff) / 30 > 1) "s" else ""} ago"
        }
        "$display • $relative"
    } catch (_: Exception) {
        pubDate
    }
}

private val Context.readNotificationStore by preferencesDataStore("read_notifications")
private val READ_GUIDS_KEY = stringPreferencesKey("read_guids")

private fun Context.readNotificationGuidsFlow(): Flow<Set<String>> {
    return readNotificationStore.data.map { prefs ->
        prefs[READ_GUIDS_KEY]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }
}

private suspend fun Context.markNotificationRead(guid: String) {
    readNotificationStore.edit { prefs ->
        val existing = prefs[READ_GUIDS_KEY]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        prefs[READ_GUIDS_KEY] = (existing + guid).joinToString(",")
    }
}

private suspend fun Context.markAllNotificationsRead(guids: List<String>) {
    readNotificationStore.edit { prefs ->
        prefs[READ_GUIDS_KEY] = guids.joinToString(",")
    }
}

private val SECTION_ORDER_KEY = stringPreferencesKey("section_order")
private val Context.layoutDataStore by preferencesDataStore("layout_prefs")

private fun Context.sectionOrderFlow(): Flow<List<String>> =
    layoutDataStore.data.map { prefs ->
        prefs[SECTION_ORDER_KEY]?.split(",")?.filter { it.isNotBlank() }
            ?: defaultSectionOrder
    }

private suspend fun Context.saveSectionOrder(order: List<String>) {
    layoutDataStore.edit { prefs ->
        prefs[SECTION_ORDER_KEY] = order.joinToString(",")
    }
}

private val defaultSectionOrder = listOf("continue_watching", "bookmarks")

private val tmdbGenres = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
    53 to "Thriller", 10752 to "War", 37 to "Western",
)

private data class NotificationItem(
    val guid: String,
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String,
    val category: String,
)

private fun fetchNotifications(): List<NotificationItem> {
    val url = URL(Urls.NOTIFICATIONS_RSS)
    val connection = url.openConnection()
    connection.setRequestProperty("Accept", "application/rss+xml")
    val inputStream = connection.getInputStream()
    val factory = XmlPullParserFactory.newInstance()
    val parser = factory.newPullParser()
    parser.setInput(inputStream, "UTF-8")

    val items = mutableListOf<NotificationItem>()
    var currentItem: MutableMap<String, String>? = null
    var currentTag: String? = null

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                currentTag = parser.name
                if (parser.name == "item") currentItem = mutableMapOf()
            }
            XmlPullParser.TEXT -> {
                currentTag?.let { tag ->
                    currentItem?.let { item ->
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) item[tag] = text
                    }
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "item") {
                    currentItem?.let {
                        items.add(NotificationItem(
                            guid = it["guid"].orEmpty(),
                            title = it["title"].orEmpty(),
                            description = it["description"].orEmpty(),
                            link = it["link"].orEmpty(),
                            pubDate = it["pubDate"].orEmpty(),
                            category = it["category"].orEmpty(),
                        ))
                    }
                    currentItem = null
                }
                currentTag = null
            }
        }
        eventType = parser.next()
    }
    inputStream.close()
    return items
}

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
    var showNotifications by remember { mutableStateOf(false) }
    var showTipJar by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var readGuids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sectionOrder by remember { mutableStateOf(defaultSectionOrder) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible && isSearchFocused && state.searchQuery.isEmpty()) {
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus()
    }

    LaunchedEffect(Unit) {
        launch {
            context.readNotificationGuidsFlow().collect { readGuids = it }
        }
        launch {
            context.sectionOrderFlow().collect { sectionOrder = it }
        }
        try {
            notifications = withContext(Dispatchers.IO) { fetchNotifications() }
        } catch (_: Exception) { }
    }

    val unreadCount = notifications.count { it.guid !in readGuids }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier
        .fillMaxSize()
        .background(theme.colors.background.main)
        .safeDrawingPadding()) {
        if (!state.enableLowPerformanceMode && !state.enableFeatured) {
            CosmicBackground()
            ParticleOverlay()
        }

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
                val displayedSearchResults = if (isSearching) {
                    if (state.selectedGenreId != null)
                        searchResults.filter { it.genreIds?.contains(state.selectedGenreId) == true }
                    else searchResults
                } else emptyList()

                val bookmarksList = state.bookmarks.flatMap { it.items }
                val progressList = state.continueWatching.flatMap { it.items }

                val uriHandler = LocalUriHandler.current
                val density = LocalDensity.current
                val topInsetPx = WindowInsets.safeDrawing.getTop(density) + WindowInsets.statusBars.getTop(density)
                val topInsetDp = with(density) { topInsetPx.toDp() }

                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                    Box {
                        if (state.enableFeatured && state.featuredMedia.isNotEmpty()) {
                            FeaturedCarousel(
                                media = state.featuredMedia,
                                nav = nav,
                                searchQuery = state.searchQuery,
                                onSearch = vm::onSearchChange,
                                isSearching = isSearchFocused || state.searchQuery.isNotEmpty(),
                                onSearchFocusedChange = { isSearchFocused = it },
                                onClearFocus = { focusManager.clearFocus() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TopNavBar(
                            onLayout = { showLayoutMenu = true },
                            onMenu = { showSandwichMenu = true },
                            onDiscord = { uriHandler.openUri(Urls.DISCORD_LINK) },
                            onNotifications = { showNotifications = true },
                            onTipJar = { showTipJar = true },
                            unreadCount = unreadCount,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .animateContentSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        if (!state.enableFeatured || state.featuredMedia.isEmpty()) {
                            item { HeroSection(state.searchQuery, vm::onSearchChange, nav) }
                            item { GenrePills(state.selectedGenreId, vm::setGenre) }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                    if (isSearching) {
                        // ... (keep search results as is)
                    } else {
                        // User sections first
                        items(state.userSections
                            .sortedBy { section ->
                                val id = when (section.title) {
                                    "Continue Watching" -> "continue_watching"
                                    "My Bookmarks" -> "bookmarks"
                                    else -> ""
                                }
                                sectionOrder.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }
                            }
                            .filter { section ->
                                when (section.title) {
                                    "Continue Watching" -> showContinueWatching
                                    "My Bookmarks" -> showBookmarks
                                    else -> true
                                }
                            }
                        ) { section -> MediaCarouselSection(section, nav, progressMap = state.progressMap) }

                        item { Spacer(Modifier.height(16.dp)) }

                        if (state.enableDiscover) {
                            item { HomeTabs(state.activeTab, vm::setTab) }

                            // Base sections second
                            items(state.baseSections.map { section ->
                                val filtered = if (state.selectedGenreId != null)
                                    section.items.filter { it.genreIds?.contains(state.selectedGenreId) == true }
                                else section.items
                                section.copy(items = filtered)
                            }.filter { it.items.isNotEmpty() }) { section -> MediaCarouselSection(section, nav, progressMap = state.progressMap) }
                        }
                    }
                }
            }
        }
        }

        if (showLayoutMenu) {
            LayoutMenuDialog(
                sectionOrder = sectionOrder,
                showContinueWatching = showContinueWatching,
                showBookmarks = showBookmarks,
                onToggle = { id, visible ->
                    when (id) {
                        "continue_watching" -> showContinueWatching = visible
                        "bookmarks" -> showBookmarks = visible
                    }
                },
                onReorder = { newOrder ->
                    sectionOrder = newOrder
                    scope.launch { context.saveSectionOrder(newOrder) }
                },
                onDismiss = { showLayoutMenu = false },
            )
        }
        if (showSandwichMenu) {
            SandwichMenuDialog(nav = nav, session = session, accountVm = accountVm, onDismiss = { showSandwichMenu = false })
        }
        if (showNotifications) {
            NotificationsDialog(
                notifications = notifications,
                readGuids = readGuids,
                onMarkRead = { guid ->
                    scope.launch { context.markNotificationRead(guid) }
                },
                onMarkAllRead = {
                    scope.launch { context.markAllNotificationsRead(notifications.map { it.guid }) }
                },
                onDismiss = { showNotifications = false },
            )
        }
        if (showTipJar) {
            TipJarDialog(onDismiss = { showTipJar = false })
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
                        MediaCard(media = media, onClick = { nav.navigate("detail/${media.type}/${media.id}") })
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
private fun TopNavBar(onLayout: () -> Unit, onMenu: () -> Unit, onDiscord: () -> Unit, onNotifications: () -> Unit, onTipJar: () -> Unit, unreadCount: Int = 0) {
    val theme = LocalZStreamTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Logo pill: icon + "Z-Stream" text
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
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
            // Discord button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .clickable(onClick = onDiscord)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Forum, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp))
            }
            // Notifications bell button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .clickable(onClick = onNotifications)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Icon(Icons.Default.Notifications, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp).align(Alignment.Center))
                if (unreadCount > 0) {

                    // Red badge for notif
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .align(Alignment.TopEnd)
                            .offset(x = 0.dp, y = (-5).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The notif number
                        Text(
                            if (unreadCount > 99) "99+" else unreadCount.toString(),
                            color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            // Tip Jar button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .clickable(onClick = onTipJar)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AttachMoney, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp))
            }
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
private fun MediaCarouselSection(
    section: MediaSection,
    nav: NavController,
    progressMap: Map<String, ProgressEntity> = emptyMap(),
) {
    val theme = LocalZStreamTheme.current
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(section.title, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 16.sp)
//            Text("View more →", color = theme.colors.type.dimmed, fontSize = 11.sp) // TODO: add ability to click this
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(section.items) { media ->
                val progress = progressMap[media.id.toString()]
                val progressInfo = progress?.let { getProgressInfo(it) }
                MediaCard(
                    media = media,
                    onClick = { nav.navigate("detail/${media.type}/${media.id}") },
                    percentage = progressInfo?.first,
                    seriesLabel = progressInfo?.second,
                )
            }
        }
    }
}

/**
 * Compute progress bar percentage and optional SE label from a ProgressEntity.
 * Returns null if progress should not be shown (not started, completed >95%, no duration).
 */
private fun getProgressInfo(p: ProgressEntity): Pair<Float, String?>? {
    if (p.watched <= 0 || p.duration <= 0) return null
    val percentage = ((p.watched.toFloat() / p.duration) * 100f).coerceIn(0f, 100f)
    if (percentage >= 95f) return null
    val seriesLabel = if (p.type == "show" && p.seasonNumber != null && p.episodeNumber != null) {
        "S${p.seasonNumber} - E${p.episodeNumber}"
    } else null
    return percentage to seriesLabel
}

@Composable
private fun LayoutMenuDialog(
    sectionOrder: List<String>,
    showContinueWatching: Boolean,
    showBookmarks: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current

    data class LayoutItem(val id: String, val label: String, val visible: Boolean)

    val sections = remember {
        mutableStateListOf<LayoutItem>().apply {
            sectionOrder.forEach { id ->
                add(
                    LayoutItem(
                        id = id,
                        label = when (id) {
                            "continue_watching" -> "Continue Watching..."
                            "bookmarks" -> "Bookmarks"
                            else -> id
                        },
                        visible = when (id) {
                            "continue_watching" -> showContinueWatching
                            "bookmarks" -> showBookmarks
                            else -> true
                        },
                    )
                )
            }
        }
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableMapOf<Int, Int>() }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.modal.background)
            .padding(20.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Edit Layout", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(16.dp))
                    }
                }

                sections.forEachIndexed { index, section ->
                    val isDragging = draggedIndex == index

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { itemHeights[index] = it.size.height }
                            .offset { IntOffset(0, if (isDragging) draggedOffset.roundToInt() else 0) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            Arrangement.spacedBy(8.dp),
                            Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.DragHandle, "Drag to reorder",
                                tint = theme.colors.type.dimmed,
                                modifier = Modifier
                                    .size(24.dp)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                draggedOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val idx = draggedIndex
                                                if (idx != null) {
                                                    draggedOffset += dragAmount.y
                                                    val h = (itemHeights[idx] ?: 0).toFloat()

                                                    if (draggedOffset > h / 2 && idx < sections.size - 1) {
                                                        val temp = sections[idx]
                                                        sections[idx] = sections[idx + 1]
                                                        sections[idx + 1] = temp
                                                        draggedIndex = idx + 1
                                                        draggedOffset -= h
                                                    } else if (draggedOffset < -(h / 2) && idx > 0) {
                                                        val temp = sections[idx]
                                                        sections[idx] = sections[idx - 1]
                                                        sections[idx - 1] = temp
                                                        draggedIndex = idx - 1
                                                        draggedOffset += h
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                onReorder(sections.map { it.id })
                                                draggedIndex = null
                                                draggedOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                draggedOffset = 0f
                                            },
                                        )
                                    },
                            )
                            Text(
                                section.label,
                                color = theme.colors.type.text, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = section.visible,
                                onCheckedChange = {
                                    sections[index] = section.copy(visible = it)
                                    onToggle(section.id, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = theme.colors.global.accentA,
                                    uncheckedThumbColor = theme.colors.type.dimmed,
                                    uncheckedTrackColor = theme.colors.background.secondary,
                                ),
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun NotificationsDialog(
    notifications: List<NotificationItem>,
    readGuids: Set<String>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    var selectedNotif by remember { mutableStateOf<NotificationItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(theme.colors.modal.background),
        ) {
            if (selectedNotif != null) {
                NotificationDetailView(
                    notif = selectedNotif!!,
                    isRead = selectedNotif!!.guid in readGuids,
                    onMarkRead = { onMarkRead(selectedNotif!!.guid) },
                    onBack = { selectedNotif = null },
                    onDismiss = onDismiss,
                    theme = theme,
                )
            } else {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header: title left, actions right, all aligned center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Notifications",
                            color = theme.colors.type.emphasis,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (notifications.any { it.guid !in readGuids }) {
                            Box(
                                modifier = Modifier
                                    .clickable(onClick = onMarkAllRead)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    "Mark all read",
                                    color = theme.colors.global.accentA,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close, null,
                                tint = Color.White, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (notifications.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No notifications", color = theme.colors.type.dimmed, fontSize = 13.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            notifications.forEach { notif ->
                                NotificationCard(
                                    notif = notif,
                                    isRead = notif.guid in readGuids,
                                    onClick = {
                                        if (notif.guid !in readGuids) onMarkRead(notif.guid)
                                        selectedNotif = notif
                                    },
                                    theme = theme,
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
private fun NotificationCard(
    notif: NotificationItem,
    isRead: Boolean,
    onClick: () -> Unit,
    theme: com.zstream.android.theme.ZStreamTheme,
) {
    val categoryColors = mapOf(
        "announcement" to Color(0xFF3B82F6),
        "feature" to Color(0xFF22C55E),
        "update" to Color(0xFFEAB308),
        "bugfix" to Color(0xFFEF4444),
    )
    val categoryColor = categoryColors[notif.category] ?: theme.colors.type.dimmed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.background.secondary.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!isRead) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor))
                Spacer(Modifier.width(6.dp))
            }
            if (notif.category.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        notif.category.replaceFirstChar { it.uppercase() },
                        color = categoryColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            notif.title,
            color = if (isRead) theme.colors.type.dimmed else theme.colors.type.emphasis,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (notif.description.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                notif.description, color = theme.colors.type.text, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            formatDate(notif.pubDate),
            color = theme.colors.type.dimmed, fontSize = 10.sp,
        )
    }
}

@Composable
private fun NotificationDetailView(
    notif: NotificationItem,
    isRead: Boolean,
    onMarkRead: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    theme: com.zstream.android.theme.ZStreamTheme,
) {
    val categoryColors = mapOf(
        "announcement" to Color(0xFF3B82F6),
        "feature" to Color(0xFF22C55E),
        "update" to Color(0xFFEAB308),
        "bugfix" to Color(0xFFEF4444),
    )
    val categoryColor = categoryColors[notif.category] ?: theme.colors.type.dimmed

    Column(modifier = Modifier.padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Notification", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (notif.category.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            notif.category.replaceFirstChar { it.uppercase() },
                            color = categoryColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(notif.title, color = theme.colors.type.emphasis, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                notif.description,
                color = theme.colors.type.text, fontSize = 13.sp, lineHeight = 20.sp,
            )
            if (notif.link.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open link →",
                    color = theme.colors.global.accentA, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatDate(notif.pubDate),
                color = theme.colors.type.dimmed, fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun TipJarDialog(onDismiss: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val clipboard = LocalClipboardManager.current

    data class CryptoAddress(val symbol: String, val name: String, val address: String, val color: Color)

    val addresses = listOf(
        CryptoAddress("BTC", "Bitcoin", "bc1qd2g7kj920tlsyeaq473lfq7udn2e0tkdx7ng4n", Color(0xFFF59E0B)),
        CryptoAddress("ETH", "Ethereum", "0xC0F1F8fFe5e05Dda1D8E539d95D81820aB6B643F", Color(0xFF6366F1)),
        CryptoAddress("USDT", "Tether USD", "0xC0F1F8fFe5e05Dda1D8E539d95D81820aB6B643F", Color(0xFF10B981)),
        CryptoAddress("LTC", "Litecoin", "LSxUmH6CFyVRn76kox6ysYixx1bt9aMkrb", Color(0xFF06B6D4)),
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(theme.colors.modal.background)
                .padding(20.dp),
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Tip Jar", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "zstream is free and 99.9% ad-free. If you'd like to support hosting + the server bill, we would love your support on any amount to one of the addresses below. Tap an address to copy it.",
                    color = theme.colors.type.text, fontSize = 12.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    addresses.forEach { crypto ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.colors.background.secondary.copy(alpha = 0.5f))
                                .clickable { clipboard.setText(AnnotatedString(crypto.address)) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(crypto.color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(crypto.symbol, color = crypto.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(crypto.name, color = theme.colors.type.emphasis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    crypto.address.take(20) + "...",
                                    color = theme.colors.type.dimmed, fontSize = 10.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("Copy", color = theme.colors.global.accentA, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Thank you 💛 every tip helps keep the lights on.",
                    color = theme.colors.type.dimmed, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(
    media: List<Media>,
    nav: NavController,
    searchQuery: String = "",
    onSearch: (String) -> Unit = {},
    isSearching: Boolean = false,
    onSearchFocusedChange: (Boolean) -> Unit = {},
    onClearFocus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalZStreamTheme.current
    val pagerState = rememberPagerState(pageCount = { media.size })
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) autoScrollEnabled = false
    }

    LaunchedEffect(autoScrollEnabled, media) {
        if (autoScrollEnabled && media.isNotEmpty()) {
            while (true) {
                delay(6000)
                if (media.isNotEmpty()) {
                    val next = (pagerState.currentPage + 1) % media.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    val height by animateDpAsState(
        targetValue = if (isSearching) 110.dp else 450.dp,
        label = "carouselHeight"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        label = "contentAlpha"
    )

    Box(modifier = modifier.height(height)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha },
            key = { media[it].id }
        ) { page ->
            val current = media[page]
            val backdropUrl = current.backdropUrl()
            val title = current.displayTitle
            val year = current.displayDate.take(4)
            val type = current.type

            Box(Modifier.fillMaxSize()) {
                // Background image
                Box(Modifier.fillMaxSize()) {
                    if (backdropUrl != null) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize().scale(1.0f),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(theme.colors.background.secondary))
                    }
                }

                // Per-item gradients for smooth transitions
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp) // Top gradient ends earlier
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = contentAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(theme.colors.background.main, Color.Transparent),
                            )
                        )
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = contentAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, theme.colors.background.main),
                            )
                        )
                )

                // Content overlay
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, bottom = 50.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // Title
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                        )

                        // Metadata row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                        ) {
                            // TMDB Rating
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("★", color = Color(0xFFF5C518), fontSize = 12.sp)
                                Text(
                                    String.format(Locale.US, "%.1f", current.voteAverage ?: 0.0),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            if (year.isNotEmpty()) {
                                Text("•", color = Color.White.copy(alpha = 0.4f))
                                Text(year, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            }

                            val typeLabel = if (type == "tv") "TV Show" else "Movie"
                            Text("•", color = Color.White.copy(alpha = 0.4f))
                            Text(typeLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }

                        // Overview
                        current.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                overview,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                maxLines = 3,
                                lineHeight = 20.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Buttons Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                        ) {
                            // Play Now button
                            Button(
                                onClick = {
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    val poster = java.net.URLEncoder.encode(current.posterPath ?: "", "UTF-8")
                                    nav.navigate("player/$type/${current.id}?title=$encodedTitle&year=${year.toIntOrNull() ?: 0}&poster=$poster")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.colors.buttons.primary,
                                    contentColor = theme.colors.buttons.primaryText,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp).weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = theme.colors.buttons.primaryText)
                                Spacer(Modifier.width(8.dp))
                                Text("Play Now", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }

                            // More Info button
                            Button(
                                onClick = { nav.navigate("detail/$type/${current.id}") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.colors.buttons.secondary,
                                    contentColor = theme.colors.buttons.secondaryText,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp).weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = theme.colors.buttons.secondaryText)
                                Spacer(Modifier.width(8.dp))
                                Text("More Info", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Search bar overlay at top
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = 60.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearch,
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onClearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { onSearchFocusedChange(it.isFocused) },
                            )
                        }
                    }
                }
            }
        }

        // Navigation dots
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .graphicsLayer { alpha = contentAlpha },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            media.forEachIndexed { i, _ ->
                val active = i == pagerState.currentPage
                val dotWidth by animateDpAsState(if (active) 18.dp else 8.dp, label = "dotWidth")
                val dotAlpha by animateFloatAsState(if (active) 1f else 0.4f, label = "dotAlpha")
                
                Box(
                    Modifier
                        .size(width = dotWidth, height = 8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = dotAlpha))
                )
            }
        }
    }
}
