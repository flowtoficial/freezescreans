package freezekit.capitaldascestas

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import freezekit.capitaldascestas.config.BrowserConfig
import freezekit.capitaldascestas.kiosk.KioskManager
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.log.EventType
import freezekit.capitaldascestas.security.SecurityStore
import freezekit.capitaldascestas.web.BrowserUrlPolicy
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var config: BrowserConfig
    private lateinit var urlPolicy: BrowserUrlPolicy
    private lateinit var container: FrameLayout
    private lateinit var webView: WebView
    private lateinit var offlineMessage: TextView
    private lateinit var connectivityManager: ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var internetValidated: Boolean? = null
    private var offlineMessageVisible = false
    private var retryUrl: String? = null
    private var pendingCameraRequest: PermissionRequest? = null
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var tapCount = 0
    private var firstTapEventTime = 0L
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var pointerDownInCorner = false
    private var pointerMovedTooFar = false
    private var pointerDownX = 0f
    private var pointerDownY = 0f
    private var suppressSetupWizardOnResume = false
    private var setupTestOverlay: View? = null
    private val eventLogMaintenanceHandler = Handler(Looper.getMainLooper())
    private val dailyEventLogMaintenance = object : Runnable {
        override fun run() {
            EventLog.maintainIfDue(applicationContext)
            eventLogMaintenanceHandler.postDelayed(this, EventLog.MAINTENANCE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeActivity = WeakReference(this)
        EventLog.initialize(this)
        EventLog.record(this, EventType.APP_STARTED)
        eventLogMaintenanceHandler.postDelayed(
            dailyEventLogMaintenance,
            EventLog.MAINTENANCE_INTERVAL_MS
        )

        config = BrowserConfig(this)
        urlPolicy = BrowserUrlPolicy(config)
        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        retryUrl = config.initialUrl

        container = FrameLayout(this)
        webView = createWebView()
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        offlineMessage = TextView(this).apply {
            text = getString(R.string.offline_message)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 45, 40))
            setBackgroundColor(Color.rgb(244, 247, 245))
            visibility = View.GONE
        }
        container.addView(
            offlineMessage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(container)
        registerBackCallbackForAndroid13AndAbove()
        hideSystemBars()
        loadUrlIfAllowed(config.initialUrl)
        val testToken = intent.getLongExtra(EXTRA_SETUP_TEST_TOKEN, 0L)
        if (intent.action == ACTION_SETUP_TEST && consumeSetupTestToken(testToken)) {
            suppressSetupWizardOnResume = true
            showSetupTestOverlay()
        }
    }

    override fun onStart() {
        super.onStart()
        startNetworkMonitoring()
    }

    override fun onResume() {
        super.onResume()
        EventLog.maintainIfDue(this)
        if (::webView.isInitialized) {
            webView.onResume()
            hideSystemBars()
            grantCameraPermissionIfDeviceOwner()
            val kiosk = KioskManager(this)
            kiosk.restoreLockTaskPackagesIfNeeded()
            val owner = isDeviceOwner()
            val securityStore = SecurityStore(this)
            if (owner && kiosk.isKioskEnabled()) {
                kiosk.entrarNoQuiosque()
            }
            if (suppressSetupWizardOnResume) {
                suppressSetupWizardOnResume = false
            } else if (owner && (!securityStore.isFirstRunComplete() || !securityStore.isTotpConfigured())) {
                startActivity(Intent(this, SetupWizardActivity::class.java))
            }
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        stopNetworkMonitoring()
        super.onStop()
    }

    override fun onDestroy() {
        eventLogMaintenanceHandler.removeCallbacks(dailyEventLogMaintenance)
        pendingCameraRequest?.deny()
        pendingCameraRequest = null
        if (activeActivity?.get() === this) activeActivity = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
            backInvokedCallback = null
        }
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val testToken = intent.getLongExtra(EXTRA_SETUP_TEST_TOKEN, 0L)
        if (intent.action == ACTION_SETUP_TEST && consumeSetupTestToken(testToken)) {
            suppressSetupWizardOnResume = true
            showSetupTestOverlay()
        }
    }

    /** Observa o gesto e devolve exatamente o resultado do despacho normal do Android/WebView. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val openAdmin = observeHiddenAdminGesture(event)
        val dispatchedNormally = super.dispatchTouchEvent(event)
        if (openAdmin) {
            container.post {
                if (!isFinishing && !isDestroyed && SecurityStore(this).isFirstRunComplete()) {
                    startActivity(Intent(this, AccessCodeActivity::class.java))
                }
            }
        }
        return dispatchedNormally
    }

    @Deprecated("Deprecated in the Android SDK; handled to keep Back within the WebView")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            ::webView.isInitialized && webView.canGoBack()
        ) {
            webView.goBack()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE) {
            return
        }

        val request = pendingCameraRequest
        pendingCameraRequest = null
        val cameraGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (request != null && cameraGranted && isCameraRequestAllowed(request)) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            request?.deny()
        }
    }

    private fun createWebView(): WebView = WebView(this).apply {
        isLongClickable = false
        setOnLongClickListener { true }

        settings.apply {
            setJavaScriptEnabled(true)
            setDomStorageEnabled(true)
            setSupportMultipleWindows(false)
            setJavaScriptCanOpenWindowsAutomatically(true)
            setAllowFileAccess(false)
            setAllowContentAccess(false)
            setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)
            setGeolocationEnabled(false)
            setSafeBrowsingEnabled(true)
        }

        setWebViewClient(createWebViewClient())
        setWebChromeClient(createWebChromeClient())
    }

    private fun createWebViewClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = shouldBlockNavigation(request.url)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            shouldBlockNavigation(Uri.parse(url))

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (request.isForMainFrame && !urlPolicy.isAllowed(request.url)) {
                recordBlockedAddress(request.url)
                showBlockedAddressNotice()
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    403,
                    "Forbidden",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (urlPolicy.isAllowed(url)) {
                retryUrl = url
            }
            hideOfflineMessage()
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            hideOfflineMessage()
            super.onPageCommitVisible(view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                val failingUrl = request.url.toString()
                retryUrl = if (urlPolicy.isAllowed(failingUrl)) {
                    failingUrl
                } else {
                    config.initialUrl
                }
                showOfflineMessage()
            }
            super.onReceivedError(view, request, error)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            if (view !== webView) {
                return false
            }
            EventLog.record(
                this@MainActivity,
                EventType.BROWSER_RENDERER_RECOVERED,
                "crashed=${detail.didCrash()}"
            )
            recreateWebViewAfterRendererExit(view)
            return true
        }
    }

    private fun createWebChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                handleWebPermissionRequest(request)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingCameraRequest === request) {
                pendingCameraRequest = null
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean = false

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback
        ) {
            callback.invoke(origin, false, false)
        }
    }

    private fun shouldBlockNavigation(uri: Uri): Boolean {
        if (urlPolicy.isAllowed(uri)) {
            return false
        }
        recordBlockedAddress(uri)
        showBlockedAddressNotice()
        return true
    }

    private fun recordBlockedAddress(uri: Uri) {
        EventLog.record(
            this,
            EventType.BROWSER_ADDRESS_BLOCKED,
            "host=${uri.host ?: "host_indisponivel"}"
        )
    }

    private fun loadUrlIfAllowed(url: String) {
        if (urlPolicy.isAllowed(url)) {
            retryUrl = url
            webView.loadUrl(url)
        } else {
            recordBlockedAddress(Uri.parse(url))
            showBlockedAddressNotice()
        }
    }

    private fun observeHiddenAdminGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount != 1) {
                    resetGesture()
                    return false
                }
                if (tapCount > 0 && event.eventTime - firstTapEventTime > GESTURE_WINDOW_MS) {
                    tapCount = 0
                }
                pointerDownX = event.getX(0)
                pointerDownY = event.getY(0)
                pointerDownInCorner = isInsideAdminCorner(pointerDownX, pointerDownY)
                activePointerId = event.getPointerId(0)
                pointerMovedTooFar = false
                if (!pointerDownInCorner) resetGesture()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = if (activePointerId == MotionEvent.INVALID_POINTER_ID) -1
                    else event.findPointerIndex(activePointerId)
                if (index < 0 || event.pointerCount != 1) {
                    resetGesture()
                } else {
                    val dx = event.getX(index) - pointerDownX
                    val dy = event.getY(index) - pointerDownY
                    val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
                    if (dx * dx + dy * dy > slop * slop ||
                        !isInsideAdminCorner(event.getX(index), event.getY(index))
                    ) {
                        resetGesture()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE -> resetGesture()

            MotionEvent.ACTION_UP -> {
                val validTap = event.pointerCount == 1 &&
                    activePointerId != MotionEvent.INVALID_POINTER_ID &&
                    event.getPointerId(0) == activePointerId &&
                    pointerDownInCorner && !pointerMovedTooFar &&
                    isInsideAdminCorner(event.getX(0), event.getY(0))
                activePointerId = MotionEvent.INVALID_POINTER_ID
                pointerDownInCorner = false
                pointerMovedTooFar = false
                if (!validTap) {
                    tapCount = 0
                    return false
                }
                if (tapCount == 0 || event.eventTime - firstTapEventTime > GESTURE_WINDOW_MS) {
                    tapCount = 0
                    firstTapEventTime = event.eventTime
                }
                tapCount += 1
                if (tapCount >= REQUIRED_GESTURE_TAPS) {
                    resetGesture()
                    return true
                }
            }
        }
        return false
    }

    private fun isInsideAdminCorner(x: Float, y: Float): Boolean {
        val width = if (::container.isInitialized && container.width > 0) container.width
            else window.decorView.width
        if (width <= 0) return false
        val areaPx = (80f * resources.displayMetrics.density + 0.5f).toInt()
        return x >= width - areaPx && x <= width && y >= 0f && y <= areaPx
    }

    private fun resetGesture() {
        tapCount = 0
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pointerDownInCorner = false
        pointerMovedTooFar = false
    }

    private fun showSetupTestOverlay() {
        setupTestOverlay?.let(container::removeView)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(UiSupport.dp(this@MainActivity, 8), UiSupport.dp(this@MainActivity, 4),
                UiSupport.dp(this@MainActivity, 8), UiSupport.dp(this@MainActivity, 4))
            setBackgroundColor(Color.WHITE)
        }
        val worked = UiSupport.button(this, "Funcionou") { finishConferTest(true) }
        val failed = UiSupport.button(this, "Não funcionou") { finishConferTest(false) }
        bar.addView(worked, LinearLayout.LayoutParams(0, UiSupport.dp(this, 48), 1f))
        bar.addView(failed, LinearLayout.LayoutParams(0, UiSupport.dp(this, 48), 1f))
        setupTestOverlay = bar
        container.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
    }

    private fun finishConferTest(passed: Boolean) {
        SecurityStore(this).setConferTestPassed(passed)
        setupTestOverlay?.let(container::removeView)
        setupTestOverlay = null
        startActivity(Intent(this, SetupWizardActivity::class.java))
    }

    private fun clearWebSession(onComplete: (Boolean) -> Unit) {
        if (!::webView.isInitialized || isFinishing) {
            onComplete(false)
            return
        }
        try {
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies { onComplete(true) }
        } catch (_: Exception) {
            onComplete(false)
        }
    }

    private fun reloadConfiguredStartPage() {
        if (!::webView.isInitialized || isFinishing) return
        config = BrowserConfig(this)
        urlPolicy = BrowserUrlPolicy(config)
        loadUrlIfAllowed(config.initialUrl)
    }

    private fun showBlockedAddressNotice() {
        runOnUiThread {
            if (!isFinishing) {
                Toast.makeText(
                    this,
                    getString(R.string.blocked_address_notice),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        if (!isCameraRequestAllowed(request)) {
            request.deny()
            return
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            return
        }

        if (isDeviceOwner()) {
            request.deny()
            return
        }

        pendingCameraRequest?.takeIf { it !== request }?.deny()
        pendingCameraRequest = request
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun isCameraRequestAllowed(request: PermissionRequest): Boolean =
        request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
            urlPolicy.isAllowed(request.origin) &&
            urlPolicy.isCameraAllowed(request.origin)

    private fun grantCameraPermissionIfDeviceOwner() {
        if (!isDeviceOwner()) {
            return
        }

        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            devicePolicyManager.setPermissionGrantState(
                ComponentName(this, AdminReceiver::class.java),
                packageName,
                Manifest.permission.CAMERA,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        } catch (_: SecurityException) {
            // Falha de concessão não deve derrubar o navegador; solicitações de vídeo continuam negadas.
        }
    }

    private fun isDeviceOwner(): Boolean {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return devicePolicyManager.isDeviceOwnerApp(packageName)
    }

    private fun startNetworkMonitoring() {
        if (networkCallback != null) {
            return
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // O estado de internet é confirmado em onCapabilitiesChanged.
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasValidatedInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                runOnUiThread {
                    updateConnectivityState(hasValidatedInternet)
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    updateConnectivityState(currentDefaultNetworkHasValidatedInternet())
                }
            }
        }

        if (internetValidated == null) {
            internetValidated = currentDefaultNetworkHasValidatedInternet()
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    private fun currentDefaultNetworkHasValidatedInternet(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateConnectivityState(hasValidatedInternet: Boolean) {
        val previousState = internetValidated
        internetValidated = hasValidatedInternet
        if (previousState == null || previousState == hasValidatedInternet) {
            if (hasValidatedInternet && offlineMessageVisible) retryAfterNetworkReturns()
            return
        }

        if (hasValidatedInternet) {
            EventLog.record(this, EventType.NETWORK_CONNECTION_RESTORED)
            if (offlineMessageVisible) retryAfterNetworkReturns()
        } else {
            EventLog.record(this, EventType.NETWORK_CONNECTION_LOST)
            rememberCurrentPageForRetry()
            showOfflineMessage()
        }
    }

    private fun stopNetworkMonitoring() {
        val callback = networkCallback ?: return
        connectivityManager.unregisterNetworkCallback(callback)
        networkCallback = null
    }

    private fun rememberCurrentPageForRetry() {
        val currentUrl = if (::webView.isInitialized) webView.url else null
        retryUrl = if (!currentUrl.isNullOrBlank() && urlPolicy.isAllowed(currentUrl)) {
            currentUrl
        } else {
            config.initialUrl
        }
    }

    private fun showOfflineMessage() {
        if (!::offlineMessage.isInitialized) {
            return
        }
        offlineMessageVisible = true
        offlineMessage.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun hideOfflineMessage() {
        if (!::offlineMessage.isInitialized) {
            return
        }
        offlineMessageVisible = false
        offlineMessage.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun retryAfterNetworkReturns() {
        if (!offlineMessageVisible) {
            return
        }
        val urlToRetry = retryUrl ?: config.initialUrl
        hideOfflineMessage()
        loadUrlIfAllowed(urlToRetry)
    }

    private fun recreateWebViewAfterRendererExit(oldWebView: WebView) {
        pendingCameraRequest?.deny()
        pendingCameraRequest = null
        (oldWebView.parent as? ViewGroup)?.removeView(oldWebView)
        oldWebView.destroy()

        retryUrl = config.initialUrl
        webView = createWebView()
        container.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        hideOfflineMessage()
        loadUrlIfAllowed(config.initialUrl)
    }

    private fun hideSystemBars() {
        val controller = window.insetsController ?: return
        controller.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsets.Type.systemBars())
    }

    private fun registerBackCallbackForAndroid13AndAbove() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val callback = OnBackInvokedCallback {
            if (::webView.isInitialized && webView.canGoBack()) {
                webView.goBack()
            }
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backInvokedCallback = callback
    }

    companion object {
        const val ACTION_SETUP_TEST = "freezekit.capitaldascestas.action.SETUP_CONFER_TEST"
        private const val EXTRA_SETUP_TEST_TOKEN = "freezekit.extra.SETUP_TEST_TOKEN"

        private const val CAMERA_PERMISSION_REQUEST_CODE = 201
        private const val GESTURE_WINDOW_MS = 3_000L
        private const val REQUIRED_GESTURE_TAPS = 7
        @Volatile private var activeActivity: WeakReference<MainActivity>? = null
        private var pendingSetupTestToken = 0L
        private val setupTestRandom = SecureRandom()

        @Synchronized
        fun createSetupTestIntent(context: Context): Intent {
            var token = 0L
            while (token == 0L) token = setupTestRandom.nextLong()
            pendingSetupTestToken = token
            return Intent(context, MainActivity::class.java)
                .setAction(ACTION_SETUP_TEST)
                .putExtra(EXTRA_SETUP_TEST_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        @Synchronized
        private fun consumeSetupTestToken(token: Long): Boolean {
            if (token == 0L || token != pendingSetupTestToken) return false
            pendingSetupTestToken = 0L
            return true
        }

        fun clearActiveWebSession(requester: Activity, onComplete: (Boolean) -> Unit) {
            val activity = activeActivity?.get()
            if (activity != null && !activity.isFinishing) {
                activity.runOnUiThread { activity.clearWebSession(onComplete) }
                return
            }
            requester.runOnUiThread {
                try {
                    val temporaryWebView = WebView(requester)
                    temporaryWebView.clearCache(true)
                    temporaryWebView.destroy()
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies { onComplete(true) }
                } catch (_: Exception) {
                    onComplete(false)
                }
            }
        }

        fun reloadConfiguredStartPage() {
            val activity = activeActivity?.get() ?: return
            activity.runOnUiThread { activity.reloadConfiguredStartPage() }
        }
    }
}
