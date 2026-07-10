package com.zstream.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.zstream.android.R
import com.zstream.android.Urls
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.adb.ReleaseUpdateManager
import com.zstream.android.data.adb.ReleaseUpdateNavigation
import com.zstream.android.data.adb.TvAdbManager
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.model.Media
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsTextButton
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val HOME_FOCUS_DEBUG_TAG = "HomeFocusDebug"

/** Cross-screen signal so Settings' "Edit home sections" row can open Home's layout dialog. */
object HomeLayoutMenuSignal {
    private val _open = MutableStateFlow(false)
    val open = _open.asStateFlow()
    fun request() { _open.value = true }
    fun consume() { _open.value = false }
}


private object TvHomeMetrics {
    val screenPadding = 32.dp
    val topBarHeight = 56.dp
    val headerButtonSize = 40.dp
    val headerIconSize = 20.dp
    val searchBarWidth = 280.dp
    val sectionTitleSize = 16.sp
    val sectionSpacing = 16.dp
    val cardWidth = 110.dp
    val cardHeight = 165.dp
    val featuredHeight = 360.dp
    val menuWidth = 320.dp
    val railItemSpacing = 12.dp
    val railVerticalSpacing = 8.dp
}

private object PhoneHomeMetrics {
    // Downward scroll threshold.
    // When the in-flow search bar's top reaches the sticky header's bottom minus this value,
    // the floating/sticky search bar takes over and follows the screen.
    // Increase this to make the sticky search start earlier. Decrease it to start later.
    val heroSearchStickOffset = 0.dp

    // Upward scroll release threshold.
    // When scrolling back up, once the in-flow search bar gets back to the sticky header's bottom
    // minus this value, the floating/sticky search bar releases and the in-flow search bar stays
    // in its original layout position again.
    // Increase this to make it snap back sooner. Decrease it to make it hang around longer.
    val heroSearchReleaseOffset = 48.dp // DO NOT CHANGE FROM 48.dp
}

private fun normalizeGroupName(group: String): String {
    val match = Regex("^\\[[^\\]]+]\\s*").find(group)
    return if (match != null) group.removePrefix(match.value).trim() else group.trim()
}

private fun groupIconKey(group: String): String? =
    Regex("^\\[([A-Za-z0-9_]+)]").find(group)?.groupValues?.getOrNull(1)?.uppercase()

