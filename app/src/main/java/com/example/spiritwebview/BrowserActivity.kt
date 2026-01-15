package com.example.spiritwebview

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class Shortcut(
    var name: String, 
    var script: String, 
    var isAutoRun: Boolean = false, 
    var isVisibleOnMain: Boolean = false
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
    
    private val shortcuts = mutableListOf<Shortcut>()
    private lateinit var menuAdapter: MenuShortcutAdapter
    private lateinit var mainBarAdapter: MainShortcutAdapter

    companion object {
        private const val TAG = "GooserBrowser"
        private const val PREF_NAME = "gooser_settings"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_SHORTCUTS = "saved_shortcuts"
        private const val DEFAULT_HOME = "https://goonee.netlify.app/"
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
        
        // โหลดข้อมูลที่บันทึกไว้
        loadShortcuts()
        val startUrl = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
        loadUrl(startUrl)
        
        showSingle()
        setupSplitter()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        var dX = 0f; var dY = 0f; var startX = 0f; var startY = 0f
        binding.fabMainToggle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX; dY = v.y - event.rawY
                    startX = event.rawX; startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - startX) < 10 && abs(event.rawY - startY) < 10) togglePanel(true)
                    else {
                        val screenWidth = binding.webRoot.width.toFloat()
                        val targetX = if (event.rawX < screenWidth / 2) 16f else screenWidth - v.width - 16f
                        v.animate().x(targetX).setDuration(200).start()
                    }
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
                var url = v.text.toString()
                if (!url.startsWith("http")) url = "https://$url"
                loadUrl(url); true
            } else false
        }

        binding.btnAddShortcut.setOnClickListener { showAddEditShortcutDialog(null) }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun togglePanel(show: Boolean) {
        isPanelVisible = show
        binding.floatingMenuPanel.visibility = if (show) View.VISIBLE else View.GONE
        binding.fabMainToggle.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setupShortcutSystem() {
        menuAdapter = MenuShortcutAdapter(shortcuts, 
            onExecute = { executeShortcut(it) },
            onToggleEye = { 
                saveShortcuts()
                notifyShortcutChanged() 
            },
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
        getWebViewForTab(activeTab).evaluateJavascript(shortcut.script, null)
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
                    else { 
                        shortcut.name = name
                        shortcut.script = script
                        shortcut.isAutoRun = autoRunCheck.isChecked 
                    }
                    saveShortcuts()
                    notifyShortcutChanged()
                }
            }.setNegativeButton("ยกเลิก", null).show()
    }

    private fun showEditDeleteDialog(shortcut: Shortcut, position: Int) {
        AlertDialog.Builder(this).setTitle(shortcut.name).setItems(arrayOf("แก้ไข", "ลบ")) { _, which ->
            if (which == 0) showAddEditShortcutDialog(shortcut, position)
            else { 
                shortcuts.removeAt(position)
                saveShortcuts()
                notifyShortcutChanged() 
            }
        }.show()
    }

    private fun saveShortcuts() {
        val array = JSONArray()
        shortcuts.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("script", it.script)
            obj.put("isAutoRun", it.isAutoRun)
            obj.put("isVisibleOnMain", it.isVisibleOnMain)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }

    private fun loadShortcuts() {
        val saved = prefs.getString(KEY_SHORTCUTS, null)
        if (saved != null) {
            val array = JSONArray(saved)
            shortcuts.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                shortcuts.add(Shortcut(
                    obj.getString("name"),
                    obj.getString("script"),
                    obj.getBoolean("isAutoRun"),
                    obj.getBoolean("isVisibleOnMain")
                ))
            }
        } else {
            // Default shortcut
            shortcuts.add(Shortcut("Dark Mode", "document.body.style.backgroundColor='#222';document.body.style.color='#fff';", false, true))
        }
    }

    private fun loadUrl(url: String) {
        getWebViewForTab(activeTab).loadUrl(url); binding.edtUrl.setText(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(w: WebView) {
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                shortcuts.filter { it.isAutoRun }.forEach { view?.evaluateJavascript(it.script, null) }
            }
        }
        w.setOnTouchListener { v, _ ->
            activeTab = if (v.id == R.id.webview_a) 1 else 2
            v.requestFocus(); binding.edtUrl.setText(w.url); false
        }
    }

    private fun setupSplitter() {
        binding.splitHandle.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_MOVE) {
                val delta = if (verticalSplit) ev.x else ev.y
                adjustWeights(delta); v.performClick(); true
            } else false
        }
    }

    private fun adjustWeights(delta: Float) {
        val lpA = binding.webContainerA.layoutParams as LinearLayout.LayoutParams
        val lpB = binding.webContainerB.layoutParams as LinearLayout.LayoutParams
        val parentSize = if (verticalSplit) binding.webRoot.width else binding.webRoot.height
        if (parentSize <= 0) return
        val change = delta / parentSize.toFloat()
        lpA.weight = (lpA.weight + change).coerceIn(0.1f, 0.9f)
        lpB.weight = 1.0f - lpA.weight
        binding.webContainerA.layoutParams = lpA; binding.webContainerB.layoutParams = lpB
    }

    fun toggleSplit(p: String = "") {
        isSplit = !isSplit
        binding.webContainerB.visibility = if (isSplit) View.VISIBLE else View.GONE
        binding.splitHandle.visibility = if (isSplit) View.VISIBLE else View.GONE
        if (isSplit && webViewB.url == null) {
            val home = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
            webViewB.loadUrl(home)
        }
        val lpA = binding.webContainerA.layoutParams as LinearLayout.LayoutParams
        lpA.weight = if (isSplit) 0.5f else 1.0f
        binding.webContainerA.layoutParams = lpA
    }

    private fun showSingle() {
        val lpA = binding.webContainerA.layoutParams as LinearLayout.LayoutParams
        lpA.weight = if (isSplit) 0.5f else 1.0f
        binding.webContainerA.layoutParams = lpA
    }

    private fun getWebViewForTab(tab: Int) = if (tab == 2 && isSplit) webViewB else webViewA

    private fun showSettingsDialog() {
        val items = arrayOf("เปิด/ปิด 2 หน้าจอ", "ตั้งค่าหน้าแรก (Home URL)")
        AlertDialog.Builder(this).setTitle("ตั้งค่า Gooser").setItems(items) { _, which ->
            when (which) {
                0 -> toggleSplit()
                1 -> showSetHomeUrlDialog()
            }
        }.show()
    }

    private fun showSetHomeUrlDialog() {
        val currentHome = prefs.getString(KEY_HOME_URL, DEFAULT_HOME)
        val input = EditText(this).apply { setText(currentHome) }
        AlertDialog.Builder(this).setTitle("ตั้งค่าหน้าแรก").setView(input)
            .setPositiveButton("บันทึก") { _, _ ->
                val newUrl = input.text.toString()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString(KEY_HOME_URL, newUrl).apply()
                    Toast.makeText(this, "บันทึกหน้าแรกเรียบร้อย", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("ยกเลิก", null).show()
    }

    // Bridge functions
    fun navigate(p: String) { try { loadUrl(JSONObject(p).getString("url")) } catch(e:Exception){} }
    fun injectLayers(p: String) {}
    fun goBack(p: String) { runOnUiThread { getWebViewForTab(activeTab).goBack() } }
    fun reload(p: String) { runOnUiThread { getWebViewForTab(activeTab).reload() } }
    fun setFallback(p: String) {}

    inner class MenuShortcutAdapter(
        private val list: List<Shortcut>,
        val onExecute: (Shortcut) -> Unit,
        val onToggleEye: (Shortcut) -> Unit,
        val onLongClick: (Shortcut, Int) -> Unit
    ) : RecyclerView.Adapter<MenuShortcutAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(android.R.id.text1)
            val eye: ImageButton = ImageButton(v.context).apply {
                layoutParams = ViewGroup.LayoutParams(100, 100)
                setBackgroundResource(0)
            }
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val layout = LinearLayout(p.context).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(10, 10, 10, 10)
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(p.context).apply { id = android.R.id.text1; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            }
            val vh = VH(layout)
            layout.addView(vh.eye)
            return vh
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = list[p]
            h.name.text = if (item.isAutoRun) "[A] ${item.name}" else item.name
            h.eye.setImageResource(if (item.isVisibleOnMain) android.R.drawable.ic_menu_view else android.R.drawable.ic_partial_secure)
            h.name.setOnClickListener { onExecute(item) }
            h.name.setOnLongClickListener { onLongClick(item, p); true }
            h.eye.setOnClickListener {
                item.isVisibleOnMain = !item.isVisibleOnMain
                onToggleEye(item)
            }
        }
        override fun getItemCount() = list.size
    }

    inner class MainShortcutAdapter(private val list: List<Shortcut>, val onClick: (Shortcut) -> Unit) : 
        RecyclerView.Adapter<MainShortcutAdapter.VH>() {
        private var visibleList = list.filter { it.isVisibleOnMain }
        fun updateList() { visibleList = list.filter { it.isVisibleOnMain }; notifyDataSetChanged() }
        
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val tv = TextView(p.context).apply { setPadding(30, 20, 30, 20); setBackgroundResource(android.R.drawable.btn_default) }
            return VH(tv)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = visibleList[p]
            (h.itemView as TextView).text = item.name
            h.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = visibleList.size
    }
}
