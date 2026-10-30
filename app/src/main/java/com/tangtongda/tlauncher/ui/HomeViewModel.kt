package com.tangtongda.tlauncher.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tangtongda.tlauncher.data.AppInfo
import com.tangtongda.tlauncher.data.AppRepository
import com.tangtongda.tlauncher.data.Backdrop
import com.tangtongda.tlauncher.data.SystemShortcut
import com.tangtongda.tlauncher.data.ACTION_STOCK_HOME
import com.tangtongda.tlauncher.data.BackdropRepository
import com.tangtongda.tlauncher.data.MediaItem
import com.tangtongda.tlauncher.data.MediaRepository
import com.tangtongda.tlauncher.data.MediaVolume
import com.tangtongda.tlauncher.data.Weather
import com.tangtongda.tlauncher.data.WeatherRepository
import com.tangtongda.tlauncher.data.defaultShortcuts
import com.tangtongda.tlauncher.data.AppStoreRepository
import com.tangtongda.tlauncher.data.CatalogApp
import com.tangtongda.tlauncher.data.StoreState
import com.tangtongda.tlauncher.data.appCatalog
import com.tangtongda.tlauncher.data.networkAvailable
import com.tangtongda.tlauncher.data.isFolderCandidate
import com.tangtongda.tlauncher.data.tvBrand
import com.tangtongda.tlauncher.data.TvBrand
import com.tangtongda.tlauncher.data.HdmiInput
import com.tangtongda.tlauncher.data.HdmiRepository
import com.tangtongda.tlauncher.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)

    /** null = still loading */
    val apps: StateFlow<List<AppInfo>?> = repo.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Built-in tiles. When the TV's own Settings app is already in the app list (with its
     * large banner), drop our Settings shortcut so it only appears once.
     */
    val shortcuts: StateFlow<List<SystemShortcut>> = apps
        .map { list ->
            val settingsPkg = repo.settingsPackage
            val hasSettingsApp = list.orEmpty().any { it.packageName == settingsPkg }
            defaultShortcuts.filter { shortcut ->
                when (shortcut.id) {
                    "settings" -> !hasSettingsApp
                    "stock_home" -> repo.hasStockHome // only when there is an original home to go back to
                    else -> true
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultShortcuts)

    // ---------- HDMI ----------

    private val hdmiRepo = HdmiRepository(app)
    private val _hdmiPrompt = MutableStateFlow<HdmiInput?>(null)

    /** "HDMI 2 已连接，要切换过去吗？" — shown while non-null. */
    val hdmiPrompt: StateFlow<HdmiInput?> = _hdmiPrompt.asStateFlow()
    private var hdmiExpiry: Job? = null
    private var lastHdmi: Pair<String, Long>? = null

    init {
        viewModelScope.launch {
            hdmiRepo.connections().collect { input ->
                // Plugging in can fire several events in a row: ask once per input per 10 s.
                val now = System.currentTimeMillis()
                val last = lastHdmi
                if (last != null && last.first == input.id && now - last.second < 10_000) return@collect
                lastHdmi = input.id to now
                showHdmiPrompt(input)
            }
        }
        // Emulator has no HDMI: with debug.demoApps=true, fake a prompt shortly after start.
        if (BuildConfig.DEBUG && BuildConfig.DEMO_APPS) {
            viewModelScope.launch {
                // A different input on each launch, to see every icon.
                val prefs = getApplication<Application>().getSharedPreferences("debug", Context.MODE_PRIVATE)
                val n = prefs.getInt("fake_hdmi", 0)
                prefs.edit().putInt("fake_hdmi", n + 1).apply()
                delay(8_000)
                showHdmiPrompt(
                    when (n % 5) {
                        0 -> HdmiInput("debug.fake.hdmi2", "HDMI 2（模拟）")
                        1 -> HdmiInput("debug.fake.hdmi3", "PlayStation 5（模拟）")
                        2 -> HdmiInput("debug.fake.hdmi4", "Xbox Series X（模拟）")
                        3 -> HdmiInput("debug.fake.hdmi1", "MacBook Pro（模拟）")
                        else -> HdmiInput("debug.fake.hdmi1", "Windows 电脑（模拟）")
                    },
                )
            }
        }
    }

    private fun showHdmiPrompt(input: HdmiInput) {
        _hdmiPrompt.value = input
        // Don't leave a stale question around (e.g. it arrived while another app was in front).
        hdmiExpiry?.cancel()
        hdmiExpiry = viewModelScope.launch {
            delay(30_000)
            if (_hdmiPrompt.value == input) _hdmiPrompt.value = null
        }
    }

    fun dismissHdmi() {
        _hdmiPrompt.value = null
    }

    fun switchToHdmi() {
        val input = _hdmiPrompt.value ?: return
        _hdmiPrompt.value = null
        if (!hdmiRepo.switchTo(input)) repo.launchLiveTv() // fall back to the TV app's own input screen
    }

    // ---------- 系统应用 folder ----------

    private val folderPrefs = app.getSharedPreferences("system_folder", Context.MODE_PRIVATE)
    private val _folderIn = MutableStateFlow(folderPrefs.getStringSet("in", emptySet()).orEmpty().toSet())
    private val _folderOut = MutableStateFlow(folderPrefs.getStringSet("out", emptySet()).orEmpty().toSet())

    /** "SONY", "Hisense", "TCL"… and its logo, shown on the folder tile. */
    val brand: TvBrand = tvBrand()

    /** Pre-installed extras (plus anything the user moved in), minus what the user moved out. */
    val folderApps: StateFlow<List<AppInfo>> = combine(apps, _folderIn, _folderOut) { list, moveIn, moveOut ->
        list.orEmpty().filter { a ->
            a.packageName in moveIn || (a.packageName !in moveOut && isFolderCandidate(a, repo.settingsPackage))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What the home grid shows: everything except the folder's contents. null = still loading. */
    val homeApps: StateFlow<List<AppInfo>?> = combine(apps, folderApps) { list, folder ->
        val hidden = folder.mapTo(HashSet()) { it.packageName }
        list?.filter { it.packageName !in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setInFolder(app: AppInfo, inFolder: Boolean) {
        val pkg = app.packageName
        _folderIn.value = if (inFolder) _folderIn.value + pkg else _folderIn.value - pkg
        _folderOut.value = if (inFolder) _folderOut.value - pkg else _folderOut.value + pkg
        folderPrefs.edit().putStringSet("in", _folderIn.value).putStringSet("out", _folderOut.value).apply()
    }

    private val backdropRepo = BackdropRepository(app)

    /** Internet up? The launcher starts at boot, usually before the network: re-fetch when it comes up. */
    private val online: StateFlow<Boolean> = app.networkAvailable()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Hero images per package name, resolved per app (own feed → its TMDB streaming service →
     * TMDB trending for video apps → none). Apps without an entry fall back to banner / tint.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val backdrops: StateFlow<Map<String, List<Backdrop>>> = combine(
        apps.filterNotNull(),
        // Re-check every 3 min: cheap (results are cached 2 h), and apps that got nothing are retried.
        flow { while (true) { emit(Unit); delay(3 * 60 * 1000L) } },
        online.onEach { if (it) backdropRepo.forgetEmpty() },
    ) { list, _, _ -> list }
        .mapLatest { list ->
            coroutineScope {
                list.map { app -> async { app.packageName to backdropRepo.forApp(app) } }
                    .awaitAll()
                    .filter { it.second.isNotEmpty() }
                    .toMap()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val _order = MutableStateFlow(
        prefs.getString(KEY_ORDER, null).orEmpty().split('\n').filter { it.isNotBlank() },
    )

    /** User's tile order (tile keys like "app:<package>" / "sys:<id>"); empty = default order. */
    val order: StateFlow<List<String>> = _order.asStateFlow()

    fun saveOrder(keys: List<String>) {
        _order.value = keys
        prefs.edit().putString(KEY_ORDER, keys.joinToString("\n")).apply()
    }

    fun launch(app: AppInfo) {
        repo.launch(app.packageName)
    }

    // ---------- 天气 ----------

    private val weatherRepo = WeatherRepository(app)

    /** Current weather for the nav bar, refreshed every 30 min (keeps the last value on failure). */
    private var lastWeather: Weather? = null

    private val weatherPrefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val _weatherCity = MutableStateFlow(weatherPrefs.getString("city", null))

    /** City picked on the TV; null = automatic (IP location / local.properties). */
    val weatherCity: StateFlow<String?> = _weatherCity.asStateFlow()

    fun setWeatherCity(city: String?) {
        if (city == _weatherCity.value) return
        weatherPrefs.edit().putString("city", city).apply()
        lastWeather = null // don't keep showing the old city while the new one loads
        _weatherCity.value = city
    }

    /** Current weather, refreshed every 30 min; retried every minute while it fails, and as soon as the network is back. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weather: StateFlow<Weather?> = combine(online.filter { it }, _weatherCity) { _, city -> city }
        .flatMapLatest { city ->
            flow {
                while (true) {
                    val w = weatherRepo.load(city)
                    if (w != null) lastWeather = w
                    emit(lastWeather)
                    delay(if (w == null) 60_000L else 30 * 60 * 1000L)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------- 媒体库 ----------

    private val mediaRepo = MediaRepository(app)

    /** Runtime permissions to request for the media library. */
    val mediaPermissions: Array<String> get() = mediaRepo.permissions

    private val _mediaPermission = MutableStateFlow(mediaRepo.hasPermission())
    val mediaPermission: StateFlow<Boolean> = _mediaPermission.asStateFlow()

    /** Drives and their media; re-reads on plug/unplug and when the media scanner finishes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val media: StateFlow<List<MediaVolume>> = _mediaPermission
        .flatMapLatest { granted -> if (granted) mediaRepo.observe() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onMediaPermissionResult() {
        _mediaPermission.value = mediaRepo.hasPermission()
    }

    fun openMedia(item: MediaItem) {
        mediaRepo.open(item)
    }

    fun uninstall(app: AppInfo) = repo.uninstall(app.packageName)

    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app.packageName)

    fun openSettings() = repo.launchSystem(android.provider.Settings.ACTION_SETTINGS)

    fun openLiveTv() {
        repo.launchLiveTv()
    }

    fun launchShortcut(shortcut: SystemShortcut) {
        if (shortcut.action == ACTION_STOCK_HOME) repo.launchStockHome() else repo.launchSystem(shortcut.action)
    }

    // ---------- 推荐应用 ----------

    private val store = AppStoreRepository(app)

    /** Catalog apps that aren't installed yet (BBLL disappears from the list once it is). */
    val catalog: StateFlow<List<CatalogApp>> = apps
        .map { list ->
            val installed = list.orEmpty()
            appCatalog.filter { c -> installed.none { c.detect.containsMatchIn(it.packageName) || c.detect.containsMatchIn(it.label) } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _storeState = MutableStateFlow<Map<String, StoreState>>(emptyMap())
    val storeState: StateFlow<Map<String, StoreState>> = _storeState.asStateFlow()

    private fun setStore(id: String, state: StoreState) {
        _storeState.value = _storeState.value + (id to state)
    }

    /** Bundled copy first, otherwise the latest GitHub release; then the system installer. */
    fun installCatalog(item: CatalogApp) {
        val current = _storeState.value[item.id]
        if (current is StoreState.Resolving || current is StoreState.Downloading || current is StoreState.Installing) return
        if (!store.canInstall()) {
            setStore(item.id, StoreState.NeedsPermission)
            store.openInstallPermission() // user allows once, comes back, presses again
            return
        }
        viewModelScope.launch {
            runCatching {
                val file = if (store.hasBundled(item)) {
                    setStore(item.id, StoreState.Installing)
                    store.extractBundled(item)
                } else {
                    // Official source first; on any failure, our mirror (if the app has one).
                    var lastError: Throwable? = null
                    var downloaded: java.io.File? = null
                    for (source in listOfNotNull(item.source, item.fallback)) {
                        try {
                            setStore(item.id, StoreState.Resolving)
                            val (url, sha) = store.resolve(item, source)
                            // Direct first, then through GitHub file proxies (see githubProxies).
                            for (candidate in com.tangtongda.tlauncher.data.downloadCandidates(url)) {
                                try {
                                    val f = store.download(item, candidate) { p, bytes -> setStore(item.id, StoreState.Downloading(p, bytes)) }
                                    if (!store.verify(f, sha)) error("安装包校验失败")
                                    downloaded = f
                                    break
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    lastError = e
                                }
                            }
                            if (downloaded != null) break
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }
                    downloaded ?: throw (lastError ?: IllegalStateException("下载失败"))
                }
                setStore(item.id, StoreState.Installing)
                store.install(file)
                delay(1500)
                setStore(item.id, StoreState.Idle) // installer is on screen; the card hides once installed
            }.onFailure { setStore(item.id, StoreState.Failed(it.message ?: "安装失败")) }
        }
    }

    private companion object {
        const val KEY_ORDER = "tile_order"
    }
}
