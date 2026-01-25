package com.example.spiritwebview

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityBrowserBinding
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class Shortcut(
    var name: String, 
    var script: String, 
    var isAutoRun: Boolean = false, 
    var isVisibleOnMain: Boolean = false
)

data class TabItem(
    val id: Long = System.currentTimeMillis(),
    var url: String = "https://goonee.netlify.app/",
    var title: String = "New Tab"
)

@SuppressLint("SetJavaScriptEnabled")
class BrowserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBrowserBinding
    private lateinit var webViewA: WebView
    private lateinit var webViewB: WebView
    private lateinit var prefs: SharedPreferences
    
    private var isSplit = false
    private var verticalSplit = false
    private var activeTab = 1
    private var isPanelVisible = false
    private var isDesktopMode = false
    private var textZoom = 100
    
    private val shortcuts = mutableListOf<Shortcut>()
    private lateinit var menuAdapter: MenuShortcutAdapter
    private lateinit var mainBarAdapter: MainShortcutAdapter

    private val tabsList = mutableListOf<TabItem>()
    private lateinit var tabsAdapter: TabsAdapter
    private var currentTabId: Long = -1

    private val historyList = mutableListOf<String>()
    private lateinit var historyAdapter: ArrayAdapter<String>

    companion object {
        private const val PREF_NAME = "gooser_settings"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_SHORTCUTS = "saved_shortcuts"
        private const val KEY_HISTORY = "url_history"
        private const val KEY_SAVED_TABS = "saved_tabs"
        private const val KEY_CURRENT_TAB_ID = "current_tab_id"
        private const val DEFAULT_HOME = "https://goonee.netlify.app/"
        private const val GOOGLE_SEARCH = "https://www.google.com/search?q="
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        webViewA = binding.webviewA
        webViewB = binding.webviewB

        setupWebView(webViewA)
        setupWebView(webViewB)
        setupUI()
        setupShortcutSystem()
        setupTabsSystem()
        
        loadShortcuts()
        loadHistory()
        loadTabs()

        val startUrl = if (tabsList.isNotEmpty()) {
            tabsList.find { it.id == currentTabId }?.url ?: tabsList.first().url
        } else {
            val home = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
            val firstTab = TabItem(url = home)
            tabsList.add(firstTab)
            currentTabId = firstTab.id
            home
        }
        
        loadUrl(startUrl)
        showSingle()
        setupSplitter()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        historyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, historyList)
        binding.edtUrl.setAdapter(historyAdapter)
        binding.edtUrl.threshold = 1

        binding.edtUrl.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val item = clipData.getItemAt(0)
                        val text = item.text?.toString() ?: item.uri?.toString()
                        if (!text.isNullOrEmpty()) {
                            val input = text.trim()
                            val urlToLoad = if (isValidUrl(input)) {
                                if (!input.startsWith("http")) "https://$input" else input
                            } else if (input.startsWith("http://") || input.startsWith("https://") || 
                                       input.startsWith("content://") || input.startsWith("file://")) {
                                input
                            } else {
                                GOOGLE_SEARCH + input
                            }
                            binding.edtUrl.setText(urlToLoad)
                            loadUrl(urlToLoad)
                        }
                    }
                    true
                }
                else -> true
            }
        }

        var dX = 0f; var dY = 0f; var startX = 0f; var startY = 0f
        binding.fabMainToggle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX; dY = v.y - event.rawY
                    startX = event.rawX; startY = event.rawY
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - startX) < 10 && abs(event.rawY - startY) < 10) {
                        if (binding.topNavigationBar.visibility == View.GONE) {
                            binding.topNavigationBar.visibility = View.VISIBLE
                        } else {
                            togglePanel(true)
                        }
                    } else {
                        val screenWidth = binding.webRoot.width.toFloat()
                        val targetX = if (event.rawX < screenWidth / 2) 16f else screenWidth - v.width - 16f
                        v.animate().x(targetX).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }

        var pX = 0f; var pY = 0f
        binding.panelHeader.setOnTouchListener { _, event ->
            val panel = binding.floatingMenuPanel
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pX = panel.x - event.rawX
                    pY = panel.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panel.animate().x(event.rawX + pX).y(event.rawY + pY).setDuration(0).start()
                    true
                }
                else -> false
            }
        }

        binding.btnClosePanel.setOnClickListener { togglePanel(false) }
        binding.btnBack.setOnClickListener { getWebViewForTab(activeTab).goBack() }
        binding.btnRefresh.setOnClickListener { getWebViewForTab(activeTab).reload() }
        binding.btnHome.setOnClickListener { 
            val home = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
            loadUrl(home) 
        }
        
        binding.edtUrl.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val input = v.text.toString()
                val urlToLoad = if (isValidUrl(input)) {
                    if (!input.startsWith("http")) "https://$input" else input
                } else {
                    GOOGLE_SEARCH + input
                }
                addToHistory(urlToLoad)
                loadUrl(urlToLoad)
                true
            } else false
        }

        binding.btnAddShortcut.setOnClickListener { showAddEditShortcutDialog(null) }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
        binding.btnSplitMode.setOnClickListener { toggleSplit() }
        binding.btnFullscreenMode.setOnClickListener {
            val isVisible = binding.topNavigationBar.visibility == View.VISIBLE
            binding.topNavigationBar.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.tabsRecyclerView.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.5f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1.0f; true }
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val item = clipData.getItemAt(0)
                        val text = item.text?.toString() ?: item.uri?.toString()
                        if (!text.isNullOrEmpty()) {
                            val input = text.trim()
                            val urlToLoad = if (isValidUrl(input)) {
                                if (!input.startsWith("http")) "https://$input" else input
                            } else if (input.startsWith("http://") || input.startsWith("https://") || 
                                       input.startsWith("content://") || input.startsWith("file://")) {
                                input
                            } else {
                                GOOGLE_SEARCH + input
                            }
                            addNewTab(urlToLoad)
                        }
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun addNewTab(url: String) {
        val newTab = TabItem(url = url)
        tabsList.add(newTab)
        currentTabId = newTab.id
        tabsAdapter.notifyItemInserted(tabsList.size - 1)
        binding.tabsRecyclerView.scrollToPosition(tabsList.size - 1)
        loadUrl(url)
        saveTabs()
    }

    private fun isValidUrl(url: String): Boolean {
        return android.util.Patterns.WEB_URL.matcher(url).matches()
    }

    private fun addToHistory(url: String) {
        if (!historyList.contains(url)) {
            historyList.add(0, url)
            if (historyList.size > 50) historyList.removeAt(50)
            historyAdapter.notifyDataSetChanged()
            saveHistory()
        }
    }

    private fun saveHistory() {
        val array = JSONArray()
        historyList.forEach { array.put(it) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun loadHistory() {
        val saved = prefs.getString(KEY_HISTORY, null)
        if (saved != null) {
            val array = JSONArray(saved)
            historyList.clear()
            for (i in 0 until array.length()) historyList.add(array.getString(i))
        }
    }

    private fun saveTabs() {
        val array = JSONArray()
        tabsList.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("url", it.url)
            obj.put("title", it.title)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_TABS, array.toString()).apply()
        prefs.edit().putLong(KEY_CURRENT_TAB_ID, currentTabId).apply()
    }

    private fun loadTabs() {
        val saved = prefs.getString(KEY_SAVED_TABS, null)
        if (saved != null) {
            try {
                val array = JSONArray(saved)
                tabsList.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    tabsList.add(TabItem(obj.getLong("id"), obj.getString("url"), obj.getString("title")))
                }
                currentTabId = prefs.getLong(KEY_CURRENT_TAB_ID, -1)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun setupTabsSystem() {
        tabsAdapter = TabsAdapter(tabsList, 
            onClick = { tab ->
                currentTabId = tab.id
                loadUrl(tab.url)
                saveTabs()
            },
            onLongClick = { tab, position ->
                if (tabsList.size > 1) {
                    showCloseTabDialog(tab, position)
                } else {
                    Toast.makeText(this, "ต้องมีอย่างน้อย 1 แท็บ", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.tabsRecyclerView.adapter = tabsAdapter
        binding.btnAddTab.setOnClickListener {
            val home = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
            addNewTab(home)
        }
    }

    private fun showCloseTabDialog(tab: TabItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("ปิดแท็บ")
            .setMessage("คุณต้องการปิดแท็บ '${tab.title}' ใช่หรือไม่?")
            .setPositiveButton("ปิด") { _, _ ->
                tabsList.removeAt(position)
                tabsAdapter.notifyItemRemoved(position)
                if (currentTabId == tab.id) {
                    val nextTab = if (position < tabsList.size) tabsList[position] else tabsList.last()
                    currentTabId = nextTab.id
                    loadUrl(nextTab.url)
                }
                
                if (isSplit && tabsList.size == 1) {
                    toggleSplit()
                }
                saveTabs()
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun togglePanel(show: Boolean) {
        isPanelVisible = show
        binding.floatingMenuPanel.visibility = if (show) View.VISIBLE else View.GONE
        binding.fabMainToggle.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setupShortcutSystem() {
        menuAdapter = MenuShortcutAdapter(shortcuts, 
            onExecute = { executeShortcut(it) },
            onToggleEye = { saveShortcuts(); notifyShortcutChanged() },
            onLongClick = { s, p -> showEditDeleteDialog(s, p) }
        )
        binding.menuShortcutRecycler.adapter = menuAdapter
        mainBarAdapter = MainShortcutAdapter(shortcuts) { executeShortcut(it) }
        binding.mainShortcutBar.adapter = mainBarAdapter
    }

    private fun notifyShortcutChanged() {
        menuAdapter.notifyDataSetChanged()
        mainBarAdapter.updateList()
    }

    private fun executeShortcut(shortcut: Shortcut) {
        val webView = getWebViewForTab(activeTab)
        val wrappedScript = "(function() { try { ${shortcut.script}; return 'SUCCESS'; } catch(e) { return 'ERROR:' + e.stack; } })()"
        
        webView.evaluateJavascript(wrappedScript) { result ->
            val cleanResult = result?.removeSurrounding("\"") ?: ""
            if (cleanResult.startsWith("ERROR:")) {
                val errorLog = cleanResult.removePrefix("ERROR:")
                val snackbar = Snackbar.make(binding.root, "Script Error!", 2000)
                snackbar.setAction("Copy Log") {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("JS Error", errorLog))
                    Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
                }
                snackbar.show()
            } else {
                Toast.makeText(this, "Script: ${shortcut.name} run success", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddEditShortcutDialog(shortcut: Shortcut?, position: Int = -1) {
        val nameInput = EditText(this).apply { hint = "ชื่อสคริปต์"; shortcut?.let { setText(it.name) } }
        val scriptInput = EditText(this).apply { hint = "JavaScript Code"; shortcut?.let { setText(it.script) } }
        val autoRunCheck = CheckBox(this).apply { text = "รันอัตโนมัติ (Background)"; isChecked = shortcut?.isAutoRun ?: false }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 20)
            addView(nameInput); addView(scriptInput); addView(autoRunCheck)
        }
        AlertDialog.Builder(this).setTitle(if (shortcut == null) "เพิ่มสคริปต์" else "แก้ไขสคริปต์").setView(layout)
            .setPositiveButton("บันทึก") { _, _ ->
                val name = nameInput.text.toString()
                val script = scriptInput.text.toString()
                if (name.isNotEmpty() && script.isNotEmpty()) {
                    if (shortcut == null) shortcuts.add(Shortcut(name, script, autoRunCheck.isChecked, false))
                    else { shortcut.name = name; shortcut.script = script; shortcut.isAutoRun = autoRunCheck.isChecked }
                    saveShortcuts(); notifyShortcutChanged()
                }
            }.setNegativeButton("ยกเลิก", null).show()
    }

    private fun showEditDeleteDialog(shortcut: Shortcut, position: Int) {
        AlertDialog.Builder(this).setTitle(shortcut.name).setItems(arrayOf("แก้ไข", "ลบ")) { _, which ->
            if (which == 0) showAddEditShortcutDialog(shortcut, position)
            else { shortcuts.removeAt(position); saveShortcuts(); notifyShortcutChanged() }
        }.show()
    }

    private fun saveShortcuts() {
        val array = JSONArray()
        shortcuts.forEach {
            val obj = JSONObject(); obj.put("name", it.name); obj.put("script", it.script)
            obj.put("isAutoRun", it.isAutoRun); obj.put("isVisibleOnMain", it.isVisibleOnMain)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }

    private fun loadShortcuts() {
        val saved = prefs.getString(KEY_SHORTCUTS, null)
        if (saved != null) {
            val array = JSONArray(saved); shortcuts.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                shortcuts.add(Shortcut(obj.getString("name"), obj.getString("script"), obj.getBoolean("isAutoRun"), obj.getBoolean("isVisibleOnMain")))
            }
        } else {
            shortcuts.add(Shortcut("Dark Mode", "document.body.style.backgroundColor='#222';document.body.style.color='#fff';", false, true))
        }
    }

    private fun loadUrl(url: String) {
        getWebViewForTab(activeTab).loadUrl(url); binding.edtUrl.setText(url)
        tabsList.find { it.id == currentTabId }?.url = url
        saveTabs()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(w: WebView) {
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            allowFileAccess = true; allowContentAccess = true; loadWithOverviewMode = true
            useWideViewPort = true; mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        }
        w.setDownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
                Toast.makeText(this, "Opening download link...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "No app to handle download", Toast.LENGTH_SHORT).show()
            }
        }
        w.setOnLongClickListener {
            val result = w.hitTestResult
            if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val url = result.extra
                if (url != null) {
                    val data = ClipData.newPlainText("url", url)
                    val shadow = View.DragShadowBuilder(w)
                    w.startDragAndDrop(data, shadow, null, 0)
                    true
                } else false
            } else false
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (view == getWebViewForTab(activeTab)) {
                    tabsList.find { it.id == currentTabId }?.let { 
                        it.title = title ?: "New Tab"
                        tabsAdapter.notifyDataSetChanged()
                        saveTabs()
                    }
                }
            }
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                shortcuts.filter { it.isAutoRun }.forEach { view?.evaluateJavascript(it.script, null) }
                if (view == getWebViewForTab(activeTab)) {
                    url?.let { 
                        binding.edtUrl.setText(it)
                        tabsList.find { it.id == currentTabId }?.url = it
                        saveTabs()
                    }
                }
            }
        }
        w.setOnTouchListener { v, _ ->
            activeTab = if (v.id == R.id.webviewA) 1 else 2
            v.requestFocus(); binding.edtUrl.setText(w.url); false
        }
    }

    override fun onBackPressed() {
        val currentWebView = getWebViewForTab(activeTab)
        if (currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        webViewA.onPause()
        webViewB.onPause()
    }

    override fun onResume() {
        super.onResume()
        webViewA.onResume()
        webViewB.onResume()
    }

    override fun onDestroy() {
        binding.webContainerA.removeAllViews()
        binding.webContainerB.removeAllViews()
        webViewA.destroy()
        webViewB.destroy()
        super.onDestroy()
    }

    private fun adjustWeightsByVal(weightA: Float) {
        val lpA = binding.webContainerA.layoutParams as LinearLayout.LayoutParams
        val lpB = binding.webContainerB.layoutParams as LinearLayout.LayoutParams
        if (verticalSplit) {
            lpA.width = 0; lpA.height = -1; lpB.width = 0; lpB.height = -1
        } else {
            lpA.width = -1; lpA.height = 0; lpB.width = -1; lpB.height = 0
        }
        lpA.weight = weightA; lpB.weight = 1.0f - weightA
        binding.webContainerA.layoutParams = lpA; binding.webContainerB.layoutParams = lpB
    }

    fun toggleSplit(p: String = "") {
        isSplit = !isSplit
        binding.webContainerB.visibility = if (isSplit) View.VISIBLE else View.GONE
        binding.splitHandle.visibility = if (isSplit) View.VISIBLE else View.GONE
        
        if (isSplit) {
            val home = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
            activeTab = 2
            addNewTab(home)
            webViewB.requestFocus()
        } else {
            activeTab = 1
            webViewB.loadUrl("about:blank")
            webViewA.requestFocus()
        }
        
        adjustWeightsByVal(if (isSplit) 0.5f else 1.0f)
    }

    private fun showSingle() { adjustWeightsByVal(if (isSplit) 0.5f else 1.0f) }
    private fun getWebViewForTab(tab: Int) = if (tab == 2 && isSplit) webViewB else webViewA

    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val cbDesktop = CheckBox(this).apply {
            text = "โหมดเดสท็อป (Desktop Mode)"
            isChecked = isDesktopMode
            textSize = 16f
            setOnCheckedChangeListener { _, isChecked ->
                isDesktopMode = isChecked
                applyDesktopMode(webViewA, isChecked)
                applyDesktopMode(webViewB, isChecked)
            }
        }
        layout.addView(cbDesktop)

        val zoomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 30, 0, 30)
        }
        val tvZoom = TextView(this).apply { text = "ขนาดตัวอักษร: $textZoom%"; textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnZoomOut = Button(this, null, android.R.attr.buttonStyleSmall).apply { text = "-" }
        val btnZoomIn = Button(this, null, android.R.attr.buttonStyleSmall).apply { text = "+" }
        
        btnZoomIn.setOnClickListener {
            textZoom += 10
            tvZoom.text = "ขนาดตัวอักษร: $textZoom%"
            webViewA.settings.textZoom = textZoom
            webViewB.settings.textZoom = textZoom
        }
        btnZoomOut.setOnClickListener {
            if (textZoom > 50) {
                textZoom -= 10
                tvZoom.text = "ขนาดตัวอักษร: $textZoom%"
                webViewA.settings.textZoom = textZoom
                webViewB.settings.textZoom = textZoom
            }
        }
        zoomLayout.addView(tvZoom); zoomLayout.addView(btnZoomOut); zoomLayout.addView(btnZoomIn)
        layout.addView(zoomLayout)

        val buttons = arrayOf(
            "เปิด/ปิด 2 หน้าจอ" to { toggleSplit() },
            "สลับแนวตั้ง/แนวนอน" to { 
                verticalSplit = !verticalSplit
                binding.webContainers.orientation = if (verticalSplit) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                adjustWeightsByVal(if (isSplit) 0.5f else 1.0f)
                val lpHandle = binding.splitHandle.layoutParams
                if (verticalSplit) {
                    lpHandle.width = (8 * resources.displayMetrics.density).toInt(); lpHandle.height = -1
                } else {
                    lpHandle.width = -1; lpHandle.height = (8 * resources.displayMetrics.density).toInt()
                }
                binding.splitHandle.layoutParams = lpHandle
            },
            "ตั้งค่าหน้าแรก (Home URL)" to { showSetHomeUrlDialog() },
            "วิธีใช้งาน (Guide)" to { showGuideDialog() }
        )

        buttons.forEach { (label, action) ->
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { action(); if (label != "ตั้งค่าหน้าแรก (Home URL)" && label != "วิธีใช้งาน (Guide)") (parent.parent.parent as? AlertDialog)?.dismiss() }
            })
        }

        AlertDialog.Builder(this).setTitle("ตั้งค่า Gooser").setView(layout).setPositiveButton("ปิด", null).show()
    }

    private fun showGuideDialog() {
        val guideText = """
            💬 [ตัวอย่างการสั่ง AI เพื่อเขียนสคริปต์]
            
            ตัวอย่างที่ 1: "ช่วยเขียน javascript สำหรับใช้ในแอพ GooneeBrowser ที่กดรันแล้วจะแสดงทุกปุ่มทุกฟังชั่นที่ถูกซ่อนในหน้าเว็บทั้งหมด"
            💡 AI: (function(){ document.querySelectorAll('*').forEach(el => { if(getComputedStyle(el).display === 'none') el.style.display = 'block'; if(getComputedStyle(el).visibility === 'hidden') el.style.visibility = 'visible'; }); })();
            
            ตัวอย่างที่ 2: "ขอสคริปต์ดึงลิ้งค์วิดีโอหรือปุ่มดาวน์โหลดที่ถูกล็อคไว้ให้แสดงออกมาหน่อย"
            💡 AI: (function(){ document.querySelectorAll('video, a[href*="download"]').forEach(el => { el.style.border = '3px solid lime'; el.style.display = 'block !important'; }); })();
            
            ตัวอย่างที่ 3: "ลบพวกหน้าต่าง Pop-up หรือ Overlay ที่บังหน้าจอออกให้หมดเพื่อกดปุ่มข้างหลังได้"
            💡 AI: (function(){ document.querySelectorAll('div[class*="popup"], div[class*="modal"], div[class*="overlay"]').forEach(el => el.remove()); })();
            
            -------------------------------------------
            📌 [คำอธิบายฟังก์ชันต่างๆ ของ Gooser]
            
            1. โหมด 2 หน้าจอ (Split Mode): กดเพื่อแบ่งหน้าจอเปิด 2 เว็บพร้อมกัน เหมาะสำหรับดูวิดีโอไปพร้อมกับอ่านแชทหรือหาข้อมูล
            2. ระบบสคริปต์ (Shortcuts): คุณสามารถนำโค้ด JavaScript มาบันทึกไว้เพื่อสั่งงานเว็บอัตโนมัติ เช่น การกดปุ่มซ้ำๆ หรือการเปลี่ยนสีหน้าเว็บ
            3. โหมดเดสท็อป (Desktop Mode): หลอกหน้าเว็บว่าเราใช้งานจากคอมพิวเตอร์ เพื่อให้เห็นหน้าตาเว็บแบบเต็ม ไม่ใช่เวอร์ชันมือถือ
            4. การลากและวาง (Drag & Drop): คุณสามารถลากลิ้งค์หรือรูปภาพจากหน้าเว็บไปวางที่ "แถบแท็บ" หรือ "ช่อง URL" เพื่อเปิดหน้านั้นได้ทันที
            5. การจดจำสถานะ: แอพจะจำแท็บทั้งหมดที่คุณเปิดค้างไว้ แม้ปิดเครื่องแล้วเปิดใหม่ แท็บเดิมก็จะยังอยู่ครบถ้วน
        """.trimIndent()

        val textView = TextView(this).apply {
            text = guideText
            setPadding(50, 40, 50, 40)
            textSize = 14f
            movementMethod = android.text.method.ScrollingMovementMethod()
        }

        AlertDialog.Builder(this)
            .setTitle("คู่มือการใช้งาน Gooser")
            .setView(textView)
            .setPositiveButton("เข้าใจแล้ว", null)
            .show()
    }

    private fun applyDesktopMode(webView: WebView, enabled: Boolean) {
        val settings = webView.settings
        if (enabled) {
            val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            settings.userAgentString = desktopUA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = null
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        webView.reload()
    }

    private fun showSetHomeUrlDialog() {
        val currentHome = prefs.getString(KEY_HOME_URL, DEFAULT_HOME)
        val input = EditText(this).apply { setText(currentHome) }
        AlertDialog.Builder(this).setTitle("ตั้งค่าหน้าแรก").setView(input)
            .setPositiveButton("บันทึก") { _, _ ->
                val newUrl = input.text.toString()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString(KEY_HOME_URL, newUrl).apply()
                    Toast.makeText(this, "บันทึกเรียบร้อย", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("ยกเลิก", null).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSplitter() {
        binding.splitHandle.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { v.performClick(); true }
                MotionEvent.ACTION_MOVE -> {
                    val container = binding.webContainers
                    val location = IntArray(2); container.getLocationOnScreen(location)
                    if (verticalSplit) {
                        val relativeX = ev.rawX - location[0]
                        adjustWeightsByVal((relativeX / container.width).coerceIn(0.05f, 0.95f))
                    } else {
                        val relativeY = ev.rawY - location[1]
                        adjustWeightsByVal((relativeY / container.height).coerceIn(0.05f, 0.95f))
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun navigate(p: String) { try { loadUrl(JSONObject(p).getString("url")) } catch(e:Exception){} }
    fun injectLayers(p: String) {}
    fun goBack(p: String) { runOnUiThread { getWebViewForTab(activeTab).goBack() } }
    fun reload(p: String) { runOnUiThread { getWebViewForTab(activeTab).reload() } }
    fun setFallback(p: String) {}

    inner class TabsAdapter(
        private val list: List<TabItem>, 
        val onClick: (TabItem) -> Unit,
        val onLongClick: (TabItem, Int) -> Unit
    ) : RecyclerView.Adapter<TabsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val tv: TextView = v as TextView }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val tv = TextView(p.context).apply {
                setPadding(20, 10, 20, 10); setBackgroundResource(android.R.drawable.btn_default)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = 300
            }
            return VH(tv)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = list[p]
            h.tv.text = item.title
            h.tv.setOnClickListener { onClick(item) }
            h.tv.setOnLongClickListener { onLongClick(item, p); true }
        }
        override fun getItemCount() = list.size
    }

    inner class MenuShortcutAdapter(private val list: List<Shortcut>, val onExecute: (Shortcut) -> Unit, val onToggleEye: (Shortcut) -> Unit, val onLongClick: (Shortcut, Int) -> Unit) : RecyclerView.Adapter<MenuShortcutAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(android.R.id.text1); val eye: ImageButton = (v as LinearLayout).getChildAt(1) as ImageButton }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val layout = LinearLayout(p.context).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(10, 10, 10, 10); gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(p.context).apply { id = android.R.id.text1; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                addView(ImageButton(p.context).apply { layoutParams = ViewGroup.LayoutParams(100, 100); setBackgroundResource(0) })
            }
            return VH(layout)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = list[p]; h.name.text = if (item.isAutoRun) "[A] ${item.name}" else item.name
            h.eye.setImageResource(if (item.isVisibleOnMain) android.R.drawable.ic_menu_view else android.R.drawable.ic_partial_secure)
            h.name.setOnClickListener { onExecute(item) }
            h.name.setOnLongClickListener { onLongClick(item, p); true }
            h.eye.setOnClickListener { item.isVisibleOnMain = !item.isVisibleOnMain; onToggleEye(item) }
        }
        override fun getItemCount() = list.size
    }

    inner class MainShortcutAdapter(private val list: List<Shortcut>, val onClick: (Shortcut) -> Unit) : RecyclerView.Adapter<MainShortcutAdapter.VH>() {
        private var visibleList = list.filter { it.isVisibleOnMain }
        fun updateList() { visibleList = list.filter { it.isVisibleOnMain }; notifyDataSetChanged() }
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val tv = TextView(p.context).apply { setPadding(30, 20, 30, 20); setBackgroundResource(android.R.drawable.btn_default) }
            return VH(tv)
        }
        override fun onBindViewHolder(h: VH, p: Int) { val item = visibleList[p]; (h.itemView as TextView).text = item.name; h.itemView.setOnClickListener { onClick(item) } }
        override fun getItemCount() = visibleList.size
    }
}
