package com.neo.wakeup

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.RippleDrawable
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ---------- Themes ----------
class Th(val name: String, b1: String, b2: String, x1: String, x2: String, t: String, s: String, c: String, bt: String, val dark: Boolean) {
    val bg1 = Color.parseColor(b1); val bg2 = Color.parseColor(b2)
    val ac1 = Color.parseColor(x1); val ac2 = Color.parseColor(x2)
    val text = Color.parseColor(t); val sub = Color.parseColor(s)
    val card = Color.parseColor(c); val btnText = Color.parseColor(bt)
}

val THEMES = listOf(
    Th("Midnight", "#0F0C29", "#302B63", "#8E7BFF", "#FF6B9D", "#FFFFFF", "#B8B5E0", "#26FFFFFF", "#FFFFFF", true),
    Th("Sunrise", "#FFF1E6", "#FFC9A8", "#FF8A00", "#FF3D77", "#3B1F1A", "#8A5A4B", "#99FFFFFF", "#FFFFFF", false),
    Th("Ocean", "#021B3A", "#06607F", "#1CB5E0", "#00E5B0", "#FFFFFF", "#A8D8EA", "#26FFFFFF", "#FFFFFF", true),
    Th("Forest", "#0B1F14", "#1E4D2B", "#A8E063", "#56AB2F", "#EAFBEA", "#A5CBA8", "#26FFFFFF", "#0B1F14", true),
    Th("Candy", "#FFE3F1", "#E0C3FC", "#FF8AD8", "#9B6BFF", "#3A1F4D", "#7C5C99", "#99FFFFFF", "#FFFFFF", false),
    Th("Mono", "#000000", "#1C1C1C", "#FFFFFF", "#BDBDBD", "#FFFFFF", "#9E9E9E", "#1AFFFFFF", "#000000", true)
)

// ---------- Core ----------
object Core {
    const val CH_ALARM = "alarm"; const val CH_INFO = "info"; const val CH_SVC = "quiet"
    val ICON = R.drawable.ic_alarm
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("p", 0)

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        try { nm.deleteNotificationChannel("svc") } catch (e: Exception) {}
        nm.createNotificationChannel(NotificationChannel(CH_SVC, "Background listening", NotificationManager.IMPORTANCE_NONE))
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Messages", NotificationManager.IMPORTANCE_HIGH))
        val a = NotificationChannel(CH_ALARM, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH)
        a.setSound(null, null); a.setBypassDnd(true)
        nm.createNotificationChannel(a)
    }

    fun send(c: Context, type: String, done: ((Boolean) -> Unit)? = null) {
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done?.invoke(false); return }
        Thread {
            var ok = false
            try {
                val h = URL("https://ntfy.sh/$t").openConnection() as HttpURLConnection
                h.requestMethod = "POST"; h.doOutput = true; h.connectTimeout = 10000; h.readTimeout = 10000
                h.outputStream.use { it.write(JSONObject().put("t", type).toString().toByteArray()) }
                ok = h.responseCode == 200; h.disconnect()
            } catch (e: Exception) {}
            if (done != null) Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    fun info(c: Context, title: String, text: String) {
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CH_INFO).setSmallIcon(ICON).setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt() + 10, n)
    }

    fun ring(c: Context) {
        val i = Intent(c, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CH_ALARM).setSmallIcon(ICON).setContentTitle("WAKE UP").setContentText("Tap to open")
            .setCategory(Notification.CATEGORY_ALARM).setFullScreenIntent(pi, true).setContentIntent(pi).setOngoing(true).build()
        c.getSystemService(NotificationManager::class.java).notify(2, n)
        try { c.startActivity(i) } catch (e: Exception) {}
    }

    fun freshWake(c: Context) {
        val p = prefs(c)
        if (System.currentTimeMillis() > p.getLong("snzu", 0L)) p.edit().putInt("snz", 0).apply()
    }

    private fun schedPi(c: Context, code: Int, k: String) = PendingIntent.getBroadcast(c, code,
        Intent(c, SchedReceiver::class.java).putExtra("k", k), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun setAt(c: Context, t: Long, code: Int, k: String) {
        try {
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(t, open), schedPi(c, code, k))
        } catch (e: Exception) {}
    }

    fun cancelAt(c: Context, code: Int) {
        try { c.getSystemService(AlarmManager::class.java).cancel(schedPi(c, code, "x")) } catch (e: Exception) {}
    }

    fun scheduleNext(c: Context) {
        try {
            val p = prefs(c)
            if (!p.getBoolean("sched_on", false)) { cancelAt(c, 4); return }
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, p.getInt("sh", 8)); cal.set(java.util.Calendar.MINUTE, p.getInt("sm", 0))
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            setAt(c, cal.timeInMillis, 4, "ring")
        } catch (e: Exception) {}
    }
}

