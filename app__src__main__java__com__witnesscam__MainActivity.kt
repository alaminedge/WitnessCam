package com.witnesscam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ───────────────────────── Activity ─────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { WitnessTheme { App() } }
    }
}

private val PERMS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasAll(ctx: Context) = PERMS.all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAll(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasAll(ctx) }

    if (!granted) {
        PermissionGate { launcher.launch(PERMS) }
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        bottomBar = { if (!recording) NavBar(tab) { tab = it } },
    ) { pad ->
        if (tab == 0) RecordScreen(pad.calculateBottomPadding()) { recording = it }
        else LibraryScreen(pad)
    }
}

// ───────────────────────── Shared UI ─────────────────────────

@Composable
fun PermissionGate(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("WitnessCam", style = MaterialTheme.typography.headlineLarge, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            "Camera and microphone record the event.\nLocation stamps where it happened.",
            style = MaterialTheme.typography.bodyMedium, color = InkDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAllow,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Allow access", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun NavBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).navigationBarsPadding()
            .height(64.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("Record", "Library").forEachIndexed { i, label ->
            val sel = tab == i
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (sel) PanelHi else Color.Transparent)
                    .clickable { onTab(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) Ink else InkDim)
            }
        }
    }
}

@Composable
fun Chip(dot: Color, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}

@Composable
fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val inner by animateDpAsState(if (recording) 30.dp else 64.dp, label = "inner")
    val corner by animateDpAsState(if (recording) 8.dp else 32.dp, label = "corner")
    Box(
        Modifier.size(88.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(Accent))
    }
}

// ───────────────────────── Record ─────────────────────────

class Fix(val lat: Double, val lon: Double, val acc: Float, val at: Long)
class Pt(val t: Int, val lat: Double?, val lon: Double?, val acc: Float?)

class LiveInfo {
    @Volatile var fix: Fix? = null
    @Volatile var rotation = 0
}

private fun fresh(f: Fix?, now: Long = SystemClock.elapsedRealtime()) =
    f?.takeIf { now - it.at < 5000 }

@SuppressLint("MissingPermission")
@Composable
fun RecordScreen(bottomInset: Dp, onRecording: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    val live = remember { LiveInfo() }
    val track = remember { mutableListOf<Pt>() }
    val previewView = remember { PreviewView(ctx).apply { keepScreenOn = true } }
    val overlayThread = remember { HandlerThread("wc-overlay").apply { start() } }

    var fix by remember { mutableStateOf<Fix?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var vc by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var active by remember { mutableStateOf<Recording?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var startMs by remember { mutableLongStateOf(0L) }
    val activeNow = rememberUpdatedState(active)

    fun sample(t: Int): Pt {
        val f = fresh(live.fix)
        return Pt(t, f?.lat, f?.lon, f?.acc)
    }

    // GPS updates (1 Hz)
    DisposableEffect(Unit) {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let {
                    val f = Fix(it.latitude, it.longitude, it.accuracy, SystemClock.elapsedRealtime())
                    live.fix = f
                    fix = f
                }
            }
        }
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L).build(),
            cb, Looper.getMainLooper(),
        )
        onDispose {
            client.removeLocationUpdates(cb)
            overlayThread.quitSafely()
        }
    }

    // 1-second ticker: freshness, timer, GPS track
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
            if (activeNow.value != null) {
                seconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                track += sample(seconds)
            }
        }
    }

    // Camera + overlay
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(ctx)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD)))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        val effect = OverlayEffect(
            CameraEffect.VIDEO_CAPTURE, 0, Handler(overlayThread.looper)
        ) { Log.e("WitnessCam", "overlay error", it) }
        effect.setOnDrawListener { frame -> stamp(frame, live); true }

        provider.unbindAll()
        val cam = provider.bindToLifecycle(
            owner, CameraSelector.DEFAULT_BACK_CAMERA,
            UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture).addEffect(effect).build(),
        )
        camInfo = cam.cameraInfo
        vc = capture
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val v = vc ?: return
        val name = "WitnessCam_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WitnessCam")
        }
        val opts = MediaStoreOutputOptions.Builder(
            ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        // Video orientation is fixed at the moment recording starts, like a real body cam.
        val rot = previewView.display?.rotation ?: Surface.ROTATION_0
        v.targetRotation = rot
        live.rotation = camInfo?.getSensorRotationDegrees(rot) ?: 0

        track.clear()
        startMs = System.currentTimeMillis()
        seconds = 0
        track += sample(0)

        active = v.output.prepareRecording(ctx, opts).withAudioEnabled()
            .start(ContextCompat.getMainExecutor(ctx)) { ev ->
                if (ev is VideoRecordEvent.Finalize) {
                    active = null
                    onRecording(false)
                    if (ev.hasError()) {
                        Toast.makeText(ctx, "Recording failed", Toast.LENGTH_SHORT).show()
                    } else {
                        val uri = ev.outputResults.outputUri
                        val pts = track.toList()
                        val s = startMs
                        val e = System.currentTimeMillis()
                        CoroutineScope(Dispatchers.IO).launch {
                            val ok = runCatching { Evidence.seal(ctx, uri, name, s, e, pts) }.isSuccess
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx, if (ok) "Saved and sealed" else "Saved, but sealing failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        onRecording(true)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val f = fresh(fix, now)
            when {
                f == null -> Chip(Warn, "Searching GPS")
                f.acc <= 20f -> Chip(Ok, "GPS ±%.0f m".format(Locale.US, f.acc))
                else -> Chip(Warn, "GPS ±%.0f m".format(Locale.US, f.acc))
            }
            if (active != null) Chip(Accent, "%02d:%02d".format(seconds / 60, seconds % 60))
        }

        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 28.dp)
        ) {
            RecordButton(recording = active != null) {
                val a = active
                if (a != null) a.stop() else start()
            }
        }
    }
}

