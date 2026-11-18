package app.yomu

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Affiche une petite poignée sur le bord de l'écran, par-dessus toutes les applis.
 * Toucher la poignée ouvre le panneau ; la faire glisser verticalement la déplace.
 */
class EdgeService : Service() {

    private var windowManager: WindowManager? = null
    private var handleView: View? = null
    private lateinit var prefs: Prefs

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.handleEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Recrée la poignée (utile après un changement de côté dans les réglages).
        removeHandle()
        addHandle()
        return START_STICKY
    }

    override fun onDestroy() {
        removeHandle()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Poignée Yomu", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        val openSettings = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, EdgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Yomu est actif")
            .setContentText("Touche la poignée sur le bord de l'écran pour analyser le texte copié.")
            .setContentIntent(openSettings)
            .addAction(
                Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Masquer la poignée", stop).build()
            )
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addHandle() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels
        val onRight = prefs.handleOnRight

        val touchW = (26 * density).toInt()   // zone tactile
        val barW = (9 * density).toInt()      // barre visible
        val barH = (110 * density).toInt()

        val bar = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 6 * density
            }
        }
        val container = FrameLayout(this).apply {
            addView(
                bar,
                FrameLayout.LayoutParams(barW, barH).apply {
                    gravity = (if (onRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                }
            )
            contentDescription = "Ouvrir Yomu"
        }

        val params = WindowManager.LayoutParams(
            touchW,
            barH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (onRight) Gravity.END else Gravity.START)
            x = 0
            y = prefs.handleY.takeIf { it >= 0 } ?: (screenH / 2 - barH / 2)
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawY = 0f
        var startY = 0
        var dragging = false

        container.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = ev.rawY
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - downRawY
                    if (!dragging && abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.y = (startY + dy.toInt()).coerceIn(0, (screenH - barH).coerceAtLeast(0))
                        try {
                            wm.updateViewLayout(container, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        prefs.handleY = params.y
                    } else {
                        openPanel()
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(container, params)
        handleView = container
    }

    private fun removeHandle() {
        handleView?.let { v ->
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
            }
        }
        handleView = null
    }

    private fun openPanel() {
        val i = Intent(this, PanelActivity::class.java)
            .setAction(PanelActivity.ACTION_FROM_HANDLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    companion object {
        private const val CHANNEL_ID = "yomu_handle"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "app.yomu.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, EdgeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EdgeService::class.java))
        }
    }
}