// ---------- Alarm sound ----------
object Alarm {
    private var mp: MediaPlayer? = null
    private var vib: Vibrator? = null
    private val h = Handler(Looper.getMainLooper())
    private var vol = 0.2f
    var ringing = false
    private var ctx: Context? = null
    private val ramp = object : Runnable {
        override fun run() {
            if (!ringing) return
            vol = minOf(1f, vol + 0.1f)
            try { mp?.setVolume(vol, vol) } catch (e: Exception) {}
            h.postDelayed(this, 6000)
        }
    }

    fun start(c: Context) {
        ctx = c.applicationContext
        val a = ctx!!
        if (ringing) return
        ringing = true; vol = 0.2f
        val p = Core.prefs(a)
        val am = a.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val s = p.getString("tone", "")!!
        val uri = if (s.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(s)
        try {
            val m = MediaPlayer()
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp = m
            m.setDataSource(a, uri); m.isLooping = true; m.setVolume(vol, vol); m.prepare(); m.start()
        } catch (e: Exception) {}
        try {
            vib = a.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        } catch (e: Exception) {}
        h.postDelayed(ramp, 6000)
        if (!p.getBoolean("forever", true)) h.postDelayed({ stop(a) }, 90000)
        Core.ring(a)
    }

    fun stop(c: Context) {
        val a = c.applicationContext
        ringing = false
        h.removeCallbacksAndMessages(null)
        val m = mp; mp = null
        if (m != null) {
            try { m.setVolume(0f, 0f) } catch (e: Exception) {}
            try { m.pause() } catch (e: Exception) {}
            try { m.stop() } catch (e: Exception) {}
            try { m.release() } catch (e: Exception) {}
        }
        try { vib?.cancel() } catch (e: Exception) {}
        try { a.getSystemService(NotificationManager::class.java).cancel(2) } catch (e: Exception) {}
    }
}

fun snoozePi(c: Context): PendingIntent =
    PendingIntent.getBroadcast(c, 3, Intent(c, SnoozeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Alarm.start(c) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.startForegroundService(Intent(c, ListenService::class.java)); Core.scheduleNext(c)
    }
}

class SchedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = Core.prefs(c)
        when (i.getStringExtra("k")) {
            "ring" -> {
                Core.scheduleNext(c)
                if (!p.getBoolean("sched_on", false)) return
                Core.freshWake(c)
                p.edit().putBoolean("pend", true).apply()
                Alarm.start(c)
                Core.setAt(c, System.currentTimeMillis() + p.getInt("grace", 15) * 60000L, 5, "check")
            }
            "check" -> if (p.getBoolean("pend", false)) { p.edit().putBoolean("pend", false).apply(); Core.send(c, "late") }
        }
    }
}

class WakeWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getBroadcast(c, 6, Intent(c, WakeWidget::class.java).setAction("com.neo.wakeup.WAKE"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val v = RemoteViews(c.packageName, R.layout.widget); v.setOnClickPendingIntent(R.id.w, pi)
        for (id in ids) m.updateAppWidget(id, v)
    }
    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == "com.neo.wakeup.WAKE") {
            Core.send(c, "wake")
            try { c.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (e: Exception) {}
        }
    }
}

class WakeTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Wake partner"; icon = Icon.createWithResource(this@WakeTile, R.drawable.ic_alarm); updateTile() }
    }
    override fun onClick() { Core.send(this, "wake") }
}

