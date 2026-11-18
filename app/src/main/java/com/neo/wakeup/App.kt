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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
    Th("Mono", "#000000", "#1C1C1C", "#FFFFFF", "#BDBDBD", "#FFFFFF", "#9E9E9E", "#1AFFFFFF", "#000000", true),
    Th("Rose Gold", "#2B1B1F", "#5E3A3F", "#F7C5A8", "#E8869A", "#FFF1EC", "#D9B3A6", "#26FFFFFF", "#2B1B1F", true),
    Th("Lavender", "#F3EEFF", "#D9CCFF", "#9D7BFF", "#6C5CE7", "#2A2150", "#6E639A", "#99FFFFFF", "#FFFFFF", false),
    Th("Dusk", "#1A1A2E", "#533A71", "#FF9A8B", "#FF6A88", "#FFFFFF", "#C9B8E0", "#26FFFFFF", "#1A1A2E", true),
    Th("Aurora", "#021124", "#0B3D3A", "#7CF9C0", "#5B8DEF", "#EAFFF7", "#9BD3C0", "#26FFFFFF", "#021124", true),
    Th("Cherry", "#2A0610", "#7A1230", "#FF5C7A", "#FF9A5C", "#FFFFFF", "#F0B2C0", "#26FFFFFF", "#2A0610", true),
    Th("Gold", "#0B0B0B", "#2A220F", "#F6D365", "#C9A227", "#FFF7DC", "#B9A56A", "#1AFFFFFF", "#0B0B0B", true),
    Th("Arctic", "#EAF6FF", "#BFE3FF", "#2D9CDB", "#56CCF2", "#0E2A3F", "#4F7694", "#99FFFFFF", "#FFFFFF", false),
    Th("Lava", "#1B0000", "#5C0A00", "#FF4E00", "#FFB200", "#FFF2E5", "#E0A890", "#26FFFFFF", "#1B0000", true),
    Th("Mint", "#E8FFF4", "#BFF2DB", "#00B894", "#55EFC4", "#0B3B2E", "#3F7F6C", "#99FFFFFF", "#0B3B2E", false),
    Th("Slate", "#14181F", "#2B3442", "#7C93FF", "#5ED3F3", "#E8EDF5", "#8FA0B8", "#26FFFFFF", "#14181F", true)
)

// card: 0 fill, 1 glass, 2 outline, 3 hard shadow, 4 soft raised, 5 hairline
// btn: 0 gradient pill, 1 glass, 2 outline, 3 hard shadow, 4 bubbly 3D, 5 soft raised, 6 editorial
// big: 0 gradient circle, 1 hard square, 2 glass circle, 3 terminal box, 4 soft circle, 5 line circle, 6 bubbly circle
// fmt: 0 normal, 1 caps, 2 [ BRACKETS ], 3 spaced caps
class Sty(val name: String, val font: String, val bold: String, val card: Int, val rad: Int, val btn: Int, val big: Int,
          val fmt: Int, val flat: Boolean, val pulse: Boolean, val spacing: Float)
