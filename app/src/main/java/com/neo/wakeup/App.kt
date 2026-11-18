package com.neo.wakeup

import android.animation.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.animation.DecelerateInterpolator
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
            log(c, 0, describe(o) + if (ok) "" else " (failed)")
            if (done != null) Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    fun send(c: Context, type: String, done: ((Boolean) -> Unit)? = null) = postJson(c, JSONObject().put("t", type), done)

    fun sendCustom(c: Context, title: String, body: String, img: Uri?, done: (Boolean) -> Unit) {
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done(false); return }
        Thread {
            var ok = false
            val o = JSONObject().put("t", "msg").put("title", title).put("body", body)
            if (img != null) o.put("photo", true)
            try {
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
            log(c, 0, describe(o) + if (ok) "" else " (failed)")
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

    @Synchronized fun log(c: Context, kind: Int, text: String) {
        try {
            val p = prefs(c)
            val old = JSONArray(p.getString("hist", "[]"))
            val n = JSONArray()
            n.put(JSONObject().put("k", kind).put("x", text).put("t", System.currentTimeMillis()))
            var i = 0
            while (i < old.length() && n.length() < 150) { n.put(old.get(i)); i++ }
            p.edit().putString("hist", n.toString()).apply()
        } catch (e: Exception) {}
        Handler(Looper.getMainLooper()).post { Hooks.onLog?.invoke() }
    }

    fun describe(o: JSONObject): String = when (o.optString("t")) {
        "wake" -> "Wake-up"
        "polite" -> "Are you awake?"
        "ack" -> "Confirmed they're up"
        "late" -> "Not up yet notice"
        "board" -> "Drawing synced"
        "msg" -> {
            val s = (o.optString("title") + " " + o.optString("body")).trim()
            "Message" + (if (s.isNotEmpty()) ": $s" else "") + (if (o.optString("img").isNotEmpty() || o.optBoolean("photo")) " (photo)" else "")
        }
        else -> o.optString("t")
    }

    fun appMode(c: Context): Boolean = prefs(c).getBoolean("stop_app", false) && !prefs(c).getString("stop_pkg", "").isNullOrEmpty()

    fun hasUsage(c: Context): Boolean = try {
        c.getSystemService(AppOpsManager::class.java).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    fun finishWake(c: Context) {
        Alarm.stop(c)
        prefs(c).edit().putInt("snz", 0).putBoolean("pend", false).apply()
        try { c.getSystemService(AlarmManager::class.java).cancel(snoozePi(c)) } catch (e: Exception) {}
        cancelAt(c, 5); log(c, 2, "You're up"); send(c, "ack")
    }

    fun sendBoard(c: Context, bmp: Bitmap, done: (Boolean) -> Unit) {
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done(false); return }
        Thread {
            var ok = false
            try {
                val bos = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val r = rawPost(t, bos.toByteArray(), true, "board.png")
                if (r.first) {
                    val o = JSONObject().put("t", "board").put("img", JSONObject(r.second).getJSONObject("attachment").getString("url"))
                    ok = rawPost(t, o.toString().toByteArray()).first
                }
            } catch (e: Exception) {}
            log(c, 0, "Drawing synced" + if (ok) "" else " (failed)")
            Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
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
    private var startAt = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (!ringing) return
            val a = ctx ?: return
            val pk = Core.prefs(a).getString("stop_pkg", "")!!
            var found = false
            try {
                val u = a.getSystemService(UsageStatsManager::class.java)
                val ev = u.queryEvents(startAt, System.currentTimeMillis()); val e = UsageEvents.Event()
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e)
                    if (e.packageName == pk && (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || e.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) found = true
                }
            } catch (e: Exception) {}
            if (found) Core.finishWake(a) else h.postDelayed(this, 1500)
        }
    }
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
        ringing = true; vol = 0.2f; startAt = System.currentTimeMillis()
        Core.log(a, 2, "Alarm rang")
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
        if (Core.appMode(a)) h.postDelayed(poll, 1500)
        else if (!p.getBoolean("forever", true)) h.postDelayed({ stop(a) }, 90000)
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
        Core.log(this, 1, Core.describe(m))
        if (t == "board") {
            Thread {
                try {
                    val bmp = URL(m.optString("img")).openStream().use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) {
                        Board.store(this, bmp)
                        Handler(Looper.getMainLooper()).post { Board.view?.reload(); Core.info(this, "Drawing updated", "Your partner saved a new drawing") }
                    }
                } catch (e: Exception) {}
            }.start()
            return
        }
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