// ---------- Listener service ----------
class ListenService : Service() {
    private var started = false
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        Core.channels(this)
        val n = Notification.Builder(this, Core.CH_SVC).setSmallIcon(Core.ICON).setContentTitle("WakeUp").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (!started) { started = true; Thread { loop() }.start() }
        return START_STICKY
    }

    private fun handle(t: String) {
        Handler(Looper.getMainLooper()).post {
            when (t) {
                "wake" -> { Core.freshWake(this); Alarm.start(this) }
                "polite" -> Core.info(this, "Are you awake?", "Your partner is checking on you")
                "ack" -> Core.info(this, "They're up", "Your partner confirmed they're awake")
                "late" -> Core.info(this, "Still asleep", "They haven't confirmed they're up. Tap to wake them!")
            }
        }
    }

    private fun loop() {
        while (true) {
            val p = Core.prefs(this)
            val topic = p.getString("mine", "")!!
            if (topic.isEmpty()) { Thread.sleep(5000); continue }
            try {
                val last = p.getLong("lastT", 0L)
                val since = if (last > 0) last else System.currentTimeMillis() / 1000
                val h = URL("https://ntfy.sh/$topic/json?since=$since").openConnection() as HttpURLConnection
                h.readTimeout = 120000
                h.inputStream.bufferedReader().forEachLine { line ->
                    if (Core.prefs(this).getString("mine", "") != topic) throw RuntimeException("code changed")
                    try {
                        val o = JSONObject(line)
                        if (o.optString("event") == "message") {
                            val tm = o.optLong("time"); val id = o.optString("id")
                            val lt = p.getLong("lastT", 0L)
                            if (tm > lt || (tm == lt && id != p.getString("lastId", ""))) {
                                p.edit().putLong("lastT", tm).putString("lastId", id).apply()
                                if (System.currentTimeMillis() / 1000 - tm < 90) handle(JSONObject(o.getString("message")).getString("t"))
                            }
                        }
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
            Thread.sleep(5000)
        }
    }
}

// ---------- UI base ----------
abstract class Base : Activity() {
    lateinit var t: Th
    private var applied = -1
    private var rootV: View? = null

    fun dp(x: Int): Float = x * resources.displayMetrics.density
    fun dpi(x: Int): Int = dp(x).toInt()
    private fun idx() = Core.prefs(this).getInt("theme", 0).coerceIn(0, THEMES.size - 1)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        applied = idx(); t = THEMES[applied]
        window.statusBarColor = t.bg1; window.navigationBarColor = t.bg2
        if (!t.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    override fun onResume() { super.onResume(); if (idx() != applied) recreate() }

    fun show(v: FrameLayout) {
        v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.bg1, t.bg2))
        v.isFocusableInTouchMode = true; rootV = v; setContentView(v)
    }
    fun shape(c: Int, r: Float) = GradientDrawable().apply { setColor(c); cornerRadius = r }
    fun tv(s: String, size: Float, color: Int = t.text, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
    fun btn(label: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; stateListAnimator = null
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) t.btnText else t.text)
        val base = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(t.ac1, t.ac2)).apply { cornerRadius = dp(28) }
                   else shape(t.card, dp(28))
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null)
        setPadding(dpi(20), dpi(14), dpi(20), dpi(14))
        setOnClickListener { click() }
    }
    fun sw(label: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = label; setTextColor(t.text); textSize = 15f; isChecked = on
        thumbTintList = ColorStateList.valueOf(t.ac1); trackTintList = ColorStateList.valueOf(t.sub)
        setOnCheckedChangeListener { _, v -> f(v) }
    }
    fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setTextColor(t.text); setHintTextColor(t.sub); textSize = 15f; isSingleLine = true
        background = shape(if (t.dark) 0x22FFFFFF else 0x14000000, dp(14))
        setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
    }
    fun LinearLayout.put(v: View, top: Int = 12, h: Int = -2) {
        addView(v, LinearLayout.LayoutParams(-1, h).apply { topMargin = dpi(top) })
    }
    fun card(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(t.card, dp(24))
        setPadding(dpi(18), dpi(16), dpi(18), dpi(16))
        v.forEachIndexed { i, x -> put(x, if (i == 0) 0 else 10) }
    }
    fun flash(v: TextView, s: String) { val o = v.text; v.text = s; v.postDelayed({ v.text = o }, 1500) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val f = currentFocus
            if (f is EditText) {
                val r = Rect(); f.getGlobalVisibleRect(r)
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    f.clearFocus(); rootV?.requestFocus()
                    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(f.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

// ---------- Alarm screen ----------
class AlarmActivity : Base() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val left = 2 - Core.prefs(this).getInt("snz", 0)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(28), dpi(28), dpi(28), dpi(28)) }
        col.put(tv("WAKE UP", 18f, t.sub, true).apply { letterSpacing = 0.3f; gravity = Gravity.CENTER }, 0)
        col.put(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 84f; setTextColor(t.text)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER
        }, 8)
        col.put(btn("I'M UP", true) { up() }.apply { textSize = 20f }, 48, dpi(104))
        if (left > 0) col.put(btn("Snooze 5 min  ($left left)", false) { snooze() }.apply { textSize = 20f }, 14, dpi(104))
        val root = FrameLayout(this); root.addView(col, FrameLayout.LayoutParams(-1, -1)); show(root)
    }
    private fun up() {
        Alarm.stop(this)
        val p = Core.prefs(this)
        p.edit().putInt("snz", 0).putBoolean("pend", false).apply()
        try { getSystemService(AlarmManager::class.java).cancel(snoozePi(this)) } catch (e: Exception) {}
        Core.cancelAt(this, 5)
        Core.send(this, "ack"); finish()
    }
    private fun snooze() {
        Alarm.stop(this)
        val p = Core.prefs(this)
        val at = System.currentTimeMillis() + 5 * 60 * 1000L
        p.edit().putInt("snz", p.getInt("snz", 0) + 1).putLong("snzu", at + 10 * 60 * 1000L).apply()
        try {
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, open), snoozePi(this))
        } catch (e: Exception) {}
        finish()
    }
}