val STYLES = listOf(
    Sty("Modern", "sans-serif", "sans-serif-medium", 0, 24, 0, 0, 0, false, true, 0f),
    Sty("Glass", "sans-serif-light", "sans-serif", 1, 30, 1, 2, 0, false, true, 0.02f),
    Sty("Soft", "sans-serif-light", "sans-serif-medium", 4, 32, 5, 4, 0, false, false, 0f),
    Sty("Brutal", "sans-serif-black", "sans-serif-black", 3, 6, 3, 1, 1, true, false, 0.04f),
    Sty("Terminal", "monospace", "monospace", 2, 0, 2, 3, 2, true, false, 0f),
    Sty("Editorial", "serif", "serif", 5, 2, 6, 5, 3, true, false, 0.12f),
    Sty("Bubbly", "sans-serif-black", "sans-serif-black", 0, 36, 4, 6, 0, false, true, 0f)
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

    private fun rawPost(topic: String, body: ByteArray, put: Boolean = false, fname: String? = null): Pair<Boolean, String> {
        val h = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
        h.requestMethod = if (put) "PUT" else "POST"; h.doOutput = true; h.connectTimeout = 15000; h.readTimeout = 30000
        if (fname != null) h.setRequestProperty("Filename", fname)
        h.outputStream.use { it.write(body) }
        val code = h.responseCode
        val txt = try { (if (code == 200) h.inputStream else h.errorStream).bufferedReader().readText() } catch (e: Exception) { "" }
        h.disconnect()
        return Pair(code == 200, txt)
    }

    fun postJson(c: Context, o: JSONObject, done: ((Boolean) -> Unit)? = null) {
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done?.invoke(false); return }
        Thread {
            var ok = false
            try { ok = rawPost(t, o.toString().toByteArray()).first } catch (e: Exception) {}
            if (done != null) Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    fun send(c: Context, type: String, done: ((Boolean) -> Unit)? = null) = postJson(c, JSONObject().put("t", type), done)

    fun sendCustom(c: Context, title: String, body: String, img: Uri?, done: (Boolean) -> Unit) {
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done(false); return }
        Thread {
            var ok = false
            try {
                val o = JSONObject().put("t", "msg").put("title", title).put("body", body)
                if (img != null) {
                    val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    c.contentResolver.openInputStream(img).use { BitmapFactory.decodeStream(it, null, opt) }
                    var ss = 1
                    while (opt.outWidth / ss > 1400 || opt.outHeight / ss > 1400) ss *= 2
                    val bmp = c.contentResolver.openInputStream(img).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = ss }) }!!
                    val bos = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    val r = rawPost(t, bos.toByteArray(), true, "photo.jpg")
                    if (!r.first) throw Exception("upload failed")
                    o.put("img", JSONObject(r.second).getJSONObject("attachment").getString("url"))
                }
                ok = rawPost(t, o.toString().toByteArray()).first
            } catch (e: Exception) {}
            Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    fun custom(c: Context, title: String, body: String, bmp: Bitmap?) {
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(c, CH_INFO).setSmallIcon(ICON).setContentTitle(if (title.isBlank()) "Message" else title)
            .setContentText(body).setContentIntent(pi).setAutoCancel(true)
        if (bmp != null) b.setStyle(Notification.BigPictureStyle().bigPicture(bmp).setSummaryText(body))
        else b.setStyle(Notification.BigTextStyle().bigText(body))
        c.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt() + 10, b.build())
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

    private fun handle(m: JSONObject) {
        val t = m.optString("t")
        if (t == "msg") {
            Thread {
                var bmp: Bitmap? = null
                try { val u = m.optString("img"); if (u.isNotEmpty()) bmp = URL(u).openStream().use { BitmapFactory.decodeStream(it) } } catch (e: Exception) {}
                Handler(Looper.getMainLooper()).post { Core.custom(this, m.optString("title"), m.optString("body"), bmp) }
            }.start()
            return
        }
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
                                if (System.currentTimeMillis() / 1000 - tm < 90) handle(JSONObject(o.getString("message")))
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
    lateinit var s: Sty
    private var ai = -1
    private var si = -1
    private var rootV: View? = null

    fun dp(x: Int): Float = x * resources.displayMetrics.density
    fun dpi(x: Int): Int = dp(x).toInt()
    private fun tIdx() = Core.prefs(this).getInt("theme", 0).coerceIn(0, THEMES.size - 1)
    private fun sIdx() = Core.prefs(this).getInt("style", 0).coerceIn(0, STYLES.size - 1)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ai = tIdx(); si = sIdx(); t = THEMES[ai]; s = STYLES[si]
        window.statusBarColor = t.bg1; window.navigationBarColor = if (s.flat) t.bg1 else t.bg2
        if (!t.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    override fun onResume() { super.onResume(); if (tIdx() != ai || sIdx() != si) recreate() }

    fun show(v: FrameLayout) {
        v.background = if (s.flat) ColorDrawable(t.bg1) else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.bg1, t.bg2))
        v.isFocusableInTouchMode = true; rootV = v; setContentView(v)
    }

    // ----- colour helpers -----
    val glass: Int get() = (t.text and 0x00FFFFFF) or 0x44000000
    fun opaque(): Int = if (t.dark) t.bg2 else Color.WHITE
    fun softFace(): Int = if (t.dark) lighten(t.bg2, 0.1f) else Color.WHITE
    fun darken(c: Int, f: Float): Int = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    fun lighten(c: Int, f: Float): Int = Color.rgb(Color.red(c) + ((255 - Color.red(c)) * f).toInt(),
        Color.green(c) + ((255 - Color.green(c)) * f).toInt(), Color.blue(c) + ((255 - Color.blue(c)) * f).toInt())

    // ----- drawables -----
    fun shape(c: Int, r: Float) = GradientDrawable().apply { setColor(c); cornerRadius = r }
    fun hard(face: Int, edge: Int, shadow: Int, r: Float, w: Int, off: Int): Drawable {
        val sh = shape(shadow, r)
        val fc = shape(face, r).apply { setStroke(w, edge) }
        return LayerDrawable(arrayOf<Drawable>(sh, fc)).apply { setLayerInset(0, off, off, 0, 0); setLayerInset(1, 0, 0, off, off) }
    }
    fun bub(face: Int, r: Float, depth: Int): Drawable {
        val sh = shape(darken(face, 0.7f), r); val fc = shape(face, r)
        return LayerDrawable(arrayOf<Drawable>(sh, fc)).apply { setLayerInset(1, 0, 0, 0, depth) }
    }
    fun cardBg(st: Sty = s): Drawable = when (st.card) {
        0 -> shape(t.card, dp(st.rad))
        1 -> shape(t.card, dp(st.rad)).apply { setStroke(dpi(1), glass) }
        2 -> shape(Color.TRANSPARENT, 0f).apply { setStroke(dpi(1), t.ac1) }
        3 -> hard(opaque(), t.text, t.ac1, dp(st.rad), dpi(3), dpi(5))
        4 -> shape(softFace(), dp(st.rad))
        else -> shape(Color.TRANSPARENT, dp(st.rad)).apply { setStroke(dpi(1), t.sub) }
    }
    fun btnFace(primary: Boolean, st: Sty = s): Drawable {
        val r = dp(when (st.btn) { 3 -> 6; 2 -> 0; 6 -> 2; else -> 28 })
        return when (st.btn) {
            0 -> if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(t.ac1, t.ac2)).apply { cornerRadius = r } else shape(t.card, r)
            1 -> shape(if (primary) (t.ac1 and 0x00FFFFFF) or 0x66000000 else t.card, r).apply { setStroke(dpi(1), if (primary) t.ac1 else glass) }
            2 -> shape(Color.TRANSPARENT, r).apply { setStroke(dpi(2), if (primary) t.ac1 else t.sub) }
            3 -> hard(if (primary) t.ac1 else opaque(), t.text, t.text, r, dpi(3), dpi(5))
            4 -> bub(if (primary) t.ac1 else softFace(), r, dpi(6))
            5 -> shape(if (primary) t.ac1 else softFace(), r)
            else -> shape(if (primary) t.text else Color.TRANSPARENT, r).apply { setStroke(dpi(1), t.text) }
        }
    }
    fun btnText(primary: Boolean, st: Sty = s): Int = when (st.btn) {
        1 -> t.text
        2 -> if (primary) t.ac1 else t.sub
        6 -> if (primary) t.bg1 else t.text
        else -> if (primary) t.btnText else t.text
    }
    private fun fieldBg(): Drawable = when (s.card) {
        2 -> shape(Color.TRANSPARENT, 0f).apply { setStroke(dpi(1), t.ac1) }
        3 -> shape(Color.TRANSPARENT, dp(4)).apply { setStroke(dpi(3), t.text) }
        5 -> shape(Color.TRANSPARENT, dp(2)).apply { setStroke(dpi(1), t.sub) }
        else -> shape(if (t.dark) 0x22FFFFFF else 0x14000000, dp(14))
    }

    // ----- text / widgets -----
    fun lab(x: String): String = when (s.fmt) { 1, 3 -> x.uppercase(); 2 -> "[ " + x.uppercase() + " ]"; else -> x }
    fun face(bold: Boolean, st: Sty = s): Typeface =
        Typeface.create(if (bold) st.bold else st.font, if (bold && st.bold == st.font) Typeface.BOLD else Typeface.NORMAL)
    fun tv(x: String, size: Float, color: Int = t.text, bold: Boolean = false) = TextView(this).apply {
        text = x; textSize = size; setTextColor(color); typeface = face(bold); letterSpacing = s.spacing
    }
    fun sec(x: String) = tv(lab(x), 12f, t.sub, true).apply { letterSpacing = 0.15f }
    fun btn(label: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = lab(label); isAllCaps = false; textSize = 16f; stateListAnimator = null
        typeface = face(true); letterSpacing = s.spacing; setTextColor(btnText(primary))
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), btnFace(primary), null)
        val ex = if (s.btn == 3) dpi(5) else 0
        val ey = if (s.btn == 3) dpi(5) else if (s.btn == 4) dpi(6) else 0
        setPadding(dpi(20), dpi(14), dpi(20) + ex, dpi(14) + ey)
        if (s.btn == 5) elevation = dp(6)
        setOnClickListener { click() }
    }
    fun iconBtn(res: Int, click: () -> Unit) = ImageView(this).apply {
        setImageResource(res); setColorFilter(btnText(false)); setPadding(dpi(15), dpi(15), dpi(15), dpi(15))
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), btnFace(false), null)
        if (s.btn == 5) elevation = dp(6)
        setOnClickListener { click() }
    }
    fun sw(label: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = label; setTextColor(t.text); textSize = 15f; typeface = face(false); isChecked = on
        thumbTintList = ColorStateList.valueOf(t.ac1); trackTintList = ColorStateList.valueOf(t.sub)
        setOnCheckedChangeListener { _, v -> f(v) }
    }
    fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setTextColor(t.text); setHintTextColor(t.sub); textSize = 15f; isSingleLine = true
        typeface = face(false); background = fieldBg(); setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
    }
    fun LinearLayout.put(v: View, top: Int = 12, h: Int = -2) {
        addView(v, LinearLayout.LayoutParams(-1, h).apply { topMargin = dpi(top) })
    }
    fun card(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = cardBg()
        val e = if (s.card == 3) dpi(5) else 0
        setPadding(dpi(18), dpi(16), dpi(18) + e, dpi(16) + e)
        if (s.card == 4) elevation = dp(8)
        v.forEachIndexed { i, x -> put(x, if (i == 0) 0 else 10) }
    }
    fun row(a: View, b: View, w1: Float = 1f, w2: Float = 1f) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, -2, w1).apply { marginEnd = dpi(6) })
        addView(b, LinearLayout.LayoutParams(0, -2, w2).apply { marginStart = dpi(6) })
    }
    fun addBack(col: LinearLayout) { col.addView(btn("‹  Back", false) { finish() }, LinearLayout.LayoutParams(dpi(130), dpi(54))) }
    fun flash(v: TextView, x: String) { val o = v.text; v.text = x; v.postDelayed({ v.text = o }, 1500) }

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
        col.put(tv(lab("WAKE UP"), 18f, t.sub, true).apply { letterSpacing = 0.3f; gravity = Gravity.CENTER }, 0)
        col.put(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 84f; setTextColor(t.text)
            typeface = Typeface.create(if (s.font == "sans-serif") "sans-serif-light" else s.font, Typeface.NORMAL); gravity = Gravity.CENTER
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

    private fun bigBg(): Drawable = when (s.big) {
        0 -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.ac1, t.ac2)).apply { shape = GradientDrawable.OVAL }
        1 -> hard(t.ac1, t.text, t.text, dp(18), dpi(4), dpi(8))
        2 -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf((t.ac1 and 0x00FFFFFF) or 0x55000000, (t.ac2 and 0x00FFFFFF) or 0x22000000)).apply { shape = GradientDrawable.OVAL; setStroke(dpi(2), t.ac1) }
        3 -> shape(Color.TRANSPARENT, 0f).apply { setStroke(dpi(3), t.ac1) }
        4 -> GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(softFace()) }
        5 -> GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dpi(2), t.text) }
        else -> LayerDrawable(arrayOf<Drawable>(
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(darken(t.ac1, 0.7f)) },
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(t.ac1) })).apply { setLayerInset(1, 0, 0, 0, dpi(10)) }
    }
    private fun bigText(): Int = when (s.big) { 2, 5 -> t.text; 3, 4 -> t.ac1; else -> t.btnText }

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

        val ringV = View(this).apply {
            visibility = if (s.pulse || s.big == 5) View.VISIBLE else View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (s.big == 5) { setColor(Color.TRANSPARENT); setStroke(dpi(1), t.sub) } else setColor((t.ac1 and 0x00FFFFFF) or 0x33000000)
            }
        }
        val big = Button(this).apply {
            text = lab("WAKE UP"); textSize = 26f; isAllCaps = false; stateListAnimator = null
            typeface = face(true); letterSpacing = s.spacing + 0.08f; setTextColor(bigText())
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bigBg(), null)
            if (s.big == 4) elevation = dp(16)
            setOnClickListener { go("wake", "Wake-up sent") }
        }
        val wrap = FrameLayout(this)
        wrap.addView(ringV, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        wrap.addView(big, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        wrap.addOnLayoutChangeListener { _, l, tp, r, bt, _, _, _, _ ->
            val m = minOf(r - l, bt - tp)
            val bs = (m * (if (s.big == 1 || s.big == 3) 0.70f else 0.78f)).toInt()
            if (m > 0 && big.layoutParams.width != bs) {
                big.layoutParams = FrameLayout.LayoutParams(bs, bs, Gravity.CENTER)
                ringV.layoutParams = FrameLayout.LayoutParams(m, m, Gravity.CENTER)
            }
        }
        if (s.pulse) ObjectAnimator.ofPropertyValuesHolder(ringV, PropertyValuesHolder.ofFloat("scaleX", 0.92f, 1f), PropertyValuesHolder.ofFloat("scaleY", 0.92f, 1f)).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }

        grace = field("minutes", p.getInt("grace", 15).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) saveGrace() }
        }
        val tb = btn("", false) {}
        fun lbl() { tb.text = lab("Wake-up time   %02d:%02d".format(p.getInt("sh", 8), p.getInt("sm", 0))) }
        lbl()
        tb.setOnClickListener {
            try {
                TimePickerDialog(this, { _, h, m -> p.edit().putInt("sh", h).putInt("sm", m).apply(); lbl(); Core.scheduleNext(this) },
                    p.getInt("sh", 8), p.getInt("sm", 0), true).show()
            } catch (e: Exception) {}
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(24), dpi(40), dpi(24), dpi(20)) }
        col.addView(wrap, LinearLayout.LayoutParams(-1, 0, 1f))
        col.put(status, 4)
        col.put(btn("Are you awake?", false) { go("polite", "Sent") }, 14)
        col.put(card(
            sw("Alarm never stops until I tap I'm up", p.getBoolean("forever", true)) { v -> p.edit().putBoolean("forever", v).apply() },
            sw("Scheduled wake-up", p.getBoolean("sched_on", false)) { v -> p.edit().putBoolean("sched_on", v).apply(); Core.scheduleNext(this) },
            tb,
            tv("If I'm not up after this many minutes, notify my partner:", 13f, t.sub),
            grace), 16)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(iconBtn(R.drawable.ic_settings) { startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(dpi(56), dpi(56)))
        bar.addView(iconBtn(R.drawable.ic_bell) { startActivity(Intent(this, NotifyActivity::class.java)) },
            LinearLayout.LayoutParams(dpi(56), dpi(56)).apply { marginStart = dpi(12) })
        col.addView(bar, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(16) })

        val root = FrameLayout(this)
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        show(root)
        startForegroundService(Intent(this, ListenService::class.java))
    }
}

