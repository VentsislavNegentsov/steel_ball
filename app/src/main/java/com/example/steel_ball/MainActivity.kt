package com.example.steel_ball

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.util.ArrayDeque
import kotlin.math.*
import kotlin.random.Random

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var primarySensor: Sensor? = null
    private var vibrator: Vibrator? = null
    private var arcadeMusicPlayer: ArcadeMusicPlayer? = null

    private var pitch by mutableFloatStateOf(0f)
    private var roll by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        primarySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (_: Exception) { null }

        arcadeMusicPlayer = ArcadeMusicPlayer().apply { start() }

        setContent {
            MaterialTheme {
                SteelBallGameScreen(
                    pitch = pitch,
                    roll = roll,
                    vibrator = vibrator,
                    arcadeMusicPlayer = arcadeMusicPlayer
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val alpha = 0.15f
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()

        val rawRoll = Math.toDegrees(atan2(x, sqrt(y * y + z * z))).toFloat()
        val rawPitch = Math.toDegrees(atan2(y, sqrt(x * x + z * z))).toFloat()

        pitch += alpha * (rawPitch - pitch)
        roll += alpha * (rawRoll - roll)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        primarySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        arcadeMusicPlayer?.stop()
    }
}

class ArcadeMusicPlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var thread: Thread? = null

    @Volatile
    var currentLevel: Int = 1

    fun start() {
        if (isPlaying) return
        isPlaying = true
        thread = Thread {
            val sampleRate = 22050
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            val rng = Random(42)
            var stepCounter = 0

            while (isPlaying) {
                val lvl = currentLevel
                val mode = (lvl - 1) % 10

                val (noteDurationSamples, waveValue) = when (mode) {
                    0 -> {
                        val dur = sampleRate / 8
                        val scale = listOf(523.25, 587.33, 659.25, 783.99, 880.00)
                        val freq = scale[stepCounter % scale.size]
                        val subBass = scale[(stepCounter / 2) % scale.size] / 2.0
                        Pair(dur) { t: Double, _: Int ->
                            val sq = if (sin(2.0 * Math.PI * freq * t) > 0.0) 0.5 else -0.5
                            val tri = asin(sin(2.0 * Math.PI * subBass * t)) / (Math.PI / 2.0)
                            (sq * 0.6 + tri * 0.4)
                        }
                    }
                    1 -> {
                        val dur = sampleRate / 10
                        val scale = listOf(440.00, 466.16, 523.25, 587.33, 659.25, 698.46)
                        val freq = scale[(stepCounter * 2) % scale.size]
                        val isSnare = stepCounter % 4 == 2
                        Pair(dur) { t: Double, _: Int ->
                            val saw = 2.0 * (freq * t - floor(freq * t + 0.5))
                            val noise = if (isSnare) (rng.nextDouble() * 2.0 - 1.0) * 0.5 else 0.0
                            (saw * 0.5 + noise * 0.5)
                        }
                    }
                    2 -> {
                        val dur = sampleRate / 14
                        val scale = listOf(587.33, 698.46, 880.00, 1046.50, 1174.66)
                        val freq = scale[(stepCounter * 3) % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            if (sin(2.0 * Math.PI * freq * t) > 0.2) 0.6 else -0.6
                        }
                    }
                    3 -> {
                        val dur = sampleRate / 6
                        val scale = listOf(220.00, 233.08, 277.18, 311.13, 349.23)
                        val freq = scale[stepCounter % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            val sub = sin(2.0 * Math.PI * (freq / 2.0) * t)
                            val growl = tan(sin(2.0 * Math.PI * freq * t)).coerceIn(-1.0, 1.0)
                            (sub * 0.6 + growl * 0.4)
                        }
                    }
                    4 -> {
                        val dur = sampleRate / 12
                        val freq = 300.0 + (stepCounter * 45.0) % 900.0
                        Pair(dur) { t: Double, i: Int ->
                            sin(2.0 * Math.PI * freq * t * (1.0 + i * 0.0001)) * 0.5
                        }
                    }
                    5 -> {
                        val dur = sampleRate / 7
                        val scale = listOf(329.63, 392.00, 440.00, 523.25)
                        val freq = scale[stepCounter % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            (sin(2.0 * Math.PI * freq * t) * 0.5 + sin(2.0 * Math.PI * (freq * 1.5) * t) * 0.3)
                        }
                    }
                    6 -> {
                        val dur = sampleRate / 16
                        val freq = 880.0
                        Pair(dur) { t: Double, _: Int ->
                            if ((t * freq) % 1.0 < 0.5) 0.7 else -0.7
                        }
                    }
                    7 -> {
                        val dur = sampleRate / 5
                        val scale = listOf(110.00, 123.47, 130.81, 146.83)
                        val freq = scale[stepCounter % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            val sq = if (sin(2.0 * Math.PI * freq * t) > 0.0) 0.7 else -0.7
                            sq * 0.6
                        }
                    }
                    8 -> {
                        val dur = sampleRate / 18
                        val scale = listOf(1046.50, 1174.66, 1318.51, 1396.91, 1567.98)
                        val freq = scale[stepCounter % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            if (sin(2.0 * Math.PI * freq * t) > 0.0) 0.5 else -0.5
                        }
                    }
                    else -> {
                        val dur = sampleRate / 9
                        val scale = listOf(164.81, 174.61, 196.00, 220.00)
                        val freq = scale[stepCounter % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            val saw1 = 2.0 * (freq * t - floor(freq * t + 0.5))
                            val saw2 = 2.0 * ((freq * 1.01) * t - floor((freq * 1.01) * t + 0.5))
                            ((saw1 + saw2) * 0.3)
                        }
                    }
                }

                stepCounter++
                val buffer = ShortArray(noteDurationSamples)

                for (i in 0 until noteDurationSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = sin((i.toDouble() / noteDurationSamples) * Math.PI)
                    val rawSample = waveValue(t, i) * envelope
                    buffer[i] = (rawSample * 3800.0).toInt().coerceIn(-32768, 32767).toShort()
                }

                try {
                    audioTrack?.write(buffer, 0, buffer.size)
                } catch (_: Exception) { break }
            }
        }
        thread?.start()
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}

data class ThemePalette(
    val name: String,
    val caseOuterBg: Color,
    val caseInnerBg: Color,
    val bezelBorderColor: Color,
    val fluidCenterColor: Color,
    val fluidEdgeColor: Color,
    val arcadeAccentColor: Color
)

val AppThemes = listOf(
    ThemePalette("Outrun 84", Color(0xFF140727), Color(0xFF220945), Color(0xFF6B117A), Color(0xFFFF0055), Color(0xFF990033), Color(0xFF00FFFF)),
    ThemePalette("Cyberpunk", Color(0xFF0B0F19), Color(0xFF131C2E), Color(0xFF1F3A60), Color(0xFFFEE715), Color(0xFFC7830A), Color(0xFFFF0055)),
    ThemePalette("Arcade Amber", Color(0xFF1A0D00), Color(0xFF2E1700), Color(0xFF663300), Color(0xFFFF8800), Color(0xFFB34700), Color(0xFFFFFF00)),
    ThemePalette("Laser Matrix", Color(0xFF021508), Color(0xFF072B12), Color(0xFF0F5927), Color(0xFF00FF66), Color(0xFF008F38), Color(0xFF00E5FF))
)

data class PlacementRect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    fun overlaps(other: PlacementRect, padding: Float = 0.015f): Boolean {
        return !(this.maxX + padding < other.minX ||
                this.minX - padding > other.maxX ||
                this.maxY + padding < other.minY ||
                this.minY - padding > other.maxY)
    }
}