private fun calculateDefaultBringIntoViewScrollDistance(
    offset: Float,
    size: Float,
    containerSize: Float,
): Float {
    val trailingEdge = offset + size
    return when {
        offset < 0f -> offset
        trailingEdge > containerSize -> trailingEdge - containerSize
        else -> 0f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberHomeBringIntoViewSpec(
    label: String,
    disableAutoScroll: Boolean,
): BringIntoViewSpec {
    val disableAutoScrollState = rememberUpdatedState(disableAutoScroll)
    return remember(label) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val distance = calculateDefaultBringIntoViewScrollDistance(
                    offset = offset,
                    size = size,
                    containerSize = containerSize,
                )

                if (disableAutoScrollState.value) {
                    if (distance != 0f) {
                        Log.d(
                            HOME_FOCUS_DEBUG_TAG,
                            "$label bringIntoView blocked distance=$distance offset=$offset size=$size containerSize=$containerSize"
                        )
                    }
                    return 0f
                }

                return distance
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBringIntoViewProvider(
    label: String,
    disableAutoScroll: Boolean,
    content: @Composable () -> Unit,
) {
    val spec = rememberHomeBringIntoViewSpec(
        label = label,
        disableAutoScroll = disableAutoScroll,
    )
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        content()
    }
}


private val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
private val displayDateFormat = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US)

@Composable
private fun featuredCarouselTextShadow(strong: Boolean = false): Shadow {
    val theme = LocalZStreamTheme.current
    return Shadow(
        color = theme.colors.video.context.background.copy(alpha = if (strong) 0.6f else 0.5f),
        offset = Offset(0f, if (strong) 4f else 2f),
        blurRadius = if (strong) 8f else 4f,
    )
}

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

private fun notificationCategoryColor(
    theme: com.zstream.android.theme.ZStreamTheme,
    category: String,
): Color = when (category) {
    "announcement" -> theme.colors.global.accentA
    "feature" -> theme.colors.type.success
    "update" -> theme.colors.dropdown.highlightHover
    "bugfix" -> theme.colors.type.danger
    else -> theme.colors.type.dimmed
}

private val defaultSectionOrder = listOf("continue_watching", "bookmarks")
private val mediaSortOptions = listOf(
    "date" to "Recently updated",
    "title-asc" to "Title: A-Z",
    "title-desc" to "Title: Z-A",
    "year-asc" to "Year: oldest first",
    "year-desc" to "Year: newest first",
)

private fun MediaSection.sorted(
    sort: String,
    progress: Map<String, ProgressEntity>,
    bookmarks: Map<String, BookmarkEntity>,
): MediaSection = copy(items = when (sort) {
    "title-asc" -> items.sortedBy { it.displayTitle.lowercase() }
    "title-desc" -> items.sortedByDescending { it.displayTitle.lowercase() }
    "year-asc" -> items.sortedWith(compareBy<Media> { it.displayDate.take(4).toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.displayTitle.lowercase() })
    "year-desc" -> items.sortedWith(compareByDescending<Media> { it.displayDate.take(4).toIntOrNull() ?: Int.MIN_VALUE }.thenBy { it.displayTitle.lowercase() })
    else -> items.sortedByDescending { media -> maxOf(progress[media.id.toString()]?.updatedAt ?: 0L, bookmarks[media.id.toString()]?.updatedAt ?: 0L) }
})

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

private val searchPlaceholders = listOf(
    "What do you want to watch?",
    "What are you in the mood for?",
    "What do you want to stream?",
    "What's on your watchlist today?",
    "How was your day?",
    "My bad the app never works...",
    ">ᴗ<"
)

@Composable
private fun rememberRandomPlaceholder(): String {
    return remember { searchPlaceholders.random() }
}

@Composable
fun HomeScreen(
    nav: NavController,
    vm: HomeViewModel = hiltViewModel(),
    isTvPipActive: Boolean = false,
    isTvPipDrawerExpanded: Boolean = false,
    onTvPipDrawerExpandedChange: (Boolean) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val numOfColumns = (screenWidth / 125).coerceAtLeast(2)
    val numOfRows = state.gridRows
    val accountVm: AccountViewModel = hiltViewModel()
    val session by accountVm.session.collectAsState()
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showSandwichMenu by remember { mutableStateOf(false) }
    var showContinueWatching by remember { mutableStateOf(true) }
    var showBookmarks by remember { mutableStateOf(true) }
    var hiddenGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showNotifications by remember { mutableStateOf(false) }
    var showTipJar by remember { mutableStateOf(false) }
    var showTvInstaller by remember { mutableStateOf(false) }
    var showTvActions by remember { mutableStateOf(false) }
    var showReleaseUpdatePrompt by remember { mutableStateOf(false) }
    var offlineBannerDismissed by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var sectionSettings by remember { mutableStateOf<String?>(null) }
    var editingBookmarks by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var bookmarkEditTitle by remember { mutableStateOf("") }
    var bookmarkEditYear by remember { mutableStateOf("") }
    var bookmarkEditGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var watchingSort by remember { mutableStateOf("date") }
    var bookmarksSort by remember { mutableStateOf("date") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var readGuids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sectionOrder by remember { mutableStateOf(defaultSectionOrder) }
    var sectionPages by remember { mutableStateOf(mapOf<String, Int>()) }
    val context = LocalContext.current
    val releaseUpdateManager = remember(context) { ReleaseUpdateManager(context.applicationContext) }
    val releaseUpdateLaunch by ReleaseUpdateNavigation.launch.collectAsState()
    val openLayoutMenuSignal by HomeLayoutMenuSignal.open.collectAsState()
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val focusRequester = remember { FocusRequester() }
    val hazeState = rememberHazeState()
    val placeholder = rememberRandomPlaceholder()

    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }
    val maxPullOffset = 120f

    val watchPartyRoomCode by vm.watchPartyRoomCode.collectAsState()
    val watchPartyEnabled by vm.watchPartyEnabled.collectAsState()
    val watchPartyIsOffline by vm.watchPartyIsOffline.collectAsState()

    var wasKeyboardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isKeyboardVisible) {
        if (wasKeyboardVisible && !isKeyboardVisible) {
            focusManager.clearFocus() // <--- Force clear focus when keyboard hides
            if (isSearchFocused && state.searchQuery.isEmpty()) {
                isSearchFocused = false
            }
        }
        wasKeyboardVisible = isKeyboardVisible
    }

    BackHandler(enabled = isSearchFocused || state.searchQuery.isNotEmpty()) {
        isSearchFocused = false
        vm.onSearchChange("")
        focusManager.clearFocus()
    }

    LaunchedEffect(Unit) {
        launch {
            vm.userPrefs.readNotificationGuids.collect { readGuids = it }
        }
        launch {
            vm.userPrefs.sectionOrderFlow(defaultSectionOrder).collect { sectionOrder = it }
        }
        launch { vm.userPrefs.watchingSort.collect { watchingSort = it } }
        launch { vm.userPrefs.bookmarksSort.collect { bookmarksSort = it } }
        try {
            notifications = withContext(Dispatchers.IO) { fetchNotifications() }
        } catch (_: Exception) { }
    }

    val unreadCount = notifications.count { it.guid !in readGuids }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            scope.launch {
                vm.refresh()
                delay(500)
                isRefreshing = false
                pullOffset = 0f
            }
        }
    }

    LaunchedEffect(releaseUpdateLaunch) {
        val launch = releaseUpdateLaunch
        if (launch != null) {
            if (launch.openTvInstaller && TvAdbManager.get(context).getSavedTvs().isNotEmpty()) {
                releaseUpdateManager.clearPendingUpdate()
                showTvInstaller = true
            } else {
                showReleaseUpdatePrompt = true
            }
            ReleaseUpdateNavigation.consume()
        } else if (releaseUpdateManager.hasPendingUpdate) {
            showReleaseUpdatePrompt = true
        }
    }

    LaunchedEffect(openLayoutMenuSignal) {
        if (openLayoutMenuSignal) {
            showLayoutMenu = true
            HomeLayoutMenuSignal.consume()
        }
    }

    LaunchedEffect(state.isOffline) {
        if (state.isOffline) offlineBannerDismissed = false
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ReleaseUpdateNavigation.launch.value == null && releaseUpdateManager.hasPendingUpdate) {
            showReleaseUpdatePrompt = true
        }
    }

    @Composable
    fun HomeDialogs()
    {
        if (showTvInstaller) {
            TvInstallerScreen(onDismiss = { showTvInstaller = false })
        }
        if (showTvActions) {
            AlertDialog(
                onDismissRequest = { showTvActions = false },
                containerColor = theme.colors.modal.background,
                title = { Text("TV actions", color = theme.colors.type.emphasis) },
                text = { Text("Choose what you want to do with your TV.", color = theme.colors.type.text) },
                confirmButton = {
                    TextButton(onClick = {
                        showTvActions = false
                        nav.navigate("tvSync")
                    }) { Text("Sync to TV", color = theme.colors.global.accentA) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showTvActions = false
                            showTvInstaller = true
                        }) { Text("Install APK", color = theme.colors.global.accentA) }
                        TextButton(onClick = { showTvActions = false }) { Text("Cancel", color = theme.colors.type.secondary) }
                    }
                },
            )
        }
        if (showReleaseUpdatePrompt) {
            if (isTv) {
                TvUpdateWizardScreen(onDismiss = {
                    releaseUpdateManager.clearPendingUpdate()
                    showReleaseUpdatePrompt = false
                })
            } else {
                val versionSuffix = releaseUpdateManager.pendingVersion.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                AlertDialog(
                    onDismissRequest = {},
                    containerColor = theme.colors.modal.background,
                    title = { Text("ZStream update available", color = theme.colors.type.emphasis) },
                    text = {
                        Text(
                            "A new APK release$versionSuffix is available. Open its GitHub release page? Background checks and their interval can be changed or disabled in Settings → Connections.",
                            color = theme.colors.type.text,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUpdateManager.pendingReleaseUrl)))
                            releaseUpdateManager.clearPendingUpdate()
                            showReleaseUpdatePrompt = false
                        }) { Text("Open GitHub releases", color = theme.colors.global.accentA) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            releaseUpdateManager.clearPendingUpdate()
                            showReleaseUpdatePrompt = false
                        }) { Text("No", color = theme.colors.type.secondary) }
                    },
                )
            }
        }
        if (showLayoutMenu) {
            val allGroups = remember(state.bookmarkEntities) {
                state.bookmarkEntities.values.flatMap { it.groups.orEmpty() }.distinct()
            }
            LayoutMenuDialog(
                sectionOrder = sectionOrder,
                showContinueWatching = showContinueWatching,
                showBookmarks = showBookmarks,
                hiddenGroups = hiddenGroups,
                allGroups = allGroups,
                vm = vm,
                onToggle = { id, visible ->
                    when (id) {
                        "continue_watching" -> showContinueWatching = visible
                        "bookmarks" -> showBookmarks = visible
                        else -> if (id.startsWith("[")) hiddenGroups = if (visible) hiddenGroups - id else hiddenGroups + id
                    }
                },
                onReorder = { newOrder ->
                    sectionOrder = newOrder
                    scope.launch { vm.userPrefs.saveSectionOrder(newOrder) }
                },
                onDismiss = { showLayoutMenu = false },
            )
        }
        if (showSandwichMenu) {
            SandwichMenuDialog(
                nav = nav,
                vm = vm,
                session = session,
                accountVm = accountVm,
                showHeaderActions = state.enableFeatured,
                unreadCount = unreadCount,
                watchPartyEnabled = watchPartyEnabled,
                watchPartyRoomCode = watchPartyRoomCode,
                watchPartyIsOffline = watchPartyIsOffline,
                onDiscord = { uriHandler.openUri(Urls.DISCORD_LINK) },
                onNotifications = { showNotifications = true },
                onTipJar = { showTipJar = true },
                onDismiss = { showSandwichMenu = false },
            )
        }
        editingBookmark?.let { bookmark ->
            val allGroupNames = state.bookmarkEntities.values.flatMap { it.groups.orEmpty() }.distinct()
            Dialog(onDismissRequest = { editingBookmark = null; showCreateGroup = false }) {
                Surface(
                    color = theme.colors.modal.background,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
                    modifier = Modifier.widthIn(max = 480.dp),
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Edit bookmark", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = bookmarkEditTitle,
                            onValueChange = { bookmarkEditTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = bookmarkEditYear,
                            onValueChange = { bookmarkEditYear = it.filter(Char::isDigit).take(4) },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Groups", color = theme.colors.type.secondary, fontWeight = FontWeight.SemiBold)
                            ZsIconButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Create group",
                                onClick = { showCreateGroup = true },
                                containerSize = 36.dp,
                                iconSize = 18.dp,
                            )
                        }
                        Surface(
                            color = theme.colors.background.main,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                allGroupNames.forEach { group ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        groupIconKey(group)?.let {
                                            Icon(groupIconPainter(it), null, tint = theme.colors.global.accentA, modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(normalizeGroupName(group), color = theme.colors.type.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Checkbox(
                                            checked = group in bookmarkEditGroups,
                                            onCheckedChange = { checked ->
                                                bookmarkEditGroups = if (checked) (bookmarkEditGroups + group).distinct() else bookmarkEditGroups - group
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = theme.colors.global.accentA),
                                        )
                                    }
                                    HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.15f))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingBookmark = null; showCreateGroup = false }) { Text("Cancel", color = theme.colors.type.secondary) }
                            TextButton(onClick = {
                                val year = bookmarkEditYear.toIntOrNull()
                            vm.updateBookmark(
                                tmdbId = bookmark.tmdbId,
                                title = bookmarkEditTitle.trim().ifBlank { bookmark.title },
                                type = bookmark.type,
                                year = year ?: bookmark.year,
                                posterPath = bookmark.posterPath,
                                groups = bookmarkEditGroups.ifEmpty { null },
                            )
                                editingBookmark = null
                                showCreateGroup = false
                            }) { Text("Save", color = theme.colors.global.accentA) }
                        }
                    }
                }
            }
            if (showCreateGroup) {
                GroupEditorDialog(
                    currentGroups = bookmarkEditGroups,
                    allGroups = allGroupNames,
                    theme = theme,
                    showExistingGroups = false,
                    onUpdateGroups = { bookmarkEditGroups = it },
                    onDismiss = { showCreateGroup = false },
                )
            }
        }
        sectionSettings?.let { section ->
            val customGroup = section.takeIf { it.startsWith("[") }
            val draftGroup = editingGroup ?: customGroup
            val iconKey = draftGroup?.let(::groupIconKey) ?: "BOOKMARK"
            val label = draftGroup?.let(::normalizeGroupName).orEmpty()
            var showSortMenu by remember { mutableStateOf(false) }
            val currentSort = if (section == "Continue Watching") watchingSort else bookmarksSort
            Dialog(onDismissRequest = { sectionSettings = null; editingGroup = null }) {
                Surface(
                    color = theme.colors.modal.background,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
                    modifier = Modifier.widthIn(max = 480.dp),
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Section settings", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Sort", color = theme.colors.type.secondary, fontWeight = FontWeight.SemiBold)
                        Box {
                            OutlinedButton(onClick = { showSortMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(mediaSortOptions.first { it.first == currentSort }.second, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                mediaSortOptions.forEach { (value, sortLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(sortLabel) },
                                        leadingIcon = { if (currentSort == value) Icon(Icons.Default.Check, null) },
                                        onClick = {
                                            if (section == "Continue Watching") {
                                                watchingSort = value
                                                scope.launch { vm.userPrefs.saveWatchingSort(value) }
                                            } else {
                                                bookmarksSort = value
                                                scope.launch { vm.userPrefs.saveBookmarksSort(value) }
                                            }
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        if (customGroup != null) {
                            HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.2f))
                            Text("Edit group", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Affects ${state.bookmarkEntities.values.count { customGroup in it.groups.orEmpty() }} bookmarks",
                                color = theme.colors.type.secondary,
                                fontSize = 13.sp,
                            )
                        OutlinedTextField(
                            value = label,
                            onValueChange = { editingGroup = "[${iconKey.lowercase()}]${it.trim()}" },
                            label = { Text("Group name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            groupIconOptions.forEach { (key, icon) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (iconKey == key) theme.colors.global.accentA.copy(alpha = 0.22f) else theme.colors.background.secondary,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (iconKey == key) theme.colors.global.accentA else theme.colors.type.divider.copy(alpha = 0.35f),
                                    ),
                                    modifier = Modifier.clickable {
                                        editingGroup = "[${key.lowercase()}]$label"
                                    },
                                ) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(painterResource(icon), null, tint = theme.colors.type.emphasis, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { sectionSettings = null; editingGroup = null }) { Text("Cancel", color = theme.colors.type.secondary) }
                            TextButton(onClick = {
                                if (customGroup != null && draftGroup != null) vm.renameGroup(customGroup, draftGroup)
                                sectionSettings = null
                                editingGroup = null
                            }) { Text("Save", color = theme.colors.global.accentA) }
                        }
                    }
                }
            }
        }
        if (showNotifications) {
            NotificationsDialog(
                notifications = notifications,
                readGuids = readGuids,
                onMarkRead = { guid ->
                    scope.launch { vm.userPrefs.markNotificationRead(guid) }
                },
                onMarkAllRead = {
                    scope.launch { vm.userPrefs.markAllNotificationsRead(notifications.map { it.guid }) }
                },
                onDismiss = { showNotifications = false },
            )
        }
        if (showTipJar) {
            TipJarDialog(onDismiss = { showTipJar = false })
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(theme.colors.background.main)) {
        if (!state.enableLowPerformanceMode && !state.enableFeatured) {
            Box(Modifier.hazeSource(hazeState)) {
                CosmicBackground()
                ParticleOverlay()
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.colors.global.accentA)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                var retryFocused by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(state.error!!, color = theme.colors.type.danger, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    ZsOutlinedWrapper(
                        visible = retryFocused,
                        shape = RoundedCornerShape(14.dp),
                        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                        gap = 4.dp
                    ) {
                        Button(
                            onClick = vm::load,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.colors.global.accentA),
                            border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.onFocusChanged { retryFocused = it.isFocused }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                if (isTv) {
                    TvHomeScreenContent(
                        state = state,
                        nav = nav,
                        vm = vm,
                        theme = theme,
                        accountVm = accountVm,
                        session = session,
                        notifications = notifications,
                        readGuids = readGuids,
                        unreadCount = unreadCount,
                        sectionOrder = sectionOrder,
                        showContinueWatching = showContinueWatching,
                        showBookmarks = showBookmarks,
                        searchResults = searchResults,
                        placeholder = placeholder,
                        onShowNotifications = { showNotifications = true },
                        onShowTipJar = { showTipJar = true },
                        onShowLayout = { nav.navigate("downloads") },
                        onShowMenu = { showSandwichMenu = true },
                        hazeState = hazeState,
                        isPipActive = isTvPipActive,
                        isPipDrawerExpanded = isTvPipDrawerExpanded,
                        onPipDrawerExpandedChange = onTvPipDrawerExpandedChange,
                    )

                    OfflineBanner(
                        visible = state.isOffline && !offlineBannerDismissed,
                        onDismiss = { offlineBannerDismissed = true },
                    )
                    HomeDialogs()
                    return
                }
                val isSearching = isSearchFocused || state.searchQuery.isNotBlank()
                val isFeaturedActive = state.enableFeatured && state.featuredMedia.isNotEmpty()
                val shouldHeaderBeSticky = true
                var stickyHeaderBottomPx by remember { mutableStateOf(0f) }
                var heroSearchBarTopPx by remember { mutableStateOf(Float.MAX_VALUE) }
                val density = LocalDensity.current
                val heroSearchStickOffsetPx = remember(density) {
                    with(density) { PhoneHomeMetrics.heroSearchStickOffset.toPx() }
                }
                val heroSearchReleaseOffsetPx = remember(density) {
                    with(density) { PhoneHomeMetrics.heroSearchReleaseOffset.toPx() }
                }
                var shouldStickHeroSearchBar by remember { mutableStateOf(false) }
                LaunchedEffect(
                    isFeaturedActive,
                    heroSearchBarTopPx,
                    stickyHeaderBottomPx,
                    heroSearchStickOffsetPx,
                    heroSearchReleaseOffsetPx,
                ) {
                    shouldStickHeroSearchBar =
                        if (isFeaturedActive) {
                            false
                        } else if (shouldStickHeroSearchBar) {
                            heroSearchBarTopPx <= stickyHeaderBottomPx - heroSearchReleaseOffsetPx
                        } else {
                            heroSearchBarTopPx <= stickyHeaderBottomPx - heroSearchStickOffsetPx
                        }
                    }

                val displayedSearchResults = if (state.searchQuery.isNotBlank()) searchResults else emptyList()

                val bookmarksList = state.bookmarks.flatMap { it.items }
                val progressList = state.continueWatching.flatMap { it.items }

                val stickyHeaderHazeStyle = remember(theme.colors.background.main) {
                    HazeStyle(
                        backgroundColor = theme.colors.background.main,
                        tint = HazeTint(theme.colors.background.main.copy(alpha = 0.55f)),
                        blurRadius = 24.dp,
                        noiseFactor = 0.08f,
                        fallbackTint = HazeTint(theme.colors.background.main.copy(alpha = 0.86f)),
                    )
                }

                Box(Modifier.fillMaxSize()) {
                    val pullNestedScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                // Eat upward scroll to retract the indicator before the list moves
                                if (pullOffset > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                                    val consumed = available.y.coerceAtLeast(-pullOffset)
                                    pullOffset = (pullOffset + consumed).coerceAtLeast(0f)
                                    return Offset(0f, consumed)
                                }
                                return Offset.Zero
                            }

                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                // available.y > 0 means the list is already at the top — accumulate pull distance
                                if (!isRefreshing && available.y > 0f && source == NestedScrollSource.UserInput) {
                                    pullOffset = (pullOffset + available.y * 0.5f).coerceIn(0f, maxPullOffset)
                                }
                                return Offset.Zero
                            }

                            override suspend fun onPreFling(available: Velocity): Velocity {
                                // Finger has lifted — decide whether to trigger refresh or snap back
                                if (!isRefreshing && pullOffset >= maxPullOffset) {
                                    isRefreshing = true
                                } else if (!isRefreshing) {
                                    pullOffset = 0f
                                }
                                return Velocity.Zero
                            }
                        }
                    }

                    // Memoized on just their real inputs (not the whole `state`, which also
                    // changes on every search keystroke) -- otherwise these list-concat/dedup
                    // getters rerun several times a second while typing in the search box, for
                    // no reason (search doesn't affect either of them). Hoisted out of the
                    // LazyColumn's content lambda since LazyListScope isn't a @Composable scope
                    // -- remember() can't be called directly inside it.
                    val userSections = remember(state.continueWatching, state.bookmarks) { state.userSections }
                    val baseSections = remember(
                        state.movieSections, state.tvSections, state.editorSections,
                        state.activeTab, state.continueWatching, state.bookmarks,
                        state.enableCarouselView, state.homeSectionCarouselLimit,
                    ) { state.baseSections }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .hazeSource(hazeState)
                            .animateContentSize()
                            .nestedScroll(pullNestedScrollConnection),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues()
                    ) {
                        item {
                            if (isFeaturedActive) {
                                FeaturedCarousel(
                                    media = state.featuredMedia,
                                    nav = nav,
                                    progressMap = state.progressMap,
                                    showImageLogos = state.enableImageLogos,
                                    isSearching = isSearching,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Spacer(Modifier.height(160.dp)) // Height for header
                            }
                        }

                        if (!state.enableFeatured || state.featuredMedia.isEmpty()) {
                            item {
                                HeroSection(
                                    searchQuery = state.searchQuery,
                                    onSearch = vm::onSearchChange,
                                    nav = nav,
                                    placeholder = placeholder,
                                    searchBarModifier = Modifier.onGloballyPositioned {
                                        heroSearchBarTopPx = it.positionInRoot().y
                                    },
                                    hideSearchBar = shouldStickHeroSearchBar,
                                    enabled = !state.isOffline,
                                )
                            }
                            item { GenrePills(state.selectedGenreId, vm::setGenre) }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        if (state.searchQuery.isNotBlank()) {
                            item { Spacer(Modifier.height(16.dp)) }
                            MediaGridLazy( section = displayedSearchResults, nav = nav, progressMap = emptyMap(), numOfColumns = numOfColumns )
                            if (state.canLoadMore || displayedSearchResults.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (state.canLoadMore) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(35))
                                                    .background(
                                                        theme.colors.background.secondary.copy(
                                                            alpha = 1f
                                                        )
                                                    )
                                                    .border(
                                                        1.dp,
                                                        theme.colors.type.divider.copy(alpha = 1f),
                                                        RoundedCornerShape(35)
                                                    )
                                                    .clickable { vm.searchLoadMore() }
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            )
                                            { Text("Load More", color = theme.colors.type.emphasis, fontSize = 18.sp) }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(35))
                                                    .background(
                                                        theme.colors.background.secondary.copy(
                                                            alpha = 1f
                                                        )
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            )
                                            { Text("That's all we have...", color = theme.colors.type.secondary, fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }

                        } else {
                            if (showContinueWatching && state.continueWatchingLoading && state.continueWatching.isEmpty()) {
                                item("continue_watching_loading") {
                                    HomeSectionLoading("Continue Watching")
                                }
                                item { Spacer(Modifier.height(20.dp)) }
                            }
                            if (showBookmarks && state.bookmarksLoading && state.bookmarks.isEmpty()) {
                                item("bookmarks_loading") {
                                    HomeSectionLoading("My Bookmarks")
                                }
                                item { Spacer(Modifier.height(20.dp)) }
                            }
                            // User sections first (userSections computed above, outside the LazyColumn content lambda)
                            userSections
                                .sortedBy { section ->
                                    val id = when (section.title) {
                                        "Continue Watching" -> "continue_watching"
                                        "My Bookmarks" -> "bookmarks"
                                        else -> section.title.takeIf { it.startsWith("[") } ?: ""
                                    }
                                    sectionOrder.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }
                                }
                                .filter { section ->
                                    when (section.title) {
                                        "Continue Watching" -> showContinueWatching
                                        "My Bookmarks" -> showBookmarks
                                        else -> if (section.title.startsWith("[")) section.title !in hiddenGroups else true
                                    }
                                }
                                .forEach { section ->
                                    val sectionId = section.title
                                    val sectionPage = sectionPages[sectionId] ?: 0
                                    val isBookmarkSection = section.title == "My Bookmarks" || section.title.startsWith("[")
                                    val sectionSort = if (section.title == "Continue Watching") watchingSort else bookmarksSort
                                    val sortedSection = section.sorted(sectionSort, state.progressMap, state.bookmarkEntities)
                                    val removeItem: (Media) -> Unit = { media ->
                                        when (section.title) {
                                            "Continue Watching" -> vm.removeProgress(media.id.toString())
                                            "My Bookmarks" -> vm.removeBookmark(media.id.toString())
                                            else -> state.bookmarkEntities[media.id.toString()]?.let { bookmark ->
                                                vm.updateBookmarkGroups(bookmark.tmdbId, bookmark.groups.orEmpty() - section.title)
                                            }
                                        }
                                    }
                                    val editItem: ((Media) -> Unit)? = if (isBookmarkSection) {{ media ->
                                        state.bookmarkEntities[media.id.toString()]?.let { entity ->
                                            editingBookmark = entity
                                            showCreateGroup = false
                                            bookmarkEditTitle = entity.title
                                            bookmarkEditYear = entity.year?.toString().orEmpty()
                                            bookmarkEditGroups = entity.groups.orEmpty()
                                        }
                                    }} else null
                                    if (!state.enableCarouselView) {
                                        MediaGridPages(
                                            sortedSection, nav, state.progressMap,
                                            numOfColumns = numOfColumns,
                                            numOfRows = numOfRows,
                                            currentPage = sectionPage,
                                            onPageChange = { newPage -> sectionPages = sectionPages + (sectionId to newPage) },
                                            editable = editingBookmarks,
                                            onRemoveItem = removeItem,
                                            onEditItem = editItem,
                                            trailingContent = {
                                                SectionEditActions(
                                                    editing = editingBookmarks,
                                                    onToggleEditing = { editingBookmarks = !editingBookmarks },
                                                    onSettings = {
                                                        sectionSettings = section.title
                                                        editingGroup = section.title.takeIf { it.startsWith("[") }
                                                    },
                                                )
                                            },
                                        )
                                        //MediaGridLazy(section.items, nav, state.progressMap, numOfColumns)
                                        // switch between these 2 lines to test the lazyview with the bookmarks and continue watching section
                                    } else {
                                        item {
                                            MediaCarouselSection(
                                                sortedSection,
                                                nav,
                                                progressMap = state.progressMap,
                                                editable = editingBookmarks,
                                                onRemoveItem = removeItem,
                                                onEditItem = editItem,
                                                trailingContent = {
                                                    SectionEditActions(
                                                        editing = editingBookmarks,
                                                        onToggleEditing = { editingBookmarks = !editingBookmarks },
                                                        onSettings = {
                                                            sectionSettings = section.title
                                                            editingGroup = section.title.takeIf { it.startsWith("[") }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }
                                    item { Spacer(Modifier.height(20.dp)) }
                                }

                            if (!isTv) {
                                item { HomeTabs(state.activeTab, vm::setTab) }

                                // Base sections second (baseSections computed above, outside the LazyColumn content lambda)
                                baseSections.map { section ->
                                    val filtered = if (state.selectedGenreId != null)
                                        section.items.filter { it.genreIds?.contains(state.selectedGenreId) == true }
                                    else section.items
                                    section.copy(items = filtered)
                                }.filter { it.items.isNotEmpty() }.forEach { section ->
                                    item {
                                        MediaCarouselSection(
                                            section,
                                            nav,
                                            progressMap = state.progressMap,
                                        )
                                    }
                                    item { Spacer(Modifier.height(20.dp)) }
                                }
                            }
                        }
                    }
                    
                    // Pull-to-Refresh Indicator
                    if (pullOffset > 0 || isRefreshing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (pullOffset / 2).dp)
                                .size(40.dp)
                                .background(
                                    color = theme.colors.modal.background,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 2.dp,
                                    color = theme.colors.global.accentA,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = theme.colors.global.accentA,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                val rotationAngle = (pullOffset / maxPullOffset * 360).coerceIn(0f, 360f)
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Pull to refresh",
                                    tint = theme.colors.global.accentA,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(rotationAngle)
                                )
                            }
                        }
                    }

                    // Sticky Header Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        // Blurred background layer (frosted glass)
                        if (isSearching && shouldHeaderBeSticky) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .hazeEffect(hazeState, stickyHeaderHazeStyle) {
                                        progressive = HazeProgressive.verticalGradient(
                                            startIntensity = 1f,
                                            endIntensity = 0f,
                                            preferPerformance = true,
                                        )
                                    }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(bottom = 28.dp)
                                .onGloballyPositioned {
                                    stickyHeaderBottomPx = it.boundsInRoot().bottom
                                }
                        ) {
                            TopNavBar(
                                hazeState = hazeState,
                                onLayout = { nav.navigate("downloads") },
                                onMenu = { showSandwichMenu = true },
                                onDiscord = { uriHandler.openUri(Urls.DISCORD_LINK) },
                                onNotifications = { showNotifications = true },
                                onTipJar = { showTipJar = true },
                                onTvInstaller = { showTvActions = true },
                                showTvInstaller = !isTv,
                                collapseActionsIntoMenu = state.enableFeatured,
                                unreadCount = unreadCount,
                            )
                            if (state.enableFeatured && state.featuredMedia.isNotEmpty()) {
                                SearchOverlay(
                                    searchQuery = state.searchQuery,
                                    onSearch = vm::onSearchChange,
                                    isSearching = isSearching,
                                    onSearchFocusedChange = { isSearchFocused = it },
                                    onClearFocus = { focusManager.clearFocus() },
                                    focusRequester = focusRequester,
                                    placeholder = placeholder,
                                    enabled = !state.isOffline,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            } else if (shouldStickHeroSearchBar) {
                                HomeSearchBarRow(
                                    searchQuery = state.searchQuery,
                                    onSearch = vm::onSearchChange,
                                    placeholder = placeholder,
                                    focusRequester = focusRequester,
                                    enabled = !state.isOffline,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        OfflineBanner(
            visible = state.isOffline && !offlineBannerDismissed,
            onDismiss = { offlineBannerDismissed = true },
        )
        HomeDialogs()
    }
}

@Composable
private fun BoxScope.OfflineBanner(visible: Boolean, onDismiss: () -> Unit) {
    val theme = LocalZStreamTheme.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Surface(
            color = theme.colors.background.secondary,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.2f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "You're offline — showing downloaded content",
                    color = theme.colors.type.text,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                ZsIconButton(
                    onClick = onDismiss,
                    icon = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    variant = ZsIconButtonVariant.Ghost,
                    containerSize = 28.dp,
                    iconSize = 16.dp,
                )
            }
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
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
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
    val theme = LocalZStreamTheme.current
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
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
            drawCircle(
                theme.colors.type.emphasis.copy(alpha = alpha.coerceIn(0f, 1f)),
                p.r,
                Offset(cx, cy)
            )
        }
    }
}

@Composable
private fun TopNavBar(
    hazeState: dev.chrisbanes.haze.HazeState,
    onLayout: () -> Unit,
    onMenu: () -> Unit,
    onDiscord: () -> Unit,
    onNotifications: () -> Unit,
    onTipJar: () -> Unit,
    onTvInstaller: () -> Unit,
    showTvInstaller: Boolean,
    collapseActionsIntoMenu: Boolean,
    unreadCount: Int = 0,
) {
    val theme = LocalZStreamTheme.current
    val hazeStyle = rememberTopNavHazeStyle()
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Logo pill: icon + "Z-Stream" text
            var logoFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(
                visible = logoFocused,
                shape = RoundedCornerShape(50),
                outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                gap = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .onFocusChanged { logoFocused = it.isFocused }
                        .focusable()
                        .clip(RoundedCornerShape(50))
                        .hazeEffect(hazeState, hazeStyle)
                        .background(theme.colors.background.secondary.copy(alpha = 0.48f))
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
            }
            if (!collapseActionsIntoMenu) {
                HeaderIconButton(icon = ImageVector.vectorResource(R.drawable.ic_discord), hazeState = hazeState, onClick = onDiscord)
                HeaderIconButton(icon = Icons.Default.AttachMoney, hazeState = hazeState, onClick = onTipJar)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showTvInstaller) {
                HeaderIconButton(icon = Icons.Default.Tv, hazeState = hazeState, onClick = onTvInstaller)
            }
            // Layout button — dark pill matching sandwich (just grid icon, no text)
            HeaderIconButton(icon = Icons.Default.Download, hazeState = hazeState, onClick = onLayout)
            // Sandwich menu
            var menuFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(
                visible = menuFocused,
                shape = RoundedCornerShape(50),
                outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                gap = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .hazeEffect(hazeState, hazeStyle)
                        .background(theme.colors.background.secondary.copy(alpha = 0.48f))
                        .border(
                            1.dp,
                            theme.colors.type.divider.copy(alpha = 0.3f),
                            RoundedCornerShape(50)
                        )
                        .onFocusChanged { menuFocused = it.isFocused }
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
}

@Composable
private fun rememberTopNavHazeStyle(): HazeStyle {
    val background = LocalZStreamTheme.current.colors.background.main
    return remember(background) {
        HazeStyle(
            backgroundColor = background,
            tint = HazeTint(background.copy(alpha = 0.28f)),
            blurRadius = 18.dp,
            noiseFactor = 0.05f,
            fallbackTint = HazeTint(background.copy(alpha = 0.58f)),
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hazeState: dev.chrisbanes.haze.HazeState,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val theme = LocalZStreamTheme.current
    val hazeStyle = rememberTopNavHazeStyle()
    var focusedMenu by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = focusedMenu,
        shape = RoundedCornerShape(50),
        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
        gap = 4.dp
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .hazeEffect(hazeState, hazeStyle)
                .background(theme.colors.background.secondary.copy(alpha = 0.48f))
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(50))
                .onFocusChanged { focusedMenu = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp))
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(theme.colors.type.danger)
                        .align(Alignment.TopEnd)
                        .offset(x = 0.dp, y = (-5).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = theme.colors.type.emphasis,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    searchQuery: String,
    onSearch: (String) -> Unit,
    nav: NavController,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    searchBarModifier: Modifier = Modifier,
    hideSearchBar: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalZStreamTheme.current
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)

    Column(
        modifier = modifier
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
            modifier = searchBarModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeSearchBarRow(
                searchQuery = searchQuery,
                onSearch = onSearch,
                placeholder = placeholder,
                focusRequester = focusRequester,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = if (hideSearchBar) 0f else 1f },
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeSearchBarRow(
    searchQuery: String,
    onSearch: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isSearchFocused by remember { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }

    ZsOutlinedWrapper(
        visible = isSearchFocused && isTv,
        shape = RoundedCornerShape(48.dp),
        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
        gap = 2.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(RoundedCornerShape(48.dp))
                .background(theme.colors.search.background)
                .border(
                    1.dp,
                    theme.colors.type.divider.copy(alpha = 0.3f),
                    RoundedCornerShape(48.dp)
                )
                .onFocusChanged { isSearchFocused = it.isFocused }
                .then(
                    if (isTv && enabled) {
                        Modifier
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .clickable { textFieldFocusRequester.requestFocus() }
                    } else {
                        Modifier
                    }
                ),
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
                        Text(placeholder, color = theme.colors.search.placeholder, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearch,
                        enabled = enabled,
                        singleLine = true,
                        textStyle = TextStyle(color = theme.colors.search.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(theme.colors.global.accentA),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                            .then(
                                if (!isTv && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
                            ),
                    )
                }
                if (!isTv && searchQuery.isNotEmpty()) {
                    ZsIconButton(
                        onClick = { onSearch("") },
                        icon = Icons.Default.Close,
                        contentDescription = "Clear search",
                        variant = ZsIconButtonVariant.Ghost,
                        containerSize = 28.dp,
                        iconSize = 16.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenrePills(
    selectedGenreId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tmdbGenres.entries.toList()) { (id, name) ->
            val selected = selectedGenreId == id
            var isFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(
                visible = isFocused && isTv,
                shape = RoundedCornerShape(50),
                outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                gap = 2.dp
            ) {
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
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelect(if (selected) null else id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeTabs(
    activeTab: HomeTab,
    onTab: (HomeTab) -> Unit,
    onFocused: (() -> Unit)? = null,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
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
            var isFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(
                visible = isFocused && isTv,
                shape = RoundedCornerShape(50),
                outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                gap = 2.dp,
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                isFocused && isTv -> theme.colors.background.secondary.copy(alpha = 0.5f)
                                active -> theme.colors.background.secondary.copy(alpha = 0.32f)
                                else -> Color.Transparent
                            }
                        )
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) onFocused?.invoke()
                        }
                        .clickable { onTab(tab) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
}

/**
 * unified section title fun for carousel, paged grid, lazy grid, main homescreen lazycolumn
 */

@Composable
private fun SyncedSectionTitle(title: String) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val match = Regex("^\\[([A-Za-z0-9_]+)](.*)$").find(title)
    val iconKey = match?.groupValues?.getOrNull(1)?.uppercase()
        ?: if (title == "My Bookmarks") "BOOKMARK" else null
    val icon = when {
        title == "Continue Watching" -> painterResource(R.drawable.ic_section_clock)
        iconKey != null -> groupIconPainter(iconKey)
        else -> null
    }
    val cleanTitle = match?.groupValues?.getOrNull(2)?.trim().orEmpty().ifBlank { title }
    Row(
        modifier = Modifier.padding(
            horizontal = if (isTv) TvHomeMetrics.screenPadding else 16.dp,
            vertical = if (isTv) 6.dp else 8.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 22.dp else 20.dp)
                    .clip(CircleShape)
                    .background(theme.colors.global.accentA.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = theme.colors.global.accentA, modifier = Modifier.size(if (isTv) 16.dp else 14.dp))
            }
        }
        Text(
            text = cleanTitle,
            color = theme.colors.type.emphasis,
            fontWeight = FontWeight.Bold,
            fontSize = if (isTv) TvHomeMetrics.sectionTitleSize else 16.sp,
        )
    }
}

@Composable
private fun SectionEditActions(
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onSettings: () -> Unit,
) {
    if (editing) {
        ZsIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Section settings",
            onClick = onSettings,
            containerSize = 38.dp,
            iconSize = 19.dp,
        )
        Spacer(Modifier.width(8.dp))
    }
    ZsIconButton(
        variant = ZsIconButtonVariant.Secondary,
        icon = if (editing) Icons.Default.Check else Icons.Default.Edit,
        contentDescription = if (editing) "Done editing" else "Edit section",
        onClick = onToggleEditing,
        modifier = Modifier.padding(end = 20.dp),
        containerSize = 38.dp,
        iconSize = 19.dp,
    )
}

@Composable
private fun MediaCarouselSection(
    section: MediaSection,
    nav: NavController,
    progressMap: Map<String, ProgressEntity> = emptyMap(),
    editable: Boolean = false,
    onRemoveItem: ((Media) -> Unit)? = null,
    onEditItem: ((Media) -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var tvEditMediaId by remember { mutableStateOf<Int?>(null) }
    val rowListState = rememberLazyListState()
    val viewMoreFocusRequester = remember { FocusRequester() }
    // Track whether the ViewMoreCard was the last focused item in this row
    var viewMoreWasLastFocused by remember { mutableStateOf(false) }
    var rowHasFocus by remember { mutableStateOf(false) }
    val hasViewMore = section.source != null && section.totalItems > section.items.size
    val viewMoreIndex = section.items.size // ViewMoreCard is appended after all media items
    // When focus re-enters the row and ViewMoreCard was last focused, scroll it back
    // into view (it may have been de-composed while scrolled off-screen) and then
    // explicitly request focus on it. focusRestorer alone can't restore a de-composed item.
    LaunchedEffect(rowHasFocus) {
        if (rowHasFocus && viewMoreWasLastFocused && hasViewMore) {
            rowListState.scrollToItem(viewMoreIndex)
            // The item needs a frame or two to compose and attach its FocusRequester after
            // scrolling. Retry across a few frames until the request succeeds.
            var attempts = 0
            while (attempts < 5) {
                withFrameMillis { }
                val ok = try {
                    viewMoreFocusRequester.requestFocus()
                    true
                } catch (_: Exception) {
                    false
                }
                if (ok) break
                attempts++
            }
        }
    }
    Column(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTv) {
                    trailingContent?.invoke(this)
                    SyncedSectionTitle(section.title)
                    Spacer(Modifier.weight(1f))
                } else {
                    SyncedSectionTitle(section.title)
                    Spacer(Modifier.weight(1f))
                    trailingContent?.invoke(this)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        LazyRow(
            state = rowListState,
            modifier = Modifier
                .focusRestorer()
                .onFocusChanged { rowHasFocus = it.hasFocus },
            contentPadding = PaddingValues(horizontal = if (isTv) TvHomeMetrics.screenPadding else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) TvHomeMetrics.railItemSpacing else 10.dp)
        ) {
            items(section.items, key = { it.id }) { media ->
                val progress = progressMap[media.id.toString()]
                val progressInfo = progress?.let { entry ->
                    val watched = entry.watched.toFloat()
                    val duration = entry.duration.toFloat()
                    if (watched <= 0f || duration <= 0f) null
                    else {
                        val percentage = (watched / duration * 100f).coerceIn(0f, 100f)
                        if (percentage >= 95f) null else percentage to if (entry.type == "show" && entry.seasonNumber != null && entry.episodeNumber != null) {
                            "S${entry.seasonNumber} - E${entry.episodeNumber}"
                        } else null
                    }
                }
                Box(
                    modifier = if (isTv) Modifier.onFocusChanged { if (it.hasFocus) viewMoreWasLastFocused = false } else Modifier
                ) {
                    MediaCard(
                        media = media,
                        onClick = {
                            if (editable && isTv) tvEditMediaId = media.id
                            else nav.navigate("detail/${media.type}/${media.id}")
                        },
                        percentage = progressInfo?.first,
                        seriesLabel = progressInfo?.second,
                        editOverlay = editable,
                    )
                    if (editable && onRemoveItem != null && !isTv) {
                        if (onEditItem != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.75f))
                            ) {
                                IconButton(onClick = { onEditItem(media) }, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Edit, "Edit bookmark", tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = 62.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                        ) {
                            IconButton(onClick = { onRemoveItem(media) }, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    if (editable && isTv && tvEditMediaId == media.id && onRemoveItem != null) {
                        TvMediaEditMenu(
                            onEdit = onEditItem?.let { { it(media) } },
                            onRemove = { onRemoveItem(media) },
                            onDismiss = { tvEditMediaId = null },
                        )
                    }
                }
            }
            if (hasViewMore) {
                item {
                    ViewMoreCard(
                        onClick = {
                            nav.navigate("more/${section.source.name}?group=${Uri.encode(section.groupKey.orEmpty())}")
                        },
                        focusRequester = viewMoreFocusRequester,
                        onFocused = { viewMoreWasLastFocused = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionLoading(title: String, modifier: Modifier = Modifier) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    Column(modifier = modifier) {
        SyncedSectionTitle(title)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 236.dp else 180.dp)
                .padding(horizontal = if (isTv) TvHomeMetrics.screenPadding else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = theme.colors.global.accentA,
                strokeWidth = 3.dp,
            )
        }
    }
}

/**
 * Alternate media grid view
 * Paged and lazy views calling single row function
 */
@Composable
private fun MediaGridRow(
    rowItems: List<Media>,
    nav: NavController,
    progressMap: Map<String, ProgressEntity> = emptyMap(),
    numOfColumns: Int,
    editable: Boolean = false,
    onRemoveItem: ((Media) -> Unit)? = null,
    onEditItem: ((Media) -> Unit)? = null,
    previousPageFocusRequester: FocusRequester? = null,
    nextPageFocusRequester: FocusRequester? = null,
    onRowFocused: ((Int, Int) -> Unit)? = null,
) {
    val isTv = LocalIsTv.current
    var tvEditMediaId by remember { mutableStateOf<Int?>(null) }
    var rowTopPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    var rowHasFocus by remember { mutableStateOf(false) }
    val currentOnRowFocused by rememberUpdatedState(onRowFocused)

    LaunchedEffect(rowHasFocus, rowTopPx, rowHeightPx) {
        if (rowHasFocus && rowHeightPx > 0) {
            withFrameMillis { }
            currentOnRowFocused?.invoke(rowTopPx, rowHeightPx)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                rowTopPx = it.positionInParent().y.roundToInt()
                rowHeightPx = it.size.height
            }
            .onFocusChanged { rowHasFocus = it.hasFocus }
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rowItems.forEachIndexed { index, media ->
            val progress = progressMap[media.id.toString()]
            val progressInfo = progress?.let { entry ->
                val watched = entry.watched.toFloat()
                val duration = entry.duration.toFloat()
                if (watched <= 0f || duration <= 0f) null
                else {
                    val percentage = (watched / duration * 100f).coerceIn(0f, 100f)
                    if (percentage >= 95f) null else percentage to if (entry.type == "show" && entry.seasonNumber != null && entry.episodeNumber != null) {
                        "S${entry.seasonNumber} - E${entry.episodeNumber}"
                    } else null
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        if (previousPageFocusRequester != null && index == 0) left = previousPageFocusRequester
                        if (nextPageFocusRequester != null && index == rowItems.lastIndex) right = nextPageFocusRequester
                    }
            ) {
                MediaCard(
                    media = media,
                    onClick = {
                        if (editable && isTv) tvEditMediaId = media.id
                        else nav.navigate("detail/${media.type}/${media.id}")
                    },
                    percentage = progressInfo?.first,
                    seriesLabel = progressInfo?.second,
                    editOverlay = editable
                )
                if (editable && onRemoveItem != null && !isTv) {
                        if (onEditItem != null) {
                            Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                        ) {
                            IconButton(onClick = { onEditItem(media) }, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Edit, "Edit bookmark", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    IconButton(
                        onClick = { onRemoveItem(media) },
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = 62.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.75f)),
                    ) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
                if (editable && isTv && tvEditMediaId == media.id && onRemoveItem != null) {
                    TvMediaEditMenu(
                        onEdit = onEditItem?.let { { it(media) } },
                        onRemove = { onRemoveItem(media) },
                        onDismiss = { tvEditMediaId = null },
                    )
                }
            }
        }
        repeat(numOfColumns - rowItems.size) {
            Box(modifier = Modifier.weight(1f)) {}
        }
    }
}

// add ur own title if calling this cuz there could be a lot of ways to format it
// currently unused
fun LazyListScope.MediaGridLazy(
    section: List<Media>,
    nav: NavController,
    progressMap: Map<String, ProgressEntity> = emptyMap(),
    numOfColumns: Int)
{  val rows = section.chunked(numOfColumns)
    items(rows) {rowData->
        MediaGridRow(
            rowItems = rowData,
            nav = nav,
            progressMap = progressMap,
            numOfColumns = numOfColumns
        )
    }
    item {
        Spacer(Modifier.height(16.dp))
    }


}

private fun LazyListScope.MediaGridPages(
    section: MediaSection,
    nav: NavController,
    progressMap: Map<String, ProgressEntity> = emptyMap(),
    numOfColumns: Int,
    numOfRows: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    editable: Boolean = false,
    onRemoveItem: ((Media) -> Unit)? = null,
    onEditItem: ((Media) -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onRowFocused: ((Int, Int) -> Unit)? = null,
) {
    item {
        val theme = LocalZStreamTheme.current
        val isTv = LocalIsTv.current
        val focusManager = LocalFocusManager.current
        val pageSize = remember(numOfColumns, numOfRows) { numOfColumns * numOfRows }
        val totalPages = remember(section.items.size, pageSize) {
            (section.items.size + pageSize - 1) / pageSize
        }
        val clampedPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        var editValue by remember { mutableStateOf((clampedPage + 1).toString()) }

        LaunchedEffect(clampedPage, pageSize) {
            editValue = (clampedPage + 1).toString()
        }

        val previousPageFocusRequester = remember { FocusRequester() }
        val nextPageFocusRequester = remember { FocusRequester() }
        var focusedRowTopPx by remember { mutableIntStateOf(0) }
        var focusedRowHeightPx by remember { mutableIntStateOf(0) }

        val pageContent: @Composable () -> Unit = {
            AnimatedContent(
                targetState = clampedPage,
                label = "pageTransition",
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                }
            ) { targetPage ->
                val cards = remember(targetPage, section.items, pageSize) {
                    section.items.drop(targetPage * pageSize).take(pageSize)
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SyncedSectionTitle(section.title)
                        Spacer(Modifier.weight(1f))
                        trailingContent?.invoke(this)
                    }
                    Spacer(Modifier.height(6.dp))
                    cards.chunked(numOfColumns).forEach { rowItems ->
                        MediaGridRow(
                            rowItems = rowItems,
                            nav = nav,
                            progressMap = progressMap,
                            numOfColumns = numOfColumns,
                            editable = editable,
                            onRemoveItem = onRemoveItem,
                            onEditItem = onEditItem,
                            previousPageFocusRequester = previousPageFocusRequester.takeIf { isTv && clampedPage > 0 },
                            nextPageFocusRequester = nextPageFocusRequester.takeIf { isTv && clampedPage < totalPages - 1 },
                            onRowFocused = { top, height ->
                                focusedRowTopPx = top
                                focusedRowHeightPx = height
                                onRowFocused?.invoke(top, height)
                            },
                        )
                    }
                }
            }
        }

        if (isTv) {
            val arrowSizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
            var gridHeightPx by remember { mutableIntStateOf(0) }
            var gridHasFocus by remember { mutableStateOf(false) }
            val maxArrowOffset = (gridHeightPx - arrowSizePx).coerceAtLeast(0)
            val restingArrowOffset = maxArrowOffset / 2
            val trackedArrowOffset = (focusedRowTopPx + focusedRowHeightPx / 2 - arrowSizePx / 2)
                .coerceIn(0, maxArrowOffset)
            val arrowOffsetY by animateIntAsState(
                targetValue = if (gridHasFocus) trackedArrowOffset else restingArrowOffset,
                animationSpec = tween(200),
                label = "gridArrowOffset",
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { gridHasFocus = it.hasFocus }
                    .onGloballyPositioned {
                        gridHeightPx = it.size.height
                    }
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 58.dp)) { pageContent() }
                ZsIconButton(
                    onClick = { if (clampedPage > 0) onPageChange(clampedPage - 1) },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    variant = ZsIconButtonVariant.Ghost,
                    enabled = clampedPage > 0,
                    containerSize = 48.dp,
                    iconSize = 36.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp)
                        .offset { IntOffset(0, arrowOffsetY) },
                    focusRequester = previousPageFocusRequester,
                )
                ZsIconButton(
                    onClick = { if (clampedPage < totalPages - 1) onPageChange(clampedPage + 1) },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                    variant = ZsIconButtonVariant.Ghost,
                    enabled = clampedPage < totalPages - 1,
                    containerSize = 48.dp,
                    iconSize = 36.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 10.dp)
                        .offset { IntOffset(0, arrowOffsetY) },
                    focusRequester = nextPageFocusRequester,
                )
            }
        } else {
            pageContent()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isTv) {
                ZsIconButton(
                    onClick = { if (clampedPage > 0) onPageChange(clampedPage - 1) },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    variant = ZsIconButtonVariant.Ghost,
                    enabled = clampedPage > 0,
                    containerSize = 36.dp,
                    iconSize = 32.dp,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.colors.background.secondary.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                BasicTextField(
                    value = editValue,
                    onValueChange = { if (it.all { c -> c.isDigit() }) editValue = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val page = editValue.toIntOrNull()
                            if (page != null && page in 1..totalPages) {
                                onPageChange(page - 1)
                            } else {
                                editValue = (clampedPage + 1).toString()
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    textStyle = TextStyle(
                        color = theme.colors.type.emphasis,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(theme.colors.global.accentA),
                    modifier = Modifier
                        .width(40.dp)
                        .focusProperties { canFocus = !isTv }
                )
                Text(
                    text = " / $totalPages",
                    color = theme.colors.type.emphasis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!isTv) {
                ZsIconButton(
                    onClick = { if (clampedPage < totalPages - 1) onPageChange(clampedPage + 1) },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    variant = ZsIconButtonVariant.Ghost,
                    enabled = clampedPage < totalPages - 1,
                    containerSize = 36.dp,
                    iconSize = 32.dp,
                )
            }
        }
    }
}

@Composable
private fun LayoutMenuDialog(
    sectionOrder: List<String>,
    showContinueWatching: Boolean,
    showBookmarks: Boolean,
    hiddenGroups: Set<String> = emptySet(),
    allGroups: List<String> = emptyList(),
    vm: HomeViewModel? = null,
    onToggle: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) {
            try {
                closeFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    data class LayoutItem(val id: String, val label: String, val visible: Boolean)

    var editingGroup by remember { mutableStateOf<String?>(null) }
    var editingGroupName by remember { mutableStateOf("") }
    var editingGroupIcon by remember { mutableStateOf("BOOKMARK") }
    val scope = rememberCoroutineScope()

    val sections = remember(sectionOrder, allGroups) {
        mutableStateListOf<LayoutItem>().apply {
            sectionOrder.forEach { id ->
                add(
                    LayoutItem(
                        id = id,
                        label = when (id) {
                            "continue_watching" -> "Continue Watching..."
                            "bookmarks" -> "Bookmarks"
                            else -> if (id.startsWith("[")) normalizeGroupName(id) else id
                        },
                        visible = when (id) {
                            "continue_watching" -> showContinueWatching
                            "bookmarks" -> showBookmarks
                            else -> if (id.startsWith("[")) id !in hiddenGroups else true
                        },
                    )
                )
            }
            allGroups.forEach { group ->
                if (group != "bookmarks" && group !in sectionOrder) {
                    add(LayoutItem(id = group, label = normalizeGroupName(group), visible = group !in hiddenGroups))
                }
            }
        }
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableMapOf<Int, Int>() }

    if (isTv) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(
                x = -(TvHomeMetrics.screenPadding.value * 2).toInt(),
                y = (TvHomeMetrics.topBarHeight.value).toInt()
            ),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true)
        ) {
            Box(Modifier
                .width(TvHomeMetrics.menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.colors.modal.background)
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(12.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Edit Layout", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        ZsIconButton(
                            onClick = onDismiss,
                            icon = Icons.Default.Close,
                            contentDescription = null,
                            variant = ZsIconButtonVariant.Ghost,
                            containerSize = 24.dp,
                            iconSize = 16.dp,
                            modifier = Modifier.focusRequester(closeFocusRequester)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    sections.forEachIndexed { index, section ->
                        var isRowFocused by remember { mutableStateOf(false) }

                        ZsOutlinedWrapper(
                            visible = isRowFocused,
                            shape = RoundedCornerShape(8.dp),
                            outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                            gap = 2.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRowFocused) theme.colors.background.secondary.copy(alpha = 0.4f) else Color.Transparent)
                                    .onFocusChanged { isRowFocused = it.isFocused }
                                    .clickable {
                                        val nextVal = !section.visible
                                        sections[index] = section.copy(visible = nextVal)
                                        onToggle(section.id, nextVal)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle, null,
                                        tint = theme.colors.type.dimmed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (section.id.startsWith("[")) {
                                        val iconKey = groupIconKey(section.id) ?: "BOOKMARK"
                                        Icon(groupIconPainter(iconKey), null, tint = theme.colors.global.accentA, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        section.label,
                                        color = theme.colors.type.text, fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                                    )
                                    if (section.id.startsWith("[")) {
                                        IconButton(onClick = {
                                            editingGroup = section.id
                                            editingGroupName = normalizeGroupName(section.id)
                                            editingGroupIcon = groupIconKey(section.id) ?: "BOOKMARK"
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Edit, "Edit group", tint = theme.colors.type.dimmed, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Switch(
                                        checked = section.visible,
                                        onCheckedChange = null,
                                        thumbContent = null,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = theme.colors.type.emphasis,
                                            checkedTrackColor = theme.colors.global.accentA,
                                            uncheckedThumbColor = theme.colors.type.dimmed,
                                            uncheckedTrackColor = theme.colors.background.secondary,
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
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
                        ZsIconButton(
                            onClick = onDismiss,
                            icon = Icons.Default.Close,
                            contentDescription = null,
                            variant = ZsIconButtonVariant.Ghost,
                            containerSize = 24.dp,
                            iconSize = 16.dp,
                            modifier = Modifier.focusRequester(closeFocusRequester)
                        )
                    }

                    sections.forEachIndexed { index, section ->
                        val isDragging = draggedIndex == index
                        var isRowFocused by remember { mutableStateOf(false) }

                        ZsOutlinedWrapper(
                            visible = isRowFocused && LocalIsTv.current,
                            shape = RoundedCornerShape(12.dp),
                            outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                            gap = 2.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .onGloballyPositioned { itemHeights[index] = it.size.height }
                                    .offset {
                                        IntOffset(
                                            0,
                                            if (isDragging) draggedOffset.roundToInt() else 0
                                        )
                                    }
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isRowFocused && LocalIsTv.current) theme.colors.background.secondary.copy(alpha = 0.4f) else Color.Transparent)
                                    .onFocusChanged { isRowFocused = it.isFocused }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
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
                                                        val orderedIds = sections.map { it.id }
                                                        onReorder(orderedIds)
                                                        val groupIds = orderedIds.filter { it.startsWith("[") }
                                                        if (groupIds.isNotEmpty()) {
                                                            scope.launch { vm?.setGroupOrder(groupIds) }
                                                        }
                                                        draggedIndex = null
                                                        draggedOffset = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = null
                                                        draggedOffset = 0f
                                                    },
                                                )
                                            }
                                    )
                                    if (section.id.startsWith("[")) {
                                        val iconKey = groupIconKey(section.id) ?: "BOOKMARK"
                                        Icon(groupIconPainter(iconKey), null, tint = theme.colors.global.accentA, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        section.label,
                                        color = theme.colors.type.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (section.id.startsWith("[")) {
                                        IconButton(onClick = {
                                            editingGroup = section.id
                                            editingGroupName = normalizeGroupName(section.id)
                                            editingGroupIcon = groupIconKey(section.id) ?: "BOOKMARK"
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Edit, "Edit group", tint = theme.colors.type.dimmed, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Switch(
                                        checked = section.visible,
                                        onCheckedChange = {
                                            sections[index] = section.copy(visible = it)
                                            onToggle(section.id, it)
                                        },
                                        thumbContent = null,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = theme.colors.type.emphasis,
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
    }

    editingGroup?.let { group ->
        val iconKey = groupIconKey(group) ?: "BOOKMARK"
        val name = normalizeGroupName(group)
        Dialog(onDismissRequest = { editingGroup = null; editingGroupName = ""; editingGroupIcon = "BOOKMARK" }) {
            Surface(
                color = theme.colors.modal.background,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
                modifier = Modifier.widthIn(max = 384.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Edit group", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editingGroupName,
                        onValueChange = { editingGroupName = it },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Icon", color = theme.colors.type.secondary, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groupIconOptions.forEach { (key, icon) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (editingGroupIcon == key) theme.colors.global.accentA.copy(alpha = 0.22f) else theme.colors.background.secondary,
                                border = BorderStroke(1.dp, if (editingGroupIcon == key) theme.colors.global.accentA else theme.colors.type.divider.copy(alpha = 0.35f)),
                                modifier = Modifier.clickable { editingGroupIcon = key },
                            ) {
                                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(icon), null, tint = if (editingGroupIcon == key) theme.colors.global.accentA else theme.colors.type.emphasis, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingGroup = null; editingGroupName = ""; editingGroupIcon = "BOOKMARK" }) { Text("Cancel", color = theme.colors.type.secondary) }
                        TextButton(enabled = editingGroupName.trim().isNotEmpty(), onClick = {
                            vm?.renameGroup(group, "[${editingGroupIcon.lowercase()}]${editingGroupName.trim()}")
                            editingGroup = null
                        }) { Text("Save", color = theme.colors.global.accentA) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandwichMenuDialog(
    nav: NavController,
    vm: HomeViewModel,
    session: com.zstream.android.data.AccountSession?,
    accountVm: AccountViewModel,
    showHeaderActions: Boolean,
    unreadCount: Int,
    watchPartyEnabled: Boolean,
    watchPartyRoomCode: String?,
    watchPartyIsOffline: Boolean,
    onDiscord: () -> Unit,
    onNotifications: () -> Unit,
    onTipJar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    val focusManager = LocalFocusManager.current
    var isJoiningWatchParty by remember { mutableStateOf(false) }
    var watchPartyCode by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    val isTv = LocalIsTv.current
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (isTv) {
        val menuMaxHeight = LocalConfiguration.current.screenHeightDp.dp - TvHomeMetrics.topBarHeight - 16.dp
        val menuScrollState = rememberScrollState()
        LaunchedEffect(isJoiningWatchParty, menuScrollState.maxValue) {
            if (isJoiningWatchParty) menuScrollState.scrollTo(menuScrollState.maxValue)
        }
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(
                x = -(TvHomeMetrics.screenPadding.value * 2).toInt(),
                y = (TvHomeMetrics.topBarHeight.value).toInt()
            ),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, clippingEnabled = false)
        ) {
            Box(Modifier
                .width(TvHomeMetrics.menuWidth)
                .heightIn(max = menuMaxHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.colors.modal.background)
                .border(
                    1.dp,
                    theme.colors.type.divider.copy(alpha = 0.2f),
                    RoundedCornerShape(16.dp)
                )
                .padding(vertical = 8.dp)) {
                Column(
                    Modifier
                        .verticalScroll(menuScrollState)
                        .imePadding()
                ) {
                    if (session != null) {
                        val displayName = session.nickname.ifBlank { session.deviceName.ifBlank { "Synced" } }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = theme.colors.type.success, modifier = Modifier.size(16.dp))
                            Text("Synced: $displayName", color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        SandwichItem(
                            Icons.Default.Star, "Sync to Cloud", theme = theme, tint = theme.colors.global.accentA,
                            modifier = Modifier.focusRequester(firstItemFocusRequester)
                        ) {
                            nav.navigate("login")
                            onDismiss()
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))

                    val settingsModifier = if (session != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                    SandwichItem(Icons.Default.Settings, "Settings", theme = theme, modifier = settingsModifier) { nav.navigate("settings"); onDismiss() }
                    SandwichItem(Icons.Default.History, "Watch History", theme = theme) { nav.navigate("watchHistory"); onDismiss() }
                    SandwichItem(Icons.Default.Download, "Downloads", theme = theme) { nav.navigate("downloads"); onDismiss() }
                    SandwichItem(Icons.Default.Notifications, if (unreadCount > 0) "Notifications ($unreadCount)" else "Notifications", theme = theme) {
                        onNotifications()
                        onDismiss()
                    }
                    if (showHeaderActions) {
                        SandwichItem(ImageVector.vectorResource(R.drawable.ic_discord), "Discord", theme = theme) { onDiscord(); onDismiss() }
                        SandwichItem(Icons.Default.AttachMoney, "Tip Jar", theme = theme) { onTipJar(); onDismiss() }
                    }
                    SandwichItem(Icons.Default.Explore, "Discover", theme = theme) { nav.navigate("search"); onDismiss() }
                    if (watchPartyEnabled) {
                        val watchPartyHostGraceDeadlineMs by vm.watchPartyHostGraceDeadlineMs.collectAsState()
                        WatchPartyActiveItem(
                            roomCode = watchPartyRoomCode,
                            isOffline = watchPartyIsOffline,
                            hostGraceDeadlineMs = watchPartyHostGraceDeadlineMs,
                            onJump = { vm.joinWatchParty(watchPartyRoomCode ?: "") },
                            onLeave = { vm.leaveWatchParty() },
                            theme = theme
                        )
                    } else {
                        var isWatchPartyFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(
                            visible = isWatchPartyFocused,
                            shape = RoundedCornerShape(10.dp),
                            outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                            gap = 2.dp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isWatchPartyFocused) theme.colors.background.secondary.copy(alpha = 0.4f) else Color.Transparent)
                                    .onFocusChanged { isWatchPartyFocused = it.isFocused }
                                    .focusProperties {
                                        left = FocusRequester.Cancel
                                        right = FocusRequester.Cancel
                                    }
                                    .then(if (!isJoiningWatchParty) Modifier.clickable { isJoiningWatchParty = true } else Modifier)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Default.Group, null, tint = theme.colors.type.secondary, modifier = Modifier.size(18.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(
                                    targetState = isJoiningWatchParty,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "joinWatchPartyInner"
                                ) { joining ->
                                    if (joining) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(32.dp)
                                                    .background(theme.colors.background.secondary, RoundedCornerShape(6.dp))
                                                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (watchPartyCode.isEmpty()) {
                                                    Text("Enter a Watch Party code", color = theme.colors.type.dimmed, fontSize = 13.sp)
                                                }
                                                BasicTextField(
                                                    value = watchPartyCode,
                                                    onValueChange = { input ->
                                                        val filtered = input.uppercase().filter { it.isLetterOrDigit() }
                                                        if (filtered.length <= 6) watchPartyCode = filtered
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .focusRequester(focusRequester),
                                                    textStyle = TextStyle(
                                                        color = theme.colors.type.emphasis,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    cursorBrush = SolidColor(theme.colors.global.accentA),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Text,
                                                        imeAction = ImeAction.Go
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onGo = {
                                                            if (watchPartyCode.length >= 4) {
                                                                vm.joinWatchParty(watchPartyCode)
                                                                onDismiss()
                                                            }
                                                        }
                                                    )
                                                )
                                                LaunchedEffect(Unit) {
                                                    focusRequester.requestFocus()
                                                }
                                            }

                                            ZsIconButton(
                                                onClick = { isJoiningWatchParty = false },
                                                icon = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                variant = ZsIconButtonVariant.Ghost,
                                                containerSize = 24.dp,
                                                iconSize = 14.dp
                                            )
                                        }
                                    } else {
                                        Text(
                                            "Join a Watch Party",
                                            color = theme.colors.type.text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))

                val links = listOf(
                    Icons.Default.Code to Urls.APP_GITHUB_REPO,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    links.forEach { (icon, url) ->
                        var isLinkFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(
                            visible = isLinkFocused,
                            shape = CircleShape,
                            outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                            gap = 2.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.background.secondary)
                                    .border(
                                        1.dp,
                                        theme.colors.type.divider.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                                    .onFocusChanged { isLinkFocused = it.isFocused }
                                    .clickable { uriHandler.openUri(Urls.APP_GITHUB_REPO) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Code, null, tint = theme.colors.type.secondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Box(Modifier
                .width(384.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(theme.colors.modal.background)
                .padding(vertical = 8.dp)) {
                Column {
                    if (session != null) {
                        val displayName = session.nickname.ifBlank { session.deviceName.ifBlank { "Synced" } }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = theme.colors.type.success, modifier = Modifier.size(18.dp))
                            Text("Synced: $displayName", color = theme.colors.type.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        SandwichItem(
                            Icons.Default.Star, "Sync to Cloud", theme = theme, tint = theme.colors.global.accentA,
                        ) {
                            nav.navigate("login")
                            onDismiss()
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))

                    SandwichItem(Icons.Default.Settings, "Settings", theme = theme) { nav.navigate("settings"); onDismiss() }
                    SandwichItem(Icons.Default.History, "Watch History", theme = theme) { nav.navigate("watchHistory"); onDismiss() }
                    SandwichItem(Icons.Default.Download, "Downloads", theme = theme) { nav.navigate("downloads"); onDismiss() }
                    SandwichItem(Icons.Default.Notifications, if (unreadCount > 0) "Notifications ($unreadCount)" else "Notifications", theme = theme) {
                        onNotifications()
                        onDismiss()
                    }
                    if (showHeaderActions) {
                        SandwichItem(ImageVector.vectorResource(R.drawable.ic_discord), "Discord", theme = theme) { onDiscord(); onDismiss() }
                        SandwichItem(Icons.Default.AttachMoney, "Tip Jar", theme = theme) { onTipJar(); onDismiss() }
                    }
                    SandwichItem(Icons.Default.Explore, "Discover", theme = theme) { nav.navigate("search"); onDismiss() }
                    if (watchPartyEnabled) {
                        val watchPartyHostGraceDeadlineMs by vm.watchPartyHostGraceDeadlineMs.collectAsState()
                        WatchPartyActiveItem(
                            roomCode = watchPartyRoomCode,
                            isOffline = watchPartyIsOffline,
                            hostGraceDeadlineMs = watchPartyHostGraceDeadlineMs,
                            onJump = { vm.joinWatchParty(watchPartyRoomCode ?: "") },
                            onLeave = { vm.leaveWatchParty() },
                            theme = theme
                        )
                    } else {
                        if (isJoiningWatchParty) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.Group, null, tint = theme.colors.type.secondary, modifier = Modifier.size(18.dp))
                                BasicTextField(
                                    value = watchPartyCode,
                                    onValueChange = { input ->
                                        watchPartyCode = input.uppercase().filter { it.isLetterOrDigit() }.take(6)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(theme.colors.background.secondary, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .focusRequester(focusRequester),
                                    textStyle = TextStyle(color = theme.colors.type.emphasis, fontSize = 14.sp),
                                    cursorBrush = SolidColor(theme.colors.global.accentA),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = {
                                        if (watchPartyCode.length >= 4) {
                                            vm.joinWatchParty(watchPartyCode)
                                            onDismiss()
                                        }
                                    }),
                                    decorationBox = { input ->
                                        if (watchPartyCode.isEmpty()) {
                                            Text("Watch Party code", color = theme.colors.type.dimmed, fontSize = 14.sp)
                                        }
                                        input()
                                    },
                                )
                                IconButton(
                                    onClick = {
                                        if (watchPartyCode.length >= 4) {
                                            vm.joinWatchParty(watchPartyCode)
                                            onDismiss()
                                        }
                                    },
                                    enabled = watchPartyCode.length >= 4,
                                ) {
                                    Icon(Icons.Default.ArrowForward, "Join Watch Party")
                                }
                            }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        } else {
                            SandwichItem(Icons.Default.Group, "Join a Watch Party", theme = theme) {
                                isJoiningWatchParty = true
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = theme.colors.type.divider.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.colors.background.secondary)
                                .border(
                                    1.dp,
                                    theme.colors.type.divider.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clickable { uriHandler.openUri(Urls.APP_GITHUB_REPO) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Code, null, tint = theme.colors.type.secondary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun WatchPartyActiveItem(
    roomCode: String?,
    isOffline: Boolean,
    hostGraceDeadlineMs: Long?,
    onJump: () -> Unit,
    onLeave: () -> Unit,
    theme: com.zstream.android.theme.ZStreamTheme,
) {
    val hostGraceProgress by produceState(initialValue = 0f, key1 = hostGraceDeadlineMs) {
        while (hostGraceDeadlineMs != null) {
            val remaining = (hostGraceDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
            value = (remaining.toFloat() / 5000f).coerceIn(0f, 1f)
            if (remaining == 0L) break
            delay(100L)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.colors.background.secondary.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Group,
            null,
            tint = if (isOffline) theme.colors.type.danger else theme.colors.type.success,
            modifier = Modifier.size(18.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Watch Party Active",
                color = theme.colors.type.emphasis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    isOffline -> "Connection lost"
                    hostGraceDeadlineMs != null -> "Host reconnect window active"
                    else -> "Room: ${roomCode ?: "..."}"
                },
                color = if (isOffline) theme.colors.type.danger else theme.colors.type.secondary,
                fontSize = 12.sp
            )
            if (hostGraceDeadlineMs != null) {
                LinearProgressIndicator(
                    progress = { hostGraceProgress },
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(),
                    color = theme.colors.buttons.secondary,
                    trackColor = theme.colors.background.main.copy(alpha = 0.4f)
                )
            }
        }

        ZsIconButton(
            onClick = onJump,
            icon = Icons.Default.PlayArrow,
            contentDescription = "Jump to Host",
            variant = ZsIconButtonVariant.Ghost,
            containerSize = 32.dp,
            iconSize = 18.dp
        )

        ZsIconButton(
            onClick = onLeave,
            icon = Icons.Default.Logout,
            contentDescription = "Leave",
            variant = ZsIconButtonVariant.Ghost,
            containerSize = 32.dp,
            iconSize = 18.dp
        )
    }
}

@Composable
private fun SandwichItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    theme: com.zstream.android.theme.ZStreamTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current

    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(10.dp),
        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
        gap = 2.dp,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused && isTv) theme.colors.background.secondary.copy(alpha = 0.4f) else Color.Transparent)
                .onFocusChanged { isFocused = it.isFocused }
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = tint ?: theme.colors.type.secondary, modifier = Modifier.size(18.dp))
            Text(label, color = tint ?: theme.colors.type.text, fontSize = 14.sp,
                fontWeight = if (tint != null) FontWeight.SemiBold else FontWeight.Normal)
        }
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
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
    var selectedNotif by remember { mutableStateOf<NotificationItem?>(null) }

    val isTv = LocalIsTv.current
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) {
            try {
                closeFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isTv) Modifier.width(480.dp) else Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                .heightIn(max = if (isTv) 400.dp else 500.dp)
                .clip(RoundedCornerShape(if (isTv) 16.dp else 20.dp))
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
                Column(modifier = Modifier.padding(if (isTv) 16.dp else 20.dp)) {
                    // Header: title left, actions right, all aligned center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Notifications",
                            color = theme.colors.type.emphasis,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTv) 14.sp else 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (notifications.any { it.guid !in readGuids }) {
                            var isMarkAllFocused by remember { mutableStateOf(false) }
                                    ZsOutlinedWrapper(
                                        visible = isMarkAllFocused && LocalIsTv.current,
                                        shape = RoundedCornerShape(4.dp),
                                        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                                        gap = 2.dp
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .onFocusChanged { isMarkAllFocused = it.isFocused }
                                                .focusProperties {
                                                    if (isTv) {
                                                        left = FocusRequester.Cancel
                                                        right = FocusRequester.Cancel
                                                    }
                                                }
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
                        }
                        ZsIconButton(
                            onClick = onDismiss,
                            icon = Icons.Default.Close,
                            contentDescription = null,
                            variant = ZsIconButtonVariant.Overlay,
                            containerSize = 32.dp,
                            iconSize = 16.dp,
                            modifier = Modifier.focusRequester(closeFocusRequester)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (notifications.isEmpty()) {
                        Box(Modifier
                            .fillMaxWidth()
                            .height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No notifications", color = theme.colors.type.dimmed, fontSize = 13.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
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
    modifier: Modifier = Modifier,
) {
    val categoryColor = notificationCategoryColor(theme, notif.category)
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }

    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(12.dp),
        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
        gap = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.colors.background.secondary.copy(alpha = 0.4f))
                .border(
                    1.dp,
                    theme.colors.type.divider.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!isRead) {
                    Box(Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(categoryColor))
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
    val categoryColor = notificationCategoryColor(theme, notif.category)
    val uriHandler = LocalUriHandler.current

    val isTv = LocalIsTv.current
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(notif) {
        if (isTv) {
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ZsIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                variant = ZsIconButtonVariant.Overlay,
                containerSize = 32.dp,
                iconSize = 16.dp,
                modifier = Modifier.focusRequester(backFocusRequester)
            )
            Spacer(Modifier.width(8.dp))
            Text("Notification", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            ZsIconButton(
                onClick = onDismiss,
                icon = Icons.Default.Close,
                contentDescription = null,
                variant = ZsIconButtonVariant.Overlay,
                containerSize = 32.dp,
                iconSize = 16.dp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (notif.category.isNotEmpty()) {
                    Text("•", color = theme.colors.type.secondary)
                }
                Text(
                    formatDate(notif.pubDate),
                    color = theme.colors.type.secondary,
                    fontSize = 11.sp,
                )
            }
            Text(notif.title, color = theme.colors.type.emphasis, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                notif.description,
                color = theme.colors.type.text, fontSize = 13.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isRead) {
                    var isMarkReadFocused by remember { mutableStateOf(false) }
                    ZsOutlinedWrapper(
                        visible = isMarkReadFocused && LocalIsTv.current,
                        shape = RoundedCornerShape(10.dp),
                        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                        gap = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.background.secondary.copy(alpha = 0.65f))
                                .border(
                                    1.dp,
                                    theme.colors.type.divider.copy(alpha = 0.3f),
                                    RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isMarkReadFocused = it.isFocused }
                                .focusProperties {
                                    if (isTv) {
                                        left = FocusRequester.Cancel
                                        right = FocusRequester.Cancel
                                    }
                                }
                                .clickable(onClick = onMarkRead)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Mark as read",
                                color = theme.colors.type.secondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (notif.link.isNotBlank()) {
                    var isOpenLinkFocused by remember { mutableStateOf(false) }
                    ZsOutlinedWrapper(
                        visible = isOpenLinkFocused && LocalIsTv.current,
                        shape = RoundedCornerShape(10.dp),
                        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                        gap = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                                .border(
                                    1.dp,
                                    theme.colors.type.divider.copy(alpha = 0.3f),
                                    RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isOpenLinkFocused = it.isFocused }
                                .focusProperties {
                                    if (isTv) {
                                        left = FocusRequester.Cancel
                                        right = FocusRequester.Cancel
                                    }
                                }
                                .clickable { uriHandler.openUri(notif.link) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Open link",
                                color = theme.colors.global.accentA,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isRead) "Read notification" else "Unread notification",
                color = theme.colors.type.dimmed, fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun TipJarDialog(onDismiss: () -> Unit) {
    val theme = LocalZStreamTheme.current
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
    val clipboard = LocalClipboardManager.current

    val isTv = LocalIsTv.current
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) {
            try {
                closeFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

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
                .then(if (isTv) Modifier.width(440.dp) else Modifier.fillMaxWidth())
                .heightIn(max = if (isTv) 400.dp else 500.dp)
                .clip(RoundedCornerShape(if (isTv) 16.dp else 20.dp))
                .background(theme.colors.modal.background)
                .padding(if (isTv) 16.dp else 20.dp),
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Tip Jar", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = if (isTv) 14.sp else 15.sp)
                    ZsIconButton(
                        onClick = onDismiss,
                        icon = Icons.Default.Close,
                        contentDescription = null,
                        variant = ZsIconButtonVariant.Ghost,
                        containerSize = 24.dp,
                        iconSize = 16.dp,
                        modifier = Modifier.focusRequester(closeFocusRequester)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "zstream is free and 99.9% ad-free. If you'd like to support hosting + the server bill, we would love your support on any amount to one of the addresses below. Tap an address to copy it.",
                    color = theme.colors.type.text, fontSize = 12.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    addresses.forEach { crypto ->
                        var isAddressFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(
                            visible = isAddressFocused && LocalIsTv.current,
                            shape = RoundedCornerShape(12.dp),
                            outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                            gap = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.colors.background.secondary.copy(alpha = 0.5f))
                                    .border(
                                        1.dp,
                                        theme.colors.type.divider.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .onFocusChanged { isAddressFocused = it.isFocused }
                                    .focusProperties {
                                        if (isTv) {
                                            left = FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                        }
                                    }
                                    .clickable { clipboard.setText(AnnotatedString(crypto.address)) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(crypto.color.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(crypto.symbol, color = crypto.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(crypto.name, color = theme.colors.type.emphasis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        crypto.address,
                                        color = theme.colors.type.dimmed, fontSize = 10.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text("Copy", color = theme.colors.global.accentA, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
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

private enum class FeaturedCarouselButtonFocus {
    Play,
    MoreInfo,
}

private data class PendingFeaturedCarouselFocus(
    val page: Int,
    val button: FeaturedCarouselButtonFocus,
)

private data class TvHomeScrollTarget(
    val requestId: Int,
    val itemIndex: Int,
    val scrollOffset: Int,
    val reason: String,
)

@Composable
private fun FeaturedCarousel(
    media: List<Media>,
    nav: NavController,
    progressMap: Map<String, ProgressEntity> = emptyMap(),
    showImageLogos: Boolean = true,
    isSearching: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    playButtonFocusRequester: FocusRequester? = null
) {
    val theme = LocalZStreamTheme.current
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
    val pagerState = rememberPagerState(pageCount = { media.size })
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val updateActivity = { lastInteractionTime = System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) updateActivity()
    }

    var isFocused by remember { mutableStateOf(false) }
    var focusedButtonType by remember { mutableStateOf(FeaturedCarouselButtonFocus.Play) }
    var pendingButtonFocus by remember { mutableStateOf<PendingFeaturedCarouselFocus?>(null) }

    fun moveFeaturedPage(targetPage: Int, focusTarget: FeaturedCarouselButtonFocus) {
        if (targetPage !in media.indices) return
        updateActivity()
        pendingButtonFocus = PendingFeaturedCarouselFocus(
            page = targetPage,
            button = focusTarget,
        )
        Log.d(
            HOME_FOCUS_DEBUG_TAG,
            "FEATURED manual page move ${pagerState.currentPage} -> $targetPage focusTarget=$focusTarget"
        )
        scope.launch {
            pagerState.animateScrollToPage(targetPage)
            if (pendingButtonFocus?.page == targetPage) {
                pendingButtonFocus = null
            }
        }
    }

    LaunchedEffect(media, isSearching, isDragged, isFocused, lastInteractionTime) {
        if (media.isEmpty() || isSearching || isDragged) {
            Log.d(HOME_FOCUS_DEBUG_TAG, "FEATURED autoScroll inactive: empty=${media.isEmpty()} searching=$isSearching dragged=$isDragged")
            return@LaunchedEffect
        }

        while (true) {
            val waitTime = if (isFocused) 10000L else 6000L
            Log.d(HOME_FOCUS_DEBUG_TAG, "FEATURED autoScroll cycle start: isFocused=$isFocused waitTime=$waitTime page=${pagerState.currentPage}")
            
            delay(waitTime)

            val next = (pagerState.currentPage + 1) % media.size
            Log.d(HOME_FOCUS_DEBUG_TAG, "FEATURED autoScroll TRIGGER: to=$next isFocused=$isFocused")

            if (isFocused) {
                pendingButtonFocus = PendingFeaturedCarouselFocus(
                    page = next,
                    button = focusedButtonType
                )
            }
            pagerState.animateScrollToPage(next)
            lastInteractionTime = System.currentTimeMillis() // Reset timer after auto-scroll
        }
    }

    val scrollProgress by produceState(initialValue = 0f, isFocused, lastInteractionTime, media) {
        if (media.isEmpty()) { value = 0f; return@produceState }
        val waitTime = if (isFocused) 10000L else 6000L
        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastInteractionTime
            value = (elapsed.toFloat() / waitTime).coerceIn(0f, 1f)
            delay(16)
        }
    }

    val isTv = LocalIsTv.current
    val height by animateDpAsState(
        targetValue = if (isSearching) 170.dp else if (isTv) TvHomeMetrics.featuredHeight else 450.dp,
        label = "carouselHeight"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        label = "contentAlpha"
    )
    val carouselOffset by animateDpAsState(
        targetValue = if (isSearching) (-200).dp else 0.dp,
        label = "carouselOffset"
    )
    val blurRadius by animateDpAsState(
        targetValue = if (isSearching) 16.dp else 0.dp,
        label = "carouselBlur"
    )

    Box(
        modifier = modifier
            .height(height)
            .onFocusChanged {
                Log.d(
                    HOME_FOCUS_DEBUG_TAG,
                    "FEATURED root focus hasFocus=${it.hasFocus} isFocused=${it.isFocused} before rootFocused=$isFocused type=$focusedButtonType page=${pagerState.currentPage}"
                )
                if (it.hasFocus != isFocused) {
                    updateActivity()
                }
                isFocused = it.hasFocus
            }
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = carouselOffset.toPx()
                }
                .blur(blurRadius),
            key = { media[it].id }
        ) { page ->
            val current = media[page]
            val backdropUrl = current.backdropUrl()
            val title = current.displayTitle
            val year = current.displayDate.take(4)
            val type = current.type
            var itemPlayButtonFocused by remember { mutableStateOf(false) }
            var itemMoreInfoButtonFocused by remember { mutableStateOf(false) }
            val itemPlayButtonFocusRequester = remember(page) { FocusRequester() }
            val effectivePlayRequester = if (page == 0 && playButtonFocusRequester != null) playButtonFocusRequester else itemPlayButtonFocusRequester
            val moreInfoButtonFocusRequester = remember(page) { FocusRequester() }

            LaunchedEffect(pagerState.currentPage, pendingButtonFocus, page) {
                val pendingFocus = pendingButtonFocus ?: return@LaunchedEffect
                if (pendingFocus.page != page || pagerState.currentPage != page) return@LaunchedEffect

                withFrameMillis { }
                when (pendingFocus.button) {
                    FeaturedCarouselButtonFocus.Play -> effectivePlayRequester.requestFocus()
                    FeaturedCarouselButtonFocus.MoreInfo -> moreInfoButtonFocusRequester.requestFocus()
                }
                pendingButtonFocus = null
            }

            Box(Modifier.fillMaxSize()) {
                // Background image
                Box(Modifier.fillMaxSize()) {
                    if (backdropUrl != null) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxSize()
                                .scale(1.0f),
                            contentScale = ContentScale.Crop
                        )
                        // Global scrim for baseline contrast
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(theme.colors.video.context.background.copy(alpha = 0.15f))
                        )
                    } else {
                        Box(Modifier
                            .fillMaxSize()
                            .background(theme.colors.background.secondary))
                    }
                }

                // Per-item gradients for smooth transitions
                if (!LocalIsTv.current) {
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
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = contentAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    theme.colors.background.main.copy(alpha = 0.5f),
                                    theme.colors.background.main
                                ),
                            )
                        )
                )

                // Content overlay
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = TvHomeMetrics.screenPadding,
                            end = TvHomeMetrics.screenPadding,
                            bottom = if (isTv) 40.dp else 50.dp
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // Title
                        current.logoUrl()?.takeIf { showImageLogos }?.let { logo ->
                            Box(
                                Modifier
                                    .height(if (isTv) 60.dp else 80.dp)
                                    .fillMaxWidth(0.7f)
                                    .padding(bottom = if (isTv) 8.dp else 8.dp)
                                    .graphicsLayer { alpha = contentAlpha }
                            ) {
                                AsyncImage(
                                    model = logo,
                                    contentDescription = title,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.BottomStart,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } ?: Text(
                            title,
                            color = theme.colors.type.emphasis,
                            fontSize = if (isTv) 28.sp else 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                            style = LocalTextStyle.current.copy(
                                shadow = featuredCarouselTextShadow(strong = true)
                            )
                        )

                        // Metadata row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                        ) {
                            // TMDB Rating
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.tmdb_logo),
                                    contentDescription = "TMDB Logo",
                                    modifier = Modifier.size(if (isTv) 18.dp else 22.dp)
                                )
                                Text(
                                    String.format(Locale.US, "%.1f", current.voteAverage ?: 0.0),
                                    color = Color.White,
                                    fontSize = if (isTv) 10.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(shadow = featuredCarouselTextShadow())
                                )
                            }

                            if (year.isNotEmpty()) {
                                Text("•", color = Color.White.copy(alpha = 0.7f))
                                Text(
                                    year,
                                    color = Color.White,
                                    fontSize = if (isTv) 10.sp else 11.sp,
                                    style = LocalTextStyle.current.copy(shadow = featuredCarouselTextShadow())
                                )
                            }

                            val typeLabel = if (type == "tv") "TV Show" else "Movie"
                            Text("•", color = Color.White.copy(alpha = 0.7f))
                            Text(
                                typeLabel,
                                color = Color.White,
                                fontSize = if (isTv) 10.sp else 11.sp,
                                style = LocalTextStyle.current.copy(shadow = featuredCarouselTextShadow())
                            )
                        }

                        // Overview or movie/show description
                        current.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                overview,
                                color = Color.White,
                                fontSize = if (isTv) 13.sp else 14.sp,
                                maxLines = if (isTv) 2 else 3,
                                lineHeight = if (isTv) 18.sp else 20.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth(if (isTv) 0.5f else 1f)
                                    .graphicsLayer { alpha = contentAlpha },
                                style = LocalTextStyle.current.copy(shadow = featuredCarouselTextShadow())
                            )
                        }

                        Spacer(Modifier.height(if (isTv) 12.dp else 20.dp))

                        // Buttons Row
                        Column(
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Play Now button
                                ZsOutlinedWrapper(
                                    visible = itemPlayButtonFocused,
                                    shape = RoundedCornerShape(10.dp),
                                    outlineColor = Color.White,
                                    gap = 2.dp,
                                    modifier = if (isTv) Modifier else Modifier.weight(1f)
                                ) {
                                    Button(
                                        onClick = {
                                            val progress = progressMap[current.id.toString()]
                                            val sParam = if (type == "tv") {
                                                val sNum = progress?.seasonNumber ?: 1
                                                val eNum = progress?.episodeNumber ?: 1
                                                "&season=$sNum&episode=$eNum"
                                            } else ""

                                            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                            val poster = java.net.URLEncoder.encode(current.posterPath ?: "", "UTF-8")
                                            updateActivity()
                                            nav.navigate("player/$type/${current.id}?title=$encodedTitle&year=${year.toIntOrNull() ?: 0}&poster=$poster$sParam")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = theme.colors.buttons.primary,
                                            contentColor = theme.colors.buttons.primaryText,
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            theme.colors.type.divider.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .height(if (isTv) 40.dp else 48.dp)
                                            .then(if (isTv) Modifier.widthIn(min = 140.dp) else Modifier.fillMaxWidth())
                                            .focusRequester(effectivePlayRequester)
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                                    if (pagerState.currentPage > 0) {
                                                        moveFeaturedPage(
                                                            targetPage = pagerState.currentPage - 1,
                                                            focusTarget = FeaturedCarouselButtonFocus.MoreInfo,
                                                        )
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .onFocusChanged {
                                                Log.d(
                                                    HOME_FOCUS_DEBUG_TAG,
                                                    "FEATURED Play button focus isFocused=${it.isFocused} hasFocus=${it.hasFocus} before play=$itemPlayButtonFocused more=$itemMoreInfoButtonFocused page=${pagerState.currentPage}"
                                                )
                                                if (it.isFocused) {
                                                    updateActivity()
                                                    focusedButtonType = FeaturedCarouselButtonFocus.Play
                                                    Log.d(
                                                        HOME_FOCUS_DEBUG_TAG,
                                                        "FEATURED Play button -> onFocused callback"
                                                    )
                                                    onFocused()
                                                }
                                                itemPlayButtonFocused = it.isFocused
                                            },
                                        contentPadding = PaddingValues(horizontal = 16.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(if (isTv) 18.dp else 20.dp), tint = theme.colors.buttons.primaryText)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Play Now", fontSize = if (isTv) 14.sp else 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // More Info button
                                ZsOutlinedWrapper(
                                    visible = itemMoreInfoButtonFocused,
                                    shape = RoundedCornerShape(10.dp),
                                    outlineColor = Color.White,
                                    gap = 2.dp,
                                    modifier = if (isTv) Modifier else Modifier.weight(1f)
                                ) {
                                    Button(
                                        onClick = {
                                            updateActivity()
                                            nav.navigate("detail/$type/${current.id}")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = theme.colors.buttons.secondary,
                                            contentColor = theme.colors.buttons.secondaryText,
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            theme.colors.type.divider.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .height(if (isTv) 40.dp else 48.dp)
                                            .then(if (isTv) Modifier.widthIn(min = 140.dp) else Modifier.fillMaxWidth())
                                            .focusRequester(moreInfoButtonFocusRequester)
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                                    if (pagerState.currentPage < media.lastIndex) {
                                                        moveFeaturedPage(
                                                            targetPage = pagerState.currentPage + 1,
                                                            focusTarget = FeaturedCarouselButtonFocus.Play,
                                                        )
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .onFocusChanged {
                                                Log.d(
                                                    HOME_FOCUS_DEBUG_TAG,
                                                    "FEATURED More Info button focus isFocused=${it.isFocused} hasFocus=${it.hasFocus} before play=$itemPlayButtonFocused more=$itemMoreInfoButtonFocused page=${pagerState.currentPage}"
                                                )
                                                if (it.isFocused) {
                                                    updateActivity()
                                                    focusedButtonType = FeaturedCarouselButtonFocus.MoreInfo
                                                    Log.d(
                                                        HOME_FOCUS_DEBUG_TAG,
                                                        "FEATURED More Info button -> onFocused callback"
                                                    )
                                                    onFocused()
                                                }
                                                itemMoreInfoButtonFocused = it.isFocused
                                            },
                                        contentPadding = PaddingValues(horizontal = 16.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(if (isTv) 18.dp else 20.dp), tint = theme.colors.buttons.secondaryText)
                                        Spacer(Modifier.width(8.dp))
                                        Text("More Info", fontSize = if (isTv) 14.sp else 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (isTv) {
                                LinearProgressIndicator(
                                    progress = { scrollProgress },
                                    modifier = Modifier
                                        .width(300.dp)
                                        .height(2.dp)
                                        .clip(CircleShape),
                                    color = Color.White.copy(alpha = 0.8f),
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navigation dots
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp) // Slightly less bottom padding to account for hit target padding
                .graphicsLayer { alpha = contentAlpha },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            media.forEachIndexed { i, _ ->
                val active = i == pagerState.currentPage
                val dotWidth by animateDpAsState(if (active) 18.dp else 8.dp, label = "dotWidth")
                val dotAlpha by animateFloatAsState(if (active) 1f else 0.4f, label = "dotAlpha")

                Box(
                    modifier = Modifier
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            Log.d(
                                HOME_FOCUS_DEBUG_TAG,
                                "FEATURED dot clicked index=$i currentPage=${pagerState.currentPage}"
                            )
                            updateActivity()
                            scope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(width = dotWidth, height = 8.dp)
                            .clip(CircleShape)
                            .background(theme.colors.type.emphasis.copy(alpha = dotAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchOverlay(
    searchQuery: String,
    onSearch: (String) -> Unit,
    isSearching: Boolean,
    onSearchFocusedChange: (Boolean) -> Unit,
    onClearFocus: () -> Unit,
    focusRequester: FocusRequester,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var focusedMenu by remember { mutableStateOf(false) }
    val focusMenuWidth by animateDpAsState(if (focusedMenu) 3.dp else 0.dp)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .animateContentSize()
                .then(if (isSearching) Modifier.weight(1f) else Modifier.size(44.dp))
                .height(44.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(RoundedCornerShape(44.dp))
                .background(theme.colors.background.secondary.copy(alpha = 0.8f))
                .border(
                    1.dp,
                    theme.colors.type.divider.copy(alpha = 0.3f),
                    RoundedCornerShape(44.dp)
                )
                .clickable(enabled && !isSearching) { onSearchFocusedChange(true) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Search, null, tint = theme.colors.type.secondary, modifier = Modifier.size(18.dp))

                if (isSearching) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(placeholder, color = theme.colors.search.placeholder, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearch,
                            enabled = enabled,
                            singleLine = true,
                            textStyle = TextStyle(color = theme.colors.search.text, fontSize = 13.sp),
                            cursorBrush = SolidColor(theme.colors.global.accentA),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onClearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onSearchFocusedChange(true)
                                    }
                                },
                        )
                    }
                    if (!isTv && searchQuery.isNotEmpty()) {
                        ZsIconButton(
                            onClick = { onSearch("") },
                            icon = Icons.Default.Close,
                            contentDescription = "Clear search",
                            variant = ZsIconButtonVariant.Ghost,
                            containerSize = 28.dp,
                            iconSize = 16.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHomeScreenContent(
    state: HomeState,
    nav: NavController,
    vm: HomeViewModel,
    theme: com.zstream.android.theme.ZStreamTheme,
    accountVm: AccountViewModel,
    session: com.zstream.android.data.AccountSession?,
    notifications: List<NotificationItem>,
    readGuids: Set<String>,
    unreadCount: Int,
    sectionOrder: List<String>,
    showContinueWatching: Boolean,
    showBookmarks: Boolean,
    searchResults: List<Media>,
    placeholder: String,
    onShowNotifications: () -> Unit,
    onShowTipJar: () -> Unit,
    onShowLayout: () -> Unit,
    onShowMenu: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    isPipActive: Boolean,
    isPipDrawerExpanded: Boolean,
    onPipDrawerExpandedChange: (Boolean) -> Unit,
) {
    val userSections = remember(state.continueWatching, state.bookmarks, showContinueWatching, showBookmarks) {
        buildList {
            if (showContinueWatching) addAll(state.continueWatching.filter { it.items.isNotEmpty() })
            if (showBookmarks) addAll(state.bookmarks.filter { it.items.isNotEmpty() })
        }
    }
    val showContinueWatchingLoading = showContinueWatching && state.continueWatchingLoading && state.continueWatching.isEmpty()
    val showBookmarksLoading = showBookmarks && state.bookmarksLoading && state.bookmarks.isEmpty()
    val discoverSections = remember(state.activeTab, state.movieSections, state.tvSections, state.editorSections, userSections) {
        val seenIds = userSections.flatMapTo(mutableSetOf()) { section -> section.items.map { it.id } }
        buildList {
            val base = when (state.activeTab) {
                HomeTab.MOVIES -> state.movieSections
                HomeTab.TV -> state.tvSections
                HomeTab.EDITOR -> state.editorSections
            }
            base.forEach { section ->
                val unique = section.items.filter { it.id !in seenIds }.take(20)
                if (unique.isNotEmpty()) {
                    add(section.copy(items = unique))
                    unique.forEach { seenIds.add(it.id) }
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val gridPages = remember { mutableStateMapOf<String, Int>() }
    var isCarouselFocused by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isTopBarFocused by remember { mutableStateOf(false) }
    var listHasFocus by remember { mutableStateOf(false) }
    val isFeaturedActive = state.enableFeatured && state.featuredMedia.isNotEmpty()
    val startIndexOffset = if (isFeaturedActive) {
        1
    } else if (!state.enableFeatured || state.featuredMedia.isEmpty()) {
        3 // Spacer(56.dp) + HeroSection + Spacer(12.dp)
    } else {
        1
    }
    val density = LocalDensity.current
    val topPaddingPx = remember(density) { with(density) { TvHomeMetrics.topBarHeight.roundToPx() } }

    val carouselFocusRequester = remember { FocusRequester() }
    val carouselPlayButtonRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val topBarFocusRequester = remember { FocusRequester() }
    val sandwichFocusRequester = remember { FocusRequester() }
    val homeContentFocusRequester = remember { FocusRequester() }
    var wasPipDrawerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isPipDrawerExpanded) {
        if (wasPipDrawerExpanded && !isPipDrawerExpanded) {
            withFrameMillis { }
            homeContentFocusRequester.requestFocus()
        }
        wasPipDrawerExpanded = isPipDrawerExpanded
    }

    LaunchedEffect(Unit) {
        if (state.loading || state.initialFocusRequested) return@LaunchedEffect
        delay(600) // Wait for content and layout to settle
        if (isFeaturedActive) {
            carouselPlayButtonRequester.requestFocus()
        } else {
            searchBarFocusRequester.requestFocus()
        }
        vm.markFocusRequested()
    }

    var tvScrollRequestId by remember { mutableStateOf(0) }
    var tvScrollTarget by remember { mutableStateOf<TvHomeScrollTarget?>(null) }

    fun requestTvHomeScroll(
        itemIndex: Int,
        scrollOffset: Int,
        reason: String,
    ) {
        tvScrollRequestId += 1
        tvScrollTarget = TvHomeScrollTarget(
            requestId = tvScrollRequestId,
            itemIndex = itemIndex,
            scrollOffset = scrollOffset,
            reason = reason,
        )

        Log.d(
            HOME_FOCUS_DEBUG_TAG,
            "TV scroll request id=$tvScrollRequestId item=$itemIndex offset=$scrollOffset reason=$reason currentIndex=${listState.firstVisibleItemIndex} currentOffset=${listState.firstVisibleItemScrollOffset}"
        )
    }

    LaunchedEffect(tvScrollTarget) {
        val target = tvScrollTarget ?: return@LaunchedEffect
        Log.d(
            HOME_FOCUS_DEBUG_TAG,
            "TV scroll start id=${target.requestId} item=${target.itemIndex} offset=${target.scrollOffset} reason=${target.reason}"
        )
        listState.animateScrollToItem(target.itemIndex, target.scrollOffset)
        Log.d(
            HOME_FOCUS_DEBUG_TAG,
            "TV scroll done id=${target.requestId} item=${target.itemIndex} offset=${target.scrollOffset} reason=${target.reason} finalIndex=${listState.firstVisibleItemIndex} finalOffset=${listState.firstVisibleItemScrollOffset}"
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(homeContentFocusRequester)
                .focusRestorer()
        ) {
        HomeBringIntoViewProvider(
            label = "TV",
            disableAutoScroll = isCarouselFocused,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .onFocusChanged {
                        Log.d(
                            HOME_FOCUS_DEBUG_TAG,
                            "TV LazyColumn focus hasFocus=${it.hasFocus} isFocused=${it.isFocused} before listHasFocus=$listHasFocus carousel=$isCarouselFocused index=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}"
                        )
                        listHasFocus = it.hasFocus
                    },
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                if (isFeaturedActive) {
                    item {
                        FeaturedCarousel(
                            media = state.featuredMedia,
                            nav = nav,
                            progressMap = state.progressMap,
                            showImageLogos = state.enableImageLogos,
                            playButtonFocusRequester = carouselPlayButtonRequester,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(carouselFocusRequester)
                                .focusProperties { up = topBarFocusRequester }
                                .onFocusChanged {
                                    Log.d(
                                        HOME_FOCUS_DEBUG_TAG,
                                        "TV FeaturedCarousel wrapper focus hasFocus=${it.hasFocus} isFocused=${it.isFocused} before carousel=$isCarouselFocused list=$listHasFocus index=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}"
                                    )
                                    isCarouselFocused = it.hasFocus
                                    if (it.hasFocus) {
                                        requestTvHomeScroll(0, 0, "featured-wrapper-focus")
                                    }
                                },
                            onFocused = {
                                Log.d(
                                    HOME_FOCUS_DEBUG_TAG,
                                    "TV FeaturedCarousel onFocused callback -> request item 0 index=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}"
                                )
                                requestTvHomeScroll(0, 0, "featured-button-focus")
                            }
                        )
                    }
                } else {
                    item {
                        Spacer(Modifier.height(TvHomeMetrics.topBarHeight))
                    }
                }

                if (!state.enableFeatured || state.featuredMedia.isEmpty()) {
                    item {
                        HeroSection(
                            searchQuery = state.searchQuery,
                            onSearch = vm::onSearchChange,
                            nav = nav,
                            placeholder = placeholder,
                            focusRequester = searchBarFocusRequester,
                            enabled = !state.isOffline,
                            modifier = Modifier
                                .focusProperties { up = sandwichFocusRequester }
                                .onFocusChanged {
                                    isSearchFocused = it.hasFocus
                                    if (it.hasFocus) {
                                        requestTvHomeScroll(
                                            itemIndex = 1,
                                            scrollOffset = -topPaddingPx,
                                            reason = "hero-section-focus"
                                        )
                                    }
                                }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }

                if (state.searchQuery.isNotBlank()) {
                    item {
                        Text(
                            "Search results",
                            color = theme.colors.type.emphasis,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    MediaGridLazy(
                        section = searchResults,
                        nav = nav,
                        progressMap = emptyMap(),
                        numOfColumns = 6,
                    )
                    if (state.canLoadMore || searchResults.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.canLoadMore) {
                                    var isLoadMoreFocused by remember { mutableStateOf(false) }
                                    ZsOutlinedWrapper(
                                        visible = isLoadMoreFocused,
                                        shape = RoundedCornerShape(35),
                                        outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                                        gap = 2.dp
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(35))
                                                .background(
                                                    theme.colors.background.secondary.copy(
                                                        alpha = 1f
                                                    )
                                                )
                                                .border(
                                                    1.dp,
                                                    theme.colors.type.divider.copy(alpha = 1f),
                                                    RoundedCornerShape(35)
                                                )
                                                .onFocusChanged { isLoadMoreFocused = it.isFocused }
                                                .clickable { vm.searchLoadMore() }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Load More", color = theme.colors.type.emphasis, fontSize = 18.sp)
                                        }
                                    }
                                } else {
                                    Text("That's all we have...", color = theme.colors.type.secondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                } else {
                    if (showContinueWatchingLoading) {
                        item(key = "continue_watching_loading") {
                            HomeSectionLoading(
                                title = "Continue Watching",
                                modifier = Modifier.onFocusChanged {
                                    if (it.hasFocus) {
                                        requestTvHomeScroll(
                                            itemIndex = startIndexOffset,
                                            scrollOffset = -topPaddingPx,
                                            reason = "loading-focus:continue-watching",
                                        )
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(TvHomeMetrics.sectionSpacing)) }
                    }
                    if (showBookmarksLoading) {
                        item(key = "bookmarks_loading") {
                            HomeSectionLoading(title = "My Bookmarks")
                        }
                        item { Spacer(Modifier.height(TvHomeMetrics.sectionSpacing)) }
                    }
                    userSections.forEachIndexed { sectionIndex, section ->
                        val loadingOffset =
                            (if (showContinueWatchingLoading) 2 else 0) +
                                (if (showBookmarksLoading) 2 else 0)
                        val sectionItemIndex = startIndexOffset + loadingOffset + sectionIndex * 2
                        if (state.enableCarouselView) {
                            item(key = section.title) {
                                MediaCarouselSection(
                                    section,
                                    nav,
                                    state.progressMap,
                                    modifier = Modifier.onFocusChanged {
                                        if (it.hasFocus) {
                                            requestTvHomeScroll(
                                                itemIndex = sectionItemIndex,
                                                scrollOffset = -topPaddingPx,
                                                reason = "section-focus:${section.title}",
                                            )
                                        }
                                    },
                                )
                            }
                        } else {
                            MediaGridPages(
                                section = section,
                                nav = nav,
                                progressMap = state.progressMap,
                                numOfColumns = 6,
                                numOfRows = state.gridRows,
                                currentPage = gridPages[section.title] ?: 0,
                                onPageChange = { gridPages[section.title] = it },
                                onRowFocused = { rowTopPx, _ ->
                                    requestTvHomeScroll(
                                        itemIndex = sectionItemIndex,
                                        scrollOffset = rowTopPx - topPaddingPx,
                                        reason = "grid-row-focus:${section.title}",
                                    )
                                },
                            )
                        }
                        item { Spacer(Modifier.height(TvHomeMetrics.sectionSpacing)) }
                    }

                    val tabsItemIndex = startIndexOffset + userSections.size * 2
                    item {
                        HomeTabs(
                            activeTab = state.activeTab,
                            onTab = vm::setTab,
                            onFocused = {
                                requestTvHomeScroll(
                                    itemIndex = tabsItemIndex,
                                    scrollOffset = -topPaddingPx,
                                    reason = "home-tabs-focus",
                                )
                            },
                        )
                    }
                    item { Spacer(Modifier.height(TvHomeMetrics.sectionSpacing)) }

                    discoverSections.forEachIndexed { sectionIndex, section ->
                        val sectionItemIndex = tabsItemIndex + 2 + sectionIndex * 2
                        val pageKey = "discover-${section.title}"
                        item(key = pageKey) {
                            MediaCarouselSection(
                                section = section,
                                nav = nav,
                                progressMap = state.progressMap,
                                modifier = Modifier.onFocusChanged {
                                    if (it.hasFocus) {
                                        requestTvHomeScroll(
                                            itemIndex = sectionItemIndex,
                                            scrollOffset = -topPaddingPx,
                                            reason = "section-focus:${section.title}",
                                        )
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(TvHomeMetrics.sectionSpacing)) }
                    }
                }
            }
        }

        val tvLogoAlpha by animateFloatAsState(
            targetValue = if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "tvLogoAlpha",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(topBarFocusRequester)
                .onFocusChanged {
                    isTopBarFocused = it.hasFocus
                    if (it.hasFocus) {
                        requestTvHomeScroll(0, 0, "top-bar-focus")
                    }
                }
                .then(
                    Modifier.focusProperties {
                        canFocus = isCarouselFocused || isSearchFocused || isTopBarFocused
                        down = if (isFeaturedActive) carouselFocusRequester else searchBarFocusRequester
                    }
                )
        ) {
            TvTopBar(
                hazeState = hazeState,
                unreadCount = unreadCount,
                collapseActionsIntoMenu = state.enableFeatured,
                onNotifications = onShowNotifications,
                onTipJar = onShowTipJar,
                onLayout = onShowLayout,
                onMenu = onShowMenu,
                onSearch = { nav.navigate("search") },
                sandwichFocusRequester = sandwichFocusRequester,
                logoAlpha = tvLogoAlpha,
            )
        }
        }

        if (isPipActive && !isPipDrawerExpanded) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(96.dp)
                        .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                        .background(theme.colors.background.secondary.copy(alpha = 0.78f))
                        .border(
                            1.dp,
                            theme.colors.type.divider.copy(alpha = 0.45f),
                            RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                        )
                        .onFocusChanged { if (it.isFocused) onPipDrawerExpandedChange(true) }
                        .clickable { onPipDrawerExpandedChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        "PiP controls",
                        tint = theme.colors.type.emphasis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TvPipDrawerPanel(
    onOpenPip: () -> Unit,
    onMovePip: () -> Unit,
    isPipPlaying: Boolean,
    onTogglePipPlayback: () -> Unit,
    onClosePip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalZStreamTheme.current
    val firstRowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { firstRowFocusRequester.requestFocus() }
    BackHandler(onBack = onDismiss)

    Box(modifier) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .background(theme.colors.background.secondary.copy(alpha = 0.94f))
                    .border(
                        1.dp,
                        theme.colors.type.divider.copy(alpha = 0.5f),
                        RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                    )
                    .padding(12.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Picture in picture",
                    color = theme.colors.type.emphasis,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                TvPipDrawerRow("Open player", Icons.Default.OpenInFull, onOpenPip, Modifier.focusRequester(firstRowFocusRequester))
                TvPipDrawerRow(
                    if (isPipPlaying) "Pause" else "Play",
                    if (isPipPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onTogglePipPlayback,
                )
                TvPipDrawerRow("Move PiP", Icons.Default.SwapHoriz, onMovePip)
                TvPipDrawerRow("Close PiP", Icons.Default.Close, onClosePip)
            }
    }
}

@Composable
private fun TvPipDrawerRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalZStreamTheme.current
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = focused,
        shape = RoundedCornerShape(10.dp),
        outlineColor = Color.White,
        gap = 2.dp,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) theme.colors.background.secondaryHover else Color.Transparent)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = theme.colors.type.secondary, modifier = Modifier.size(22.dp))
            Text(label, color = theme.colors.type.emphasis, fontSize = 16.sp)
        }
    }
}

@Composable
private fun TvTopBar(
    hazeState: dev.chrisbanes.haze.HazeState,
    unreadCount: Int,
    collapseActionsIntoMenu: Boolean,
    onNotifications: () -> Unit,
    onTipJar: () -> Unit,
    onLayout: () -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    sandwichFocusRequester: FocusRequester,
    logoAlpha: Float = 1f,
) {
    val theme = LocalZStreamTheme.current
    val hazeStyle = rememberTopNavHazeStyle()
    var focusedMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvHomeMetrics.screenPadding, vertical = 12.dp)
            .height(TvHomeMetrics.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = logoAlpha }
                    .clip(RoundedCornerShape(50))
                    .hazeEffect(hazeState, hazeStyle)
                    .background(theme.colors.background.secondary.copy(alpha = 0.48f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = com.zstream.android.R.mipmap.ic_launcher,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                )
                Text("Z-Stream", color = theme.colors.type.emphasis, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (collapseActionsIntoMenu) {
                TvHeaderButton(Icons.Default.Search, null, hazeState, onClick = onSearch)
            } else {
                TvHeaderButton(Icons.Default.AttachMoney, null, hazeState, onClick = onTipJar)
            }
            TvHeaderButton(Icons.Default.Download, null, hazeState, onClick = onLayout)
            ZsOutlinedWrapper(
                visible = focusedMenu && LocalIsTv.current,
                shape = RoundedCornerShape(50),
                outlineColor = Color.White,
                gap = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .height(TvHomeMetrics.headerButtonSize)
                        .clip(RoundedCornerShape(50))
                        .hazeEffect(hazeState, hazeStyle)
                        .background(theme.colors.background.secondary.copy(alpha = 0.48f))
                        .border(
                            1.dp,
                            theme.colors.type.divider.copy(alpha = 0.3f),
                            RoundedCornerShape(50)
                        )
                        .focusRequester(sandwichFocusRequester)
                        .onFocusChanged { focusedMenu = it.isFocused }
                        .clickable(onClick = onMenu)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Menu, null, tint = theme.colors.type.secondary, modifier = Modifier.size(TvHomeMetrics.headerIconSize))
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(TvHomeMetrics.headerIconSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    hazeState: dev.chrisbanes.haze.HazeState,
    onClick: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    val hazeStyle = rememberTopNavHazeStyle()
    var focused by remember { mutableStateOf(false) }

    ZsOutlinedWrapper(
        visible = focused && LocalIsTv.current,
        shape = RoundedCornerShape(50),
        outlineColor = Color.White,
        gap = 2.dp
    ) {
        Box(
            modifier = Modifier
                .size(TvHomeMetrics.headerButtonSize)
                .clip(RoundedCornerShape(50))
                .hazeEffect(hazeState, hazeStyle)
                .background(theme.colors.background.secondary.copy(alpha = 0.48f))
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(50))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box {
                Icon(icon, null, tint = theme.colors.type.secondary, modifier = Modifier.size(TvHomeMetrics.headerIconSize))
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(theme.colors.type.danger)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