// ---------- Custom notification ----------
class NotifyActivity : Base() {
    private var img: Uri? = null
    private lateinit var preview: ImageView
    private lateinit var tField: EditText
    private lateinit var bField: EditText
    private lateinit var tplBox: LinearLayout
    private lateinit var status: TextView

    private fun tpls(): JSONArray = try { JSONArray(Core.prefs(this).getString("tpl", "[]")) } catch (e: Exception) { JSONArray() }

    private fun renderTpl() {
        tplBox.removeAllViews()
        val a = tpls()
        if (a.length() == 0) tplBox.addView(tv("No templates yet. Write a notification and tap Save template.", 13f, t.sub))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val nm = o.optString("title").ifBlank { o.optString("body") }.take(26)
            val use = btn(nm, false) { tField.setText(o.optString("title")); bField.setText(o.optString("body")) }
            val del = btn("Delete", false) {
                val n = JSONArray()
                for (j in 0 until a.length()) if (j != i) n.put(a.get(j))
                Core.prefs(this).edit().putString("tpl", n.toString()).apply(); renderTpl()
            }
            tplBox.put(row(use, del, 3f, 2f), if (i == 0) 0 else 8)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        status = tv("", 14f, t.sub).apply { gravity = Gravity.CENTER }
        tField = field("Title (e.g. Good luck at school)", "")
        bField = field("Message", "").apply {
            isSingleLine = false; minLines = 3; gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        val add = btn("Add photo", false) { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 8) }
        val rem = btn("Remove photo", false) { img = null; preview.visibility = View.GONE }
        val send = btn("Send", true) {
            val ti = tField.text.toString().trim(); val bo = bField.text.toString().trim()
            if (Core.prefs(this).getString("theirs", "").isNullOrBlank()) status.text = "Add your partner's code in Settings"
            else if (ti.isEmpty() && bo.isEmpty() && img == null) status.text = "Write something or add a photo first"
            else {
                status.text = "Sending..."
                Core.sendCustom(this, ti, bo, img) { ok -> status.text = if (ok) "Sent" else "Couldn't send. Check your connection" }
            }
        }
        val save = btn("Save template", false) {
            val ti = tField.text.toString().trim(); val bo = bField.text.toString().trim()
            if (ti.isEmpty() && bo.isEmpty()) status.text = "Nothing to save yet"
            else {
                val a = tpls(); a.put(JSONObject().put("title", ti).put("body", bo))
                Core.prefs(this).edit().putString("tpl", a.toString()).apply(); renderTpl(); status.text = "Template saved"
            }
        }
        tplBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(32)) }
        addBack(col)
        col.put(tv("Custom notification", 28f, t.text, true), 8)
        col.put(card(sec("TITLE"), tField, sec("MESSAGE"), bField, preview, row(add, rem), status, send, save), 16)
        col.put(card(sec("TEMPLATES"), tplBox), 14)
        col.put(tv("Photos are sent through ntfy.sh and stay available for about 3 hours. Templates save text only.", 12f, t.sub), 10)
        preview.layoutParams = LinearLayout.LayoutParams(-1, dpi(180))
        renderTpl()
        val scroll = ScrollView(this); scroll.addView(col)
        val root = FrameLayout(this); root.addView(scroll); show(root)
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        val u = d?.data ?: return
        if (r == 8 && c == RESULT_OK) { img = u; preview.setImageURI(u); preview.visibility = View.VISIBLE }
    }
}