data class Wall(
    val x: Float, val y: Float, val width: Float, val height: Float,
    val isMoving: Boolean = false, val moveAxis: Char = 'H', val moveRange: Float = 0.12f, val speed: Float = 0.8f
) {
    fun getPlacementRect(): PlacementRect {
        val sweepMinX = x - if (isMoving && moveAxis == 'H') moveRange else 0f
        val sweepMaxX = x + width + if (isMoving && moveAxis == 'H') moveRange else 0f
        val sweepMinY = y - if (isMoving && moveAxis == 'V') moveRange else 0f
        val sweepMaxY = y + height + if (isMoving && moveAxis == 'V') moveRange else 0f
        return PlacementRect(sweepMinX, sweepMinY, sweepMaxX, sweepMaxY)
    }
}

data class Hazard(
    val x: Float, val y: Float, val radius: Float,
    val isMoving: Boolean = false, val speed: Float = 0.8f, val pathRadius: Float = 0.12f, val seedOffset: Float = 0f
) {
    fun getPlacementRect(): PlacementRect {
        val rFrac = 0.04f
        val sweepRadius = if (isMoving) pathRadius + rFrac else rFrac
        return PlacementRect(x - sweepRadius, y - sweepRadius, x + sweepRadius, y + sweepRadius)
    }
}

data class BlackHole(val x: Float, val y: Float, val radius: Float = 22f, val strength: Float = 0.85f) {
    fun getPlacementRect(): PlacementRect {
        val frac = (radius / 300f).coerceAtLeast(0.035f)
        return PlacementRect(x - frac, y - frac, x + frac, y + frac)
    }
}

data class SpeedBooster(val x: Float, val y: Float, val radius: Float = 22f, val dirX: Float, val dirY: Float) {
    fun getPlacementRect(): PlacementRect = PlacementRect(x - 0.035f, y - 0.035f, x + 0.035f, y + 0.035f)
}

data class Portal(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val radius: Float = 20f) {
    fun getPlacementRect1(): PlacementRect = PlacementRect(x1 - 0.035f, y1 - 0.035f, x1 + 0.035f, y1 + 0.035f)
    fun getPlacementRect2(): PlacementRect = PlacementRect(x2 - 0.035f, y2 - 0.035f, x2 + 0.035f, y2 + 0.035f)
}

