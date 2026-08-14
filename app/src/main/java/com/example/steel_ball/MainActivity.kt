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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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

// =====================================================================================
// MAIN ACTIVITY
// =====================================================================================

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

// =====================================================================================
// ENHANCED MULTI-VOICE POLYPHONIC RETRO MUSIC ENGINE
// Features 4-channel synthesis: Lead Pulse + Polyphonic Arp/Harmony + FM Sub-Bass + Percussion
// =====================================================================================

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

            // Multi-channel retro scales (Frequency sequences)
            val scales = listOf(
                listOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25), // C Major Chiptune
                listOf(220.00, 261.63, 293.66, 329.63, 392.00, 440.00), // A Minor Synth
                listOf(196.00, 220.00, 246.94, 293.66, 329.63, 392.00), // G Pentatonic
                listOf(174.61, 220.00, 261.63, 329.63, 349.23, 440.00), // F Lydian Dynamic
                listOf(146.83, 174.61, 196.00, 220.00, 261.63, 293.66)  // D Minor Dark
            )

            while (isPlaying) {
                val lvl = currentLevel
                val scale = scales[(lvl - 1) % scales.size]
                val tempoBoost = (1f - (lvl % 10) * 0.012f).coerceIn(0.75f, 1f)
                val baseDuration = (sampleRate / 8 * tempoBoost).toInt()

                // Polyphonic voices selection
                val leadFreq = scale[stepCounter % scale.size]
                val arpFreq = scale[(stepCounter * 2 + 1) % scale.size] * 1.5
                val bassFreq = scale[(stepCounter / 2) % scale.size] / 2.0

                val isKick = stepCounter % 4 == 0
                val isSnare = stepCounter % 4 == 2
                val isHat = stepCounter % 2 == 1

                stepCounter++
                val buffer = ShortArray(baseDuration)

                for (i in 0 until baseDuration) {
                    val t = i.toDouble() / sampleRate
                    val env = sin((i.toDouble() / baseDuration) * Math.PI)

                    // Channel 1: Lead Pulse Wave (duty cycle 25% to 50%)
                    val pulseDuty = 0.25 + 0.25 * sin(2.0 * Math.PI * 0.5 * t)
                    val leadPhase = (leadFreq * t) % 1.0
                    val leadWave = if (leadPhase < pulseDuty) 0.5 else -0.5

                    // Channel 2: Arpeggiator / Harmony (Sawtooth / Triangle blend)
                    val arpSaw = 2.0 * (arpFreq * t - floor(arpFreq * t + 0.5))
                    val arpTri = asin(sin(2.0 * Math.PI * arpFreq * t)) / (Math.PI / 2.0)
                    val harmonyWave = (arpSaw * 0.4 + arpTri * 0.6) * 0.35

                    // Channel 3: Deep Sub-Bass (FM Modulated Sine Wave)
                    val bassMod = sin(2.0 * Math.PI * (bassFreq * 0.5) * t) * 2.0
                    val bassWave = sin(2.0 * Math.PI * bassFreq * t + bassMod) * 0.45

                    // Channel 4: Percussion Engine
                    val kick = if (isKick) sin(2.0 * Math.PI * (70.0 * exp(-i / 500.0)) * t) * exp(-i / 800.0) else 0.0
                    val snare = if (isSnare) (rng.nextDouble() * 2.0 - 1.0) * exp(-i / 600.0) else 0.0
                    val hat = if (isHat && i < 400) (rng.nextDouble() * 2.0 - 1.0) * exp(-i / 150.0) * 0.4 else 0.0

                    val drums = (kick * 0.5 + snare * 0.3 + hat * 0.2)

                    // Master Polyphonic Mix
                    val mixed = (leadWave * 0.35 + harmonyWave + bassWave + drums) * env
                    buffer[i] = (mixed * 4200.0).toInt().coerceIn(-32768, 32767).toShort()
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

// =====================================================================================
// VISUAL THEMES — eight distinct arcade cabinet palettes, cycling every level
// =====================================================================================

data class ThemePalette(
    val name: String,
    val caseOuterBg: Color,
    val caseInnerBg: Color,
    val bezelBorderColor: Color,
    val fluidCenterColor: Color,
    val fluidEdgeColor: Color,
    val arcadeAccentColor: Color,
    val gridLineColor: Color,
    val skyTop: Color,
    val skyBottom: Color
)

val AppThemes = listOf(
    ThemePalette("Outrun 84", Color(0xFF140727), Color(0xFF220945), Color(0xFF6B117A), Color(0xFFFF0055), Color(0xFF990033), Color(0xFF00FFFF), Color(0xFFFF2E9C), Color(0xFF2A0A4A), Color(0xFF120220)),
    ThemePalette("Cyberpunk", Color(0xFF0B0F19), Color(0xFF131C2E), Color(0xFF1F3A60), Color(0xFFFEE715), Color(0xFFC7830A), Color(0xFFFF0055), Color(0xFF20E0FF), Color(0xFF0E1830), Color(0xFF05070E)),
    ThemePalette("Arcade Amber", Color(0xFF1A0D00), Color(0xFF2E1700), Color(0xFF663300), Color(0xFFFF8800), Color(0xFFB34700), Color(0xFFFFFF00), Color(0xFFFFB347), Color(0xFF331A00), Color(0xFF0D0600)),
    ThemePalette("Laser Matrix", Color(0xFF021508), Color(0xFF072B12), Color(0xFF0F5927), Color(0xFF00FF66), Color(0xFF008F38), Color(0xFF00E5FF), Color(0xFF00FF88), Color(0xFF04220E), Color(0xFF010A04)),
    ThemePalette("Neon Void", Color(0xFF0A0014), Color(0xFF160026), Color(0xFF3D0066), Color(0xFFB300FF), Color(0xFF6A00A8), Color(0xFF39FF14), Color(0xFFB300FF), Color(0xFF1C0033), Color(0xFF08000F)),
    ThemePalette("Deep Reef", Color(0xFF001220), Color(0xFF002A40), Color(0xFF004D66), Color(0xFF00E5FF), Color(0xFF0088AA), Color(0xFFFFC300), Color(0xFF00CFFF), Color(0xFF012B3D), Color(0xFF000B14)),
    ThemePalette("Solar Flare", Color(0xFF1A0300), Color(0xFF330500), Color(0xFF801000), Color(0xFFFFD400), Color(0xFFFF7A00), Color(0xFF00FFD0), Color(0xFFFF9100), Color(0xFF3A0900), Color(0xFF120000)),
    ThemePalette("Ghost Grid", Color(0xFF08080C), Color(0xFF121218), Color(0xFF2A2A38), Color(0xFFE0E0FF), Color(0xFF8888AA), Color(0xFFFF3D6E), Color(0xFF8A8AFF), Color(0xFF181820), Color(0xFF050508))
)

fun difficultyLabel(level: Int): Pair<String, Color> = when {
    level <= 3 -> "ROOKIE" to Color(0xFF00FF66)
    level <= 7 -> "ARCADE" to Color(0xFFFFD400)
    level <= 12 -> "EXPERT" to Color(0xFFFF8800)
    level <= 18 -> "MASTER" to Color(0xFFFF3300)
    else -> "LEGEND" to Color(0xFFFF00CC)
}

// =====================================================================================
// LEVEL GEOMETRY / OBJECT MODELS
// =====================================================================================

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
    val isMoving: Boolean = false, val moveAxis: Char = 'H', val moveRange: Float = 0.5f, val speed: Float = 0.8f
) {
    fun getPlacementRect(): PlacementRect {
        val sweepMinX = if (isMoving && moveAxis == 'H') 0f else x
        val sweepMaxX = if (isMoving && moveAxis == 'H') 1f else x + width
        val sweepMinY = if (isMoving && moveAxis == 'V') 0f else y
        val sweepMaxY = if (isMoving && moveAxis == 'V') 1f else y + height
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

data class SpikeTrap(val x: Float, val y: Float, val size: Float) {
    fun getPlacementRect(): PlacementRect = PlacementRect(x - 0.03f, y - 0.03f, x + 0.03f, y + 0.03f)
}

data class WindZone(
    val x: Float, val y: Float, val width: Float, val height: Float,
    val forceX: Float, val forceY: Float
) {
    fun getPlacementRect(): PlacementRect = PlacementRect(x, y, x + width, y + height)
}

data class RotatingBeam(
    val cx: Float, val cy: Float, val length: Float, val speed: Float, val phase: Float
) {
    fun getPlacementRect(): PlacementRect {
        val r = length / 300f
        return PlacementRect(cx - r, cy - r, cx + r, cy + r)
    }
}

data class LevelLayout(
    val walls: List<Wall>,
    val hazards: List<Hazard>,
    val blackHoles: List<BlackHole>,
    val boosters: List<SpeedBooster>,
    val portal: Portal?,
    val bumpers: List<PinballBumper>,
    val icePatches: List<IcePatch>,
    val spikes: List<SpikeTrap>,
    val windZones: List<WindZone>,
    val beams: List<RotatingBeam>,
    val goalX: Float,
    val goalY: Float,
    val goalRadius: Float
)

fun isLevelPassable(layout: LevelLayout): Boolean {
    val gridSize = 50
    val grid = Array(gridSize) { BooleanArray(gridSize) { true } }
    val ballMargin = 0.035f

    // Static wall placement check
    for (wall in layout.walls) {
        if (!wall.isMoving) {
            val rect = wall.getPlacementRect()
            val startGx = ((rect.minX - ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
            val endGx = ((rect.maxX + ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
            val startGy = ((rect.minY - ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
            val endGy = ((rect.maxY + ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)

            for (gx in startGx..endGx) {
                for (gy in startGy..endGy) { grid[gx][gy] = false }
            }
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

// =====================================================================================
// PROCEDURAL LEVEL GENERATOR
// =====================================================================================

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
    val spikes = mutableListOf<SpikeTrap>()
    val windZones = mutableListOf<WindZone>()
    val beams = mutableListOf<RotatingBeam>()

    val speedMultiplier = 1f + (level - 1) * 0.30f
    val dynamicSpeed = (0.80f + rng.nextFloat() * 0.50f) * speedMultiplier

    fun canPlace(rect: PlacementRect, padding: Float = 0.01f): Boolean {
        return occupied.none { it.overlaps(rect, padding) }
    }

    val minGoalDistance = (0.50f + (level * 0.012f)).coerceAtMost(0.72f)
    val goalRadius = (32f - level * 0.55f).coerceAtLeast(19f)

    var goalX = 0.15f
    var goalY = 0.15f
    for (attempt in 0..100) {
        val gx = 0.10f + rng.nextFloat() * 0.70f
        val gy = 0.10f + rng.nextFloat() * 0.70f
        val distanceFromStart = hypot(gx - 0.85f, gy - 0.85f)

        if (distanceFromStart >= minGoalDistance) {
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
        val isMoving = rng.nextFloat() < (0.35f + level * 0.05f).coerceAtMost(0.75f)
        val isVert = rng.nextBoolean()
        val baseW = if (isVert) 0.035f else 0.20f + rng.nextFloat() * 0.18f
        val baseH = if (isVert) 0.20f + rng.nextFloat() * 0.18f else 0.035f

        for (attempt in 0..40) {
            val wx = 0.06f + rng.nextFloat() * (0.88f - baseW)
            val wy = 0.06f + rng.nextFloat() * (0.88f - baseH)

            val candidate = Wall(wx, wy, baseW, baseH, isMoving, if (rng.nextBoolean()) 'H' else 'V', 0.5f, dynamicSpeed)
            val rect = candidate.getPlacementRect()

            if (canPlace(rect, 0.01f)) {
                walls.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    val lavaCount = (5 + level * 2).coerceAtMost(18)
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

    val bhCount = (2 + level).coerceAtMost(6)
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

    val bumperCount = (2 + level).coerceAtMost(6)
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

    val iceCount = (2 + level).coerceAtMost(5)
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

    val spikeCount = (1 + level / 2).coerceAtMost(8)
    for (i in 0 until spikeCount) {
        for (attempt in 0..40) {
            val sx = 0.10f + rng.nextFloat() * 0.80f
            val sy = 0.10f + rng.nextFloat() * 0.80f
            val candidate = SpikeTrap(sx, sy, size = 26f + rng.nextFloat() * 10f)
            val rect = candidate.getPlacementRect()
            if (canPlace(rect, 0.01f)) {
                spikes.add(candidate)
                occupied.add(rect)
                break
            }
        }
    }

    if (level >= 2) {
        val windCount = (level / 2).coerceAtMost(4)
        for (i in 0 until windCount) {
            for (attempt in 0..40) {
                val ww = 0.16f + rng.nextFloat() * 0.10f
                val wh = 0.16f + rng.nextFloat() * 0.10f
                val wx = 0.08f + rng.nextFloat() * (0.84f - ww)
                val wy = 0.08f + rng.nextFloat() * (0.84f - wh)
                val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
                val strength = 0.10f + (level * 0.006f).coerceAtMost(0.10f)
                val candidate = WindZone(wx, wy, ww, wh, cos(angle) * strength, sin(angle) * strength)
                val rect = candidate.getPlacementRect()
                if (canPlace(rect, 0.01f)) {
                    windZones.add(candidate)
                    occupied.add(rect)
                    break
                }
            }
        }
    }

    if (level >= 3) {
        val beamCount = (1 + (level - 3) / 2).coerceAtMost(4)
        for (i in 0 until beamCount) {
            for (attempt in 0..40) {
                val bcx = 0.15f + rng.nextFloat() * 0.70f
                val bcy = 0.15f + rng.nextFloat() * 0.70f
                val len = 90f + rng.nextFloat() * 60f
                val beamSpeed = (0.6f + rng.nextFloat() * 0.6f) * speedMultiplier
                val candidate = RotatingBeam(bcx, bcy, len, beamSpeed, rng.nextFloat() * 6.28f)
                val rect = candidate.getPlacementRect()
                if (canPlace(rect, 0.015f)) {
                    beams.add(candidate)
                    occupied.add(rect)
                    break
                }
            }
        }
    }

    return LevelLayout(walls, hazards, blackHoles, boosters, portal, bumpers, icePatches, spikes, windZones, beams, goalX, goalY, goalRadius)
}

fun generateRandomizedAILevel(level: Int): LevelLayout {
    var seed = level * 77777L
    while (true) {
        val layout = buildSingleLevelCandidate(level, seed)
        if (isLevelPassable(layout)) return layout
        seed += 10007L
    }
}

fun pointToSegmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val abx = bx - ax; val aby = by - ay
    val apx = px - ax; val apy = py - ay
    val abLenSq = abx * abx + aby * aby
    val t = if (abLenSq > 0f) ((apx * abx + apy * aby) / abLenSq).coerceIn(0f, 1f) else 0f
    val closestX = ax + abx * t
    val closestY = ay + aby * t
    return hypot(px - closestX, py - closestY)
}

// =====================================================================================
// GAME SCREEN
// =====================================================================================

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
    val (diffText, diffColor) = difficultyLabel(currentLevel)

    var gameState by remember { mutableStateOf("PLAYING") }
    var ballX by remember { mutableFloatStateOf(-1f) }
    var ballY by remember { mutableFloatStateOf(-1f) }
    var velocityX by remember { mutableFloatStateOf(0f) }
    var velocityY by remember { mutableFloatStateOf(0f) }
    var portalCooldown by remember { mutableIntStateOf(0) }

    val congratulationTitles = remember { listOf(" UNBELIEVABLE ", " PERFECT ", " INSANE SKILL ", " VICTORY ", " MASTERED ") }
    var winTitle by remember { mutableStateOf(" PERFECT ") }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    val trail = remember { mutableStateListOf<Offset>() }
    var shakeMagnitude by remember { mutableFloatStateOf(0f) }

    var stageStartTime by remember { mutableFloatStateOf(0f) }
    var frozenStageTime by remember { mutableFloatStateOf(0f) }
    var isNewBest by remember { mutableStateOf(false) }
    var frozenPrevBest by remember { mutableFloatStateOf(-1f) }

    val ballRadius = 22f
    val baseFriction = 0.95f
    val accelerationFactor = 0.40f

    val levelLayout = remember(currentLevel) { generateRandomizedAILevel(currentLevel) }
    var elapsedTime by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "lava")
    val lavaOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "lavaOffset"
    )

    LaunchedEffect(currentLevel) {
        arcadeMusicPlayer?.currentLevel = currentLevel
        stageStartTime = elapsedTime
        trail.clear()
    }

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
                trail.clear()
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
        // Header Controls
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
                    Text(text = diffText, color = diffColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
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

        // Viewport Box
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

                                for (bh in levelLayout.blackHoles) {
                                    val bhX = bh.x * width
                                    val bhY = bh.y * height
                                    val hitArea = bh.radius * 2.5f

                                    if (hypot(tapOffset.x - bhX, tapOffset.y - bhY) <= hitArea) {
                                        ballX = bhX
                                        ballY = bhY
                                        val randomAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
                                        val impulseSpeed = 18f
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

                if (ballX < 0f || ballY < 0f) {
                    ballX = width * 0.85f
                    ballY = height * 0.85f
                    velocityX = 0f
                    velocityY = 0f
                }

                val startPos = Offset(width * 0.85f, height * 0.85f)
                val goalPos = Offset(levelLayout.goalX * width, levelLayout.goalY * height)
                val goalRadius = levelLayout.goalRadius

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
                    if (shakeMagnitude > 0f) shakeMagnitude *= 0.9f

                    val subSteps = 6
                    val subAx = (-roll / 30f).coerceIn(-1f, 1f) * accelerationFactor / subSteps
                    val subAy = (pitch / 30f).coerceIn(-1f, 1f) * accelerationFactor / subSteps

                    for (step in 0 until subSteps) {
                        velocityX = (velocityX + subAx) * (friction.pow(1f / subSteps))
                        velocityY = (velocityY + subAy) * (friction.pow(1f / subSteps))

                        for (bh in levelLayout.blackHoles) {
                            val bhX = bh.x * width
                            val bhY = bh.y * height
                            val dist = hypot(ballX - bhX, ballY - bhY)
                            if (dist < bh.radius * 3.8f && dist > 4f) {
                                val pull = (bh.strength / 5f) / (dist * 0.03f)
                                velocityX += ((bhX - ballX) / dist) * pull
                                velocityY += ((bhY - ballY) / dist) * pull
                            }
                        }

                        for (wind in levelLayout.windZones) {
                            val wx = wind.x * width; val wy = wind.y * height
                            val ww = wind.width * width; val wh = wind.height * height
                            if (ballX in wx..(wx + ww) && ballY in wy..(wy + wh)) {
                                velocityX += wind.forceX / subSteps
                                velocityY += wind.forceY / subSteps
                            }
                        }

                        var nextX = ballX + velocityX / subSteps
                        var nextY = ballY + velocityY / subSteps

                        if (nextX - ballRadius < 0f || nextX + ballRadius > width ||
                            nextY - ballRadius < 0f || nextY + ballRadius > height) {
                            gameState = "DEAD"
                            shakeMagnitude = 14f
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                            break
                        }

                        // Wall Collisions (Full frame edge-to-edge calculation for moving walls)
                        for (wall in levelLayout.walls) {
                            val currentWx = if (wall.isMoving && wall.moveAxis == 'H') {
                                (0.5f + 0.5f * sin(elapsedTime * wall.speed)) * (1f - wall.width)
                            } else wall.x
                            val currentWy = if (wall.isMoving && wall.moveAxis == 'V') {
                                (0.5f + 0.5f * cos(elapsedTime * wall.speed)) * (1f - wall.height)
                            } else wall.y

                            val wx = currentWx * width; val wy = currentWy * height
                            val ww = wall.width * width; val wh = wall.height * height

                            if (nextX + ballRadius > wx && nextX - ballRadius < wx + ww && nextY + ballRadius > wy && nextY - ballRadius < wy + wh) {
                                if (wall.isMoving) {
                                    gameState = "DEAD"
                                    shakeMagnitude = 14f
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
                            if (hypot(nextX - bx, nextY - by) < ballRadius + booster.radius) {
                                velocityX += booster.dirX * 3.8f
                                velocityY += booster.dirY * 3.8f
                            }
                        }

                        levelLayout.portal?.let { p ->
                            if (portalCooldown == 0) {
                                val p1x = p.x1 * width; val p1y = p.y1 * height
                                val p2x = p.x2 * width; val p2y = p.y2 * height

                                if (hypot(nextX - p1x, nextY - p1y) < ballRadius + p.radius) {
                                    nextX = p2x; nextY = p2y; portalCooldown = 30
                                } else if (hypot(nextX - p2x, nextY - p2y) < ballRadius + p.radius) {
                                    nextX = p1x; nextY = p1y; portalCooldown = 30
                                }
                            }
                        }

                        for (bumper in levelLayout.bumpers) {
                            val bx = bumper.x * width; val by = bumper.y * height
                            val dist = hypot(nextX - bx, nextY - by)
                            if (dist < ballRadius + bumper.radius) {
                                val nx = (nextX - bx) / dist; val ny = (nextY - by) / dist
                                nextX = bx + nx * (ballRadius + bumper.radius)
                                nextY = by + ny * (ballRadius + bumper.radius)
                                val dot = velocityX * nx + velocityY * ny
                                velocityX = (velocityX - 2 * dot * nx) * 1.35f
                                velocityY = (velocityY - 2 * dot * ny) * 1.35f
                            }
                        }

                        ballX = nextX; ballY = nextY
                    }

                    // Sharp Mines Hazard Collisions
                    for (hazard in levelLayout.hazards) {
                        val hx = (if (hazard.isMoving) (hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.x) * width
                        val hy = (if (hazard.isMoving) (hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.y) * height
                        if (hypot(ballX - hx, ballY - hy) < ballRadius + hazard.radius * 0.85f) {
                            gameState = "DEAD"
                            shakeMagnitude = 14f
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                        }
                    }

                    for (spike in levelLayout.spikes) {
                        val sx = spike.x * width; val sy = spike.y * height
                        if (hypot(ballX - sx, ballY - sy) < ballRadius + spike.size * 0.55f) {
                            gameState = "DEAD"
                            shakeMagnitude = 14f
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                        }
                    }

                    for (beam in levelLayout.beams) {
                        val angle = elapsedTime * beam.speed + beam.phase
                        val bcx = beam.cx * width; val bcy = beam.cy * height
                        val ex = bcx + cos(angle) * beam.length
                        val ey = bcy + sin(angle) * beam.length
                        val sx = bcx - cos(angle) * beam.length * 0.15f
                        val sy = bcy - sin(angle) * beam.length * 0.15f
                        if (pointToSegmentDistance(ballX, ballY, sx, sy, ex, ey) < ballRadius + 5f) {
                            gameState = "DEAD"
                            shakeMagnitude = 14f
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                        }
                    }

                    if (hypot(ballX - goalPos.x, ballY - goalPos.y) < goalRadius * 0.75f) {
                        winTitle = congratulationTitles.random()
                        val stageTime = elapsedTime - stageStartTime
                        val bestKey = "best_time_$currentLevel"
                        val prevBest = prefs.getFloat(bestKey, -1f)
                        val betterRun = prevBest < 0f || stageTime < prevBest
                        if (betterRun) prefs.edit().putFloat(bestKey, stageTime).apply()
                        frozenStageTime = stageTime
                        frozenPrevBest = prevBest
                        isNewBest = betterRun
                        gameState = "WON"
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else { @Suppress("DEPRECATION") vibrator?.vibrate(100) }
                        } catch (_: Exception) {}
                    }

                    trail.add(Offset(ballX, ballY))
                    while (trail.size > 14) trail.removeAt(0)
                }

                val shakeX = if (shakeMagnitude > 0.3f) (Random.nextFloat() - 0.5f) * shakeMagnitude else 0f
                val shakeY = if (shakeMagnitude > 0.3f) (Random.nextFloat() - 0.5f) * shakeMagnitude else 0f
                translate(shakeX, shakeY) {

                    // ---------------------------------------------------------------
                    // FULL SCREEN DYNAMIC WARP BACKGROUND (Changes Style Per Level)
                    // Spans the entirety of the display canvas (Full Screen)
                    // ---------------------------------------------------------------
                    drawRect(
                        brush = Brush.verticalGradient(colors = listOf(theme.skyTop, theme.skyBottom)),
                        topLeft = Offset.Zero, size = Size(width, height)
                    )

                    val bgStyleMode = (currentLevel - 1) % 3
                    when (bgStyleMode) {
                        0 -> { // HYPERDRIVE RADIAL WARP TUNNEL
                            val centerX = width / 2f
                            val centerY = height / 2f
                            val rayCount = 18
                            val speed = (elapsedTime * 120f) % 80f

                            for (r in 0 until rayCount) {
                                val angle = (r * 360f / rayCount) * (Math.PI / 180f).toFloat()
                                val cosA = cos(angle)
                                val sinA = sin(angle)
                                var distance = speed
                                while (distance < max(width, height)) {
                                    val start = Offset(centerX + cosA * distance, centerY + sinA * distance)
                                    val end = Offset(centerX + cosA * (distance + 25f), centerY + sinA * (distance + 25f))
                                    val alpha = (distance / max(width, height)).coerceIn(0.1f, 0.6f)
                                    drawLine(
                                        color = theme.gridLineColor.copy(alpha = alpha),
                                        start = start, end = end, strokeWidth = 2f
                                    )
                                    distance += 70f
                                }
                            }
                        }
                        1 -> { // FULL-SCREEN PERSPECTIVE WARP GRID
                            val scrollY = (elapsedTime * 60f) % 40f
                            var gy = scrollY
                            while (gy < height) {
                                drawLine(
                                    color = theme.gridLineColor.copy(alpha = 0.22f),
                                    start = Offset(0f, gy), end = Offset(width, gy), strokeWidth = 1.5f
                                )
                                gy += 40f
                            }
                            var gx = 0f
                            while (gx < width) {
                                drawLine(
                                    color = theme.gridLineColor.copy(alpha = 0.15f),
                                    start = Offset(gx, 0f), end = Offset(gx, height), strokeWidth = 1.2f
                                )
                                gx += 40f
                            }
                        }
                        else -> { // HIGH-SPEED VECTOR SPEED LINES
                            val speed = (elapsedTime * 300f) % width
                            for (line in 0..12) {
                                val yPos = (height / 12) * line + (sin(elapsedTime + line) * 20f)
                                val startX = (speed + line * 60f) % width
                                drawLine(
                                    color = theme.arcadeAccentColor.copy(alpha = 0.3f),
                                    start = Offset(startX, yPos),
                                    end = Offset((startX + 80f) % width, yPos),
                                    strokeWidth = 2.5f
                                )
                            }
                        }
                    }

                    // Twinkling Starfield Across Full Viewport
                    val starRng = Random(currentLevel * 991L)
                    repeat(55) {
                        val sx = starRng.nextFloat() * width
                        val sy = starRng.nextFloat() * height
                        val twinkle = 0.35f + 0.35f * ((sin(elapsedTime * 2.5f + sx * 0.05f) + 1f) / 2f)
                        drawCircle(color = Color.White.copy(alpha = twinkle * 0.7f), radius = 1.8f, center = Offset(sx, sy))
                    }

                    // ---------------------------------------------------------------
                    // REALISTIC ITEM: WORMHOLE TELEPORT / PORTALS
                    // ---------------------------------------------------------------
                    levelLayout.portal?.let { p ->
                        val p1 = Offset(p.x1 * width, p.y1 * height)
                        val p2 = Offset(p.x2 * width, p.y2 * height)

                        for (portalPos in listOf(p1, p2)) {
                            // Swirling Vortex Arc Rings
                            rotate(degrees = elapsedTime * 120f, pivot = portalPos) {
                                drawCircle(
                                    brush = Brush.sweepGradient(colors = listOf(Color(0xFFFF00CC), Color(0xFF00FFFF), Color(0xFFFF00CC)), center = portalPos),
                                    radius = p.radius * 1.3f, center = portalPos, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f)))
                                )
                            }
                            // Deep Space Void Center
                            drawCircle(brush = Brush.radialGradient(colors = listOf(Color.Black, Color(0xFF20003B)), center = portalPos, radius = p.radius), radius = p.radius, center = portalPos)
                            drawCircle(color = Color.White, radius = p.radius * 0.3f, center = portalPos)
                        }
                    }

                    // Start Zone & Goal Hole
                    drawCircle(color = Color.Green.copy(alpha = 0.25f), radius = 34f, center = startPos)
                    drawCircle(color = Color.Green, radius = 34f, center = startPos, style = Stroke(width = 2f))

                    val goalPulse = 1f + 0.08f * sin(elapsedTime * 4f)
                    drawCircle(brush = Brush.radialGradient(colors = listOf(theme.fluidCenterColor, theme.caseOuterBg), center = goalPos, radius = goalRadius * goalPulse), radius = goalRadius * goalPulse, center = goalPos)
                    drawCircle(color = theme.fluidCenterColor, radius = goalRadius * goalPulse, center = goalPos, style = Stroke(width = 3f))

                    // Wind zones
                    for (wind in levelLayout.windZones) {
                        val wx = wind.x * width; val wy = wind.y * height
                        val ww = wind.width * width; val wh = wind.height * height
                        drawRoundRect(
                            color = theme.arcadeAccentColor.copy(alpha = 0.10f),
                            topLeft = Offset(wx, wy), size = Size(ww, wh), cornerRadius = CornerRadius(10f, 10f)
                        )
                        drawRoundRect(
                            color = theme.arcadeAccentColor.copy(alpha = 0.5f),
                            topLeft = Offset(wx, wy), size = Size(ww, wh), cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), (elapsedTime * 30f) % 18f))
                        )
                        val angle = atan2(wind.forceY, wind.forceX)
                        val cx = wx + ww / 2f; val cy = wy + wh / 2f
                        val arrowLen = min(ww, wh) * 0.3f
                        val ax = cx + cos(angle) * arrowLen; val ay = cy + sin(angle) * arrowLen
                        drawLine(color = theme.arcadeAccentColor, start = Offset(cx - cos(angle) * arrowLen, cy - sin(angle) * arrowLen), end = Offset(ax, ay), strokeWidth = 2.5f)
                    }

                    // Static Spike Traps
                    for (spike in levelLayout.spikes) {
                        val sx = spike.x * width; val sy = spike.y * height
                        drawCircle(color = Color(0xFFFF1744).copy(alpha = 0.3f), radius = spike.size * 0.9f, center = Offset(sx, sy))
                        for (k in 0 until 6) {
                            val ang = (k * 60f) * (Math.PI / 180.0).toFloat() + elapsedTime * 0.5f
                            val tipX = sx + cos(ang) * spike.size
                            val tipY = sy + sin(ang) * spike.size
                            val baseAng1 = ang + 0.5f; val baseAng2 = ang - 0.5f
                            val b1x = sx + cos(baseAng1) * spike.size * 0.35f; val b1y = sy + sin(baseAng1) * spike.size * 0.35f
                            val b2x = sx + cos(baseAng2) * spike.size * 0.35f; val b2y = sy + sin(baseAng2) * spike.size * 0.35f
                            val path = Path().apply {
                                moveTo(tipX, tipY); lineTo(b1x, b1y); lineTo(b2x, b2y); close()
                            }
                            drawPath(path, color = Color(0xFFB71C1C))
                            drawPath(path, color = Color(0xFFFF5252), style = Stroke(width = 1.5f))
                        }
                        drawCircle(color = Color(0xFF1A1A1A), radius = spike.size * 0.3f, center = Offset(sx, sy))
                    }

                    // Rotating Beams
                    for (beam in levelLayout.beams) {
                        val angle = elapsedTime * beam.speed + beam.phase
                        val bcx = beam.cx * width; val bcy = beam.cy * height
                        val ex = bcx + cos(angle) * beam.length
                        val ey = bcy + sin(angle) * beam.length
                        val sx = bcx - cos(angle) * beam.length * 0.15f
                        val sy = bcy - sin(angle) * beam.length * 0.15f
                        drawLine(color = Color(0xFFFF1744).copy(alpha = 0.35f), start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = 14f)
                        drawLine(
                            brush = Brush.linearGradient(colors = listOf(Color(0xFFFFEB3B), Color(0xFFFF1744))),
                            start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = 5f
                        )
                        drawCircle(color = Color(0xFFFFEB3B), radius = 7f, center = Offset(bcx, bcy))
                    }

                    // ---------------------------------------------------------------
                    // REALISTIC ITEM: PINBALL BUMPERS
                    // ---------------------------------------------------------------
                    for (bumper in levelLayout.bumpers) {
                        val bx = bumper.x * width; val by = bumper.y * height

                        // Chrome Outer Ring & Spring Coils
                        drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White, Color.Gray, Color.DarkGray), center = Offset(bx - 5f, by - 5f), radius = bumper.radius * 1.2f), radius = bumper.radius * 1.15f, center = Offset(bx, by))
                        drawCircle(color = Color(0xFF222222), radius = bumper.radius * 0.85f, center = Offset(bx, by))
                        // Glowing Bumper Dome Center
                        drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFFFFFF00), Color(0xFFFF9900)), center = Offset(bx - 3f, by - 3f), radius = bumper.radius * 0.6f), radius = bumper.radius * 0.6f, center = Offset(bx, by))
                        drawCircle(color = Color.White.copy(alpha = 0.8f), radius = bumper.radius * 0.2f, center = Offset(bx - 4f, by - 4f))
                    }

                    // ---------------------------------------------------------------
                    // SHARP MINES HAZARDS (Updated Visuals: Spiked Naval Mines)
                    // ---------------------------------------------------------------
                    for (hazard in levelLayout.hazards) {
                        val hx = (if (hazard.isMoving) (hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.x) * width
                        val hy = (if (hazard.isMoving) (hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius).coerceIn(0.05f, 0.95f) else hazard.y) * height
                        val hCenter = Offset(hx, hy)

                        // Draw Sharp Metallic Spikes (10 sharp spikes around mine body)
                        val numSpikes = 10
                        val spikePath = Path()
                        for (k in 0 until numSpikes * 2) {
                            val r = if (k % 2 == 0) hazard.radius * 1.45f else hazard.radius * 0.60f
                            val a = (k * Math.PI / numSpikes).toFloat() + elapsedTime * 2f
                            val px = hx + cos(a) * r
                            val py = hy + sin(a) * r
                            if (k == 0) spikePath.moveTo(px, py) else spikePath.lineTo(px, py)
                        }
                        spikePath.close()

                        // Sharp Outer Metallic Mine Body
                        drawPath(spikePath, brush = Brush.radialGradient(colors = listOf(Color(0xFFFF3300), Color(0xFF550000)), center = hCenter, radius = hazard.radius * 1.4f))
                        drawPath(spikePath, color = Color(0xFFFFCC00), style = Stroke(width = 2f))

                        // Inner Pulsing Warning Core
                        val corePulse = 0.4f + 0.2f * sin(elapsedTime * 8f)
                        drawCircle(color = Color.Red, radius = hazard.radius * 0.45f, center = hCenter)
                        drawCircle(color = Color.Yellow.copy(alpha = corePulse), radius = hazard.radius * 0.25f, center = hCenter)
                    }

                    // ---------------------------------------------------------------
                    // REALISTIC ITEM: ICE PATCHES (Translucent Crystal & Frost)
                    // ---------------------------------------------------------------
                    for (ice in levelLayout.icePatches) {
                        val ix = ice.x * width; val iy = ice.y * height
                        val iw = ice.width * width; val ih = ice.height * height

                        drawRoundRect(
                            brush = Brush.linearGradient(colors = listOf(Color(0xBB88E5FF), Color(0x440088CC))),
                            topLeft = Offset(ix, iy), size = Size(iw, ih),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        // Frost Crack lines
                        drawLine(color = Color.White.copy(alpha = 0.7f), start = Offset(ix + 10f, iy + 10f), end = Offset(ix + iw * 0.4f, iy + ih * 0.6f), strokeWidth = 1.5f)
                        drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(ix + iw * 0.4f, iy + ih * 0.6f), end = Offset(ix + iw - 10f, iy + ih * 0.3f), strokeWidth = 1f)

                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = Offset(ix, iy), size = Size(iw, ih),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = Stroke(width = 2f)
                        )
                    }

                    // ---------------------------------------------------------------
                    // REALISTIC ITEM: EJECT POINTS / SPEED BOOSTERS
                    // ---------------------------------------------------------------
                    for (booster in levelLayout.boosters) {
                        val bx = booster.x * width; val by = booster.y * height
                        val angle = atan2(booster.dirY, booster.dirX)

                        // Launch Base Pad
                        drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF00FF66), Color(0xFF004411)), center = Offset(bx, by), radius = booster.radius * 1.3f), radius = booster.radius * 1.2f, center = Offset(bx, by))
                        drawCircle(color = Color.White, radius = booster.radius * 1.2f, center = Offset(bx, by), style = Stroke(width = 2f))

                        // Animated Launch Arrow Chevrons (>> Directional Eject)
                        val offsetPulse = (elapsedTime * 40f) % 15f
                        rotate(degrees = Math.toDegrees(angle.toDouble()).toFloat(), pivot = Offset(bx, by)) {
                            for (c in -1..1) {
                                val arrowX = bx + c * 10f + offsetPulse - 5f
                                val path = Path().apply {
                                    moveTo(arrowX - 6f, by - 8f)
                                    lineTo(arrowX + 4f, by)
                                    lineTo(arrowX - 6f, by + 8f)
                                }
                                drawPath(path, color = Color.White, style = Stroke(width = 3f))
                            }
                        }
                    }

                    // ---------------------------------------------------------------
                    // REALISTIC ITEM: BLACK HOLES (Accretion Disk & Gravitational Ring)
                    // ---------------------------------------------------------------
                    for (bh in levelLayout.blackHoles) {
                        val bhX = bh.x * width; val bhY = bh.y * height
                        val bhCenter = Offset(bhX, bhY)

                        // Gravitational Lensing Aura
                        drawCircle(color = Color(0xFF9C27B0).copy(alpha = 0.25f), radius = bh.radius * 2.2f, center = bhCenter)
                        // Swirling Accretion Disk
                        rotate(degrees = -elapsedTime * 90f, pivot = bhCenter) {
                            drawCircle(
                                brush = Brush.sweepGradient(colors = listOf(Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF3F51B5), Color(0xFFFF9800)), center = bhCenter),
                                radius = bh.radius * 1.5f, center = bhCenter, style = Stroke(width = 6f)
                            )
                        }
                        // Pitch Black Event Horizon Void
                        drawCircle(brush = Brush.radialGradient(colors = listOf(Color.Black, Color(0xFF110022)), center = bhCenter, radius = bh.radius), radius = bh.radius, center = bhCenter)
                        drawCircle(color = Color(0xFFE040FB), radius = bh.radius, center = bhCenter, style = Stroke(width = 1.5f))
                    }

                    // ---------------------------------------------------------------
                    // WALLS (Moving walls reach full screen boundaries)
                    // ---------------------------------------------------------------
                    for (wall in levelLayout.walls) {
                        val currentWx = if (wall.isMoving && wall.moveAxis == 'H') {
                            (0.5f + 0.5f * sin(elapsedTime * wall.speed)) * (1f - wall.width)
                        } else wall.x
                        val currentWy = if (wall.isMoving && wall.moveAxis == 'V') {
                            (0.5f + 0.5f * cos(elapsedTime * wall.speed)) * (1f - wall.height)
                        } else wall.y

                        val wallTopLeft = Offset(currentWx * width, currentWy * height)
                        val wallSize = Size(wall.width * width, wall.height * height)

                        if (wall.isMoving) {
                            drawRoundRect(brush = Brush.linearGradient(colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red)), topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f))
                            drawRoundRect(color = Color.White, topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 2f))
                        } else {
                            drawRoundRect(color = theme.bezelBorderColor, topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f))
                            drawRoundRect(color = theme.arcadeAccentColor.copy(alpha = 0.6f), topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 1.5f))
                        }
                    }

                    // Ball Motion Trail
                    if (gameState != "DEAD") {
                        for ((idx, pos) in trail.withIndex()) {
                            val fraction = (idx + 1f) / (trail.size + 1f)
                            drawCircle(
                                color = theme.arcadeAccentColor.copy(alpha = fraction * 0.35f),
                                radius = ballRadius * fraction * 0.85f,
                                center = pos
                            )
                        }
                    }

                    // Steel Ball
                    if (gameState != "DEAD") {
                        val ballCenter = Offset(ballX, ballY)
                        val highlightOffset = Offset(ballCenter.x - ballRadius * 0.35f, ballCenter.y - ballRadius * 0.35f)
                        drawCircle(
                            brush = Brush.radialGradient(colors = listOf(Color.White, Color(0xFFD0D0D0), Color(0xFF505050), Color(0xFF202020)), center = highlightOffset, radius = ballRadius * 1.3f),
                            radius = ballRadius, center = ballCenter
                        )
                    } else {
                        drawOval(brush = Brush.radialGradient(colors = listOf(Color(0xFFFF4500), Color(0xFF8B0000), Color.Transparent)), topLeft = Offset(ballX - 35f, ballY - 5f), size = Size(70f, 30f))
                    }

                    // Animated Fire Border Frame
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Red, Color(0xFFFF4500), Color.Yellow, Color(0xFFFF4500), Color.Red),
                            start = Offset(0f, lavaOffset),
                            end = Offset(width, height + lavaOffset)
                        ),
                        topLeft = Offset.Zero,
                        size = Size(width, height),
                        style = Stroke(width = 10f)
                    )

                    // CRT Scanlines
                    var scanY = 0f
                    while (scanY < height) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.10f),
                            start = Offset(0f, scanY), end = Offset(width, scanY), strokeWidth = 1f
                        )
                        scanY += 4f
                    }
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                            center = Offset(width / 2f, height / 2f),
                            radius = max(width, height) * 0.75f
                        ),
                        topLeft = Offset.Zero, size = Size(width, height)
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
                            style = TextStyle(
                                brush = lavaBrush,
                                shadow = Shadow(color = Color(0xFFFF8800), offset = Offset(0f, 0f), blurRadius = 24f)
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "STAGE $currentLevel CLEARED!",
                            color = Color.Green,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TIME: %.2fs".format(frozenStageTime),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isNewBest) "★ NEW BEST TIME ★" else "BEST: %.2fs".format(frozenPrevBest),
                            color = if (isNewBest) Color(0xFFFFD400) else Color(0xFFAAAAAA),
                            fontSize = 12.sp,
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
                            style = TextStyle(
                                brush = lavaBrush,
                                shadow = Shadow(color = Color(0xFFFF3300), offset = Offset(0f, 0f), blurRadius = 28f)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "TOUCHED A HAZARD",
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