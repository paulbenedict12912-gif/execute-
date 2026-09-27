package com.kvk.robloxexec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var launchBtn: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#0B0B10"))
        }

        val title = TextView(this).apply {
            text = "RblxExec"
            setTextColor(Color.parseColor("#B983FF"))
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "floating script executor"
            setTextColor(Color.parseColor("#6A6A7A"))
            textSize = 12f
            setPadding(0, 6, 0, 0)
        }

        statusText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }

        launchBtn = Button(this).apply {
            text = "Launch floating menu"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C4DFF"))
                cornerRadius = 24f
            }
            setPadding(48, 32, 48, 32)
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, FloatingExecService::class.java)
                    )
                    handler.postDelayed({ moveTaskToBack(true) }, 300)
                }
            }
        }

        fun lp() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText, lp())
        root.addView(launchBtn, lp())

        setContentView(root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "Overlay permission required.\nOpening settings..."
            launchBtn.isEnabled = false
            handler.postDelayed({
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }, 500)
        } else {
            statusText.text = "Permission granted. Ready."
            launchBtn.isEnabled = true
        }
    }
}

class FloatingExecService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var circleView: View
    private lateinit var panelView: View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var attachBtn: Button
    private lateinit var execBtn: Button
    private lateinit var scriptInput: EditText
    private lateinit var outputView: TextView
    private lateinit var outputScroll: ScrollView
    private var attached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildCircle()
        buildPanel()
        showCircle()
    }

    private fun startForegroundInternal() {
        val chId = "rblx_exec_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(chId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(chId, "Executor", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notif: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("RblxExec active")
            .setContentText("Tap the floating button in Roblox")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        startForeground(1001, notif)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun baseParams(w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun buildCircle() {
        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC7C4DFF"))
                setStroke(dp(2), Color.parseColor("#FFB983FF"))
            }
        }
        val label = TextView(this).apply {
            text = "R"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        circle.addView(label)
        circleView = circle
        attachDrag(circle, circle) { expandToPanel() }
    }

    private fun buildPanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F00B0B10"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.parseColor("#7C4DFF"))
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                rightMargin = dp(8)
            }
        }

        val title = TextView(this).apply {
            text = "RblxExec"
            setTextColor(Color.parseColor("#B983FF"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minimizeBtn = headerBtn("—")
        val closeBtn = headerBtn("✕")

        minimizeBtn.setOnClickListener { collapseToCircle() }
        closeBtn.setOnClickListener { stopSelf() }

        header.addView(dot)
        header.addView(title)
        header.addView(minimizeBtn)
        header.addView(closeBtn)

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#2A2A3A"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        }

        val scriptLabel = TextView(this).apply {
            text = "SCRIPT"
            setTextColor(Color.parseColor("#7C4DFF"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            setPadding(dp(4), 0, 0, dp(4))
        }

        scriptInput = EditText(this).apply {
            setTextColor(Color.parseColor("#E0E0E6"))
            setHintTextColor(Color.parseColor("#555566"))
            hint = "-- print(\"hello roblox\")"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15151C"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#25252F"))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
            )
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }

        attachBtn = actionBtn("Attach", "#7C4DFF")
        execBtn = actionBtn("Execute", "#00C853")

        attachBtn.setOnClickListener { toggleAttach() }
        execBtn.setOnClickListener { executeScript() }

        btnRow.addView(attachBtn, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) })
        btnRow.addView(execBtn, LinearLayout.LayoutParams(0, dp(42), 1f))

        val outLabel = TextView(this).apply {
            text = "OUTPUT"
            setTextColor(Color.parseColor("#7C4DFF"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            setPadding(dp(4), 0, 0, dp(4))
        }

        outputView = TextView(this).apply {
            setTextColor(Color.parseColor("#9BE7A0"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101016"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#25252F"))
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            text = "> waiting for attach...\n"
        }

        outputScroll = ScrollView(this).apply {
            addView(outputView)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(110)
            )
        }

        panel.addView(header)
        panel.addView(divider)
        panel.addView(scriptLabel)
        panel.addView(scriptInput)
        panel.addView(btnRow)
        panel.addView(outLabel)
        panel.addView(outputScroll)

        panelView = panel
        attachDrag(header, panel) { /* no-op tap */ }
    }

    private fun headerBtn(label: String) = TextView(this).apply {
        text = label
        setTextColor(Color.parseColor("#B983FF"))
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(2), dp(10), dp(2))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun actionBtn(label: String, colorHex: String) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = dp(10).toFloat()
        }
        setPadding(dp(4), 0, dp(4), 0)
    }

    private fun showCircle() {
        params = baseParams(dp(56), dp(56)).apply {
            x = dp(80)
            y = dp(300)
        }
        windowManager.addView(circleView, params)
    }

    private fun expandToPanel() {
        try { windowManager.removeView(circleView) } catch (_: Exception) {}

        params = baseParams(dp(320), dp(460)).apply {
            x = dp(20)
            y = dp(150)
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        windowManager.addView(panelView, params)
    }

    private fun collapseToCircle() {
        try { windowManager.removeView(panelView) } catch (_: Exception) {}

        params = baseParams(dp(56), dp(56)).apply {
            x = dp(80)
            y = dp(300)
        }
        windowManager.addView(circleView, params)
    }

    /**
     * handle = view that receives touch (header or the circle)
     * container = view actually attached to WindowManager (updateViewLayout targets this)
     * onTap = action fired when user taps without dragging
     */
    private fun attachDrag(handle: View, container: View, onTap: () -> Unit) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = initialX + dx.roundToInt()
                    params.y = initialY + dy.roundToInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleAttach() {
        if (!attached) {
            attached = true
            attachBtn.text = "Detach"
            attachBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#D50000"))
                cornerRadius = dp(10).toFloat()
            }
            appendOut("> attach requested")
            appendOut("> native bridge not linked")
            appendOut("> UI is ready — injection pending")
        } else {
            attached = false
            attachBtn.text = "Attach"
            attachBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C4DFF"))
                cornerRadius = dp(10).toFloat()
            }
            appendOut("> detached")
        }
    }

    private fun executeScript() {
        val code = scriptInput.text.toString().trim()
        if (code.isEmpty()) {
            appendOut("> empty script")
            return
        }
        if (!attached) {
            appendOut("> not attached — press Attach first")
            return
        }
        appendOut("> queueing script (${code.length} chars)")
        appendOut("  ${code.lineSequence().first().take(60)}")
        appendOut("> no native handler — script not sent")
    }

    private fun appendOut(line: String) {
        outputView.text = "${outputView.text}$line\n"
        outputScroll.post {
            outputScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::circleView.isInitialized && circleView.isAttachedToWindow)
                windowManager.removeView(circleView)
        } catch (_: Exception) {}
        try {
            if (::panelView.isInitialized && panelView.isAttachedToWindow)
                windowManager.removeView(panelView)
        } catch (_: Exception) {}
    }
}