// ---------- Hooks / Board ----------
object Hooks { var onLog: (() -> Unit)? = null }

val PAPER = Color.parseColor("#FAF8F2")

object Board {
    const val W = 1000; const val H = 1400
    var view: DrawView? = null
    fun file(c: Context) = java.io.File(c.filesDir, "board.png")
    fun load(c: Context): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.drawColor(PAPER)
        try {
            val f = file(c)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { cv.drawBitmap(Bitmap.createScaledBitmap(it, W, H, true), 0f, 0f, null) }
        } catch (e: Exception) {}
        return bmp
    }
    fun store(c: Context, bmp: Bitmap) {
        try { file(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) {}
    }
}

class DrawView(private val c: Context) : View(c) {
    private val bmp = Board.load(c)
    private val cv = Canvas(bmp)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val m = Matrix()
    private val inv = Matrix()
    private val undo = ArrayList<Bitmap>()
    var color = Color.parseColor("#111111")
    var eraser = false
    var size = 14f
    private var lx = 0f; private var ly = 0f; private var mx = 0f; private var my = 0f

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        m.reset(); m.postScale(s, s); m.postTranslate((width - bmp.width * s) / 2, (height - bmp.height * s) / 2); m.invert(inv)
        canvas.drawBitmap(bmp, m, null)
    }
    private fun push() {
        bmp.copy(Bitmap.Config.ARGB_8888, false)?.let { undo.add(it) }
        if (undo.size > 6) undo.removeAt(0).recycle()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        val pt = floatArrayOf(e.x, e.y); inv.mapPoints(pt)
        paint.color = if (eraser) PAPER else color; paint.strokeWidth = size
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { push(); lx = pt[0]; ly = pt[1]; mx = lx; my = ly; cv.drawPoint(lx, ly, paint) }
            MotionEvent.ACTION_MOVE -> {
                val midx = (pt[0] + lx) / 2; val midy = (pt[1] + ly) / 2
                path.reset(); path.moveTo(mx, my); path.quadTo(lx, ly, midx, midy); cv.drawPath(path, paint)
                mx = midx; my = midy; lx = pt[0]; ly = pt[1]
            }
            MotionEvent.ACTION_UP -> { path.reset(); path.moveTo(mx, my); path.lineTo(lx, ly); cv.drawPath(path, paint) }
        }
        invalidate(); return true
    }
    fun undo() { if (undo.isNotEmpty()) { val b = undo.removeAt(undo.size - 1); cv.drawBitmap(b, 0f, 0f, null); b.recycle(); invalidate() } }
    fun clear() { push(); cv.drawColor(PAPER); invalidate() }
    fun reload() { cv.drawBitmap(Board.load(c), 0f, 0f, null); invalidate() }
    fun saveLocal() = Board.store(c, bmp)
    fun snapshot(): Bitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
}

// ---------- Swipe pager ----------
class Pager(c: Context, private val onScroll: (Float) -> Unit, private val onSettle: (Int) -> Unit) : FrameLayout(c) {
    var pos = 0f
    private var downX = 0f; private var downY = 0f; private var lastX = 0f
    private var drag = false
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var vt: VelocityTracker? = null
    private var anim: ValueAnimator? = null