// Draws the live stamp into every video frame: top-right, body-cam style.
private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
private val deviceTag = "WITNESSCAM " + Build.MODEL.uppercase(Locale.US)
private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textAlign = Paint.Align.RIGHT
}
private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }

private fun stamp(frame: Frame, live: LiveInfo) {
    val c = frame.overlayCanvas
    c.drawColor(0, PorterDuff.Mode.CLEAR)

    val w = frame.size.width.toFloat()
    val h = frame.size.height.toFloat()
    val rot = live.rotation
    val swap = rot == 90 || rot == 270
    val ow = if (swap) h else w   // output width after rotation

    c.save()
    when (rot) {
        90 -> { c.translate(0f, h); c.rotate(-90f) }
        180 -> { c.translate(w, h); c.rotate(180f) }
        270 -> { c.translate(w, 0f); c.rotate(90f) }
    }

    val s = ow / 1080f
    val f = fresh(live.fix)
    val lines = listOf(
        timeFmt.format(Date()),
        if (f != null) String.format(Locale.US, "%.6f, %.6f  ±%.0f m", f.lat, f.lon, f.acc) else "NO GPS FIX",
        deviceTag,
    )

    textPaint.textSize = 28f * s
    textPaint.setShadowLayer(3f * s, 0f, 1.5f * s, 0xCC000000.toInt())
    val lineH = 38f * s
    val pad = 14f * s
    val margin = 28f * s
    val boxW = lines.maxOf { textPaint.measureText(it) } + pad * 2
    val boxH = lines.size * lineH + pad * 2 - 8f * s

    val right = ow - margin
    val top = margin
    c.drawRoundRect(RectF(right - boxW, top, right, top + boxH), 10f * s, 10f * s, boxPaint)
    lines.forEachIndexed { i, line ->
        c.drawText(line, right - pad, top + pad + (i + 1) * lineH - 12f * s, textPaint)
    }
    c.restore()
}

// ───────────────────────── Library ─────────────────────────

@Composable
fun LibraryScreen(pad: PaddingValues) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<Entry>?>(null) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { Evidence.all(ctx) } }

    Column(Modifier.fillMaxSize().background(Bg).padding(pad)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                items?.let { "${it.size} recording${if (it.size == 1) "" else "s"} · checked on open" } ?: " ",
                style = MaterialTheme.typography.bodyMedium, color = InkDim,
            )
        }
        val list = items
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recordings yet", style = MaterialTheme.typography.bodyMedium, color = InkDim)
            }
        } else if (list != null) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { items(list) { EntryRow(it) } }
        }
    }
}

