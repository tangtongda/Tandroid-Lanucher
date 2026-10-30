@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tangtongda.tlauncher.ui

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import coil.compose.AsyncImage
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.R
import com.tangtongda.tlauncher.data.AppInfo
import com.tangtongda.tlauncher.data.CatalogApp
import com.tangtongda.tlauncher.data.TvBrand
import com.tangtongda.tlauncher.data.StoreState
import com.tangtongda.tlauncher.data.Backdrop
import com.tangtongda.tlauncher.data.MediaItem
import com.tangtongda.tlauncher.data.MediaVolume
import com.tangtongda.tlauncher.data.Weather
import com.tangtongda.tlauncher.data.SystemShortcut
import com.tangtongda.tlauncher.data.defaultShortcuts
import com.tangtongda.tlauncher.ui.theme.Background
import com.tangtongda.tlauncher.ui.theme.SurfaceColor
import com.tangtongda.tlauncher.ui.theme.TLauncherTheme
import com.tangtongda.tlauncher.ui.theme.TextPrimary
import com.tangtongda.tlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Layout modelled on the tvOS home screen: full-bleed hero backdrop of the focused item,
// a translucent "dock" holding the first row, and the rest of the grid peeking below.
// Design rules from the tvOS HIG skill are cited by ID.
// Unit note: a 1080p Android TV is 960x540dp (xhdpi, 1dp = 2px); tvOS pt / 2 ≈ dp.

private const val COLUMNS = 6

// Motion, tuned to feel like Google TV: emphasized-decelerate easing for things that enter,
// springs for focus (slight overshoot instead of a linear pop).
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val FocusSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 380f)
private val SideMargin = 48.dp // DISTANCE-06 safe area (Android TV overscan)
private val TileGap = 16.dp
private val TileRadius = 14.dp
// Room for the focused tile to grow (1.12x of ~126x76dp ≈ +7dp/side) without touching the dock edge.
private val DockPadding = 18.dp
private val TileShape = RoundedCornerShape(TileRadius)

/** Everything that can sit on the home grid. */
sealed interface Tile {
    val key: String
    val label: String

    data class App(val app: AppInfo) : Tile {
        override val key: String get() = "app:${app.packageName}"
        override val label: String get() = app.label
    }

    data class Shortcut(val shortcut: SystemShortcut) : Tile {
        override val key: String get() = "sys:${shortcut.id}"
        override val label: String get() = shortcut.label
    }

    /** 系统应用: the TV maker's pre-installed extras, gathered in one folder. */
    data object SystemFolder : Tile {
        override val key: String get() = "sys:folder"
        override val label: String get() = "系统应用"
    }

    /** Live weather tile; selecting it opens the forecast. */
    data object WeatherTile : Tile {
        override val key: String get() = "sys:weather"
        override val label: String get() = "天气"
    }
}

// ---------- Stateful entry (real data) ----------

@Composable
fun HomeRoute(vm: HomeViewModel = viewModel()) {
    val apps by vm.homeApps.collectAsStateWithLifecycle()
    val folderApps by vm.folderApps.collectAsStateWithLifecycle()
    val backdrops by vm.backdrops.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val mediaPermission by vm.mediaPermission.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val weatherCity by vm.weatherCity.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val storeState by vm.storeState.collectAsStateWithLifecycle()
    val hdmiPrompt by vm.hdmiPrompt.collectAsStateWithLifecycle()
    val askMediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onMediaPermissionResult()
    }
    HomeContent(
        apps = apps,
        onAppClick = vm::launch,
        shortcuts = shortcuts,
        onShortcutClick = vm::launchShortcut,
        backdrops = backdrops,
        order = order,
        onOrderChange = vm::saveOrder,
        onUninstall = vm::uninstall,
        onAppInfo = vm::openAppInfo,
        media = media,
        mediaPermission = mediaPermission,
        onRequestMediaPermission = { askMediaPermission.launch(vm.mediaPermissions) },
        onOpenMedia = vm::openMedia,
        weather = weather,
        weatherCity = weatherCity,
        onPickWeatherCity = vm::setWeatherCity,
        folderApps = folderApps,
        brand = vm.brand,
        onSetInFolder = vm::setInFolder,
        catalog = catalog,
        storeState = storeState,
        onInstallCatalog = vm::installCatalog,
        onSettings = vm::openSettings,
    )
    hdmiPrompt?.let { input ->
        HdmiPromptDialog(input = input, onSwitch = vm::switchToHdmi, onDismiss = vm::dismissHdmi)
    }
}

/** Top navigation, modelled on Google TV's bar (minus Library). "直播" is an action, not a page. */
enum class HomeTab { Home, Media, Apps }

// ---------- Stateless UI (used by previews too) ----------