// ---------- Main screen ----------
class MainActivity : Base() {
    private lateinit var grace: EditText
    private fun saveGrace() {
        Core.prefs(this).edit().putInt("grace", grace.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 15).apply()
    }
    override fun onPause() { saveGrace(); super.onPause() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Core.channels(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val p = Core.prefs(this)
        if (p.getString("mine", "").isNullOrEmpty()) {
            val c = "abcdefghijkmnpqrstuvwxyz23456789"
            p.edit().putString("mine", "wake-" + (1..12).map { c.random() }.joinToString("")).apply()
        }
        val status = tv("", 14f, t.sub).apply { gravity = Gravity.CENTER }
        fun go(type: String, ok: String) {
            if (p.getString("theirs", "").isNullOrBlank()) { status.text = "Add your partner's code in Settings"; return }
            status.text = "Sending..."
            Core.send(this, type) { good -> status.text = if (good) ok else "Couldn't send. Check your connection" }
        }

        val ring = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((t.ac1 and 0x00FFFFFF) or 0x33000000) }
        }
        val big = Button(this).apply {
            text = "WAKE UP"; textSize = 28f; letterSpacing = 0.1f; isAllCaps = false; stateListAnimator = null
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(t.btnText)
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.ac1, t.ac2)).apply { shape = GradientDrawable.OVAL }, null)
            setOnClickListener { go("wake", "Wake-up sent") }
        }
        val wrap = FrameLayout(this)
        wrap.addView(ring, FrameLayout.LayoutParams(dpi(300), dpi(300), Gravity.CENTER))
        wrap.addView(big, FrameLayout.LayoutParams(dpi(236), dpi(236), Gravity.CENTER))
        ObjectAnimator.ofPropertyValuesHolder(ring, PropertyValuesHolder.ofFloat("scaleX", 0.92f, 1f), PropertyValuesHolder.ofFloat("scaleY", 0.92f, 1f)).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }

        grace = field("minutes", p.getInt("grace", 15).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) saveGrace() }
        }
        val tb = btn("", false) {}
        fun lbl() { tb.text = "Wake-up time   %02d:%02d".format(p.getInt("sh", 8), p.getInt("sm", 0)) }
        lbl()
        tb.setOnClickListener {
            try {
                TimePickerDialog(this, { _, h, m -> p.edit().putInt("sh", h).putInt("sm", m).apply(); lbl(); Core.scheduleNext(this) },
                    p.getInt("sh", 8), p.getInt("sm", 0), true).show()
            } catch (e: Exception) {}
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dpi(24), dpi(56), dpi(24), dpi(110)) }
        col.put(tv("Wake Up", 18f, t.sub, true).apply { letterSpacing = 0.3f; gravity = Gravity.CENTER }, 0)
        col.addView(wrap, LinearLayout.LayoutParams(dpi(300), dpi(300)).apply { topMargin = dpi(24) })
        col.put(status, 4)
        col.put(btn("Are you awake?", false) { go("polite", "Sent") }, 16)
        col.put(card(
            sw("Alarm never stops until I tap I'm up", p.getBoolean("forever", true)) { v -> p.edit().putBoolean("forever", v).apply() },
            sw("Scheduled wake-up", p.getBoolean("sched_on", false)) { v -> p.edit().putBoolean("sched_on", v).apply(); Core.scheduleNext(this) },
            tb,
            tv("If I'm not up after this many minutes, ping my partner:", 13f, t.sub),
            grace), 20)

        val gear = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings); setColorFilter(t.text)
            setPadding(dpi(14), dpi(14), dpi(14), dpi(14)); background = shape(t.card, dp(28))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        val root = FrameLayout(this)
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(col) })
        root.addView(gear, FrameLayout.LayoutParams(dpi(56), dpi(56), Gravity.BOTTOM or Gravity.START).apply { setMargins(dpi(24), 0, 0, dpi(28)) })
        show(root)
        startForegroundService(Intent(this, ListenService::class.java))
    }
}