    private fun apply() {
        for (i in 0 until childCount) {
            val v = getChildAt(i); val d = i - pos
            v.translationX = d * width
            v.visibility = if (Math.abs(d) < 1f) View.VISIBLE else View.INVISIBLE
        }
        onScroll(pos)
    }
    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) { super.onLayout(ch, l, t, r, b); apply() }

    fun goTo(i: Int, animate: Boolean = true) {
        anim?.cancel()
        try { context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0) } catch (e: Exception) {}
        val target = i.coerceIn(0, childCount - 1)
        if (!animate) { pos = target.toFloat(); apply(); onSettle(target); return }
        anim = ValueAnimator.ofFloat(pos, target.toFloat()).apply {
            duration = 320; interpolator = DecelerateInterpolator()
            addUpdateListener { pos = it.animatedValue as Float; apply() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onSettle(target) } })
            start()
        }
    }
    private fun track(e: MotionEvent) { if (vt == null) vt = VelocityTracker.obtain(); vt!!.addMovement(e) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX = e.x; downY = e.y; lastX = e.x; drag = false; anim?.cancel(); vt?.recycle(); vt = null }
        track(e)
        if (e.actionMasked == MotionEvent.ACTION_MOVE && !drag &&
            Math.abs(e.x - downX) > slop && Math.abs(e.x - downX) > Math.abs(e.y - downY)) { drag = true; lastX = e.x }
        return drag
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX = e.x; downY = e.y; lastX = e.x; drag = false; anim?.cancel(); vt?.recycle(); vt = null }
        track(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!drag && Math.abs(e.x - downX) > slop && Math.abs(e.x - downX) > Math.abs(e.y - downY)) { drag = true; lastX = e.x }
                if (drag) { pos = (pos - (e.x - lastX) / width).coerceIn(0f, childCount - 1f); lastX = e.x; apply() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000); val vx = vt?.xVelocity ?: 0f
                vt?.recycle(); vt = null
                var tgt = Math.round(pos)
                if (vx < -800f) tgt = Math.ceil(pos.toDouble()).toInt() else if (vx > 800f) tgt = Math.floor(pos.toDouble()).toInt()
                drag = false; goTo(tgt)
            }
        }
        return true
    }
}

// ---------- UI base ----------
abstract class Base : Activity() {
    lateinit var t: Th
    private var ai = -1
    private var rootV: View? = null

    fun dp(x: Int): Float = x * resources.displayMetrics.density
    fun dpi(x: Int): Int = dp(x).toInt()
    private fun tIdx() = Core.prefs(this).getInt("theme", 0).coerceIn(0, THEMES.size - 1)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ai = tIdx(); t = THEMES[ai]
        window.statusBarColor = t.bg1; window.navigationBarColor = t.bg2
        if (!t.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    override fun onResume() { super.onResume(); if (tIdx() != ai) recreate() }

    fun show(v: FrameLayout) {
        v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.bg1, t.bg2))
        v.isFocusableInTouchMode = true; rootV = v; setContentView(v)
    }
    fun shape(c: Int, r: Float) = GradientDrawable().apply { setColor(c); cornerRadius = r }
    fun tv(x: String, size: Float, color: Int = t.text, bold: Boolean = false) = TextView(this).apply {
        text = x; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
    fun sec(x: String) = tv(x, 12f, t.sub, true).apply { letterSpacing = 0.15f }
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
    fun mini(label: String, primary: Boolean, click: () -> Unit) =
        btn(label, primary, click).apply { textSize = 14f; setPadding(dpi(10), dpi(8), dpi(10), dpi(8)) }
    fun sw(label: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = label; setTextColor(t.text); textSize = 15f; isChecked = on
        thumbTintList = ColorStateList.valueOf(t.ac1); trackTintList = ColorStateList.valueOf(t.sub)
        setOnCheckedChangeListener { _, v -> f(v) }
    }
    fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setTextColor(t.text); setHintTextColor(t.sub); textSize = 15f; isSingleLine = true
        background = shape(if (t.dark) 0x22FFFFFF else 0x14000000, dp(14)); setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
    }
    fun LinearLayout.put(v: View, top: Int = 12, h: Int = -2) {
        addView(v, LinearLayout.LayoutParams(-1, h).apply { topMargin = dpi(top) })
    }
    fun card(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(t.card, dp(24))
        setPadding(dpi(18), dpi(16), dpi(18), dpi(16))
        v.forEachIndexed { i, x -> put(x, if (i == 0) 0 else 10) }
    }
    fun row(a: View, b: View, w1: Float = 1f, w2: Float = 1f) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, -2, w1).apply { marginEnd = dpi(6) })
        addView(b, LinearLayout.LayoutParams(0, -2, w2).apply { marginStart = dpi(6) })
    }
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
    private val h = Handler(Looper.getMainLooper())
    private val chk = object : Runnable { override fun run() { if (!Alarm.ringing) finish() else h.postDelayed(this, 500) } }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val p = Core.prefs(this)
        val left = 2 - p.getInt("snz", 0)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(28), dpi(28), dpi(28), dpi(28)) }
        col.put(tv("WAKE UP", 18f, t.sub, true).apply { letterSpacing = 0.3f; gravity = Gravity.CENTER }, 0)
        col.put(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 84f; setTextColor(t.text)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER
        }, 8)
        if (Core.appMode(this)) {
            val nm = p.getString("stop_name", "the app")!!
            col.put(tv("Open $nm to stop the alarm", 18f, t.text, true).apply { gravity = Gravity.CENTER }, 40)
            col.put(btn("Open $nm", true) { openApp() }.apply { textSize = 20f }, 24, dpi(104))
        } else {
            col.put(btn("I'M UP", true) { up() }.apply { textSize = 20f }, 48, dpi(104))
            if (left > 0) col.put(btn("Snooze 5 min  ($left left)", false) { snooze() }.apply { textSize = 20f }, 14, dpi(104))
        }
        val root = FrameLayout(this); root.addView(col, FrameLayout.LayoutParams(-1, -1)); show(root)
        h.postDelayed(chk, 1500)
    }
    private fun openApp() {
        val pk = Core.prefs(this).getString("stop_pkg", "")!!
        try { packageManager.getLaunchIntentForPackage(pk)?.let { startActivity(it) } } catch (e: Exception) {}
        if (!Core.hasUsage(this)) { Core.finishWake(this); finish() }
    }
    private fun up() { Core.finishWake(this); finish() }
    private fun snooze() {
        Alarm.stop(this)
        val p = Core.prefs(this)
        val at = System.currentTimeMillis() + 5 * 60 * 1000L
        p.edit().putInt("snz", p.getInt("snz", 0) + 1).putLong("snzu", at + 10 * 60 * 1000L).apply()
        Core.log(this, 2, "You snoozed")
        try {
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, open), snoozePi(this))
        } catch (e: Exception) {}
        finish()
    }
}