data class PinballBumper(val x: Float, val y: Float, val radius: Float) {
    fun getPlacementRect(): PlacementRect = PlacementRect(x - 0.035f, y - 0.035f, x + 0.035f, y + 0.035f)
}

data class IcePatch(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun getPlacementRect(): PlacementRect = PlacementRect(x, y, x + width, y + height)
}

data class LevelLayout(
    val walls: List<Wall>,
    val hazards: List<Hazard>,
    val blackHoles: List<BlackHole>,
    val boosters: List<SpeedBooster>,
    val portal: Portal?,
    val bumpers: List<PinballBumper>,
    val icePatches: List<IcePatch>,
    val goalX: Float,
    val goalY: Float
)

fun isLevelPassable(layout: LevelLayout): Boolean {
    val gridSize = 50
    val grid = Array(gridSize) { BooleanArray(gridSize) { true } }
    val ballMargin = 0.035f

    for (wall in layout.walls) {
        val rect = wall.getPlacementRect()
        val startGx = ((rect.minX - ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
        val endGx = ((rect.maxX + ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
        val startGy = ((rect.minY - ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
        val endGy = ((rect.maxY + ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)

        for (gx in startGx..endGx) {
            for (gy in startGy..endGy) { grid[gx][gy] = false }
        }
    }

    val startGx = (0.85f * gridSize).toInt().coerceIn(0, gridSize - 1)
    val startGy = (0.85f * gridSize).toInt().coerceIn(0, gridSize - 1)
    val goalGx = (layout.goalX * gridSize).toInt().coerceIn(0, gridSize - 1)
    val goalGy = (layout.goalY * gridSize).toInt().coerceIn(0, gridSize - 1)

    if (!grid[startGx][startGy] || !grid[goalGx][goalGy]) return false

    val queue = ArrayDeque<Pair<Int, Int>>()
    val visited = Array(gridSize) { BooleanArray(gridSize) }
    queue.add(Pair(startGx, startGy))
    visited[startGx][startGy] = true

    val dx = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    val dy = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    while (queue.isNotEmpty()) {
        val (cx, cy) = queue.poll() ?: break
        if (abs(cx - goalGx) <= 1 && abs(cy - goalGy) <= 1) return true

        for (i in 0..7) {
            val nx = cx + dx[i]
            val ny = cy + dy[i]
            if (nx in 0 until gridSize && ny in 0 until gridSize && grid[nx][ny] && !visited[nx][ny]) {
                visited[nx][ny] = true
                queue.add(Pair(nx, ny))
            }
        }
    }
    return false
}

fun buildSingleLevelCandidate(level: Int, seed: Long): LevelLayout {
    val rng = Random(seed)
    val occupied = mutableListOf<PlacementRect>()

    val startZone = PlacementRect(0.75f, 0.75f, 0.95f, 0.95f)
    occupied.add(startZone)

    val walls = mutableListOf<Wall>()
    val hazards = mutableListOf<Hazard>()
    val blackHoles = mutableListOf<BlackHole>()
    val boosters = mutableListOf<SpeedBooster>()
    var portal: Portal? = null
    val bumpers = mutableListOf<PinballBumper>()
    val icePatches = mutableListOf<IcePatch>()

    val speedMultiplier = 1f + (level - 1) * 0.25f
    val dynamicSpeed = (0.80f + rng.nextFloat() * 0.50f) * speedMultiplier

    fun canPlace(rect: PlacementRect, padding: Float = 0.01f): Boolean {
        return occupied.none { it.overlaps(rect, padding) }
    }

    var goalX = 0.15f
    var goalY = 0.15f
    for (attempt in 0..100) {
        val gx = 0.10f + rng.nextFloat() * 0.70f
        val gy = 0.10f + rng.nextFloat() * 0.70f
        val distanceFromStart = hypot(gx - 0.85f, gy - 0.85f)

        if (distanceFromStart >= 0.55f) {
            val goalRect = PlacementRect(gx - 0.05f, gy - 0.05f, gx + 0.05f, gy + 0.05f)
            if (canPlace(goalRect, 0.02f)) {
                goalX = gx
                goalY = gy
                occupied.add(goalRect)
                break
            }
        }
    }

    if (level >= 2 || rng.nextFloat() < 0.8f) {
        for (attempt in 0..100) {
            val p1x = 0.10f + rng.nextFloat() * 0.38f; val p1y = 0.10f + rng.nextFloat() * 0.38f
            val p2x = 0.48f + rng.nextFloat() * 0.40f; val p2y = 0.48f + rng.nextFloat() * 0.40f
            val candidate = Portal(p1x, p1y, p2x, p2y)
            if (canPlace(candidate.getPlacementRect1(), 0.015f) && canPlace(candidate.getPlacementRect2(), 0.015f)) {
                portal = candidate
                occupied.add(candidate.getPlacementRect1())
                occupied.add(candidate.getPlacementRect2())
                break
            }
        }
    }

    val totalWalls = (4 + level * 2).coerceAtMost(14)
    for (w in 0 until totalWalls) {
        val isMoving = rng.nextFloat() < (0.35f + level * 0.05f).coerceAtMost(0.70f)
        val isVert = rng.nextBoolean()
        val baseW = if (isVert) 0.030f else 0.20f + rng.nextFloat() * 0.18f
        val baseH = if (isVert) 0.20f + rng.nextFloat() * 0.18f else 0.030f
        val range = if (isMoving) 0.08f + rng.nextFloat() * 0.06f else 0f

        for (attempt in 0..40) {
            val wx = 0.06f + range + rng.nextFloat() * (0.88f - baseW - 2f * range)
            val wy = 0.06f + range + rng.nextFloat() * (0.88f - baseH - 2f * range)

            val candidate = Wall(wx, wy, baseW, baseH, isMoving, if (rng.nextBoolean()) 'H' else 'V', range, dynamicSpeed)
            val rect = candidate.getPlacementRect()

            if (canPlace(rect, 0.01f)) {
                walls.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    val lavaCount = (6 + level * 3).coerceAtMost(24)
    for (i in 0 until lavaCount) {
        for (attempt in 0..40) {
            val pathR = 0.06f + rng.nextFloat() * 0.08f
            val hx = 0.08f + pathR + rng.nextFloat() * (0.84f - 2f * pathR)
            val hy = 0.08f + pathR + rng.nextFloat() * (0.84f - 2f * pathR)

            val candidate = Hazard(
                x = hx, y = hy,
                radius = 18f + rng.nextFloat() * 14f,
                isMoving = true,
                speed = dynamicSpeed * 1.2f,
                pathRadius = pathR,
                seedOffset = rng.nextFloat() * 10f
            )
            val rect = candidate.getPlacementRect()
            if (canPlace(rect, 0.01f)) {
                hazards.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    val bhCount = (2 + level).coerceAtMost(7)
    val holeStrength = (0.65f + (level - 1) * 0.08f).coerceAtMost(1.1f)
    for (i in 0 until bhCount) {
        for (attempt in 0..40) {
            val bhx = 0.08f + rng.nextFloat() * 0.82f
            val bhy = 0.08f + rng.nextFloat() * 0.82f
            val candidate = BlackHole(bhx, bhy, radius = 20f + rng.nextFloat() * 18f, strength = holeStrength)
            val rect = candidate.getPlacementRect()
            if (canPlace(rect, 0.01f)) {
                blackHoles.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    val bumperCount = (2 + level).coerceAtMost(7)
    for (i in 0 until bumperCount) {
        for (attempt in 0..40) {
            val bx = 0.10f + rng.nextFloat() * 0.80f
            val by = 0.10f + rng.nextFloat() * 0.80f
            val candidate = PinballBumper(bx, by, radius = 18f + rng.nextFloat() * 10f)
            val rect = candidate.getPlacementRect()
            if (canPlace(rect, 0.01f)) {
                bumpers.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    val iceCount = (2 + level).coerceAtMost(6)
    for (i in 0 until iceCount) {
        for (attempt in 0..40) {
            val ix = 0.10f + rng.nextFloat() * 0.60f
            val iy = 0.10f + rng.nextFloat() * 0.60f
            val candidate = IcePatch(ix, iy, 0.20f, 0.20f)
            val rect = candidate.getPlacementRect()
            if (canPlace(rect, 0.01f)) {
                icePatches.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    val boosterCount = (1 + level).coerceAtMost(5)
    for (i in 0 until boosterCount) {
        for (attempt in 0..40) {
            val bx = 0.10f + rng.nextFloat() * 0.80f
            val by = 0.10f + rng.nextFloat() * 0.80f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val candidate = SpeedBooster(bx, by, dirX = cos(angle) * 3.0f, dirY = sin(angle) * 3.0f)
            val rect = candidate.getPlacementRect()
            if (canPlace(rect, 0.01f)) {
                boosters.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    return LevelLayout(walls, hazards, blackHoles, boosters, portal, bumpers, icePatches, goalX, goalY)
}

fun generateRandomizedAILevel(level: Int): LevelLayout {
    var seed = level * 77777L
    while (true) {
        val layout = buildSingleLevelCandidate(level, seed)
        if (isLevelPassable(layout)) return layout
        seed += 10007L
    }
}

@Composable
fun SteelBallGameScreen(
    pitch: Float, roll: Float, vibrator: Vibrator?, arcadeMusicPlayer: ArcadeMusicPlayer?
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("steel_ball_prefs", Context.MODE_PRIVATE) }

    var currentLevel by remember { mutableIntStateOf(prefs.getInt("current_level", 1).coerceAtLeast(1)) }
    var maxLevelReached by remember { mutableIntStateOf(prefs.getInt("max_level", currentLevel).coerceAtLeast(currentLevel)) }

    val theme = AppThemes[(currentLevel - 1) % AppThemes.size]

    var gameState by remember { mutableStateOf("PLAYING") }
    var ballX by remember { mutableFloatStateOf(-1f) }
    var ballY by remember { mutableFloatStateOf(-1f) }
    var velocityX by remember { mutableFloatStateOf(0f) }
    var velocityY by remember { mutableFloatStateOf(0f) }
    var portalCooldown by remember { mutableIntStateOf(0) }

    val congratulationTitles = remember { listOf(" UNBELIEVABLE ", " PERFECT ", " INSANE SKILL ", " VICTORY ", " MASTERED ") }
    var winTitle by remember { mutableStateOf(" PERFECT ") }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    val baseFriction = 0.95f
    val baseAcceleration = 0.40f

    val levelLayout = remember(currentLevel) { generateRandomizedAILevel(currentLevel) }
    var elapsedTime by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "lava")
    val lavaOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "lavaOffset"
    )

    LaunchedEffect(currentLevel) { arcadeMusicPlayer?.currentLevel = currentLevel }

    LaunchedEffect(gameState) {
        if (gameState == "WON") {
            countdownSeconds = 3
            delay(1000L); if (gameState == "WON") countdownSeconds = 2
            delay(1000L); if (gameState == "WON") countdownSeconds = 1
            delay(1000L)
            if (gameState == "WON") {
                currentLevel++
                if (currentLevel > maxLevelReached) {
                    maxLevelReached = currentLevel
                    prefs.edit().putInt("max_level", maxLevelReached).apply()
                }
                prefs.edit().putInt("current_level", currentLevel).apply()
                gameState = "PLAYING"
                ballX = -1f; ballY = -1f; velocityX = 0f; velocityY = 0f
            }
        } else if (gameState == "DEAD") {
            countdownSeconds = 3
            delay(1000L); if (gameState == "DEAD") countdownSeconds = 2
            delay(1000L); if (gameState == "DEAD") countdownSeconds = 1
            delay(1000L)
            if (gameState == "DEAD") {
                gameState = "PLAYING"
                ballX = -1f; ballY = -1f; velocityX = 0f; velocityY = 0f
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.caseOuterBg)
            .systemBarsPadding()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAtLevelOne = (currentLevel == 1)
            Button(
                onClick = {
                    if (isAtLevelOne) {
                        currentLevel = 1
                        maxLevelReached = 1
                        prefs.edit().putInt("current_level", 1).putInt("max_level", 1).apply()
                        gameState = "PLAYING"
                        ballX = -1f; ballY = -1f; velocityX = 0f; velocityY = 0f
                    }
                },
                enabled = isAtLevelOne,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAtLevelOne) Color(0xFF990000) else Color.DarkGray,
                    disabledContainerColor = Color(0xFF222222)
                ),
                border = BorderStroke(1.dp, if (isAtLevelOne) Color.Red else Color.Gray),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "RESET",
                    color = if (isAtLevelOne) Color.White else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        if (currentLevel > 1) {
                            currentLevel--
                            prefs.edit().putInt("current_level", currentLevel).apply()
                            gameState = "PLAYING"
                            ballX = -1f; ballY = -1f; velocityX = 0f; velocityY = 0f
                        }
                    },
                    enabled = currentLevel > 1,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                    border = BorderStroke(1.dp, theme.bezelBorderColor),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(text = "◀", color = if (currentLevel > 1) Color.White else Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.rotate(90f))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.rotate(90f)
                ) {
                    Text(text = "STAGE", color = theme.arcadeAccentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(text = "$currentLevel", color = theme.arcadeAccentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (currentLevel < maxLevelReached) {
                            currentLevel++
                            prefs.edit().putInt("current_level", currentLevel).apply()
                            gameState = "PLAYING"
                            ballX = -1f; ballY = -1f; velocityX = 0f; velocityY = 0f
                        }
                    },
                    enabled = currentLevel < maxLevelReached,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                    border = BorderStroke(1.dp, theme.bezelBorderColor),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(text = "▶", color = if (currentLevel < maxLevelReached) Color.White else Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.rotate(90f))
                }
            }

            Button(
                onClick = { activity?.finish() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                border = BorderStroke(1.dp, Color.Gray),
                modifier = Modifier.height(30.dp)
            ) {
                Text(text = "EXIT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(theme.caseInnerBg)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentLevel, levelLayout) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val refScale = min(width, height) / 1000f

                                for (bh in levelLayout.blackHoles) {
                                    val bhX = bh.x * width
                                    val bhY = bh.y * height
                                    val scaledBhRadius = bh.radius * refScale
                                    val hitArea = scaledBhRadius * 2.5f

                                    if (hypot(tapOffset.x - bhX, tapOffset.y - bhY) <= hitArea) {
                                        ballX = bhX
                                        ballY = bhY
                                        val randomAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
                                        val impulseSpeed = 18f * refScale
                                        velocityX = cos(randomAngle) * impulseSpeed
                                        velocityY = sin(randomAngle) * impulseSpeed

                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                                            } else { @Suppress("DEPRECATION") vibrator?.vibrate(40) }
                                        } catch (_: Exception) {}
                                        break
                                    }
                                }
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@Canvas

                // Reference Scale Rework for Resolution Independence (Target: 1000 Virtual Reference Base)
                val refScale = min(width, height) / 1000f

                val ballRadius = 22f * refScale
                val goalRadius = 32f * refScale
                val startRadius = 34f * refScale

                if (ballX < 0f || ballY < 0f) {
                    ballX = width * 0.85f
                    ballY = height * 0.85f
                    velocityX = 0f
                    velocityY = 0f
                }

                val startPos = Offset(width * 0.85f, height * 0.85f)
                val goalPos = Offset(levelLayout.goalX * width, levelLayout.goalY * height)

                var friction = baseFriction
                for (ice in levelLayout.icePatches) {
                    val ix = ice.x * width; val iy = ice.y * height
                    val iw = ice.width * width; val ih = ice.height * height
                    if (ballX in ix..(ix + iw) && ballY in iy..(iy + ih)) {
                        friction = 0.996f
                    }
                }

                if (gameState == "PLAYING") {
                    if (portalCooldown > 0) portalCooldown--

                    val subSteps = 6
                    val subAx = (-roll / 30f).coerceIn(-1f, 1f) * (baseAcceleration * refScale) / subSteps
                    val subAy = (pitch / 30f).coerceIn(-1f, 1f) * (baseAcceleration * refScale) / subSteps

                    for (step in 0 until subSteps) {
                        velocityX = (velocityX + subAx) * (friction.pow(1f / subSteps))
                        velocityY = (velocityY + subAy) * (friction.pow(1f / subSteps))

                        for (bh in levelLayout.blackHoles) {
                            val bhX = bh.x * width
                            val bhY = bh.y * height
                            val scaledBhRadius = bh.radius * refScale
                            val dist = hypot(ballX - bhX, ballY - bhY)

                            if (dist < scaledBhRadius * 3.8f && dist > 4f * refScale) {
                                val distVirtual = dist / refScale
                                val pullVirtual = (bh.strength / 5f) / (distVirtual * 0.03f)
                                val pull = pullVirtual * refScale
                                velocityX += ((bhX - ballX) / dist) * pull
                                velocityY += ((bhY - ballY) / dist) * pull
                            }
                        }

                        var nextX = ballX + velocityX / subSteps
                        var nextY = ballY + velocityY / subSteps

                        if (nextX - ballRadius < 0f || nextX + ballRadius > width ||
                            nextY - ballRadius < 0f || nextY + ballRadius > height) {
                            gameState = "DEAD"
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                            break
                        }

                        for (wall in levelLayout.walls) {
                            val currentWx = if (wall.isMoving && wall.moveAxis == 'H') (wall.x + sin(elapsedTime * wall.speed) * wall.moveRange).coerceIn(0.04f, 1f - wall.width - 0.04f) else wall.x
                            val currentWy = if (wall.isMoving && wall.moveAxis == 'V') (wall.y + cos(elapsedTime * wall.speed) * wall.moveRange).coerceIn(0.04f, 1f - wall.height - 0.04f) else wall.y
                            val wx = currentWx * width; val wy = currentWy * height
                            val ww = wall.width * width; val wh = wall.height * height

                            if (nextX + ballRadius > wx && nextX - ballRadius < wx + ww && nextY + ballRadius > wy && nextY - ballRadius < wy + wh) {
                                if (wall.isMoving) {
                                    gameState = "DEAD"
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                                    } catch (_: Exception) {}
                                    break
                                } else {
                                    val overlapX = min(nextX + ballRadius - wx, wx + ww - (nextX - ballRadius))
                                    val overlapY = min(nextY + ballRadius - wy, wy + wh - (nextY - ballRadius))
                                    if (overlapX < overlapY) {
                                        nextX = if (nextX < wx + ww / 2) wx - ballRadius else wx + ww + ballRadius
                                        velocityX = -velocityX * 0.2f
                                    } else {
                                        nextY = if (nextY < wy + wh / 2) wy - ballRadius else wy + wh + ballRadius
                                        velocityY = -velocityY * 0.2f
                                    }
                                }
                            }
                        }

                        if (gameState != "PLAYING") break

                        for (booster in levelLayout.boosters) {
                            val bx = booster.x * width
                            val by = booster.y * height
                            val scaledBoosterRadius = booster.radius * refScale
                            if (hypot(nextX - bx, nextY - by) < ballRadius + scaledBoosterRadius) {
                                velocityX += booster.dirX * 3.8f * refScale
                                velocityY += booster.dirY * 3.8f * refScale
                            }
                        }

                        levelLayout.portal?.let { p ->
                            if (portalCooldown == 0) {
                                val p1x = p.x1 * width; val p1y = p.y1 * height
                                val p2x = p.x2 * width; val p2y = p.y2 * height
                                val scaledPortalRadius = p.radius * refScale

                                if (hypot(nextX - p1x, nextY - p1y) < ballRadius + scaledPortalRadius) {
                                    nextX = p2x; nextY = p2y; portalCooldown = 30
                                } else if (hypot(nextX - p2x, nextY - p2y) < ballRadius + scaledPortalRadius) {
                                    nextX = p1x; nextY = p1y; portalCooldown = 30
                                }
                            }
                        }

                        for (bumper in levelLayout.bumpers) {
                            val bx = bumper.x * width; val by = bumper.y * height
                            val scaledBumperRadius = bumper.radius * refScale
                            val dist = hypot(nextX - bx, nextY - by)
                            if (dist < ballRadius + scaledBumperRadius) {
                                val nx = (nextX - bx) / dist; val ny = (nextY - by) / dist
                                nextX = bx + nx * (ballRadius + scaledBumperRadius)
                                nextY = by + ny * (ballRadius + scaledBumperRadius)
                                val dot = velocityX * nx + velocityY * ny
                                velocityX = (velocityX - 2 * dot * nx) * 1.35f
                                velocityY = (velocityY - 2 * dot * ny) * 1.35f
                            }
                        }

                        ballX = nextX; ballY = nextY
                    }

                    for (hazard in levelLayout.hazards) {
                        val hx = (if (hazard.isMoving) (hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.x) * width
                        val hy = (if (hazard.isMoving) (hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.y) * height
                        val scaledHazardRadius = hazard.radius * refScale
                        if (hypot(ballX - hx, ballY - hy) < ballRadius + scaledHazardRadius * 0.70f) {
                            gameState = "DEAD"
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                        }
                    }

                    if (hypot(ballX - goalPos.x, ballY - goalPos.y) < goalRadius * 0.75f) {
                        winTitle = congratulationTitles.random()
                        gameState = "WON"
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else { @Suppress("DEPRECATION") vibrator?.vibrate(100) }
                        } catch (_: Exception) {}
                    }
                }

                // Render Animated Boundary Frame
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Red, Color(0xFFFF4500), Color.Yellow, Color(0xFFFF4500), Color.Red),
                        start = Offset(0f, lavaOffset),
                        end = Offset(width, height + lavaOffset)
                    ),
                    topLeft = Offset.Zero,
                    size = Size(width, height),
                    style = Stroke(width = 10f * refScale)
                )

                // Render Ice Patches
                for (ice in levelLayout.icePatches) {
                    val ix = ice.x * width; val iy = ice.y * height
                    val iw = ice.width * width; val ih = ice.height * height
                    val cr = 12f * refScale
                    drawRoundRect(
                        color = Color(0x8888E5FF),
                        topLeft = Offset(ix, iy), size = Size(iw, ih),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.8f),
                        topLeft = Offset(ix, iy), size = Size(iw, ih),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 2f * refScale)
                    )
                }

                // Render Speed Boosters
                for (booster in levelLayout.boosters) {
                    val bx = booster.x * width; val by = booster.y * height
                    val scaledRadius = booster.radius * refScale
                    drawCircle(color = Color(0xFF00FF66).copy(alpha = 0.35f), radius = scaledRadius * 1.3f, center = Offset(bx, by))
                    drawCircle(color = Color(0xFF00FF66), radius = scaledRadius, center = Offset(bx, by), style = Stroke(width = 3.5f * refScale))
                }

                // Render Black Holes
                for (bh in levelLayout.blackHoles) {
                    val bhX = bh.x * width; val bhY = bh.y * height
                    val bhCenter = Offset(bhX, bhY)
                    val scaledRadius = bh.radius * refScale

                    drawCircle(color = Color(0xFF3E2723).copy(alpha = 0.45f), radius = scaledRadius * 1.25f, center = bhCenter)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF8D6E63), Color(0xFF3E2723), Color(0xFF120804)),
                            center = Offset(bhX - scaledRadius * 0.25f, bhY - scaledRadius * 0.25f),
                            radius = scaledRadius * 1.2f
                        ),
                        radius = scaledRadius, center = bhCenter
                    )
                    drawCircle(color = Color(0xFF5D4037), radius = scaledRadius, center = bhCenter, style = Stroke(width = 2.5f * refScale))
                }

                // Render Wormhole Portals
                levelLayout.portal?.let { p ->
                    val p1 = Offset(p.x1 * width, p.y1 * height)
                    val p2 = Offset(p.x2 * width, p.y2 * height)
                    val scaledRadius = p.radius * refScale

                    drawCircle(color = Color(0xFFFF00CC), radius = scaledRadius, center = p1, style = Stroke(width = 4f * refScale))
                    drawCircle(color = Color(0xFF00FFFF), radius = scaledRadius, center = p2, style = Stroke(width = 4f * refScale))
                }

                // Start Zone & Goal Hole
                drawCircle(color = Color.Green.copy(alpha = 0.25f), radius = startRadius, center = startPos)
                drawCircle(color = Color.Green, radius = startRadius, center = startPos, style = Stroke(width = 2f * refScale))

                drawCircle(brush = Brush.radialGradient(colors = listOf(theme.fluidCenterColor, theme.caseOuterBg), center = goalPos, radius = goalRadius), radius = goalRadius, center = goalPos)
                drawCircle(color = theme.fluidCenterColor, radius = goalRadius, center = goalPos, style = Stroke(width = 3f * refScale))

                // Render Pinball Bumpers
                for (bumper in levelLayout.bumpers) {
                    val bx = bumper.x * width; val by = bumper.y * height
                    val scaledRadius = bumper.radius * refScale
                    drawCircle(color = Color(0xFFFFCC00).copy(alpha = 0.35f), radius = scaledRadius * 1.2f, center = Offset(bx, by))
                    drawCircle(color = Color(0xFFFFCC00), radius = scaledRadius, center = Offset(bx, by), style = Stroke(width = 3.5f * refScale))
                }

                // Render Hazards
                for (hazard in levelLayout.hazards) {
                    val hx = (if (hazard.isMoving) (hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.x) * width
                    val hy = (if (hazard.isMoving) (hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.y) * height
                    val hCenter = Offset(hx, hy)
                    val scaledRadius = hazard.radius * refScale

                    drawCircle(color = Color(0xFFFF3300).copy(alpha = 0.45f), radius = scaledRadius * 1.4f, center = hCenter)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFF00), Color(0xFFFF6600), Color(0xFFFF0000), Color(0xFF800000)),
                            center = Offset(hCenter.x - scaledRadius * 0.25f, hCenter.y - scaledRadius * 0.25f),
                            radius = scaledRadius * 1.2f
                        ),
                        radius = scaledRadius, center = hCenter
                    )
                    drawCircle(color = Color(0xFFFFCC00), radius = scaledRadius, center = hCenter, style = Stroke(width = 2f * refScale))
                }

                // Render Walls
                for (wall in levelLayout.walls) {
                    val currentWx = if (wall.isMoving && wall.moveAxis == 'H') (wall.x + sin(elapsedTime * wall.speed) * wall.moveRange).coerceIn(0.04f, 1f - wall.width - 0.04f) else wall.x
                    val currentWy = if (wall.isMoving && wall.moveAxis == 'V') (wall.y + cos(elapsedTime * wall.speed) * wall.moveRange).coerceIn(0.04f, 1f - wall.height - 0.04f) else wall.y
                    val wallTopLeft = Offset(currentWx * width, currentWy * height)
                    val wallSize = Size(wall.width * width, wall.height * height)
                    val cr = 8f * refScale

                    if (wall.isMoving) {
                        drawRoundRect(brush = Brush.linearGradient(colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red)), topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(cr, cr))
                    } else {
                        drawRoundRect(color = theme.bezelBorderColor, topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(cr, cr))
                        drawRoundRect(color = theme.arcadeAccentColor.copy(alpha = 0.6f), topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(cr, cr), style = Stroke(width = 1.5f * refScale))
                    }
                }

                // Render Steel Ball
                if (gameState != "DEAD") {
                    val ballCenter = Offset(ballX, ballY)
                    val highlightOffset = Offset(ballCenter.x - ballRadius * 0.35f, ballCenter.y - ballRadius * 0.35f)
                    drawCircle(
                        brush = Brush.radialGradient(colors = listOf(Color.White, Color(0xFFD0D0D0), Color(0xFF505050), Color(0xFF202020)), center = highlightOffset, radius = ballRadius * 1.3f),
                        radius = ballRadius, center = ballCenter
                    )
                } else {
                    drawOval(
                        brush = Brush.radialGradient(colors = listOf(Color(0xFFFF4500), Color(0xFF8B0000), Color.Transparent)),
                        topLeft = Offset(ballX - 35f * refScale, ballY - 5f * refScale),
                        size = Size(70f * refScale, 30f * refScale)
                    )
                }
            }

            // Victory Screen Overlay
            if (gameState == "WON") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(24.dp)
                            .rotate(90f)
                    ) {
                        val lavaBrush = Brush.linearGradient(
                            colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red, Color(0xFFFF8C00)),
                            start = Offset(0f, lavaOffset),
                            end = Offset(300f, lavaOffset + 300f)
                        )
                        Text(
                            text = winTitle,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = androidx.compose.ui.text.TextStyle(brush = lavaBrush)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "STAGE $currentLevel CLEARED!",
                            color = Color.Green,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "NEXT STAGE IN $countdownSeconds...",
                            color = theme.arcadeAccentColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Death Screen Overlay
            if (gameState == "DEAD") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(24.dp)
                            .rotate(90f)
                    ) {
                        val lavaBrush = Brush.linearGradient(
                            colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red, Color(0xFFFF8C00)),
                            start = Offset(0f, lavaOffset),
                            end = Offset(300f, lavaOffset + 300f)
                        )
                        Text(
                            text = "INCINERATED",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            style = androidx.compose.ui.text.TextStyle(brush = lavaBrush)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "TOUCHED THE FIRE BOUNDARY",
                            color = Color(0xFFFFB366),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "RESTARTING IN $countdownSeconds...",
                            color = theme.arcadeAccentColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime -> elapsedTime = (frameTime - startTime) / 1_000_000_000f }
        }
    }
}