package dev.personal.tvlauncher.home

import android.app.AlertDialog
import android.app.LocaleManager
import android.os.LocaleList
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.personal.tvlauncher.LauncherApplication
import dev.personal.tvlauncher.R
import dev.personal.tvlauncher.apps.*
import dev.personal.tvlauncher.channels.*
import dev.personal.tvlauncher.settings.*
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {
    private val graph get() = application as LauncherApplication
    private val model by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(graph.apps, graph.channels, graph.preferences) as T
        })[HomeViewModel::class.java]
    }
    private enum class Screen { HOME, APPS, SETTINGS, FAVOURITES, LISTS }
    private var screen = Screen.HOME
    private lateinit var root: RemoteRoot
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var settingsButton: Button
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private var state = HomeState()
    private var dialog: AlertDialog? = null
    private var renderGeneration = 0
    private var renderedAppColumns = 0
    private var appliedBackground: BackgroundTheme? = null
    private val rowViews = mutableListOf<RecyclerView>()
    private val rowHeadings = mutableMapOf<String, TextView>()
    private val focusRows = mutableListOf<FocusRow>()
    private val rowPositions = mutableMapOf<String, Int>()
    private var chromeFocus: String? = null
    private var restoringFocus = false
    private var editFallback: String? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message(getString(R.string.permission_denied))
        model.refresh()
        if (screen == Screen.SETTINGS) render()
    }
    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.refresh()
        if (screen == Screen.SETTINGS) render()
    }
    private val timeChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { updateDateTime() }
    }

    private val timeFormatChanges = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { updateDateTime() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = savedInstanceState?.getString("screen")?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.HOME
        if (savedInstanceState?.containsKey("focusRow") == true) {
            model.focus = FocusAnchor(savedInstanceState.getString("focusRow")!!, savedInstanceState.getString("focusItem")!!,
                savedInstanceState.getInt("focusRowIndex"), savedInstanceState.getInt("focusItemIndex"))
        }
        if (savedInstanceState?.containsKey("allFocusRow") == true) {
            model.allAppsFocus = FocusAnchor(savedInstanceState.getString("allFocusRow")!!, savedInstanceState.getString("allFocusItem")!!,
                savedInstanceState.getInt("allFocusRowIndex"), savedInstanceState.getInt("allFocusItemIndex"))
        }
        root = RemoteRoot(this) { focused ->
            val tag = focused?.tag as? String
            chromeFocus = tag?.takeUnless { it.startsWith("card:") }
        }.apply {
            tag = "launcher-root"
            orientation = LinearLayout.VERTICAL
            // Park fallback focus here while card rows rebuild, rather than on a header button.
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            background = backgroundGradient(BackgroundTheme.MIDNIGHT)
            setPadding(dp(48), dp(24), dp(48), dp(24))
            // Keep the scrolling viewport from drawing over the fixed header.
            clipChildren = true; clipToPadding = false
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val clockBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = TextView(this).apply {
            textSize = 32f; setTextColor(TvColors.text)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        }
        clockBlock.addView(clock)
        date = label("", 14f).apply {
            tag = "date"; isFocusable = true; isClickable = false
            setPadding(dp(6), dp(3), dp(6), 0)
            background = dateFocusBackground()
            setTextColor(android.content.res.ColorStateList(arrayOf(
                intArrayOf(android.R.attr.state_focused), intArrayOf(),
            ), intArrayOf(TvColors.text, TvColors.secondary)))
            setOnKeyListener { _, code, event ->
                if (screen != Screen.HOME || event.action != KeyEvent.ACTION_DOWN) false
                else when (code) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { settingsButton.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { focusHomeContent(); true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> true
                    else -> false
                }
            }
        }
        clockBlock.addView(date, LinearLayout.LayoutParams(-2, -2)); updateDateTime()
        header.addView(clockBlock, LinearLayout.LayoutParams(0, -2, 1f))
        settingsButton = iconAction(getString(R.string.settings), "settings", R.drawable.ic_settings) { navigate(Screen.SETTINGS) }
        settingsButton.setOnKeyListener { _, code, event ->
            if (screen != Screen.HOME || event.action != KeyEvent.ACTION_DOWN) false
            else when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { date.requestFocus(); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { focusHomeContent(); true }
                else -> false
            }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
        scroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false; clipChildren = true; clipToPadding = true }
        scroll.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) scroll.post {
                if (screen == Screen.HOME && state.preferences.showApps && state.preferences.appsLayout == AppsLayout.GRID &&
                    homeGridColumns() != renderedAppColumns) render()
            }
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(0, dp(10), 0, dp(8))
        }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        window.insetsController?.hide(WindowInsets.Type.systemBars())
        window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == Screen.HOME) backOnHome() else navigate(parentScreen())
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect { state = it; render() } }
                launch { model.messages.collect { message(getString(it)) } }
            }
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(timeChanges, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }, Context.RECEIVER_NOT_EXPORTED)
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, timeFormatChanges)
    }
    override fun onStop() {
        contentResolver.unregisterContentObserver(timeFormatChanges)
        unregisterReceiver(timeChanges)
        super.onStop()
    }
    override fun onResume() { super.onResume(); model.refresh(); updateDateTime(); if (screen == Screen.SETTINGS) render() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dialog?.dismiss(); dialog = null
        // A Home press explicitly restarts selection; ordinary refreshes keep it.
        // Clear the anchor before rendering so delayed preferences/content also
        // follow the same first-app, first-channel, then date fallback as startup.
        model.focus = null; chromeFocus = null; editFallback = null
        screen = Screen.HOME; scroll.scrollTo(0, 0)
        render(false)
        model.refresh()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("screen", screen.name)
        model.focus?.let { focus ->
            out.putString("focusRow", focus.rowKey); out.putString("focusItem", focus.itemKey)
            out.putInt("focusRowIndex", focus.rowIndex); out.putInt("focusItemIndex", focus.itemIndex)
        }
        model.allAppsFocus?.let { focus ->
            out.putString("allFocusRow", focus.rowKey); out.putString("allFocusItem", focus.itemKey)
            out.putInt("allFocusRowIndex", focus.rowIndex); out.putInt("allFocusItemIndex", focus.itemIndex)
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { dialog?.dismiss(); super.onDestroy() }

    private fun updateDateTime() {
        if (!::clock.isInitialized || !::date.isInitialized) return
        // App language overrides must not change the TV's date/time language or preferences.
        val systemContext = createConfigurationContext(Configuration(resources.configuration).apply {
            setLocales(getSystemService(LocaleManager::class.java).systemLocales)
        })
        val now = Date()
        clock.text = android.text.format.DateFormat.getTimeFormat(systemContext).format(now)
        date.text = android.text.format.DateFormat.getLongDateFormat(systemContext).format(now)
    }

    private fun parentScreen() = when (screen) {
        Screen.FAVOURITES, Screen.LISTS -> Screen.SETTINGS
        else -> Screen.HOME
    }

    private fun pageHeader(): LinearLayout {
        val titleId = when (screen) {
            Screen.SETTINGS -> R.string.settings
            Screen.FAVOURITES -> R.string.apps_favourites
            Screen.LISTS -> R.string.channel_lists
            Screen.APPS -> R.string.all_apps
            Screen.HOME -> R.string.home
        }
        return LinearLayout(this).apply {
            tag = "page-header"; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            addView(action(getString(if (parentScreen() == Screen.SETTINGS) R.string.back_settings else R.string.home),
                "back", R.drawable.ic_back) { navigate(parentScreen()) }, LinearLayout.LayoutParams(-2, dp(46)))
            addView(label(getString(titleId), TvTypography.pageTitle).apply {
                tag = "page-title"; gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; isAccessibilityHeading = true
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(24) })
        }
    }

    private fun navigate(target: Screen) {
        if (target == screen) return
        screen = target; chromeFocus = null; editFallback = null; scroll.scrollTo(0, 0); render(false)
    }

    private fun render(keepFocus: Boolean = true) {
        // Only remote navigation records a chrome selection. Android can temporarily
        // focus the header when children are detached; that must not replace a card anchor.
        if (appliedBackground != state.preferences.background) {
            root.background = backgroundGradient(state.preferences.background)
            appliedBackground = state.preferences.background
        }
        date.isFocusable = screen == Screen.HOME
        val oldScroll = if (keepFocus) scroll.scrollY else 0
        val generation = ++renderGeneration
        // Detach adapters to cancel image requests owned by removed rows.
        rowViews.forEach { it.adapter = null }
        body.removeAllViews(); rowViews.clear(); rowHeadings.clear(); focusRows.clear()
        if (screen != Screen.HOME) body.addView(pageHeader())
        if (screen != Screen.HOME) state.error?.let { body.addView(notice(getString(it))) }
        when (screen) {
            Screen.HOME -> renderHome()
            Screen.APPS -> renderApps()
            Screen.SETTINGS -> renderSettings()
            Screen.FAVOURITES -> renderFavourites()
            Screen.LISTS -> renderLists()
        }
        if (screen == Screen.HOME || screen == Screen.APPS || screen == Screen.FAVOURITES) {
            bodyFocusStops().filter { it !is RecyclerView }.forEach { control ->
                control.setOnKeyListener { _, code, event ->
                    val group = control.parent as? ViewGroup
                    when {
                        event.action != KeyEvent.ACTION_DOWN -> false
                        code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ->
                            moveBodyFocus(control, if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                        group?.tag == "home-apps-options" && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) -> {
                            group.getChildAt(if (code == KeyEvent.KEYCODE_DPAD_LEFT) 0 else group.childCount - 1).requestFocus()
                            true
                        }
                        else -> false
                    }
                }
            }
        }
        body.post {
            if (generation != renderGeneration || dialog?.isShowing == true) return@post
            // Retain the viewport first. Focusing an edited control may need to reveal a new position.
            if (screen != Screen.HOME && keepFocus) scroll.scrollTo(0, oldScroll)
            val tag = if (keepFocus) chromeFocus else null
            val tagged = listOfNotNull(tag?.takeUnless { it.startsWith("card:") }, editFallback.takeIf { keepFocus })
                .mapNotNull { window.decorView.findViewWithTag<View>(it) }.firstOrNull { it.isFocusable && it.isEnabled }
            if (tagged != null && tagged.isFocusable && tagged.isEnabled) tagged.requestFocus()
            else if (screen == Screen.HOME || screen == Screen.APPS || screen == Screen.FAVOURITES) {
                val saved = when (screen) {
                    Screen.HOME -> homeAnchor()
                    Screen.FAVOURITES -> model.favouriteAppsFocus
                    else -> model.allAppsFocus
                }
                val previous = relocateAppAnchor(saved)
                val desired = restoreFocus(previous, focusRows)
                if (desired != null) focusCard(desired.rowIndex, desired.itemIndex, restore = true)
                else (if (screen == Screen.HOME) date else settingsButton).requestFocus()
            } else body.findViewWithTag<View>("back")?.requestFocus()
        }
    }

    private fun homeAnchor() = model.focus?.takeUnless {
        !state.preferences.showApps && (it.rowKey == "apps" || it.rowKey.startsWith("apps:"))
    }

    private fun relocateAppAnchor(anchor: FocusAnchor?): FocusAnchor? {
        if (anchor == null) return null
        // App components can move between grid rows. Channel item IDs are only
        // unique inside their source row, so their row identity must be retained.
        if (screen == Screen.HOME && anchor.rowKey != "apps" && !anchor.rowKey.startsWith("apps:")) return anchor
        val row = focusRows.indexOfFirst { anchor.itemKey in it.items }
        return if (row >= 0) anchor.copy(rowKey = focusRows[row].key, rowIndex = row) else anchor
    }

    private fun focusHomeContent() {
        val desired = restoreFocus(relocateAppAnchor(homeAnchor()), focusRows)
        if (desired != null) focusCard(desired.rowIndex, desired.itemIndex)
        else date.requestFocus()
    }

    private fun backOnHome() {
        val firstRow = rowViews.indexOfFirst { it.visibility == View.VISIBLE && (it.adapter?.itemCount ?: 0) > 0 }
        if (firstRow < 0) {
            // This is an explicit selection, so a refresh must retain it rather than old header focus.
            chromeFocus = "date"
            date.requestFocus()
            return
        }
        val rowIndex = rowViews.indexOfFirst { it.hasFocus() }
        val recycler = rowViews.getOrNull(rowIndex)
        val item = window.decorView.findFocus()?.let { recycler?.findContainingItemView(it) }
        val position = item?.let { recycler?.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
        if (rowIndex == firstRow && position == 0) return
        val targetRow = if (position > 0) rowIndex else firstRow
        val row = focusRows[targetRow]
        // Record the explicit destination before posting focus: a refresh may rebuild
        // these rows and invalidate the pending request, then restore this anchor.
        chromeFocus = null
        model.focus = FocusAnchor(row.key, row.items.first(), targetRow, 0)
        focusCard(targetRow, 0)
    }

    private fun homeGridColumns(): Int {
        val viewport = scroll.width.takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - root.paddingLeft - root.paddingRight)
        val panel = cardGeometry(CardPresentation.COMPACT_APP).width + dp(4)
        return ((viewport - dp(64)).coerceAtLeast(0) / panel).coerceAtLeast(1)
    }

    private fun renderHome() {
        renderedAppColumns = 0
        if (state.preferences.showApps) {
            val installed = state.apps.associateBy { it.key }
            val entries = state.preferences.favouriteApps.map { key ->
                val app = installed[key]
                CardEntry(key, app?.title ?: getString(R.string.unavailable_app_title, key.substringBefore('/')),
                    drawable = app?.banner ?: app?.icon, logo = app?.banner == null, presentation = CardPresentation.COMPACT_APP,
                    open = { app?.let { launch(graph.launcher.app(it.component)) }
                        ?: launch(LaunchResult.Failed(R.string.error_favourite_unavailable)) })
            } + CardEntry("all-apps", getString(R.string.all_apps), drawable = getDrawable(R.drawable.ic_grid), logo = true,
                presentation = CardPresentation.COMPACT_APP, open = { navigate(Screen.APPS) })
            if (state.preferences.appsLayout == AppsLayout.GRID) {
                renderedAppColumns = homeGridColumns()
                rowHeading("apps:0", getString(R.string.apps))
                entries.chunked(renderedAppColumns).forEachIndexed { index, row -> addRow("apps:$index", row) }
            } else {
                rowHeading("apps", getString(R.string.apps))
                addRow("apps", entries)
            }
        }
        if (state.channelState != ChannelLoadState.PermissionDenied) {
            reconcileLists(state.preferences.lists, state.lastRows).forEach { managed ->
                val row = managed.row
                if (row != null && row.items.isNotEmpty()) {
                    rowHeading(row.key, channelListTitle(row.ownerPackage, row.title))
                    addRow(row.key, row.items.map { channelCard(row, it) })
                }
            }
        }
    }

    private fun renderApps() {
        if (state.apps.isEmpty()) body.addView(label(getString(R.string.no_tv_apps)))
        state.apps.chunked(5).forEachIndexed { index, apps ->
            addRow("all:$index", apps.map { app -> CardEntry(app.key, app.title, drawable = app.banner ?: app.icon, logo = app.banner == null,
                presentation = CardPresentation.APP, open = { launch(graph.launcher.app(app.component)) }) })
        }
    }

    private fun rowHeading(key: String, title: String) {
        val heading = label(title, 18f).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; isAccessibilityHeading = true
        }
        rowHeadings[key] = heading
        body.addView(heading)
    }

    private fun channelCard(row: ChannelRow, item: ChannelItem): CardEntry {
        val media = item.artwork()
        return CardEntry(item.key, item.title.ifBlank { getString(R.string.untitled_item) }, artwork = media?.uri ?: row.logoUri,
            logo = media?.isLogo ?: true, ratio = media?.aspectRatio ?: 16f / 9f,
            open = { launch(graph.launcher.channel(row.ownerPackage, item.intentUri), row.ownerPackage) })
    }

    private fun addRow(key: String, entries: List<CardEntry>) {
        val rowIndex = rowViews.size
        val geometry = cardGeometry(entries.firstOrNull()?.presentation ?: CardPresentation.CONTENT)
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            isFocusable = false; clipToPadding = false; clipChildren = false
            setPadding(dp(32), geometry.rowPadding, dp(32), geometry.rowPadding)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            itemAnimator = null
        }
        focusRows += FocusRow(key, entries.map { it.key })
        rowViews += recycler
        recycler.adapter = CardAdapter(entries, { position, itemKey ->
            rowPositions[key] = position; chromeFocus = null
            val anchor = FocusAnchor(key, itemKey, rowIndex, position)
            if (screen == Screen.HOME) {
                // A temporary fallback while the first snapshot loads must not erase a saved channel selection.
                val awaitingSavedRow = restoringFocus && state.channelState == ChannelLoadState.Loading &&
                    model.focus?.rowKey?.let { saved -> focusRows.none { it.key == saved } } == true
                // All apps is the only card before preferences arrive. Do not save
                // that automatic fallback as the user's initial selection.
                val awaitingPreferences = restoringFocus && !state.preferencesLoaded
                if (!awaitingSavedRow && !awaitingPreferences) model.focus = anchor
            } else if (screen == Screen.APPS) model.allAppsFocus = anchor
            else if (screen == Screen.FAVOURITES) model.favouriteAppsFocus = anchor
        }, { direction ->
            moveBodyFocus(recycler, direction)
        }, confineHorizontalFocus = screen == Screen.HOME)
        body.addView(recycler, LinearLayout.LayoutParams(-1, geometry.rowHeight))
    }

    private fun bodyFocusStops(): List<View> = (0 until body.childCount).flatMap { index ->
        val child = body.getChildAt(index)
        when {
            child.tag == "page-header" -> listOfNotNull(child.findViewWithTag<View>("back"))
            child.tag == "home-apps-options" && child is ViewGroup -> (0 until child.childCount).map { child.getChildAt(it) }
            else -> listOf(child)
        }
    }.filter { it.visibility == View.VISIBLE && it.isEnabled &&
        (it is RecyclerView && (it.adapter?.itemCount ?: 0) > 0 || it.isFocusable) }

    private fun moveBodyFocus(from: View, direction: Int): Boolean {
        val group = from.parent as? ViewGroup
        // Up/Down leave the whole two-column row; Left/Right move within it.
        val stops = bodyFocusStops().filter { group?.tag != "home-apps-options" || it === from || it.parent !== group }
        val target = stops.getOrNull(stops.indexOf(from) + direction)
        if (target is RecyclerView) {
            val index = rowViews.indexOf(target)
            val fromRow = rowViews.indexOf(from)
            val sameGrid = screen == Screen.HOME && focusRows.getOrNull(fromRow)?.key?.startsWith("apps:") == true &&
                focusRows[index].key.startsWith("apps:")
            val column = if (sameGrid) rowPositions[focusRows[fromRow].key] else rowPositions[focusRows[index].key]
            focusCard(index, column ?: 0)
        } else if (target != null) target.requestFocus()
        else if (direction < 0) (if (screen == Screen.HOME) date else settingsButton).requestFocus()
        return true
    }

    private fun focusCard(rowIndex: Int, itemIndex: Int, restore: Boolean = false) {
        val recycler = rowViews.getOrNull(rowIndex) ?: return
        val generation = renderGeneration
        val heading = rowHeadings[focusRows.getOrNull(rowIndex)?.key]
        val index = itemIndex.coerceIn(0, (recycler.adapter?.itemCount ?: 1).minus(1).coerceAtLeast(0))
        recycler.scrollToPosition(index)
        fun request() {
            if (generation != renderGeneration) return
            val view = recycler.findViewHolderForAdapterPosition(index)?.itemView ?: return
            restoringFocus = restore
            try {
                if (view.requestFocus() && screen == Screen.HOME) {
                    // Reveal the section as a unit; child focus alone omits its heading.
                    scroll.requestChildRectangleOnScreen(body, android.graphics.Rect(
                        0, ((heading?.top ?: recycler.top) - dp(8)).coerceAtLeast(0), body.width, recycler.bottom,
                    ), true)
                }
            } finally { restoringFocus = false }
        }
        recycler.post {
            if (recycler.findViewHolderForAdapterPosition(index) != null) request()
            else recycler.post { request() }
        }
    }

    private fun renderSettings() {
        val columns = LinearLayout(this)
        fun group(title: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(settingsSection(title))
            columns.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) })
        }
        fun setting(parent: LinearLayout, text: String, key: String, icon: Int, block: () -> Unit): Button {
            val button = action(text, key, icon, block).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                minHeight = dp(62)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) }
            }
            parent.addView(button)
            return button
        }
        fun statusSetting(parent: LinearLayout, text: String, key: String, icon: Int, done: Boolean, explanation: String, block: () -> Unit) {
            setting(parent, text, key, icon, block).apply { isEnabled = !done; alpha = if (done) 0.45f else 1f }
            parent.addView(label(explanation, TvTypography.description).apply {
                tag = "$key-status"
                setPadding(dp(12), 0, dp(12), dp(12))
            })
        }
        val content = group(getString(R.string.content))
        setting(content, getString(R.string.apps_favourites), "settings-apps", R.drawable.ic_grid) { navigate(Screen.FAVOURITES) }
        setting(content, getString(R.string.channel_lists), "settings-lists", R.drawable.ic_lists) { navigate(Screen.LISTS) }
        setting(content, getString(R.string.language), "language", R.drawable.ic_language) { chooseLanguage() }
        setting(content, getString(R.string.background_choice, getString(state.preferences.background.titleRes)), "background", R.drawable.ic_palette) {
            chromeFocus = "background"; chooseBackground()
        }
        val device = group(getString(R.string.device))
        setting(device, getString(R.string.tv_settings), "system-settings", R.drawable.ic_device_settings) {
            openSystem(Intent(Settings.ACTION_SETTINGS))
        }.apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(20) }
        val hasAccess = checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED
        statusSetting(device, getString(R.string.channel_access), "permission", R.drawable.ic_access, hasAccess,
            getString(if (hasAccess) R.string.channel_access_granted else R.string.channel_access_help)) { requestChannels() }
        val home = isDefaultHome()
        statusSetting(device, getString(R.string.set_home), "home-role", R.drawable.ic_home, home,
            getString(if (home) R.string.home_active else R.string.home_help)) { requestHome() }
        body.addView(columns)
    }

    private fun settingsPanel(key: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; tag = key
        background = roundedSurface(TvColors.surface)
        setPadding(dp(16), dp(10), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(10) }
    }

    private fun editAction(text: String, key: String, fallback: String, block: () -> Unit) = action(text, key) {
        chromeFocus = key; editFallback = fallback; block()
    }

    private fun iconAction(description: String, key: String, icon: Int, block: () -> Unit) = action("", key, block = block).apply {
        contentDescription = description; tooltipText = description
        foreground = getDrawable(icon); foregroundGravity = Gravity.CENTER; foregroundTintList = textColors
        setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0
    }

    private fun moveControl(parent: LinearLayout, description: String, key: String, fallback: String, icon: Int, enabled: Boolean, block: () -> Unit) {
        parent.addView(iconAction(description, key, icon) { chromeFocus = key; editFallback = fallback; block() }.apply {
            isEnabled = enabled; alpha = if (enabled) 1f else 0.35f
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(6) })
    }

    private fun renderFavourites() {
        val installed = state.apps.associateBy { it.key }
        val favourites = state.preferences.favouriteApps
        body.addView(settingsSection(getString(R.string.favorite)))
        if (favourites.isEmpty()) body.addView(notice(getString(R.string.no_favourites)))
        else addRow("favourites", favourites.map { key ->
            val app = installed[key]
            val title = app?.title ?: getString(R.string.unavailable_app_title, key.substringBefore('/'))
            CardEntry(key, title, drawable = app?.banner ?: app?.icon, logo = app?.banner == null, presentation = CardPresentation.COMPACT_APP,
                open = { editApp(key, title, true) })
        })
        body.addView(settingsSection(getString(R.string.available_apps)))
        val available = state.apps.filterNot { it.key in favourites }
        if (available.isEmpty()) body.addView(label(getString(R.string.no_available_apps), TvTypography.description))
        available.chunked(5).forEachIndexed { index, apps ->
            addRow("available-apps:$index", apps.map { app ->
                CardEntry(app.key, app.title, drawable = app.banner ?: app.icon, logo = app.banner == null, presentation = CardPresentation.COMPACT_APP,
                    open = { editApp(app.key, app.title, false) })
            })
        }
        body.addView(settingsSection(getString(R.string.options), gap = 24))
        val homeOptions = LinearLayout(this).apply {
            tag = "home-apps-options"; orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
        }
        homeOptions.addView(action(getString(R.string.apps_layout_choice,
            getString(if (state.preferences.appsLayout == AppsLayout.ROW) R.string.layout_row else R.string.layout_grid)),
            "home-apps-layout", R.drawable.ic_grid) {
            chromeFocus = "home-apps-layout"; chooseAppsLayout()
        }.apply { minHeight = dp(60) }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(8) })
        homeOptions.addView(Switch(this).apply {
            text = getString(R.string.show_apps_home); tag = "home-apps-visible"
            textSize = TvTypography.item; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(controlTextColors()); background = focusBackground(context, control = true)
            isChecked = state.preferences.showApps; isFocusable = true; showText = false
            setPadding(dp(18), dp(8), dp(18), dp(8)); minHeight = dp(60); maxLines = 2
            focusVisuals()
            setOnCheckedChangeListener { _, checked ->
                chromeFocus = "home-apps-visible"; model.setShowApps(checked)
            }
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(8) })
        body.addView(homeOptions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }

    private fun editApp(key: String, title: String, favourite: Boolean) {
        val choices = mutableListOf<Pair<Int, () -> Unit>>()
        if (favourite) {
            val index = state.preferences.favouriteApps.indexOf(key)
            if (index > 0) choices += R.string.move_left to { model.moveApp(key, -1) }
            if (index in 0 until state.preferences.favouriteApps.lastIndex) choices += R.string.move_right to { model.moveApp(key, 1) }
            choices += R.string.remove to { model.favourite(key, false) }
        } else choices += R.string.add_to_favourites to { model.favourite(key, true) }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(title)
            .setItems(choices.map { getString(it.first) }.toTypedArray()) { _, index -> choices[index].second() }
            .setNegativeButton(R.string.cancel, null).create().also { created ->
                created.setOnDismissListener { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) render() }
                created.show()
            }
    }

    private fun settingsSection(title: String, gap: Int = 0) = label(title, TvTypography.section).apply {
        setTextColor(TvColors.secondary)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAccessibilityHeading = true
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap); bottomMargin = dp(10) }
    }

    private fun settingsItem(text: String, size: Float = TvTypography.item) = label(text, size).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(TvColors.text)
    }

    private fun renderLists() {
        channelStatus()
        val readable = state.channelState is ChannelLoadState.Ready
        val managed = reconcileLists(state.preferences.lists, state.lastRows)
        body.addView(settingsSection(getString(R.string.selected_lists)))
        if (managed.isEmpty()) body.addView(notice(getString(R.string.no_selected_lists)))
        managed.forEachIndexed { index, entry ->
            val panel = settingsPanel("selected:${entry.saved.key}")
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(label((index + 1).toString(), TvTypography.description).apply {
                gravity = Gravity.CENTER; background = roundedSurface(TvColors.ambient, 10)
            }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(settingsItem(channelListTitle(entry.saved.ownerPackage, entry.saved.title)).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val status = when {
                !readable -> getString(R.string.access_unconfirmed)
                entry.row == null -> getString(R.string.unavailable)
                entry.row.items.isEmpty() -> getString(R.string.empty_list_status)
                else -> resources.getQuantityString(R.plurals.published_items, entry.row.items.size, entry.row.items.size)
            }
            details.addView(label(status, TvTypography.description))
            line.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            val prefix = "list:${entry.saved.key}"
            val fallback = "$prefix:forget"
            moveControl(line, getString(R.string.move_up), "$prefix:up", fallback, R.drawable.ic_arrow_up, index > 0) { model.move(entry.saved.key, -1) }
            moveControl(line, getString(R.string.move_down), "$prefix:down", fallback, R.drawable.ic_arrow_down, index < managed.lastIndex) { model.move(entry.saved.key, 1) }
            val neighbour = managed.getOrNull(index + 1) ?: managed.getOrNull(index - 1)
            line.addView(editAction(getString(R.string.remove), fallback, neighbour?.let { "list:${it.saved.key}:forget" } ?: "add:${entry.saved.key}") {
                model.forget(entry.saved.key)
            }, LinearLayout.LayoutParams(dp(114), -2).apply { marginStart = dp(8) })
            panel.addView(line); body.addView(panel)
        }
        if (readable) {
            body.addView(settingsSection(getString(R.string.available_lists), gap = 24))
            val savedKeys = state.preferences.lists.map { it.key }.toSet()
            val available = state.lastRows.filterNot { it.key in savedKeys }
            if (available.isEmpty()) body.addView(notice(if (state.lastRows.isEmpty())
                getString(R.string.no_readable_lists)
                else getString(R.string.all_lists_selected)))
            available.groupBy { it.ownerPackage }.entries.sortedBy { sourceName(it.key).lowercase(resources.configuration.locales[0]) }.forEach { (owner, rows) ->
                val panel = settingsPanel("available:$owner").apply {
                    background = roundedSurface(TvColors.background).apply { setStroke(dp(1), TvColors.surface) }
                }
                val groupHeader = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(4), dp(4), dp(8))
                }
                val appIcon = state.apps.firstOrNull { it.component.packageName == owner }?.icon
                    ?: runCatching { packageManager.getApplicationIcon(owner) }.getOrNull()
                    ?: getDrawable(R.drawable.ic_grid)
                groupHeader.addView(ImageView(this).apply {
                    setImageDrawable(appIcon); scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
                groupHeader.addView(settingsItem(sourceName(owner), TvTypography.group).apply {
                    setTextColor(TvColors.accent); isAccessibilityHeading = true
                }, LinearLayout.LayoutParams(0, -2, 1f))
                panel.addView(groupHeader)
                rows.forEach { row ->
                    val line = LinearLayout(this).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = roundedSurface(TvColors.surface, 10)
                        setPadding(dp(12), dp(8), dp(8), dp(8))
                    }
                    val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    details.addView(settingsItem(channelListTitle(row.ownerPackage, row.title)))
                    details.addView(label(resources.getQuantityString(R.plurals.published_items, row.items.size, row.items.size), TvTypography.description))
                    line.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    line.addView(editAction(getString(R.string.add), "add:${row.key}", "list:${row.key}:forget") { model.select(row) },
                        LinearLayout.LayoutParams(dp(110), -2).apply { marginStart = dp(12) })
                    panel.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                }
                body.addView(panel)
            }
        }
        body.addView(action(getString(R.string.refresh), "lists-refresh", R.drawable.ic_refresh) { model.refresh() })
    }

    private fun channelStatus() {
        when (val load = state.channelState) {
            ChannelLoadState.Loading -> if (state.lastRows.isEmpty()) body.addView(label(getString(R.string.loading_channels), TvTypography.description))
            ChannelLoadState.PermissionDenied -> {
                body.addView(notice(getString(R.string.channel_permission_intro)))
                body.addView(action(getString(R.string.allow_channels), "allow", R.drawable.ic_access) { requestChannels() })
            }
            is ChannelLoadState.Failed -> {
                body.addView(notice(getString(R.string.channel_failure, getString(load.message))))
                body.addView(action(getString(R.string.retry), "retry", R.drawable.ic_refresh) { model.refresh() })
            }
            is ChannelLoadState.Ready -> Unit
        }
    }

    private fun chooseAppsLayout() {
        val choices = arrayOf(getString(R.string.layout_row), getString(R.string.layout_grid))
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(R.string.apps_layout)
            .setSingleChoiceItems(choices, state.preferences.appsLayout.ordinal) { selected, index ->
                selected.dismiss(); model.setAppsLayout(AppsLayout.entries[index])
            }.setNegativeButton(R.string.cancel, null).create().also { created ->
                created.setOnDismissListener { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) render() }
                created.show()
            }
    }

    private fun chooseBackground() {
        val themes = BackgroundTheme.entries.toList()
        val choices = object : ArrayAdapter<BackgroundTheme>(this, android.R.layout.simple_list_item_single_choice, themes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val theme = themes[position]
                view.text = getString(theme.titleRes)
                val swatch = backgroundGradient(theme).apply {
                    cornerRadius = dp(6).toFloat(); setStroke(dp(1), TvColors.secondary)
                    setBounds(0, 0, dp(52), dp(28))
                }
                view.setCompoundDrawablesRelative(swatch, null, null, null)
                view.compoundDrawablePadding = dp(12)
                return view
            }
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(R.string.background)
            .setSingleChoiceItems(choices, themes.indexOf(state.preferences.background)) { selected, index ->
                selected.dismiss(); model.setBackground(themes[index])
            }.setNegativeButton(R.string.cancel, null).create().also { created ->
                created.setOnDismissListener { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) render() }
                created.show()
            }
    }

    private fun chooseLanguage() {
        val manager = getSystemService(LocaleManager::class.java)
        val tags = arrayOf("", "en", "uk")
        val current = manager.applicationLocales.get(0)?.language.orEmpty()
        val options = arrayOf(getString(R.string.language_system), getString(R.string.language_english), getString(R.string.language_ukrainian))
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(R.string.language)
            .setSingleChoiceItems(options, tags.indexOf(current).coerceAtLeast(0)) { selectedDialog, index ->
                selectedDialog.dismiss()
                manager.applicationLocales = LocaleList.forLanguageTags(tags[index])
            }.setNegativeButton(R.string.cancel, null).create().also { it.show() }
    }

    private fun launch(result: LaunchResult, owner: String? = null) {
        if (result is LaunchResult.Failed) {
            dialog?.dismiss()
            val builder = AlertDialog.Builder(this).setTitle(getString(R.string.unable_open)).setMessage(getString(result.message)).setNegativeButton(getString(R.string.close), null)
            if (owner != null) builder.setPositiveButton(getString(R.string.open_source)) { _, _ -> launch(graph.launcher.source(owner)) }
            dialog = builder.create().also { created ->
                created.setOnDismissListener { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) render() }
                created.show()
            }
        }
    }
    private fun requestChannels() { permission.launch(READ_TV_LISTINGS) }
    private fun isDefaultHome(): Boolean {
        // This reports the saved Home choice. TV firmware can still prefer its
        // higher-priority system launcher when the Home button is pressed.
        val roleHeld = runCatching {
            val manager = getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) manager.isRoleHeld(RoleManager.ROLE_HOME) else null
        }.getOrNull()
        if (roleHeld != null) return roleHeld
        return runCatching {
            packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == packageName
        }.getOrDefault(false)
    }

    private fun requestHome() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            if (manager.isRoleHeld(RoleManager.ROLE_HOME)) message(getString(R.string.already_home))
            else try { homeRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
            catch (_: Exception) { message(getString(R.string.home_chooser_failed)) }
        } else message(getString(R.string.home_role_unavailable))
    }
    private fun openSystem(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { message(getString(R.string.system_settings_unavailable)) }
    }
    private fun listTitle(value: String) = value.ifBlank { getString(R.string.untitled_list) }
    private fun channelListTitle(owner: String, title: String) = getString(R.string.application_channel, sourceName(owner), listTitle(title))
    private fun sourceName(owner: String): String = state.apps.firstOrNull { it.component.packageName == owner }?.title ?: runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(owner, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(owner)
    private fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
}