// ---------- Main: 5 swipeable tabs ----------
class MainActivity : Base() {
    companion object { var keepPage = 2; var keepY = 0; var keepX = 0 }
    private lateinit var pager: Pager
    private lateinit var draw: DrawView
    private lateinit var grace: EditText
    private lateinit var histBox: LinearLayout
    private lateinit var mine: EditText
    private lateinit var theirs: EditText
    private lateinit var musicLbl: TextView
    private lateinit var stopLbl: TextView
    private lateinit var tField: EditText
    private lateinit var bField: EditText
    private lateinit var preview: ImageView
    private lateinit var tplBox: LinearLayout
    private lateinit var nStatus: TextView
    private var img: Uri? = null
    private val argb = ArgbEvaluator()

    private fun saveGrace() { Core.prefs(this).edit().putInt("grace", grace.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 15).apply() }
    private fun saveCodes() {
        Core.prefs(this).edit().putString("mine", mine.text.toString().trim()).putString("theirs", theirs.text.toString().trim()).apply()
    }
    override fun onPause() { saveGrace(); saveCodes(); draw.saveLocal(); super.onPause() }
    override fun onDestroy() { Hooks.onLog = null; Board.view = null; super.onDestroy() }

    private fun updateTabs(tabs: List<ImageView>, pos: Float) {
        tabs.forEachIndexed { i, v ->
            val k = 1f - Math.min(1f, Math.abs(i - pos))
            val sc = 1f + 0.6f * k
            v.scaleX = sc; v.scaleY = sc; v.translationY = -dpi(7) * k
            v.setColorFilter(argb.evaluate(k, t.sub, t.ac1) as Int)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Core.channels(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val p = Core.prefs(this)
        if (p.getString("mine", "").isNullOrEmpty()) {
            val c = "abcdefghijkmnpqrstuvwxyz23456789"
            p.edit().putString("mine", "wake-" + (1..12).map { c.random() }.joinToString("")).apply()
        }
        val pages = listOf(drawPage(), notifyPage(), wakePage(p), histPage(), settingsPage(p))
        Hooks.onLog = { renderHist() }

        val tabs = ArrayList<ImageView>()
        pager = Pager(this, { pos -> updateTabs(tabs, pos) }, { i -> if (i == 3) renderHist() })
        pager.pos = keepPage.toFloat()
        pages.forEach { pager.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = shape(t.card, dp(32)); clipChildren = false; clipToPadding = false
        }
        val icons = intArrayOf(R.drawable.ic_brush, R.drawable.ic_bell, R.drawable.ic_alarm, R.drawable.ic_history, R.drawable.ic_settings)
        icons.forEachIndexed { i, r ->
            val v = ImageView(this).apply { setImageResource(r); scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(dpi(20), dpi(20), dpi(20), dpi(20)); setOnClickListener { pager.goTo(i) } }
            tabs.add(v); bar.addView(v, LinearLayout.LayoutParams(0, dpi(68), 1f))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(14), dpi(6), dpi(14), dpi(14)) })
        val root = FrameLayout(this); root.clipChildren = false
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        show(root)
        keepPage = 2
        startForegroundService(Intent(this, ListenService::class.java))
    }

