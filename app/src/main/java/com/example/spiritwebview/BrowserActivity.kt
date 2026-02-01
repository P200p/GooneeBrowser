package com.example.spiritwebview

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityBrowserBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

// --- AI Preset for non-dev users ---
data class AiPreset(
    val icon: String,
    val name: String,
    val prompt: String,
    val category: String = "general"
)

data class Shortcut(
    var name: String, 
    var script: String, 
    var isAutoRun: Boolean = false, 
    var isVisibleOnMain: Boolean = false,
    var icon: String = "🔧",
    var description: String = "",
    var isTrusted: Boolean = false
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
    
    private var isSimpleMode = false
    private var isSandboxMode = true
    private val visitCounts = mutableMapOf<String, Int>()
    
    private val aiPresets = listOf(
        AiPreset("🚫", "Hide Ads / ซ่อนโฆษณา", 
            "Create a JavaScript that hides all ads, banners, and sponsored content on the page"),
        AiPreset("🛠️", "Dev Tools", 
            "Inject Eruda developer tools from CDN and initialize it"),
        AiPreset("🌐", "Translate / แปลภาษา", 
            "Create a script that opens Google Translate for the current page URL"),
        AiPreset("🌙", "Dark Mode / โหมดมืด", 
            "Create a script that inverts colors and applies a dark theme to the page"),
        AiPreset("📖", "Reader Mode / โหมดอ่าน", 
            "Create a script that removes clutter and makes the page easier to read"),
        AiPreset("❌", "Remove Popups / ลบ Popup", 
            "Create a script that removes all popups, modals, overlays and cookie banners")
    )
    
    private val shortcuts = mutableListOf<Shortcut>()
    private lateinit var menuAdapter: MenuShortcutAdapter
    private lateinit var mainBarAdapter: MainShortcutAdapter

    private val tabsList = mutableListOf<TabItem>()
    private lateinit var tabsAdapter: TabsAdapter
    private var currentTabId: Long = -1

    private val historyList = mutableListOf<String>()
    private lateinit var historyAdapter: ArrayAdapter<String>

    private var _generativeModel: GenerativeModel? = null
    private fun getGenerativeModel(forceNew: Boolean = false): GenerativeModel? {
        val apiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (apiKey.isEmpty()) return null
        val modelName = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        if (_generativeModel == null || forceNew) {
            val config = generationConfig {
                responseMimeType = "application/json"
            }
            val safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
            )
            _generativeModel = GenerativeModel(
                modelName = modelName, 
                apiKey = apiKey,
                generationConfig = config,
                safetySettings = safetySettings
            )
        }
        return _generativeModel
    }

    private suspend fun fetchAvailableModels(apiKey: String): List<String> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                // Try v1beta first as it contains most recent preview models
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList<String>()
                    val modelNames = mutableListOf<String>()
                    
                    for (i in 0 until modelsArray.length()) {
                        val model = modelsArray.getJSONObject(i)
                        val name = model.getString("name").substringAfter("/")
                        val supportedActions = model.optJSONArray("supportedActions")
                        
                        var canGenerate = false
                        if (supportedActions != null) {
                            for (j in 0 until supportedActions.length()) {
                                if (supportedActions.getString(j) == "generateContent") {
                                    canGenerate = true; break
                                }
                            }
                        } else {
                            // Fallback: assume if it's a gemini model, it can generate content
                            if (name.contains("gemini", ignoreCase = true)) canGenerate = true
                        }
                        
                        if (canGenerate) modelNames.add(name)
                    }
                    modelNames.distinct().sortedDescending()
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("GooneeAI", "Server returned $responseCode: $errorText")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("GooneeAI", "Network error while fetching models", e)
                emptyList()
            } finally {
                connection?.disconnect()
            }
        }
    }

    companion object {
        private const val PREF_NAME = "gooser_settings"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_SHORTCUTS = "saved_shortcuts"
        private const val KEY_HISTORY = "url_history"
        private const val KEY_SAVED_TABS = "saved_tabs"
        private const val KEY_CURRENT_TAB_ID = "current_tab_id"
        private const val DEFAULT_HOME = "https://goonee.netlify.app/"
        private const val GOOGLE_SEARCH = "https://www.google.com/search?q="
        
        private const val KEY_SANDBOX_MODE = "sandbox_mode"
        private const val KEY_SIMPLE_MODE = "simple_mode"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_VISIT_COUNTS = "visit_counts"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val DEFAULT_MODEL = "gemini-1.5-flash"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        isSandboxMode = prefs.getBoolean(KEY_SANDBOX_MODE, true)
        isSimpleMode = prefs.getBoolean(KEY_SIMPLE_MODE, false)

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
        loadVisitCounts()

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
        applySimpleMode(isSimpleMode)
        checkFirstRun()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentWebView = getWebViewForTab(activeTab)
                if (currentWebView.canGoBack()) {
                    currentWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun checkFirstRun() {
        if (prefs.getBoolean(KEY_FIRST_RUN, true)) {
            showOnboardingDialog()
            prefs.edit { putBoolean(KEY_FIRST_RUN, false) }
        }
    }

    private fun showOnboardingDialog() {
        val options = aiPresets.map { "${it.icon} ${it.name}" }.toTypedArray()
        val checkedItems = BooleanArray(options.size) { false }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.onboarding_title)
            .setMessage(R.string.onboarding_question)
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.onboarding_done) { _, _ ->
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        val preset = aiPresets[i]
                        shortcuts.add(Shortcut(preset.name, "/* AI Preset */", isAutoRun = false, isVisibleOnMain = true, icon = preset.icon, isTrusted = true))
                    }
                }
                saveShortcuts()
                notifyShortcutChanged()
            }
            .setCancelable(false)
            .show()
    }

    private fun applySimpleMode(enabled: Boolean) {
        isSimpleMode = enabled
        prefs.edit { putBoolean(KEY_SIMPLE_MODE, enabled) }
        
        binding.btnSplitMode.isVisible = !enabled
        binding.mainShortcutBar.isVisible = !enabled
        binding.btnFullscreenMode.isVisible = !enabled
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
                            val urlToLoad = prepareUrl(input)
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
                        if (binding.topNavigationBar.isGone) {
                            binding.topNavigationBar.isVisible = true
                        } else {
                            togglePanel(true)
                        }
                    } else {
                        val screenWidth = binding.webRoot.width.toFloat()
                        val targetX = if (event.rawX < screenWidth / 2) 16f else screenWidth - v.width - 16f
                        v.animate().x(targetX).setDuration(200).start()
                    }
                    v.performClick()
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
                val urlToLoad = prepareUrl(input)
                addToHistory(urlToLoad)
                loadUrl(urlToLoad)
                true
            } else false
        }

        binding.btnAddShortcut.setOnClickListener { showAddEditShortcutDialog(null) }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
        binding.btnSplitMode.setOnClickListener { toggleSplit() }
        binding.btnFullscreenMode.setOnClickListener {
            binding.topNavigationBar.isVisible = !binding.topNavigationBar.isVisible
        }

        binding.btnAskAi.setOnClickListener { 
            if (prefs.getString(KEY_GEMINI_API_KEY, "").isNullOrEmpty()) {
                showApiKeySetupDialog { showAiGeneratorDialog() }
            } else {
                showAiGeneratorDialog()
            }
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
                            val urlToLoad = prepareUrl(input)
                            addNewTab(urlToLoad)
                        }
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun prepareUrl(input: String): String {
        return if (isValidUrl(input)) {
            if (!input.startsWith("http")) "https://$input" else input
        } else if (input.startsWith("http://") || input.startsWith("https://") || 
                   input.startsWith("content://") || input.startsWith("file://")) {
            input
        } else {
            GOOGLE_SEARCH + input
        }
    }

    private fun showApiKeySetupDialog(onSuccess: () -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val tvInstructions = TextView(this).apply {
            text = getString(R.string.api_key_instructions)
            setPadding(0, 0, 0, 30)
        }
        layout.addView(tvInstructions)

        val btnGetLink = Button(this).apply {
            text = getString(R.string.get_api_key_now)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, "https://aistudio.google.com/app/apikey".toUri()))
            }
        }
        layout.addView(btnGetLink)

        val input = EditText(this).apply {
            hint = getString(R.string.api_key_hint)
            setText(prefs.getString(KEY_GEMINI_API_KEY, ""))
            setPadding(20, 20, 20, 10)
            setBackgroundResource(android.R.drawable.edit_text)
        }
        layout.addView(input)

        val btnFetch = Button(this).apply {
            text = getString(R.string.fetch_models)
            isAllCaps = false
        }
        layout.addView(btnFetch)

        val tvModelLabel = TextView(this).apply {
            text = getString(R.string.select_model)
            setPadding(0, 20, 0, 0)
            isVisible = false
        }
        layout.addView(tvModelLabel)

        val modelSpinner = Spinner(this).apply {
            setPadding(0, 10, 0, 20)
            isVisible = false
        }
        layout.addView(modelSpinner)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            isVisible = false
        }
        layout.addView(progress)

        btnFetch.setOnClickListener {
            val key = input.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, R.string.no_api_key_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                progress.isVisible = true
                val models = fetchAvailableModels(key)
                progress.isVisible = false
                if (models.isNotEmpty()) {
                    val adapter = ArrayAdapter(this@BrowserActivity, android.R.layout.simple_spinner_dropdown_item, models)
                    modelSpinner.adapter = adapter
                    modelSpinner.isVisible = true
                    tvModelLabel.isVisible = true
                    // Try to pre-select current model
                    val current = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL)
                    val idx = models.indexOf(current)
                    if (idx >= 0) modelSpinner.setSelection(idx)
                } else {
                    Toast.makeText(this@BrowserActivity, getString(R.string.fetch_models_error, "Invalid Key or Network Error"), Toast.LENGTH_LONG).show()
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.api_key_required_title)
            .setView(layout)
            .setPositiveButton(R.string.test_and_save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val key = input.text.toString().trim()
                val selectedModel = if (modelSpinner.isVisible) modelSpinner.selectedItem?.toString() ?: DEFAULT_MODEL else DEFAULT_MODEL
                
                if (key.isNotEmpty()) {
                    testApiKey(key, selectedModel, progress) { success ->
                        if (success) {
                            prefs.edit { 
                                putString(KEY_GEMINI_API_KEY, key)
                                putString(KEY_GEMINI_MODEL, selectedModel)
                            }
                            _generativeModel = null // Reset to apply changes
                            dialog.dismiss()
                            onSuccess()
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.no_api_key_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun testApiKey(key: String, modelName: String, progress: View, onResult: (Boolean) -> Unit) {
        progress.isVisible = true
        val testModel = GenerativeModel(modelName = modelName, apiKey = key)
        
        lifecycleScope.launch {
            try {
                // simple test prompt
                testModel.generateContent("hi")
                progress.isVisible = false
                Toast.makeText(this@BrowserActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                onResult(true)
            } catch (e: Exception) {
                progress.isVisible = false
                Log.e("GooneeAI", "API Key Test Failed", e)
                val errorMsg = if (e.message?.contains("API_KEY_INVALID") == true) "API Key ไม่ถูกต้อง" else "Error: ${e.message}"
                Toast.makeText(this@BrowserActivity, errorMsg, Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }
    }

    private fun showAiGeneratorDialog() {
        val scroll = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }
        
        val titleText = TextView(this).apply {
            text = getString(R.string.ai_tool_builder)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        val subtitleText = TextView(this).apply {
            text = getString(R.string.ai_tool_builder_subtitle)
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(titleText)
        mainLayout.addView(subtitleText)
        
        val presetsLabel = TextView(this).apply {
            text = getString(R.string.or_choose_preset)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }
        mainLayout.addView(presetsLabel)
        
        val gridLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        var dialogRef: AlertDialog? = null
        
        for (i in aiPresets.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
            }
            
            val btn1 = Button(this).apply {
                text = getString(R.string.preset_format, aiPresets[i].icon, aiPresets[i].name)
                textSize = 12f
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 5 }
                setOnClickListener {
                    dialogRef?.dismiss()
                    generateAiScript(aiPresets[i].prompt)
                }
            }
            row.addView(btn1)
            
            if (i + 1 < aiPresets.size) {
                val btn2 = Button(this).apply {
                    text = getString(R.string.preset_format, aiPresets[i+1].icon, aiPresets[i+1].name)
                    textSize = 12f
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 5 }
                    setOnClickListener {
                        dialogRef?.dismiss()
                        generateAiScript(aiPresets[i + 1].prompt)
                    }
                }
                row.addView(btn2)
            }
            gridLayout.addView(row)
        }
        mainLayout.addView(gridLayout)
        
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply { topMargin = 20; bottomMargin = 20 }
            setBackgroundColor(Color.LTGRAY)
        }
        mainLayout.addView(divider)
        
        val customLabel = TextView(this).apply {
            text = getString(R.string.try_speaking)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        mainLayout.addView(customLabel)
        
        val inputEdit = EditText(this).apply {
            hint = "e.g. 'ช่วยให้เว็บนี้ดูง่ายขึ้น' or 'remove sticky headers'"
            setPadding(30, 30, 30, 30)
            textSize = 14f
            minLines = 2
            setBackgroundResource(android.R.drawable.edit_text)
        }
        mainLayout.addView(inputEdit)
        
        val generateBtn = Button(this).apply {
            text = getString(R.string.generate)
            textSize = 14f
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 15 }
            setOnClickListener {
                val idea = inputEdit.text.toString()
                if (idea.isNotEmpty()) {
                    dialogRef?.dismiss()
                    generateAiScript(idea)
                } else {
                    Toast.makeText(this@BrowserActivity, R.string.try_speaking, Toast.LENGTH_SHORT).show()
                }
            }
        }
        mainLayout.addView(generateBtn)
        
        scroll.addView(mainLayout)
        
        dialogRef = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialogRef.show()
    }

    private fun generateAiScript(idea: String) {
        val model = getGenerativeModel()
        if (model == null) {
            Toast.makeText(this, R.string.no_api_key_error, Toast.LENGTH_LONG).show()
            showApiKeySetupDialog { generateAiScript(idea) }
            return
        }

        val loadingDialog = AlertDialog.Builder(this)
            .setMessage(R.string.ai_generating)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val prompt = """
                    You are an expert Android WebView JavaScript developer. 
                    The user wants to create a browser tool with this idea: "$idea".
                    Please provide a response in JSON format with three fields:
                    1. "name": A short, catchy name for this tool.
                    2. "script": The JavaScript code (IIFE format) that performs the action.
                    3. "explanation": A brief explanation in Thai of how the code works.
                    
                    Only return valid JSON. No markdown formatting.
                """.trimIndent()

                val response = model.generateContent(prompt)
                var rawText = response.text ?: ""

                // --- Robust JSON extraction: find first '{' and matching '}' ---
                var jsonText = rawText
                val firstOpen = rawText.indexOf('{')
                if (firstOpen >= 0) {
                    var depth = 0
                    var endIndex = -1
                    for (i in firstOpen until rawText.length) {
                        when (rawText[i]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) { endIndex = i; break }
                            }
                        }
                    }
                    if (endIndex >= 0) {
                        jsonText = rawText.substring(firstOpen, endIndex + 1)
                    }
                }

                loadingDialog.dismiss()

                try {
                    val json = JSONObject(jsonText)
                    showAiResultDialog(
                        json.optString("name", "AI Tool"),
                        json.optString("script", "alert('No script generated')"),
                        json.optString("explanation", "ไม่มีคำอธิบาย")
                    )
                } catch (e: Exception) {
                    Log.e("GooneeAI", "JSON Parse Error: $rawText", e)
                    showAiResultDialog("AI Tool", "alert('AI generated script error')", "ขอโทษทีครับ เฮียเอ๋อไปนิด ลองสั่งใหม่นะ (JSON Error)")
                }

            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("GooneeAI", "AI Error", e)
                val errorMsg = if (e.message?.contains("SAFETY") == true) "เนื้อหานี้ถูกบล็อกโดยระบบความปลอดภัย" else "Error: ${e.message}"
                Toast.makeText(this@BrowserActivity, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAiResultDialog(name: String, script: String, explanation: String) {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val expText = TextView(this).apply {
            text = getString(R.string.explanation_format, explanation)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        
        val codeText = TextView(this).apply {
            text = script
            setBackgroundColor(Color.LTGRAY)
            setPadding(20, 20, 20, 20)
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }

        layout.addView(expText)
        layout.addView(codeText)
        scroll.addView(layout)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_done_format, name))
            .setView(scroll)
            .setPositiveButton(R.string.save_to_tools) { _, _ ->
                shortcuts.add(Shortcut(name, script, isAutoRun = false, isVisibleOnMain = true, description = explanation))
                saveShortcuts()
                notifyShortcutChanged()
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.try_first) { _, _ ->
                actuallyExecuteShortcut(Shortcut(name, script))
            }
            .setNegativeButton(R.string.copy_code) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AI Script", script))
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            .show()
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
        prefs.edit { putString(KEY_HISTORY, array.toString()) }
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
        prefs.edit { 
            putString(KEY_SAVED_TABS, array.toString())
            putLong(KEY_CURRENT_TAB_ID, currentTabId)
        }
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
                // If prefs didn't store a valid current tab id, default to first tab (if exists)
                if (currentTabId == -1L && tabsList.isNotEmpty()) {
                    currentTabId = tabsList.first().id
                }
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
                    Toast.makeText(this, R.string.min_one_tab, Toast.LENGTH_SHORT).show()
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
            .setTitle(R.string.close_tab)
            .setMessage(getString(R.string.close_tab_confirm, tab.title, "Goonee"))
            .setPositiveButton(R.string.close) { _, _ ->
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun togglePanel(show: Boolean) {
        isPanelVisible = show
        binding.floatingMenuPanel.isVisible = show
        binding.fabMainToggle.isVisible = !show
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
        if (isSandboxMode && !shortcut.isTrusted) {
            showSandboxConfirmDialog(shortcut)
        } else {
            actuallyExecuteShortcut(shortcut)
        }
    }

    private fun showSandboxConfirmDialog(shortcut: Shortcut) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        if (shortcut.description.isNotEmpty()) {
            layout.addView(TextView(this).apply {
                text = getString(R.string.explanation_format, shortcut.description)
                setPadding(0, 0, 0, 20)
            })
        }
        
        val codeView = TextView(this).apply {
            text = shortcut.script
            textSize = 10f
            setBackgroundColor(Color.LTGRAY)
            isVisible = false
        }
        
        layout.addView(Button(this).apply {
            text = getString(R.string.what_does_this_do)
            setOnClickListener { codeView.isVisible = !codeView.isVisible }
        })
        layout.addView(codeView)

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_run)
            .setView(layout)
            .setPositiveButton(R.string.confirm_run) { _, _ -> actuallyExecuteShortcut(shortcut) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun actuallyExecuteShortcut(shortcut: Shortcut) {
        val webView = getWebViewForTab(activeTab)
        val wrappedScript = "(function() { try { ${shortcut.script}; return 'SUCCESS'; } catch(e) { return 'ERROR:' + e.stack; } })()"
        
        webView.evaluateJavascript(wrappedScript) { result ->
            val cleanResult = result?.removeSurrounding("\"") ?: ""
            if (cleanResult.startsWith("ERROR:")) {
                Toast.makeText(this, R.string.script_error, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.run_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddEditShortcutDialog(shortcut: Shortcut?) {
        val nameInput = EditText(this).apply { hint = getString(R.string.tool_name); shortcut?.let { setText(it.name) } }
        val scriptInput = EditText(this).apply { hint = getString(R.string.tool_script); shortcut?.let { setText(it.script) } }
        val autoRunCheck = CheckBox(this).apply { text = getString(R.string.auto_run); isChecked = shortcut?.isAutoRun ?: false }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 20)
            addView(nameInput); addView(scriptInput); addView(autoRunCheck)
        }
        AlertDialog.Builder(this).setTitle(if (shortcut == null) R.string.add_tool else R.string.edit_tool).setView(layout)
            .setPositiveButton(R.string.saved) { _, _ ->
                val name = nameInput.text.toString()
                val script = scriptInput.text.toString()
                if (name.isNotEmpty() && script.isNotEmpty()) {
                    if (shortcut == null) shortcuts.add(Shortcut(name, script, autoRunCheck.isChecked, false))
                    else { shortcut.name = name; shortcut.script = script; shortcut.isAutoRun = autoRunCheck.isChecked }
                    saveShortcuts(); notifyShortcutChanged()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showEditDeleteDialog(shortcut: Shortcut, position: Int) {
        AlertDialog.Builder(this).setTitle(shortcut.name).setItems(arrayOf(getString(R.string.edit_tool), getString(R.string.delete_tool))) { _, which ->
            if (which == 0) showAddEditShortcutDialog(shortcut)
            else { shortcuts.removeAt(position); saveShortcuts(); notifyShortcutChanged() }
        }.show()
    }

    private fun saveShortcuts() {
        val array = JSONArray()
        shortcuts.forEach {
            val obj = JSONObject(); obj.put("name", it.name); obj.put("script", it.script)
            obj.put("isAutoRun", it.isAutoRun); obj.put("isVisibleOnMain", it.isVisibleOnMain)
            obj.put("icon", it.icon); obj.put("description", it.description)
            obj.put("isTrusted", it.isTrusted)
            array.put(obj)
        }
        prefs.edit { putString(KEY_SHORTCUTS, array.toString()) }
    }

    private fun loadShortcuts() {
        val saved = prefs.getString(KEY_SHORTCUTS, null)
        if (saved != null) {
            val array = JSONArray(saved); shortcuts.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                shortcuts.add(Shortcut(
                    obj.getString("name"), 
                    obj.getString("script"), 
                    obj.optBoolean("isAutoRun", false), 
                    obj.optBoolean("isVisibleOnMain", false),
                    obj.optString("icon", "🔧"),
                    obj.optString("description", ""),
                    obj.optBoolean("isTrusted", false)
                ))
            }
        } else {
            shortcuts.add(Shortcut("Dark Mode", "document.body.style.backgroundColor='#222';document.body.style.color='#fff';", false, true, "🌙", "โหมดมืด", true))
        }
    }

    private fun loadUrl(url: String) {
        getWebViewForTab(activeTab).loadUrl(url); binding.edtUrl.setText(url)
        tabsList.find { it.id == currentTabId }?.url = url
        saveTabs()
        trackVisit(url)
    }

    private fun trackVisit(url: String) {
        val domain = Uri.parse(url).host ?: return
        if (domain.isEmpty() || domain == "google.com") return
        
        val count = visitCounts.getOrDefault(domain, 0) + 1
        visitCounts[domain] = count
        saveVisitCounts()
        
        val currentHome = prefs.getString(KEY_HOME_URL, DEFAULT_HOME) ?: DEFAULT_HOME
        if (count >= 5 && !currentHome.contains(domain)) {
            suggestSetAsHome(domain)
        }
    }

    private fun suggestSetAsHome(domain: String) {
        Snackbar.make(binding.root, getString(R.string.suggest_set_home, domain, "Goonee"), Snackbar.LENGTH_LONG)
            .setAction(R.string.set_now) {
                prefs.edit { putString(KEY_HOME_URL, "https://$domain") }
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun saveVisitCounts() {
        val obj = JSONObject()
        visitCounts.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit { putString(KEY_VISIT_COUNTS, obj.toString()) }
    }

    private fun loadVisitCounts() {
        val saved = prefs.getString(KEY_VISIT_COUNTS, null)
        if (saved != null) {
            val obj = JSONObject(saved)
            obj.keys().forEach { visitCounts[it] = obj.getInt(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(w: WebView) {
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = true; allowContentAccess = true; loadWithOverviewMode = true
            useWideViewPort = true; mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        }
        w.addJavascriptInterface(AndroidBridge(this), "Android")
        w.setDownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = url.toUri()
                startActivity(intent)
            } catch (e: Exception) {}
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (view == getWebViewForTab(activeTab)) {
                    tabsList.find { it.id == currentTabId }?.let { 
                        it.title = title ?: "New Tab"
                        tabsAdapter.notifyItemChanged(tabsList.indexOf(it))
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
            v.requestFocus(); binding.edtUrl.setText(w.url)
            v.performClick()
            false
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

    fun toggleSplit() {
        isSplit = !isSplit
        binding.webContainerB.isVisible = isSplit
        binding.splitHandle.isVisible = isSplit
        
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

        layout.addView(CheckBox(this).apply {
            text = getString(R.string.desktop_mode)
            isChecked = isDesktopMode
            setOnCheckedChangeListener { _, isChecked ->
                isDesktopMode = isChecked
                applyDesktopMode(webViewA, isChecked)
                applyDesktopMode(webViewB, isChecked)
            }
        })

        layout.addView(CheckBox(this).apply {
            text = getString(R.string.sandbox_mode)
            isChecked = isSandboxMode
            setOnCheckedChangeListener { _, isChecked ->
                isSandboxMode = isChecked
                prefs.edit { putBoolean(KEY_SANDBOX_MODE, isChecked) }
            }
        })

        layout.addView(CheckBox(this).apply {
            text = getString(R.string.simple_mode)
            isChecked = isSimpleMode
            setOnCheckedChangeListener { _, isChecked ->
                applySimpleMode(isChecked)
            }
        })

        val zoomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 30, 0, 30)
        }
        val tvZoom = TextView(this).apply { text = getString(R.string.text_zoom_format, textZoom); textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnZoomOut = Button(this, null, android.R.attr.buttonStyleSmall).apply { text = "-" }
        val btnZoomIn = Button(this, null, android.R.attr.buttonStyleSmall).apply { text = "+" }
        
        btnZoomIn.setOnClickListener {
            textZoom += 10
            tvZoom.text = getString(R.string.text_zoom_format, textZoom)
            webViewA.settings.textZoom = textZoom
            webViewB.settings.textZoom = textZoom
        }
        btnZoomOut.setOnClickListener {
            if (textZoom > 50) {
                textZoom -= 10
                tvZoom.text = getString(R.string.text_zoom_format, textZoom)
                webViewA.settings.textZoom = textZoom
                webViewB.settings.textZoom = textZoom
            }
        }
        zoomLayout.addView(tvZoom); zoomLayout.addView(btnZoomOut); zoomLayout.addView(btnZoomIn)
        layout.addView(zoomLayout)

        val buttons = arrayOf(
            getString(R.string.split_mode) to { toggleSplit() },
            getString(R.string.toggle_orientation) to { 
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
            getString(R.string.set_home) to { showSetHomeUrlDialog() },
            getString(R.string.guide) to { showGuideDialog() },
            getString(R.string.api_key_setting) to { showApiKeySetupDialog {} }
        )

        buttons.forEach { (label, action) ->
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { action(); if (label != getString(R.string.set_home) && label != getString(R.string.guide) && label != getString(R.string.api_key_setting)) (parent.parent.parent as? AlertDialog)?.dismiss() }
            })
        }

        AlertDialog.Builder(this).setTitle(R.string.settings).setView(layout).setPositiveButton(R.string.close, null).show()
    }

    private fun showGuideDialog() {
        val textView = TextView(this).apply {
            text = getString(R.string.guide)
            setPadding(50, 40, 50, 40)
            textSize = 14f
            movementMethod = android.text.method.ScrollingMovementMethod()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.guide)
            .setView(textView)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun applyDesktopMode(webView: WebView, enabled: Boolean) {
        val settings = webView.settings
        if (enabled) {
            val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            settings.userAgentString = desktopUA
        } else {
            // Restore a reliable default user agent instead of setting null
            try {
                settings.userAgentString = WebSettings.getDefaultUserAgent(this)
            } catch (e: Exception) {
                // fallback: clear custom UA if default retrieval fails
                settings.userAgentString = null
            }
        }
        webView.reload()
    }

    private fun showSetHomeUrlDialog() {
        val currentHome = prefs.getString(KEY_HOME_URL, DEFAULT_HOME)
        val input = EditText(this).apply { setText(currentHome) }
        AlertDialog.Builder(this).setTitle(R.string.set_home).setView(input)
            .setPositiveButton(R.string.saved) { _, _ ->
                val newUrl = input.text.toString()
                if (newUrl.isNotEmpty()) {
                    prefs.edit { putString(KEY_HOME_URL, newUrl) }
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
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
                        val denom = container.width
                        if (denom > 0) {
                            val relativeX = ev.rawX - location[0]
                            adjustWeightsByVal((relativeX / denom).coerceIn(0.05f, 0.95f))
                        }
                    } else {
                        val denom = container.height
                        if (denom > 0) {
                            val relativeY = ev.rawY - location[1]
                            adjustWeightsByVal((relativeY / denom).coerceIn(0.05f, 0.95f))
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Bridge methods
    fun navigate(payloadJson: String) {
        try {
            val json = JSONObject(payloadJson)
            val url = json.optString("url")
            if (url.isNotEmpty()) {
                loadUrl(prepareUrl(url))
            }
        } catch (e: Exception) {
            Log.e("BrowserActivity", "navigate error: ${e.message}")
        }
    }

    fun injectLayers(payloadJson: String) {
        // Implementation placeholder
    }

    fun goBack(payloadJson: String) {
        getWebViewForTab(activeTab).goBack()
    }

    fun reload(payloadJson: String) {
        getWebViewForTab(activeTab).reload()
    }

    fun toggleSplit(payloadJson: String) {
        toggleSplit()
    }

    fun setFallback(payloadJson: String) {
        // Implementation placeholder
    }

    inner class TabsAdapter(
        private val list: List<TabItem>, 
        val onClick: (TabItem) -> Unit,
        val onLongClick: (TabItem, Int) -> Unit
    ) : RecyclerView.Adapter<TabsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val tv: TextView = v as TextView }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val tv = TextView(p.context).apply {
                setPadding(20, 10, 20, 10); setBackgroundResource(android.R.drawable.btn_default)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = 300
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
        inner class VH(v: View, val icon: TextView, val name: TextView, val desc: TextView, val eye: ImageButton) : RecyclerView.ViewHolder(v)
        
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val context = p.context
            val iconView = TextView(context).apply { textSize = 24f; layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 20 } }
            val nameView = TextView(context).apply { textSize = 16f; setTypeface(null, Typeface.BOLD) }
            val descView = TextView(context).apply { textSize = 12f; setTextColor(Color.GRAY); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val eyeView = ImageButton(context).apply { layoutParams = ViewGroup.LayoutParams(100, 100); setBackgroundResource(0) }

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(nameView); addView(descView)
            }
            
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(20, 20, 20, 20); gravity = Gravity.CENTER_VERTICAL
                addView(iconView); addView(textLayout); addView(eyeView)
            }
            return VH(layout, iconView, nameView, descView, eyeView)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = list[p]
            h.icon.text = item.icon
            h.name.text = if (item.isAutoRun) getString(R.string.auto_run_format, item.name) else item.name
            h.desc.text = item.description.ifEmpty { "Custom Script" }
            h.eye.setImageResource(if (item.isVisibleOnMain) android.R.drawable.ic_menu_view else android.R.drawable.ic_partial_secure)
            
            h.itemView.setOnClickListener { onExecute(item) }
            h.itemView.setOnLongClickListener { onLongClick(item, p); true }
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
        override fun onBindViewHolder(h: VH, p: Int) { 
            val item = visibleList[p]
            (h.itemView as TextView).text = getString(R.string.preset_format, item.icon, item.name)
            h.itemView.setOnClickListener { onClick(item) } 
        }
        override fun getItemCount() = visibleList.size
    }
}