// ---------- Settings ----------
class SettingsActivity : Base() {
    private lateinit var mine: EditText
    private lateinit var theirs: EditText
    private lateinit var musicLbl: TextView
    private fun save() {
        Core.prefs(this).edit().putString("mine", mine.text.toString().trim()).putString("theirs", theirs.text.toString().trim()).apply()
    }
    override fun onPause() { save(); super.onPause() }
    private fun row(a: View, b: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(6) })
        addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(6) })
    }
    private fun sec(s: String) = tv(s, 12f, t.sub, true).apply { letterSpacing = 0.15f }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Core.prefs(this)
        mine = field("Your code", p.getString("mine", "")!!)
        theirs = field("Paste your partner's code", p.getString("theirs", "")!!)
        for (e in listOf(mine, theirs)) e.onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) save() }

        val copy = btn("Copy", false) {}
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", mine.text.toString())); flash(copy, "Copied")
        }
        val share = btn("Share", true) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "My WakeUp code: " + mine.text.toString()), null))
        }
        musicLbl = tv(if (p.getString("tone", "").isNullOrEmpty()) "Default alarm sound" else "Custom music selected", 14f, t.sub)
        val pick = btn("Choose music", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 7)
        }
        val reset = btn("Use default", false) { p.edit().remove("tone").apply(); musicLbl.text = "Default alarm sound" }
        val bg = btn("Allow background running", true) {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }

        val cur = p.getInt("theme", 0).coerceIn(0, THEMES.size - 1)
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        THEMES.forEachIndexed { i, th ->
            val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(th.bg1, th.ac1, th.ac2)).apply {
                shape = GradientDrawable.OVAL; if (i == cur) setStroke(dpi(3), t.text)
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(6), dpi(4), dpi(6), dpi(4))
                addView(View(this@SettingsActivity).apply { background = d }, LinearLayout.LayoutParams(dpi(56), dpi(56)))
                addView(tv(th.name, 12f, t.sub).apply { gravity = Gravity.CENTER })
                setOnClickListener { p.edit().putInt("theme", i).apply(); recreate() }
            }
            themeRow.addView(item)
        }
        val themeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(themeRow) }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(32)) }
        col.put(tv("‹  Back", 16f, t.sub, true).apply { setOnClickListener { finish() }; setPadding(0, dpi(8), dpi(16), dpi(8)) }, 0, -2)
        col.put(tv("Settings", 30f, t.text, true), 4)
        col.put(card(sec("YOUR CODE"), mine, row(copy, share), tv("Send this to your partner so they can paste it into their app.", 13f, t.sub)), 20)
        col.put(card(sec("PARTNER'S CODE"), theirs, tv("Paste the code your partner sent you.", 13f, t.sub)), 14)
        col.put(card(sec("ALARM MUSIC"), musicLbl, row(pick, reset)), 14)
        col.put(card(sec("BACKGROUND RUNNING"), tv("Lets wake-ups arrive while the app is closed.", 13f, t.sub), bg), 14)
        col.put(card(sec("THEME"), themeScroll), 14)
        val root = FrameLayout(this); root.addView(ScrollView(this).apply { addView(col) }); show(root)
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        val u = d?.data ?: return
        if (r == 7 && c == RESULT_OK) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            Core.prefs(this).edit().putString("tone", u.toString()).apply(); musicLbl.text = "Custom music selected"
        }
    }
}