// ---------- Settings ----------
class SettingsActivity : Base() {
    companion object { var keepY = 0 }
    private lateinit var mine: EditText
    private lateinit var theirs: EditText
    private lateinit var musicLbl: TextView
    private fun save() {
        Core.prefs(this).edit().putString("mine", mine.text.toString().trim()).putString("theirs", theirs.text.toString().trim()).apply()
    }
    override fun onPause() { save(); super.onPause() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Core.prefs(this)
        val scroll = ScrollView(this)
        mine = field("Your code", p.getString("mine", "")!!)
        theirs = field("Paste your partner's code", p.getString("theirs", "")!!)
        for (e in listOf(mine, theirs)) e.onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) save() }

        val copy = btn("Copy", false) {}
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", mine.text.toString())); flash(copy, lab("Copied"))
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

        val curT = p.getInt("theme", 0).coerceIn(0, THEMES.size - 1)
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        THEMES.forEachIndexed { i, th ->
            val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(th.bg1, th.ac1, th.ac2)).apply {
                shape = GradientDrawable.OVAL; if (i == curT) setStroke(dpi(3), t.text)
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(6), dpi(4), dpi(6), dpi(4))
                addView(View(this@SettingsActivity).apply { background = d }, LinearLayout.LayoutParams(dpi(56), dpi(56)))
                addView(tv(th.name, 12f, t.sub).apply { gravity = Gravity.CENTER })
                setOnClickListener { keepY = scroll.scrollY; p.edit().putInt("theme", i).apply(); this@SettingsActivity.recreate(); overridePendingTransition(0, 0) }
            }
            themeRow.addView(item)
        }
        val curS = p.getInt("style", 0).coerceIn(0, STYLES.size - 1)
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        STYLES.forEachIndexed { i, st ->
            val chip = TextView(this).apply {
                text = st.name; setTextColor(t.text); typeface = face(true, st); textSize = 14f; gravity = Gravity.CENTER
                background = cardBg(st); setPadding(dpi(18), dpi(16), dpi(18), dpi(16))
            }
            val bar = View(this).apply { setBackgroundColor(t.ac1); visibility = if (i == curS) View.VISIBLE else View.INVISIBLE }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dpi(6), dpi(4), dpi(10), dpi(4))
                addView(chip, LinearLayout.LayoutParams(-2, -2))
                addView(bar, LinearLayout.LayoutParams(-1, dpi(3)).apply { topMargin = dpi(8) })
                setOnClickListener { keepY = scroll.scrollY; p.edit().putInt("style", i).apply(); this@SettingsActivity.recreate(); overridePendingTransition(0, 0) }
            }
            styleRow.addView(item)
        }
        val themeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(themeRow) }
        val styleScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(styleRow) }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(32)) }
        addBack(col)
        col.put(tv("Settings", 30f, t.text, true), 8)
        col.put(card(sec("YOUR CODE"), mine, row(copy, share), tv("Send this to your partner so they can paste it into their app.", 13f, t.sub)), 18)
        col.put(card(sec("PARTNER'S CODE"), theirs, tv("Paste the code your partner sent you.", 13f, t.sub)), 14)
        col.put(card(sec("ALARM MUSIC"), musicLbl, row(pick, reset)), 14)
        col.put(card(sec("BACKGROUND RUNNING"), tv("Lets wake-ups arrive while the app is closed.", 13f, t.sub), bg), 14)
        col.put(card(sec("THEME"), themeScroll), 14)
        col.put(card(sec("STYLE"), styleScroll), 14)
        scroll.addView(col)
        val y = keepY; keepY = 0
        if (y > 0) scroll.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() { scroll.viewTreeObserver.removeOnGlobalLayoutListener(this); scroll.scrollTo(0, y) }
        })
        val root = FrameLayout(this); root.addView(scroll); show(root)
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
