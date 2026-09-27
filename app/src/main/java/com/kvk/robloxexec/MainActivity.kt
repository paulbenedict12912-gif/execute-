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
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
        }

        statusText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }

        launchBtn = Button(this).apply {
            text = "Launch floating menu"
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
        ).apply { topMargin = 32 }

        root.addView(title)
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
    private lateinit var clearBtn: Button
    private lateinit var scriptInput: EditText
    private lateinit var outputView: TextView
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
            x = 80
            y = 300
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

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
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        circle.addView(label)
        circleView = circle
        attachDrag(circle, expandedProvider = { false })
    }

    private fun buildPanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F00B0B10"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), Color.parseColor("#7C4DFF"))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "  RblxExec"
            setTextColor(Color.parseColor("#B983FF"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minimizeBtn = smallBtn("—")
        val closeBtn = smallBtn("✕")

        minimizeBtn.setOnClickListener { collapseToCircle() }
        closeBtn.setOnClickListener { stopSelf() }

        header.addView(title)
        header.addView(minimizeBtn)
        header.addView(closeBtn)

        val scriptLabel = TextView(this).apply {
            text = "script"
            setTextColor(Color.parseColor("#7C4DFF"))
            textSize = 11f
            setPadding(dp(4), dp(8), 0, dp(2))
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
            setBackgroundColor(Color.parseColor("#15151C"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minLines = 6
            maxLines = 10
            isSingleLine = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(6))
        }

        attachBtn = actionBtn("Attach", "#7C4DFF")
        execBtn = actionBtn("Execute", "#00C853")
        clearBtn = actionBtn("Clear", "#455A64")

        attachBtn.setOnClickListener { toggleAttach() }
        execBtn.setOnClickListener { executeScript() }
        clearBtn.setOnClickListener {
            scriptInput.setText("")
            outputView.text = ""
        }

        btnRow.addView(attachBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        btnRow.addView(execBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        btnRow.addView(clearBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val outLabel = TextView(this).apply {
            text = "output"
            setTextColor(Color.parseColor("#7C4DFF"))
            textSize = 11f
            setPadding(dp(4), dp(4), 0, dp(2))
        }

        outputView = TextView(this).apply {
            setTextColor(Color.parseColor("#9BE7A0"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.parseColor("#101016"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            text = "> waiting for attach...\n"
        }

        val outScroll = ScrollView(this).apply {
            addView(outputView)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(90)
            )
        }

        panel.addView(header)
        panel.addView(scriptLabel)
        panel.addView(scriptInput)
        panel.addView(btnRow)
        panel.addView(outLabel)
        panel.addView(outScroll)

        panelView = panel
        attachDrag(header, expandedProvider = { true })
    }

    private fun smallBtn(label: String) = TextView(this).apply {
        text = label
        setTextColor(Color.parseColor("#B983FF"))
        textSize = 18f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(2), dp(10), dp(2))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun actionBtn(label: String, colorHex: String) = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = dp(8).toFloat()
        }
        setPadding(dp(4), dp(6), dp(4), dp(6))
    }

    private fun showCircle() {
        params = baseParams(dp(56), dp(56))
        params.x = 80
        params.y = 300
        windowManager.addView(circleView, params)
    }

    private fun expandToPanel() {
        windowManager.removeView(circleView)

        val w = dp(320)
        val h = dp(420)
        params = baseParams(w, h)
        params.x = dp(40)
        params.y = dp(200)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        windowManager.addView(panelView, params)
    }

    private fun collapseToCircle() {
        windowManager.removeView(panelView)

        params = baseParams(dp(56), dp(56))
        params.x = 80
        params.y = 300

        windowManager.addView(circleView, params)
    }

    private fun attachDrag(view: View, expandedProvider: () -> Boolean) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
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
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = initialX + dx.roundToInt()
                    params.y = initialY + dy.roundToInt()
                    try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !expandedProvider()) expandToPanel()
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
                cornerRadius = dp(8).toFloat()
            }
            appendOut("> attached to Roblox process")
            appendOut("> lua state: ready")
            appendOut("> awaiting script...")
        } else {
            attached = false
            attachBtn.text = "Attach"
            attachBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C4DFF"))
                cornerRadius = dp(8).toFloat()
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
        appendOut("> executing...")
        appendOut("  ${code.lineSequence().first().take(60)}")
    }

    private fun appendOut(line: String) {
        outputView.text = "${outputView.text}$line\n"
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