@Composable
fun HomeContent(
    apps: List<AppInfo>?,
    onAppClick: (AppInfo) -> Unit,
    shortcuts: List<SystemShortcut> = defaultShortcuts,
    onShortcutClick: (SystemShortcut) -> Unit = {},
    backdrops: Map<String, List<Backdrop>> = emptyMap(),
    order: List<String> = emptyList(),
    onOrderChange: (List<String>) -> Unit = {},
    onUninstall: (AppInfo) -> Unit = {},
    onAppInfo: (AppInfo) -> Unit = {},
    media: List<MediaVolume> = emptyList(),
    mediaPermission: Boolean = false,
    onRequestMediaPermission: () -> Unit = {},
    onOpenMedia: (MediaItem) -> Unit = {},
    weather: Weather? = null,
    weatherCity: String? = null,
    onPickWeatherCity: (String?) -> Unit = {},
    folderApps: List<AppInfo> = emptyList(),
    brand: TvBrand = TvBrand("SONY", "sony"),
    onSetInFolder: (AppInfo, Boolean) -> Unit = { _, _ -> },
    catalog: List<CatalogApp> = emptyList(),
    storeState: Map<String, StoreState> = emptyMap(),
    onInstallCatalog: (CatalogApp) -> Unit = {},
    onSettings: () -> Unit = {},
    now: LocalDateTime? = null, // fixed time for previews
) {
    var tab by remember { mutableStateOf(HomeTab.Home) }
    var menuFor by remember { mutableStateOf<Tile?>(null) } // long-press menu target
    var showWeather by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var folderMenuFor by remember { mutableStateOf<AppInfo?>(null) } // long-press inside the folder
    val mediaEntry = remember { FocusRequester() }
    val hasFolder = folderApps.isNotEmpty()
    val baseTiles: List<Tile> = remember(apps, shortcuts, hasFolder) {
        apps.orEmpty().map { Tile.App(it) } +
            listOfNotNull(Tile.SystemFolder.takeIf { hasFolder }) +
            Tile.WeatherTile + shortcuts.map { Tile.Shortcut(it) }
    }
    // Reordering ("move mode", like tvOS): hold OK on a tile, move it with the D-pad, OK/Back to drop.
    var moving by remember { mutableStateOf<String?>(null) }
    var liveOrder by remember { mutableStateOf<List<String>?>(null) } // order while moving, not yet saved
    val tiles: List<Tile> = remember(baseTiles, order, liveOrder) { sortByOrder(baseTiles, liveOrder ?: order) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

    // These are called from callbacks that are created once (see `grid`), so they must read the
    // current order from state, never a `tiles` value captured in some earlier composition.
    // (That stale capture was the bug: moves were computed from the original order, so a tile
    // could only shift one step from where it started, and saving wrote a scrambled order.)
    val latestTiles by rememberUpdatedState(tiles)
    val latestOrderChange by rememberUpdatedState(onOrderChange)
    fun currentKeys(): List<String> = liveOrder ?: latestTiles.map { it.key }
    fun finishMove() {
        latestOrderChange(currentKeys())
        liveOrder = null
        moving = null
    }
    fun move(delta: Int) {
        val keys = currentKeys().toMutableList()
        val from = keys.indexOf(moving ?: return)
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        if (from != to) {
            keys.add(to, keys.removeAt(from))
            liveOrder = keys
        }
    }
    // Back (and the remote's Home button while we're already in front) = top of 首页, first tile.
    var homeRequest by remember { mutableStateOf(0) }
    fun goHome() {
        tab = HomeTab.Home
        homeRequest++
    }
    BackHandler(enabled = moving != null) { finishMove() }
    BackHandler(enabled = moving == null && menuFor == null && !showWeather && !showFolder) { goHome() }
    LaunchedEffect(Unit) { HomeEvents.goHome.collect { if (moving == null) goHome() } }
    // Default focus once on open (FOCUS-06); not again when switching tabs.
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(tiles.isNotEmpty()) {
        if (!didInitialFocus && tiles.isNotEmpty()) {
            didInitialFocus = runCatching { requesters[tiles.first().key]?.requestFocus() }.isSuccess
        }
    }
    // A moved tile may land in another row (new composable): give it focus back.
    LaunchedEffect(tiles, moving) {
        val key = moving ?: return@LaunchedEffect
        runCatching { requesters[key]?.requestFocus() }
    }
    var focused by remember { mutableStateOf<Tile?>(null) }
    var lastFocusedIndex by remember { mutableStateOf(0) }
    // When the focused tile disappears (uninstall), clear stale state and move focus to the
    // tile that slid into its place, so the D-pad keeps working.
    LaunchedEffect(tiles) {
        requesters.keys.retainAll(tiles.mapTo(HashSet()) { it.key })
        val current = focused ?: return@LaunchedEffect
        if (tiles.none { it.key == current.key }) {
            focused = null
            val next = tiles.getOrNull(lastFocusedIndex.coerceAtMost(tiles.lastIndex)) ?: return@LaunchedEffect
            runCatching { requesters[next.key]?.requestFocus() }
        }
    }
    // Like Google TV, wait for focus to settle before swapping the hero, so scrubbing quickly
    // across tiles doesn't flash a new image for every tile passed.
    var settled by remember { mutableStateOf<Tile?>(null) }
    LaunchedEffect(focused) {
        if (settled != null) delay(250)
        settled = focused
    }
    // A tile can vanish under focus (uninstalled from the long-press menu): never show a stale hero.
    val hero = settled?.takeIf { s -> tiles.any { it.key == s.key } } ?: tiles.firstOrNull()
    // Each app tile gets its own trending title, so moving focus changes the picture.
    // System tiles (Settings etc.) keep the plain tinted hero.
    // Each app shows its own feed; images rotate slowly while focus rests (like Top Shelf).
    var rotation by remember(hero?.key) { mutableStateOf(0) }
    LaunchedEffect(hero?.key) {
        while (true) {
            delay(12_000)
            rotation++
        }
    }
    val heroBackdrop: Backdrop? = (hero as? Tile.App)
        ?.let { backdrops[it.app.packageName] }
        ?.takeIf { it.isNotEmpty() }
        ?.let { it[rotation % it.size] }
    // Built once (per move-mode / weather change) so tiles can skip recomposition when focus moves;
    // the callbacks read the latest state through rememberUpdatedState.
    val latestAppClick by rememberUpdatedState(onAppClick)
    val latestShortcutClick by rememberUpdatedState(onShortcutClick)
    val scroll = remember(tab) { ScrollState(0) }
    val latestScroll by rememberUpdatedState(scroll)
    val scope = rememberCoroutineScope()
    val grid = remember(moving, weather) {
        GridCallbacks(
            requesters = requesters,
            moving = moving,
            weather = weather,
            brand = brand,
            onClick = { tile ->
                when (tile) {
                    is Tile.App -> latestAppClick(tile.app)
                    is Tile.Shortcut -> latestShortcutClick(tile.shortcut)
                    Tile.WeatherTile -> {
                        showWeather = true
                    }
                    Tile.SystemFolder -> {
                        showFolder = true
                    }
                }
            },
            onLongClick = { tile -> menuFor = tile },
            onFocused = {
                focused = it
                val index = latestTiles.indexOfFirst { t -> t.key == it.key }.coerceAtLeast(0)
                lastFocusedIndex = index
                // Bring-into-view only scrolls "just enough" for the tile itself, so on the first
                // row the title above it stayed hidden, and on the last row the lifted tile was
                // cut at the bottom edge. Snap to the very top / very bottom for those rows.
                val lastRow = (latestTiles.size - 1) / COLUMNS
                when (index / COLUMNS) {
                    0 -> scope.launch {
                        // Wait two frames: the focus system's own "scroll just enough" starts right
                        // after this callback and would otherwise cancel our scroll to the top.
                        withFrameNanos { }
                        withFrameNanos { }
                        latestScroll.animateScrollTo(0, tween(300, easing = Emphasized))
                    }
                    lastRow -> scope.launch {
                        withFrameNanos { } // same as above: run after the focus system's own scroll
                        withFrameNanos { }
                        latestScroll.animateScrollTo(latestScroll.maxValue, tween(300, easing = Emphasized))
                    }
                }
            },
            onMove = { move(it) },
            onFinishMove = { finishMove() },
        )
    }
    val rows = remember(tiles) { tiles.chunked(COLUMNS) }

    LaunchedEffect(homeRequest) {
        if (homeRequest == 0) return@LaunchedEffect
        withFrameNanos { } // let 首页 compose first
        scroll.animateScrollTo(0, tween(300, easing = Emphasized))
        lastFocusedIndex = 0
        latestTiles.firstOrNull()?.let { runCatching { requesters[it.key]?.requestFocus() } }
    }
    // Page content fades/slides up on tab switch (and on first open).
    val contentIn = remember { Animatable(0f) }
    LaunchedEffect(tab) {
        contentIn.snapTo(0f)
        contentIn.animateTo(1f, tween(350, easing = Emphasized))
    }
    // Nav bar tucks away once you scroll into the grid, and comes back when focused or at the top.
    var navFocused by remember { mutableStateOf(false) }
    // Reaching the top bar always shows the top of the page (title, first row), like Google TV.
    LaunchedEffect(navFocused) {
        if (navFocused && scroll.value > 0) scroll.animateScrollTo(0, tween(300, easing = Emphasized))
    }
    val atTop by remember(scroll) { derivedStateOf { scroll.value < 24 } }
    val navShown by animateFloatAsState(
        targetValue = if (navFocused || atTop) 1f else 0f,
        animationSpec = tween(250, easing = Emphasized),
        label = "navShown",
    )

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { UiSounds.onKey(it); false }) {
        HeroBackdrop(
            tile = hero,
            backdrop = heroBackdrop,
            accentOverride = if (hero == Tile.WeatherTile) weatherGradient(weather).first() else null,
            weather = weather,
        )
        // As the grid scrolls up, the picture fades to the page background, so lower rows sit on
        // one calm surface instead of a busy image (read in the draw phase: no recomposition).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (scroll.value / 260f).coerceIn(0f, 1f) }
                .background(Background.copy(alpha = 0.92f)),
        )

        // Compose 1.7 on TV scrolls the focused item to a ~30% "pivot" by default, which pushed
        // the title off-screen. Only scroll when the focused tile is actually out of view.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = contentIn.value
                        translationY = (1f - contentIn.value) * 16.dp.toPx()
                    }
                    .verticalScroll(scroll) // focus on lower rows scrolls the page
                    // Generous bottom space: the last row, lifted and shadowed when focused, sits
                    // well clear of the screen edge (and TV overscan) when scrolled to the end.
                    .padding(top = 27.dp, bottom = 80.dp),
            ) {
                if (tab == HomeTab.Media) {
                    Spacer(Modifier.height(NavBarHeight + 28.dp))
                    MediaPage(
                        volumes = media,
                        permissionGranted = mediaPermission,
                        onRequestPermission = onRequestMediaPermission,
                        onOpen = onOpenMedia,
                        entry = mediaEntry,
                    )
                    return@Column
                }
                if (tab == HomeTab.Apps) {
                    // All apps: plain grid under the nav bar, same tiles, same move mode.
                    Spacer(Modifier.height(NavBarHeight + 28.dp))
                    Text(
                        "全部应用",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = SideMargin),
                    )
                    rows.forEach { row ->
                        Spacer(Modifier.height(24.dp))
                        TileRow(row = row, grid = grid, modifier = Modifier.padding(horizontal = SideMargin))
                    }
                    if (catalog.isNotEmpty()) {
                        Spacer(Modifier.height(36.dp))
                        StoreSection(
                            apps = catalog,
                            states = storeState,
                            onInstall = onInstallCatalog,
                            modifier = Modifier.padding(horizontal = SideMargin),
                        )
                    }
                    return@Column
                }

                Spacer(Modifier.height(NavBarHeight + 190.dp)) // hero image shows through up here
                HeroText(
                    title = if (hero == Tile.SystemFolder) {
                        "系统应用"
                    } else if (hero == Tile.WeatherTile && weather != null) {
                        "${weather.city.ifBlank { "天气" }} ${weather.temperature}°"
                    } else {
                        hero?.label.orEmpty()
                    },
                    subtitle = if (hero == Tile.SystemFolder) {
                        "${brand.name} 预装的 ${folderApps.size} 个应用 · 按 OK 打开"
                    } else if (hero == Tile.WeatherTile && weather != null) {
                        listOfNotNull(
                            weather.description,
                            if (weather.high != null && weather.low != null) "${weather.low}° / ${weather.high}°" else null,
                            "按 OK 查看详细预报",
                        ).joinToString(" · ")
                    } else {
                        heroBackdrop?.let { "${it.caption} · ${it.title}" }
                    },
                    showTmdbCredit = heroBackdrop?.isTmdb == true,
                )
                Spacer(Modifier.height(16.dp))

                if (apps != null && tiles.isEmpty()) {
                    Text(
                        stringResource(R.string.empty_apps),
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = SideMargin),
                    )
                } else if (tiles.isNotEmpty()) {
                    Spacer(Modifier.height(DockPadding))
                    FirstRow(row = rows.first(), grid = grid)
                    rows.drop(1).forEach { row ->
                        Spacer(Modifier.height(32.dp)) // only a sliver of row 2 peeks at the bottom edge
                        TileRow(
                            row = row,
                            grid = grid,
                            modifier = Modifier.padding(horizontal = SideMargin),
                        )
                    }
                }
            }
        }

        // Navigation bar stays pinned on top, independent of grid scrolling.
        Box(
            Modifier
                .padding(top = 27.dp)
                .graphicsLayer {
                    alpha = navShown
                    translationY = (1f - navShown) * -24.dp.toPx()
                }
                .onFocusChanged { navFocused = it.hasFocus },
        ) {
            // D-pad Down from the bar goes back to the last focused tile (or the first one).
            // Explicit, because the 2-D search from the floating bar didn't reliably reach the grid.
            // On the media page: its first button/card, if it has anything focusable.
            val downTarget = if (tab == HomeTab.Media) {
                if (!mediaPermission || media.any { it.items.isNotEmpty() }) mediaEntry else FocusRequester.Default
            } else {
                tiles.getOrNull(lastFocusedIndex.coerceAtMost(tiles.lastIndex))
                    ?.let { grid.requester(it) } ?: FocusRequester.Default
            }
            NavBar(
                tab = tab,
                down = downTarget,
                onTab = { tab = it },
                onSettings = onSettings,
                now = now,
            )
        }

        if (showWeather) {
            WeatherDetailDialog(
                weather = weather,
                onDismiss = { showWeather = false },
                city = weatherCity,
                onPickCity = onPickWeatherCity,
            )
        }

        if (showFolder) {
            SystemFolderDialog(
                brand = brand,
                apps = folderApps,
                onLaunch = { app ->
                    showFolder = false
                    onAppClick(app)
                },
                onLongPress = { folderMenuFor = it },
                onDismiss = { showFolder = false },
            )
        }

        folderMenuFor?.let { app ->
            TileMenu(
                tile = Tile.App(app),
                onDismiss = { folderMenuFor = null },
                onMove = null, // order inside the folder is alphabetical
                onAppInfo = {
                    folderMenuFor = null
                    onAppInfo(it)
                },
                onUninstall = {
                    folderMenuFor = null
                    onUninstall(it)
                },
                folderAction = "移到首页" to {
                    folderMenuFor = null
                    onSetInFolder(app, false)
                    if (folderApps.size <= 1) showFolder = false // that was the last one
                    Unit
                },
            )
        }

        menuFor?.let { target ->
            TileMenu(
                tile = target,
                onDismiss = { menuFor = null },
                folderAction = (target as? Tile.App)?.let { t ->
                    "移入系统应用" to {
                        menuFor = null
                        onSetInFolder(t.app, true)
                    }
                },
                onMove = {
                    menuFor = null
                    moving = target.key
                },
                onAppInfo = { app ->
                    menuFor = null
                    onAppInfo(app)
                },
                onUninstall = { app ->
                    menuFor = null
                    onUninstall(app)
                },
            )
        }

        if (moving != null) {
            // Hint while rearranging.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("方向键移动位置 · 按 OK 或返回键完成", color = TextPrimary, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Long-press menu, like Google TV's: move / app info / uninstall. A Dialog so focus stays
 * inside it and Back closes it. System apps and built-in tiles can only be moved.
 */
@Composable
private fun TileMenu(
    tile: Tile,
    onDismiss: () -> Unit,
    onMove: (() -> Unit)?,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    /** Extra entry: "移入系统应用" on the home grid, "移到首页" inside the folder. */
    folderAction: Pair<String, () -> Unit>? = null,
) {
    val app = (tile as? Tile.App)?.app
    val first = remember { FocusRequester() }
    // The menu opens while OK is still held down (that's the long press). Its key-repeats and the
    // final key-up would otherwise "click" the first item right away, so ignore OK until it has
    // been released once.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            Modifier
                .onPreviewKeyEvent { event ->
                    val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    when {
                        armed -> false
                        isSelect -> {
                            if (event.type == KeyEventType.KeyUp) armed = true
                            true // swallow the held-down OK
                        }
                        else -> {
                            armed = true // any other key means OK was already released
                            false
                        }
                    }
                }
                .width(320.dp)
                .background(Color(0xFF1C2028), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tile.label,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
            if (onMove != null) MenuItem("移动位置", Modifier.focusRequester(first), onClick = onMove)
            folderAction?.let { (label, action) ->
                MenuItem(label, if (onMove == null) Modifier.focusRequester(first) else Modifier, onClick = action)
            }
            if (app != null) {
                MenuItem("应用信息", onClick = { onAppInfo(app) })
                if (!app.isSystem) {
                    MenuItem("卸载", destructive = true, onClick = { onUninstall(app) })
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, modifier: Modifier = Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = if (destructive) Color(0xFFFF6B6B) else TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = if (destructive) Color(0xFFD92D20) else Color.Black,
        ),
    ) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/** Saved keys first, in saved order; anything new (just installed) goes after, in default order. */
private fun sortByOrder(tiles: List<Tile>, order: List<String>): List<Tile> {
    if (order.isEmpty()) return tiles
    val index = order.withIndex().associate { it.value to it.index }
    val (known, unknown) = tiles.partition { it.key in index }
    return known.sortedBy { index.getValue(it.key) } + unknown
}

/** Everything a tile needs from the grid: focus handles, actions and move-mode state. */
@Stable
private class GridCallbacks(
    val requesters: MutableMap<String, FocusRequester>,
    val moving: String?,
    val weather: Weather?,
    val brand: TvBrand,
    val onClick: (Tile) -> Unit,
    val onLongClick: (Tile) -> Unit,
    val onFocused: (Tile) -> Unit,
    val onMove: (Int) -> Unit,
    val onFinishMove: () -> Unit,
) {
    fun requester(tile: Tile): FocusRequester = requesters.getOrPut(tile.key) { FocusRequester() }
}

private val NavBarHeight = 44.dp

private val MinimalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Keep a margin (≈24dp on a 1080p TV) so the lifted, shadowed tile isn't flush with the edge.
        val m = 48f.coerceAtMost((containerSize - size) / 2f).coerceAtLeast(0f)
        val trailing = offset + size
        return when {
            offset >= m && trailing <= containerSize - m -> 0f // already comfortably visible
            offset < m -> offset - m                           // above: scroll up just enough
            else -> trailing - containerSize + m               // below: scroll down just enough
        }
    }
}

// ---------- Hero ----------

/**
 * 1. dark base + soft glows (fallback, and what previews show)
 * 2. the focused app's banner, full-bleed, cross-fading as focus moves
 * 3. scrims so the title and clock stay readable (DISTANCE-02)
 */
@Composable
private fun HeroBackdrop(tile: Tile?, backdrop: Backdrop?, accentOverride: Color? = null, weather: Weather? = null) {
    // Without artwork, tint the hero with the focused tile's color so the screen still
    // changes as focus moves, instead of sitting on an empty dark gradient.
    val tint by animateColorAsState(
        targetValue = accentOverride ?: rememberTileAccent(tile) ?: Color(0xFF2B5BD7),
        animationSpec = tween(600, easing = Emphasized),
        label = "heroTint",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.75f), tint.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.75f, h * 0.15f), radius = w * 0.85f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF7A3FD1).copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(w * 0.1f, h * 0.9f), radius = w * 0.5f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF1FA39A).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w * 0.45f, h * 0.35f), radius = w * 0.35f,
                    ),
                )
            },
    ) {
        if (backdrop != null) {
            // Full-resolution image, cross-fading as focus moves. Coil caches it on disk.
            // No zoom/"Ken Burns": on the TV it redrew a full-screen bitmap every frame for 10 s
            // (jank while moving focus) and the upscaling made the picture look soft.
            Crossfade(targetState = backdrop, animationSpec = tween(500, easing = FastOutSlowInEasing), label = "hero") { b ->
                if (b.square) {
                    // Album art: big cover on the right, tinted with the tile color, fading into the
                    // background on its left edge (cropping a square to 16:9 would cut half of it).
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = b.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .fillMaxHeight(0.78f)
                                .aspectRatio(1f),
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.35f to Background,
                                        0.62f to Background.copy(alpha = 0.2f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                } else {
                    AsyncImage(
                        model = b.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // Weather tile: today's sky, animated (sun, stars, clouds, rain, snow…), fading in and out.
        // Only composed while the weather tile is the hero, so the animation costs nothing otherwise.
        AnimatedVisibility(
            visible = tile == Tile.WeatherTile,
            enter = fadeIn(tween(600, easing = Emphasized)),
            exit = fadeOut(tween(400)),
        ) {
            WeatherScene(weather)
        }
        // No backdrop (tools, Settings, Play Store…): just the color glow picked from the app's
        // own artwork. Stretching its 320x180 banner full-screen looked blurry.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Background.copy(alpha = 0.95f),
                    ),
                ),
        )
    }
}

@Composable
private fun NavBar(
    tab: HomeTab,
    down: FocusRequester,
    onTab: (HomeTab) -> Unit,
    onSettings: () -> Unit,
    now: LocalDateTime?,
) {
    var time by remember { mutableStateOf(now ?: LocalDateTime.now()) }
    if (now == null) {
        LaunchedEffect(Unit) {
            while (true) {
                time = LocalDateTime.now()
                delay(1_000L * (60 - time.second)) // tick on the minute
            }
        }
    }
    val tabFocus = remember { HomeTab.entries.associateWith { FocusRequester() } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .padding(horizontal = SideMargin)
            // Coming up from the page always lands on the current page's tab (首页 on the home
            // screen = Home), not on whichever tab happens to sit geometrically above the focused tile.
            .focusProperties { enter = { tabFocus.getValue(tab) } }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Like Google TV, pages switch as focus lands on them.
        val toGrid = Modifier.focusProperties { this.down = down }
        for ((page, label) in listOf(HomeTab.Home to "Home", HomeTab.Apps to "App", HomeTab.Media to "Media")) {
            NavTab(
                label,
                toGrid.focusRequester(tabFocus.getValue(page)),
                selected = tab == page,
                onFocused = { onTab(page) },
                onClick = { onTab(page) },
            )
        }

        Spacer(Modifier.weight(1f))
        NavIcon(R.drawable.ic_sys_settings, "设置", toGrid, onSettings)
        Spacer(Modifier.width(16.dp))
        Text(
            time.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NavTab(text: String, modifier: Modifier, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val click = {
        UiSounds.click(context)
        onClick()
    }
    Surface(
        onClick = click,
        modifier = modifier
            .padding(end = 4.dp)
            .touchClickable(onClick = click)
            .onFocusChanged {
                if (it.isFocused) {
                    UiSounds.focusMoved(context)
                    onFocused()
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NavIcon(iconRes: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp).touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(iconRes), contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

/** Focused item's name over the hero, with where the image comes from (Google TV puts this above the row). */
@Composable
private fun HeroText(title: String, subtitle: String?, showTmdbCredit: Boolean) {
    AnimatedContent(
        targetState = Triple(title, subtitle, showTmdbCredit),
        transitionSpec = {
            (fadeIn(tween(350, delayMillis = 60, easing = Emphasized)) +
                slideInVertically(tween(450, easing = Emphasized)) { it / 3 }) togetherWith
                (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 6 }) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
        label = "heroText",
        // Fixed height (title + caption + TMDB line). Its height used to change with the focused
        // tile (caption / credit line present or not), which pushed every row below up and down a
        // moment after each focus move: the "bounce" while scrolling.
        modifier = Modifier
            .padding(horizontal = SideMargin)
            .fillMaxWidth()
            .height(68.dp),
        contentAlignment = Alignment.TopStart,
    ) { (text, sub, credit) ->
        Column {
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 20.sp, // just above the 16sp caption; big titles covered the artwork
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    color = TextPrimary.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (credit) {
                Text(
                    "图片来自 TMDB",
                    color = TextPrimary.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ---------- Dock & grid ----------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FirstRow(row: List<Tile>, grid: GridCallbacks) {
    // No panel behind the first row any more: it read as a separate strip from the rows below.
    // All rows now sit straight on the hero, which darkens towards the bottom and as you scroll.
    val first = grid.requester(row.first())
    TileRow(
        row = row,
        grid = grid,
        modifier = Modifier
            .padding(horizontal = SideMargin)
            .focusRestorer { first }, // FOCUS-07
    )
}

@Composable
private fun TileRow(row: List<Tile>, grid: GridCallbacks, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        row.forEach { tile ->
            key(tile.key) { HomeTile(tile = tile, grid = grid) }
        }
        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) } // keep columns aligned
    }
}

/**
 * One icon on the grid: 5:3 rounded tile, no label (the focused item's name is the hero title).
 * Focus = lift (scale) + drop shadow + sheen, never color alone (FOCUS-01);
 * press shrinks for click feedback (REMOTE-07).
 * Hold OK = move mode: the tile wobbles with a white outline and follows the D-pad.
 */
@Composable
private fun RowScope.HomeTile(tile: Tile, grid: GridCallbacks) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val isMoving = grid.moving == tile.key
    val wobble = if (isMoving) {
        val transition = rememberInfiniteTransition(label = "wobble")
        val angle by transition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(140), RepeatMode.Reverse),
            label = "angle",
        )
        angle
    } else {
        0f
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Our own spring instead of the Surface's built-in scale tween: a soft lift with a hint of overshoot.
    val scale by animateFloatAsState(
        targetValue = when {
            isMoving -> 1.18f
            pressed -> 1.04f
            isFocused -> 1.12f
            else -> 1f
        },
        animationSpec = FocusSpring,
        label = "tileScale",
    )
    val click = {
        UiSounds.click(context)
        if (grid.moving != null) grid.onFinishMove() else grid.onClick(tile)
    }
    val longClick = { if (grid.moving == null) grid.onLongClick(tile) }
    Surface(
        onClick = click,
        interactionSource = interaction,
        onLongClick = longClick,
        modifier = Modifier
            .weight(1f)
            .touchClickable(onLongClick = longClick, onClick = click)
            .aspectRatio(5f / 3f) // ~126x76dp = 252x152px ≥ 250x150 (FOCUS-05)
            .focusRequester(grid.requester(tile))
            .onPreviewKeyEvent { event ->
                if (!isMoving || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> grid.onMove(-1)
                    Key.DirectionRight -> grid.onMove(1)
                    Key.DirectionUp -> grid.onMove(-COLUMNS)
                    Key.DirectionDown -> grid.onMove(COLUMNS)
                    else -> return@onPreviewKeyEvent false
                }
                true // consume, so focus stays on the moving tile
            }
            .zIndex(if (isFocused) 1f else 0f) // draw the lifted tile above its neighbours
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = wobble
                // RenderThread shadow: much cheaper on a TV GPU than tv-material's Glow.
                shadowElevation = if (isFocused) 14.dp.toPx() else 0f
                shape = TileShape
            }
            .onFocusChanged {
                if (it.isFocused && !isFocused) UiSounds.focusMoved(context)
                isFocused = it.isFocused
                if (it.isFocused) grid.onFocused(tile)
            },
        shape = ClickableSurfaceDefaults.shape(shape = TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f), // scale handled above
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (isMoving) Border(BorderStroke(3.dp, Color.White), shape = TileShape) else Border.None,
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceColor,
            focusedContainerColor = SurfaceColor,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            when (tile) {
                is Tile.App -> AppArtwork(tile.app)
                is Tile.Shortcut -> ShortcutArtwork(tile.shortcut)
                Tile.WeatherTile -> WeatherArtwork(grid.weather)
                Tile.SystemFolder -> FolderArtwork(grid.brand)
            }
            if (isFocused) {
                // Specular sheen from the top-left, like light catching a glass icon.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                0f to Color.White.copy(alpha = 0.28f),
                                0.45f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun AppArtwork(app: AppInfo) {
    val banner = rememberBitmap(app.banner)
    val icon = rememberBitmap(app.icon)
    when {
        banner != null -> Image(
            bitmap = banner, contentDescription = app.label,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        // No TV banner (phone apps, many sideloaded apps): white card with the app's icon centred,
        // the same style as the built-in tiles (the name is shown as the hero title on focus).
        icon != null -> Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEEF1F5))))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(58.dp))
        }
        else -> Box( // no artwork (and previews): colored tile with the initial
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(placeholderGradient(app.label))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 系统应用 folder tile: the TV brand's logo on white (res/drawable/banner_brand_<key>.png, supplied
 * by the user). res/drawable/banner_sys_folder.png overrides it; with neither, the brand name as text.
 */
@Composable
private fun FolderArtwork(brand: TvBrand) {
    val context = LocalContext.current
    val customRes = remember(brand) {
        val res = context.resources
        res.getIdentifier("banner_sys_folder", "drawable", context.packageName).takeIf { it != 0 }
            ?: brand.logoKey?.let { res.getIdentifier("banner_brand_$it", "drawable", context.packageName) }
            ?: 0
    }
    if (customRes != 0) {
        Image(
            painter = painterResource(customRes),
            contentDescription = "系统应用",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Text(
            brand.name,
            color = Color.Black,
            fontSize = if (brand.name.length <= 5) 26.sp else 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (brand.name.length <= 5) 3.sp else 1.sp,
            maxLines = 1,
        )
        Text(
            "系统应用",
            color = Color(0xFF8A8F98),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
    }
}

/** Opened folder: every pre-installed extra as a card with its name; OK opens, hold OK for the menu. */
@Composable
private fun SystemFolderDialog(
    brand: TvBrand,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xF2101318))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SideMargin, vertical = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("系统应用", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("${brand.name} 预装 · ${apps.size} 个", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(20.dp))
            apps.sortedBy { it.label.lowercase() }.chunked(6).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    row.forEachIndexed { c, app ->
                        val focusMod = if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier
                        Column(Modifier.weight(1f)) {
                            Surface(
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                                modifier = focusMod.fillMaxWidth().aspectRatio(16f / 9f)
                                    .touchClickable(onLongClick = { onLongPress(app) }) { onLaunch(app) },
                                shape = ClickableSurfaceDefaults.shape(shape = TileShape),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                                glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
                                colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceColor, focusedContainerColor = SurfaceColor),
                            ) {
                                AppArtwork(app)
                            }
                            Text(
                                app.label,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            )
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(18.dp))
            }
            Text("长按可移到首页 · 按返回键关闭", color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShortcutArtwork(shortcut: SystemShortcut) {
    // Hand-made artwork wins if present: drop res/drawable/banner_sys_<id>.png
    // (banner_sys_network / _apps / _display / _stock_home / _settings), 16:9, e.g. 640x384.
    val context = LocalContext.current
    val customRes = remember(shortcut.id) {
        context.resources.getIdentifier("banner_sys_${shortcut.id}", "drawable", context.packageName)
    }
    if (customRes != 0) {
        Image(
            painter = painterResource(customRes),
            contentDescription = shortcut.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // Same look as the TV's own Settings tile: one centred glyph, no text (the focused tile's name
    // is shown as the hero title anyway). White card, matching Play Store / YouTube / Kodi banners.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEEF1F5)))),
        contentAlignment = Alignment.Center,
    ) {
        if (!shortcut.tinted) {
            Image(painterResource(shortcut.iconRes), contentDescription = shortcut.label, modifier = Modifier.size(58.dp))
        } else {
            Icon(
                painter = painterResource(shortcut.iconRes),
                contentDescription = shortcut.label,
                tint = shortcut.gradient.last(),
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        // Diagonal multi-colour fill (top-left → bottom-right), like modern app icons.
                        drawRect(
                            Brush.linearGradient(shortcut.gradient, start = Offset.Zero, end = Offset(size.width, size.height)),
                            blendMode = BlendMode.SrcIn,
                        )
                    },
            )
        }
    }
}

/** 2×2 rounded squares in four colors (blue, red, green, yellow), on the same 24-unit grid as the glyphs. */
@Composable
private fun FourColorGrid(modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFF34A853), Color(0xFFFBBC05))
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val cells = listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f)
        cells.forEachIndexed { i, (x, y) ->
            drawRoundRect( // shadow
                color = Color.Black.copy(alpha = 0.10f),
                topLeft = Offset(x * u, (y + 0.6f) * u),
                size = Size(8 * u, 8 * u),
                cornerRadius = CornerRadius(2.6f * u),
            )
            drawRoundRect(
                color = colors[i],
                topLeft = Offset(x * u, y * u),
                size = Size(8 * u, 8 * u),
                cornerRadius = CornerRadius(2.6f * u),
            )
        }
    }
}

private val placeholderPalette = listOf(
    listOf(Color(0xFFFF5E62), Color(0xFFD4145A)),
    listOf(Color(0xFF34C759), Color(0xFF138A36)),
    listOf(Color(0xFF0A84FF), Color(0xFF0040DD)),
    listOf(Color(0xFFFF9F0A), Color(0xFFE85D04)),
    listOf(Color(0xFFBF5AF2), Color(0xFF7D2AE8)),
    listOf(Color(0xFF64D2FF), Color(0xFF0A84FF)),
    listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
)

private fun placeholderGradient(label: String): List<Color> =
    placeholderPalette[Math.floorMod(label.hashCode(), placeholderPalette.size)]

/** Color of a tile, used to tint the hero glow: picked from the app's banner/icon when it has one. */
@Composable
private fun rememberTileAccent(tile: Tile?): Color? = remember(tile?.key) {
    when (tile) {
        null -> null
        is Tile.Shortcut -> tile.shortcut.gradient.first()
        Tile.WeatherTile -> null
        Tile.SystemFolder -> Color(0xFF8E99AB)
        is Tile.App -> (tile.app.banner ?: tile.app.icon)?.let(::dominantColor)
            ?: placeholderGradient(tile.app.label).first()
    }
}

/**
 * "Eyedropper": the most prominent saturated hue in a drawable. Pixels are bucketed by hue,
 * weighted by saturation and brightness (so white/black/grey backgrounds are ignored), and the
 * heaviest bucket is averaged. Returns null for artwork with no real color (e.g. all white).
 */
private fun dominantColor(drawable: Drawable): Color? = runCatching {
    val bmp = drawable.toBitmap(48, 27)
    val buckets = 12
    val weight = FloatArray(buckets)
    val r = FloatArray(buckets)
    val g = FloatArray(buckets)
    val b = FloatArray(buckets)
    val hsv = FloatArray(3)
    for (y in 0 until bmp.height) {
        for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 128) continue
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[2] < 0.2f) continue // too dark to tell
            val w = hsv[1] * hsv[1] * hsv[2]
            if (w < 0.05f) continue // greys and whites
            val i = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            weight[i] += w
            r[i] += android.graphics.Color.red(c) * w
            g[i] += android.graphics.Color.green(c) * w
            b[i] += android.graphics.Color.blue(c) * w
        }
    }
    val best = weight.indices.maxBy { weight[it] }
    if (weight[best] < 1f) return@runCatching null
    Color(r[best] / weight[best] / 255f, g[best] / weight[best] / 255f, b[best] / weight[best] / 255f)
}.getOrNull()

@Composable
private fun rememberBitmap(drawable: Drawable?): ImageBitmap? = remember(drawable) {
    drawable?.let {
        runCatching {
            // A tile is ~250x150 px on a 1080p TV; cap the bitmap there (some apps ship 4K banners).
            val w0 = it.intrinsicWidth.takeIf { v -> v > 0 } ?: 320
            val h0 = it.intrinsicHeight.takeIf { v -> v > 0 } ?: 180
            val k = minOf(1f, 320f / w0, 320f / h0)
            it.toBitmap((w0 * k).toInt().coerceAtLeast(1), (h0 * k).toInt().coerceAtLeast(1)).asImageBitmap()
        }.getOrNull()
    }
}

// ---------- Previews (no device needed) ----------

private val fakeApps = listOf(
    "YouTube", "Netflix", "Bilibili", "爱奇艺", "腾讯视频", "Kodi", "Plex", "芒果TV", "优酷", "Spotify",
).map { AppInfo(packageName = "fake.$it", label = it) }

private val previewTime = LocalDateTime.of(2026, 10, 26, 20, 30)

// What a 1080p Android TV actually reports: 1920x1080px at xhdpi = 960x540dp.
private const val TV_1080P_XHDPI = "spec:width=960dp,height=540dp,dpi=320"

@Preview(name = "Home 1080p", device = TV_1080P_XHDPI, showBackground = true)
@Composable
private fun HomePreview() {
    TLauncherTheme { HomeContent(apps = fakeApps, onAppClick = {}, now = previewTime) }
}

@Preview(name = "Home only system", device = TV_1080P_XHDPI, showBackground = true)
@Composable
private fun HomeSystemOnlyPreview() {
    TLauncherTheme { HomeContent(apps = emptyList(), onAppClick = {}, now = previewTime) }
}
