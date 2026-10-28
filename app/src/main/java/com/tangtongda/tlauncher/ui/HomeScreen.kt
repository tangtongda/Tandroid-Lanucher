@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tangtongda.tlauncher.ui

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
// Concentric corners: outer radius = inner radius + padding, so the gap stays even around the curve.
private val DockShape = RoundedCornerShape(TileRadius + DockPadding)

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

    /** Live weather tile; selecting it opens the forecast. */
    data object WeatherTile : Tile {
        override val key: String get() = "sys:weather"
        override val label: String get() = "天气"
    }
}

// ---------- Stateful entry (real data) ----------

@Composable
fun HomeRoute(vm: HomeViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val backdrops by vm.backdrops.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val mediaPermission by vm.mediaPermission.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val storeState by vm.storeState.collectAsStateWithLifecycle()
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
        catalog = catalog,
        storeState = storeState,
        onInstallCatalog = vm::installCatalog,
        onLive = vm::openLiveTv,
        onSettings = vm::openSettings,
    )
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
    catalog: List<CatalogApp> = emptyList(),
    storeState: Map<String, StoreState> = emptyMap(),
    onInstallCatalog: (CatalogApp) -> Unit = {},
    onLive: () -> Unit = {},
    onSettings: () -> Unit = {},
    now: LocalDateTime? = null, // fixed time for previews
) {
    var tab by remember { mutableStateOf(HomeTab.Home) }
    var menuFor by remember { mutableStateOf<Tile?>(null) } // long-press menu target
    var showWeather by remember { mutableStateOf(false) }
    val mediaEntry = remember { FocusRequester() }
    val baseTiles: List<Tile> = remember(apps, shortcuts) {
        apps.orEmpty().map { Tile.App(it) } + Tile.WeatherTile + shortcuts.map { Tile.Shortcut(it) }
    }
    // Reordering ("move mode", like tvOS): hold OK on a tile, move it with the D-pad, OK/Back to drop.
    var moving by remember { mutableStateOf<String?>(null) }
    var liveOrder by remember { mutableStateOf<List<String>?>(null) } // order while moving, not yet saved
    val tiles: List<Tile> = remember(baseTiles, order, liveOrder) { sortByOrder(baseTiles, liveOrder ?: order) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

    fun finishMove() {
        onOrderChange(tiles.map { it.key })
        liveOrder = null
        moving = null
    }
    fun move(delta: Int) {
        val keys = tiles.map { it.key }.toMutableList()
        val from = keys.indexOf(moving ?: return)
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        if (from != to) {
            keys.add(to, keys.removeAt(from))
            liveOrder = keys
        }
    }
    BackHandler(enabled = moving != null) { finishMove() }
    BackHandler(enabled = moving == null && tab != HomeTab.Home) { tab = HomeTab.Home }
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
    val grid = GridCallbacks(
        requesters = requesters,
        moving = moving,
        weather = weather,
        onClick = { tile ->
            when (tile) {
                is Tile.App -> onAppClick(tile.app)
                is Tile.Shortcut -> onShortcutClick(tile.shortcut)
                Tile.WeatherTile -> {
                    showWeather = true
                }
            }
        },
        onLongClick = { tile -> menuFor = tile },
        onFocused = {
            focused = it
            lastFocusedIndex = tiles.indexOfFirst { t -> t.key == it.key }.coerceAtLeast(0)
        },
        onMove = ::move,
        onFinishMove = ::finishMove,
    )

    val scroll = remember(tab) { ScrollState(0) }
    // Page content fades/slides up on tab switch (and on first open).
    val contentIn = remember { Animatable(0f) }
    LaunchedEffect(tab) {
        contentIn.snapTo(0f)
        contentIn.animateTo(1f, tween(350, easing = Emphasized))
    }
    // Nav bar tucks away once you scroll into the grid, and comes back when focused or at the top.
    var navFocused by remember { mutableStateOf(false) }
    val navShown by animateFloatAsState(
        targetValue = if (navFocused || scroll.value < 24) 1f else 0f,
        animationSpec = tween(250, easing = Emphasized),
        label = "navShown",
    )

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(
            tile = hero,
            backdrop = heroBackdrop,
            accentOverride = if (hero == Tile.WeatherTile) weatherGradient(weather).first() else null,
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
                    .padding(vertical = 27.dp),
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
                    tiles.chunked(COLUMNS).forEach { row ->
                        Spacer(Modifier.height(24.dp))
                        TileRow(row = row, grid = grid, modifier = Modifier.padding(horizontal = SideMargin + DockPadding))
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

                Spacer(Modifier.height(NavBarHeight + 185.dp)) // hero image shows through up here
                HeroText(
                    title = if (hero == Tile.WeatherTile && weather != null) {
                        "${weather.city.ifBlank { "天气" }} ${weather.temperature}°"
                    } else {
                        hero?.label.orEmpty()
                    },
                    subtitle = if (hero == Tile.WeatherTile && weather != null) {
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
                    val rows = tiles.chunked(COLUMNS)
                    Dock(row = rows.first(), grid = grid)
                    // Remaining rows: same columns as the dock, aligned to its inner edge.
                    rows.drop(1).forEach { row ->
                        Spacer(Modifier.height(32.dp)) // only a sliver of row 2 peeks at the bottom edge
                        TileRow(
                            row = row,
                            grid = grid,
                            modifier = Modifier.padding(horizontal = SideMargin + DockPadding),
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
                onLive = onLive,
                onSettings = onSettings,
                now = now,
            )
        }

        if (showWeather) {
            WeatherDetailDialog(weather = weather, onDismiss = { showWeather = false })
        }

        menuFor?.let { target ->
            TileMenu(
                tile = target,
                onDismiss = { menuFor = null },
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
    onMove: () -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
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
            MenuItem("移动位置", Modifier.focusRequester(first), onClick = onMove)
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
        modifier = modifier.fillMaxWidth(),
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
private class GridCallbacks(
    val requesters: MutableMap<String, FocusRequester>,
    val moving: String?,
    val weather: Weather?,
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
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f // already fully visible
            offset < 0f -> offset                           // above: scroll up just enough
            else -> trailing - containerSize                // below: scroll down just enough
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
private fun HeroBackdrop(tile: Tile?, backdrop: Backdrop?, accentOverride: Color? = null) {
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
            // TMDB image (1280px), cross-fading as focus moves. Coil caches it on disk.
            Crossfade(targetState = backdrop, animationSpec = tween(700, easing = FastOutSlowInEasing), label = "hero") { b ->
                // Slow "Ken Burns" settle: each new image starts slightly zoomed in and eases back.
                val zoom = remember(b.imageUrl) { Animatable(1.08f) }
                LaunchedEffect(b.imageUrl) { zoom.animateTo(1f, tween(10_000, easing = LinearOutSlowInEasing)) }
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
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = zoom.value
                                    scaleY = zoom.value
                                },
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
                        alpha = 0.9f,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom.value
                                scaleY = zoom.value
                            },
                    )
                }
            }
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
    onLive: () -> Unit,
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
    Row(
        Modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .padding(horizontal = SideMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Like Google TV, pages switch as focus lands on them; "直播" opens the TV app on OK.
        val toGrid = Modifier.focusProperties { this.down = down }
        NavTab("首页", toGrid, selected = tab == HomeTab.Home, onFocused = { onTab(HomeTab.Home) }, onClick = { onTab(HomeTab.Home) })
        NavTab("直播", toGrid, selected = false, onFocused = {}, onClick = onLive)
        NavTab("媒体库", toGrid, selected = tab == HomeTab.Media, onFocused = { onTab(HomeTab.Media) }, onClick = { onTab(HomeTab.Media) })
        NavTab("应用", toGrid, selected = tab == HomeTab.Apps, onFocused = { onTab(HomeTab.Apps) }, onClick = { onTab(HomeTab.Apps) })

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
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(end = 4.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
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
        modifier = modifier.size(36.dp),
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
                (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 6 })
        },
        label = "heroText",
        modifier = Modifier.padding(horizontal = SideMargin),
    ) { (text, sub, credit) ->
        Column {
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 40.sp, // DISTANCE-01: large header
                fontWeight = FontWeight.Bold,
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
private fun Dock(row: List<Tile>, grid: GridCallbacks) {
    val first = grid.requester(row.first())
    // Translucent "glass" panel. Real background blur needs Android 12+ and the A80J is
    // Android 10, so this is a tinted, bordered panel instead.
    Box(
        Modifier
            .padding(horizontal = SideMargin)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f), DockShape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), DockShape)
            .padding(DockPadding),
    ) {
        TileRow(
            row = row,
            grid = grid,
            modifier = Modifier.focusRestorer { first }, // FOCUS-07
        )
    }
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
    Surface(
        onClick = { if (grid.moving != null) grid.onFinishMove() else grid.onClick(tile) },
        interactionSource = interaction,
        onLongClick = { if (grid.moving == null) grid.onLongClick(tile) },
        modifier = Modifier
            .weight(1f)
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
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) grid.onFocused(tile)
            },
        shape = ClickableSurfaceDefaults.shape(shape = TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f), // scale handled above
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (isMoving) Border(BorderStroke(3.dp, Color.White), shape = TileShape) else Border.None,
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 16.dp),
        ),
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
        icon != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(44.dp))
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

    // Light tile + colored glyph, matching the white banners of Play Store / YouTube / Kodi next
    // to it (a modern "white card, colorful symbol" icon style). The glyph is painted with the
    // shortcut's gradient and sits on a faint drop shadow; "应用管理" gets a four-color grid.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6EBF1)))),
        contentAlignment = Alignment.Center,
    ) {
        if (shortcut.id == "apps") {
            FourColorGrid(Modifier.size(42.dp))
        } else {
            Box {
                Icon(
                    painter = painterResource(shortcut.iconRes),
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.12f),
                    modifier = Modifier.offset(y = 1.dp).size(42.dp),
                )
                Icon(
                    painter = painterResource(shortcut.iconRes),
                    contentDescription = shortcut.label,
                    tint = shortcut.gradient.last(),
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(shortcut.gradient), blendMode = BlendMode.SrcIn)
                        },
                )
            }
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
            val w = it.intrinsicWidth.takeIf { v -> v > 0 } ?: 320
            val h = it.intrinsicHeight.takeIf { v -> v > 0 } ?: 180
            it.toBitmap(w, h).asImageBitmap()
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