    // ----- tab 0: drawing pad -----
    private fun drawPage(): View {
        draw = DrawView(this); Board.view = draw
        val frame = FrameLayout(this).apply { background = shape(PAPER, dp(20)); clipToOutline = true; addView(draw, FrameLayout.LayoutParams(-1, -1)) }
        val status = tv("", 13f, t.sub).apply { gravity = Gravity.CENTER }
        val colors = listOf("#111111", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA")
        val dots = ArrayList<View>()
        val eraseBtn = mini("Eraser", false) {}
        fun refresh() {
            dots.forEachIndexed { i, v ->
                val cc = Color.parseColor(colors[i]); val sel = !draw.eraser && draw.color == cc
                v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cc); setStroke(dpi(if (sel) 3 else 1), if (sel) t.ac1 else t.sub) }
            }
            eraseBtn.text = if (draw.eraser) "Eraser on" else "Eraser"
        }
        val dotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEach { cs ->
            val v = View(this); dots.add(v)
            v.setOnClickListener { draw.color = Color.parseColor(cs); draw.eraser = false; refresh() }
            dotRow.addView(v, LinearLayout.LayoutParams(0, dpi(34), 1f).apply { setMargins(dpi(3), 0, dpi(3), 0) })
        }
        eraseBtn.setOnClickListener { draw.eraser = !draw.eraser; refresh() }
        refresh()
        val undoBtn = mini("Undo", false) { draw.undo() }
        val clearBtn = mini("Clear", false) { draw.clear() }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(eraseBtn, undoBtn, clearBtn).forEachIndexed { i, x -> tools.addView(x, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dpi(if (i == 0) 0 else 4), 0, dpi(if (i == 2) 0 else 4), 0) }) }
        val seek = SeekBar(this).apply {
            max = 56; progress = 10
            progressTintList = ColorStateList.valueOf(t.ac1); thumbTintList = ColorStateList.valueOf(t.ac1)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) { draw.size = 4f + v }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val save = btn("Save & sync", true) {
            draw.saveLocal()
            if (Core.prefs(this).getString("theirs", "").isNullOrBlank()) status.text = "Saved here. Add your partner's code in Settings to sync."
            else { status.text = "Syncing..."; Core.sendBoard(this, draw.snapshot()) { ok -> status.text = if (ok) "Synced" else "Couldn't sync. Check your connection" } }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(8)) }
        col.put(dotRow, 0); col.put(tools, 10); col.put(seek, 4)
        col.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dpi(6) })
        col.put(status, 6); col.put(save, 6)
        return col
    }

    // ----- tab 1: custom notification -----
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
    private fun notifyPage(): View {
        nStatus = tv("", 14f, t.sub).apply { gravity = Gravity.CENTER }
        tField = field("Title", "").apply { id = 1001 }
        bField = field("Message", "").apply {
            id = 1002; isSingleLine = false; minLines = 3; gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        val add = btn("Add photo", false) { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 8) }
        val rem = btn("Remove photo", false) { img = null; preview.visibility = View.GONE }
        val send = btn("Send", true) {
            val ti = tField.text.toString().trim(); val bo = bField.text.toString().trim()
            if (Core.prefs(this).getString("theirs", "").isNullOrBlank()) nStatus.text = "Add your partner's code in Settings"
            else if (ti.isEmpty() && bo.isEmpty() && img == null) nStatus.text = "Write something or add a photo first"
            else {
                nStatus.text = "Sending..."
                Core.sendCustom(this, ti, bo, img) { ok -> nStatus.text = if (ok) "Sent" else "Couldn't send. Check your connection" }
            }
        }
        val save = btn("Save template", false) {
            val ti = tField.text.toString().trim(); val bo = bField.text.toString().trim()
            if (ti.isEmpty() && bo.isEmpty()) nStatus.text = "Nothing to save yet"
            else {
                val a = tpls(); a.put(JSONObject().put("title", ti).put("body", bo))
                Core.prefs(this).edit().putString("tpl", a.toString()).apply(); renderTpl(); nStatus.text = "Template saved"
            }
        }
        tplBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(16)) }
        col.put(tv("Custom notification", 28f, t.text, true), 0)
        col.put(card(sec("TITLE"), tField, sec("MESSAGE"), bField, preview, row(add, rem), nStatus, send, save), 16)
        preview.layoutParams = (preview.layoutParams as LinearLayout.LayoutParams).apply { height = dpi(180) }
        col.put(card(sec("TEMPLATES"), tplBox), 14)
        col.put(tv("Photos are sent through ntfy.sh and stay available for about 3 hours. Templates save text only.", 12f, t.sub), 10)
        renderTpl()
        return ScrollView(this).apply { addView(col) }
    }

    // ----- tab 2: wake up alarm -----
    private fun wakePage(p: SharedPreferences): View {
        val status = tv("", 14f, t.sub).apply { gravity = Gravity.CENTER }
        fun go(type: String, ok: String) {
            if (p.getString("theirs", "").isNullOrBlank()) { status.text = "Add your partner's code in Settings"; return }
            status.text = "Sending..."
            Core.send(this, type) { good -> status.text = if (good) ok else "Couldn't send. Check your connection" }
        }
        val ringV = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((t.ac1 and 0x00FFFFFF) or 0x33000000) } }
        val big = Button(this).apply {
            text = "WAKE UP"; textSize = 26f; isAllCaps = false; stateListAnimator = null
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = 0.08f; setTextColor(t.btnText)
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.ac1, t.ac2)).apply { shape = GradientDrawable.OVAL }, null)
            setOnClickListener { go("wake", "Wake-up sent") }
        }
        val wrap = FrameLayout(this)
        wrap.addView(ringV, FrameLayout.LayoutParams(1, 1, Gravity.CENTER)); wrap.addView(big, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        wrap.addOnLayoutChangeListener { _, l, tp, r, bt, _, _, _, _ ->
            val m = minOf(r - l, bt - tp); val bs = (m * 0.78f).toInt()
            if (m > 0 && big.layoutParams.width != bs) {
                big.layoutParams = FrameLayout.LayoutParams(bs, bs, Gravity.CENTER); ringV.layoutParams = FrameLayout.LayoutParams(m, m, Gravity.CENTER)
            }
        }
        ObjectAnimator.ofPropertyValuesHolder(ringV, PropertyValuesHolder.ofFloat("scaleX", 0.92f, 1f), PropertyValuesHolder.ofFloat("scaleY", 0.92f, 1f)).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        grace = field("min", p.getInt("grace", 15).toString()).apply {
            id = 1005; inputType = InputType.TYPE_CLASS_NUMBER
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
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(24), dpi(40), dpi(24), dpi(8)) }
        col.addView(wrap, LinearLayout.LayoutParams(-1, 0, 1f))
        col.put(status, 4)
        col.put(btn("Are you awake?", false) { go("polite", "Sent") }, 14)
        col.put(card(
            sw("Alarm never stops", p.getBoolean("forever", true)) { v -> p.edit().putBoolean("forever", v).apply() },
            sw("Scheduled wake-up", p.getBoolean("sched_on", false)) { v -> p.edit().putBoolean("sched_on", v).apply(); Core.scheduleNext(this) },
            row(tb, grace, 3f, 1f),
            tv("Wake-up time, and minutes before notifying my partner", 12f, t.sub)), 16)
        return col
    }

    // ----- tab 3: history -----
    private fun histPage(): View {
        histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clear = btn("Clear history", false) { Core.prefs(this).edit().putString("hist", "[]").apply(); renderHist() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(8)) }
        col.put(tv("History", 30f, t.text, true), 0)
        col.addView(ScrollView(this).apply { addView(histBox) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dpi(14) })
        col.put(clear, 10)
        renderHist()
        return col
    }
    private fun renderHist() {
        histBox.removeAllViews()
        val a = try { JSONArray(Core.prefs(this).getString("hist", "[]")) } catch (e: Exception) { JSONArray() }
        if (a.length() == 0) { histBox.addView(tv("Nothing yet. Wake-ups, messages and drawings will show up here.", 14f, t.sub)); return }
        val f = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); val k = o.optInt("k")
            val who = when (k) { 0 -> "SENT"; 1 -> "RECEIVED"; else -> "YOU" }
            val col = when (k) { 0 -> t.ac1; 1 -> t.ac2; else -> t.sub }
            histBox.put(card(tv(who + "  ·  " + f.format(java.util.Date(o.optLong("t"))), 12f, col, true), tv(o.optString("x"), 15f)), if (i == 0) 0 else 8)
        }
    }

    // ----- tab 4: settings -----
    private fun stopText(p: SharedPreferences): String { val n = p.getString("stop_name", ""); return if (n.isNullOrEmpty()) "No app chosen" else "App: $n" }
    private fun pickApp() {
        val pm = packageManager
        val ri = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }.sortedBy { it.loadLabel(pm).toString().lowercase() }
        val names = ri.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose app").setItems(names) { _, i ->
            val pr = Core.prefs(this)
            pr.edit().putString("stop_pkg", ri[i].activityInfo.packageName).putString("stop_name", names[i]).apply()
            stopLbl.text = stopText(pr)
        }.show()
    }
    private fun settingsPage(p: SharedPreferences): View {
        val sScroll = ScrollView(this); val tScroll = HorizontalScrollView(this)
        mine = field("Your code", p.getString("mine", "")!!).apply { id = 1003 }
        theirs = field("Paste your partner's code", p.getString("theirs", "")!!).apply { id = 1004 }
        for (e in listOf(mine, theirs)) e.onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) saveCodes() }
        val copy = btn("Copy", false) {}
        copy.setOnClickListener { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", mine.text.toString())); flash(copy, "Copied") }
        val share = btn("Share", true) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "My WakeUp code: " + mine.text.toString()), null))
        }
        musicLbl = tv(if (p.getString("tone", "").isNullOrEmpty()) "Default alarm sound" else "Custom music selected", 14f, t.sub)
        val pick = btn("Choose music", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 7)
        }
        val reset = btn("Use default", false) { p.edit().remove("tone").apply(); musicLbl.text = "Default alarm sound" }
        stopLbl = tv(stopText(p), 13f, t.sub)
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
                addView(View(this@MainActivity).apply { background = d }, LinearLayout.LayoutParams(dpi(56), dpi(56)))
                addView(tv(th.name, 12f, t.sub).apply { gravity = Gravity.CENTER })
                setOnClickListener {
                    keepPage = 4; keepY = sScroll.scrollY; keepX = tScroll.scrollX
                    p.edit().putInt("theme", i).apply(); this@MainActivity.recreate(); overridePendingTransition(0, 0)
                }
            }
            themeRow.addView(item)
        }
        tScroll.isHorizontalScrollBarEnabled = false; tScroll.addView(themeRow)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(16)) }
        col.put(tv("Settings", 30f, t.text, true), 0)
        col.put(card(sec("YOUR CODE"), mine, row(copy, share), tv("Send this to your partner so they can paste it into their app.", 13f, t.sub)), 16)
        col.put(card(sec("PARTNER'S CODE"), theirs, tv("Paste the code your partner sent you.", 13f, t.sub)), 14)
        col.put(card(sec("ALARM MUSIC"), musicLbl, row(pick, reset)), 14)
        col.put(card(sec("STOP ALARM WITH AN APP"),
            sw("Alarm only stops when I open an app", p.getBoolean("stop_app", false)) { v -> p.edit().putBoolean("stop_app", v).apply() },
            stopLbl,
            row(btn("Choose app", true) { pickApp() }, btn("Usage access", false) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }),
            tv("Usage access lets the app notice you opened it. Without it, the alarm stops when you tap the Open button.", 12f, t.sub)), 14)
        col.put(card(sec("BACKGROUND RUNNING"), tv("Lets wake-ups arrive while the app is closed.", 13f, t.sub), bg), 14)
        col.put(card(sec("THEME"), tScroll), 14)
        sScroll.addView(col)
        val y = keepY; val x = keepX; keepY = 0; keepX = 0
        if (y > 0 || x > 0) sScroll.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (sScroll.height == 0) return
                sScroll.viewTreeObserver.removeOnGlobalLayoutListener(this); sScroll.scrollTo(0, y); tScroll.scrollTo(x, 0)
            }
        })
        return sScroll
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        val u = d?.data ?: return
        if (c != RESULT_OK) return
        if (r == 7) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            Core.prefs(this).edit().putString("tone", u.toString()).apply(); musicLbl.text = "Custom music selected"
        } else if (r == 8) { img = u; preview.setImageURI(u); preview.visibility = View.VISIBLE }
    }
}