@Composable
fun EntryRow(e: Entry) {
    val ctx = LocalContext.current
    val (label, color) = when (e.status) {
        Status.VERIFIED -> "Verified" to Ok
        Status.MODIFIED -> "Modified" to Accent
        Status.MISSING -> "Missing" to Warn
    }
    val date = remember(e) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(e.startedMs))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .clickable {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(e.uri, "video/mp4")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 104.dp, height = 78.dp).clip(RoundedCornerShape(12.dp)).background(PanelHi)) {
            e.thumb?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(
                "%d:%02d".format(e.durationS / 60, e.durationS % 60) +
                    if (e.lat != null) "  ·  %.4f, %.4f".format(Locale.US, e.lat, e.lon) else "  ·  no GPS",
                style = MaterialTheme.typography.bodyMedium, color = InkDim,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

// ───────────────────────── Evidence (hash + signature) ─────────────────────────

enum class Status { VERIFIED, MODIFIED, MISSING }

class Entry(
    val uri: Uri, val startedMs: Long, val durationS: Int,
    val lat: Double?, val lon: Double?, val status: Status, val thumb: Bitmap?,
)

object Evidence {
    private const val ALIAS = "witnesscam_signing"
    private const val ALGO = "SHA256withECDSA"

    private fun dir(ctx: Context) = File(ctx.filesDir, "evidence").apply { mkdirs() }

    private fun keys(): Pair<PrivateKey, PublicKey> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
            }.generateKeyPair()
        }
        return (ks.getKey(ALIAS, null) as PrivateKey) to ks.getCertificate(ALIAS).publicKey
    }

    fun sha256(ctx: Context, uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(uri)!!.use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun iso(ms: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    fun seal(ctx: Context, uri: Uri, name: String, startMs: Long, endMs: Long, track: List<Pt>) {
        val pts = JSONArray()
        track.forEach {
            pts.put(
                JSONObject().put("t", it.t).apply {
                    if (it.lat != null) put("lat", it.lat).put("lon", it.lon).put("acc_m", it.acc?.toDouble())
                    else put("fix", "no_fix")
                }
            )
        }
        val log = JSONObject()
            .put("name", name)
            .put("uri", uri.toString())
            .put("start_ms", startMs)
            .put("start_utc", iso(startMs))
            .put("end_utc", iso(endMs))
            .put("duration_s", ((endMs - startMs) / 1000).toInt())
            .put("sha256", sha256(ctx, uri))
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("app", "WitnessCam 1.0")
            .put("gps_track", pts)
            .toString()

        val sig = Signature.getInstance(ALGO).run {
            initSign(keys().first); update(log.toByteArray()); sign()
        }
        File(dir(ctx), "$name.json").writeText(
            JSONObject().put("log", log).put("sig", Base64.encodeToString(sig, Base64.NO_WRAP)).toString(2)
        )
    }

    fun all(ctx: Context): List<Entry> =
        (dir(ctx).listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedByDescending { it.name }
            .mapNotNull { runCatching { check(ctx, it) }.getOrNull() }

    private fun check(ctx: Context, f: File): Entry {
        val root = JSONObject(f.readText())
        val logStr = root.getString("log")
        val log = JSONObject(logStr)

        val sigOk = Signature.getInstance(ALGO).run {
            initVerify(keys().second); update(logStr.toByteArray())
            verify(Base64.decode(root.getString("sig"), Base64.NO_WRAP))
        }
        val uri = Uri.parse(log.getString("uri"))
        val hash = runCatching { sha256(ctx, uri) }.getOrNull()
        val status = when {
            hash == null -> Status.MISSING
            sigOk && hash == log.getString("sha256") -> Status.VERIFIED
            else -> Status.MODIFIED
        }
        val first = log.getJSONArray("gps_track").let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.has("lat") }
        }
        val thumb = if (status == Status.MISSING) null else runCatching {
            ctx.contentResolver.loadThumbnail(uri, Size(320, 240), null)
        }.getOrNull()

        return Entry(
            uri, log.getLong("start_ms"), log.getInt("duration_s"),
            first?.getDouble("lat"), first?.getDouble("lon"), status, thumb,
        )
    }
}
