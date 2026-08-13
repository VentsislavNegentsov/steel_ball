package com.example.steel_ball

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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.withFrameNanos
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
                    arcadeMusicPlayer = arcadeMusicPlayer,
                    onExit = { finish() }
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
                val mode = (lvl - 1) % 4

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
                    else -> {
                        val dur = sampleRate / 6
                        val scale = listOf(220.00, 233.08, 277.18, 311.13, 349.23)
                        val freq = scale[stepCounter % scale.size]
                        Pair(dur) { t: Double, _: Int ->
                            val sub = sin(2.0 * Math.PI * (freq / 2.0) * t)
                            val growl = tan(sin(2.0 * Math.PI * freq * t)).coerceIn(-1.0, 1.0)
                            (sub * 0.6 + growl * 0.4)
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

data class Wall(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val isMoving: Boolean = false,
    val moveAxis: Char = 'H',
    val moveRange: Float = 0.12f,
    val speed: Float = 0.6f
)

data class Hazard(
    val x: Float,
    val y: Float,
    val radius: Float,
    val isMoving: Boolean = false,
    val speed: Float = 0.5f,
    val pathRadius: Float = 0.1f,
    val seedOffset: Float = 0f
)

data class PinballBumper(
    val x: Float,
    val y: Float,
    val radius: Float
)

data class LevelLayout(
    val walls: List<Wall>,
    val hazards: List<Hazard>,
    val bumpers: List<PinballBumper>,
    val goalX: Float,
    val goalY: Float
)

fun isOverlapping(
    x1: Float, y1: Float, w1: Float, h1: Float,
    x2: Float, y2: Float, w2: Float, h2: Float,
    margin: Float = 0.04f
): Boolean {
    return !(x1 + w1 + margin < x2 || x1 > x2 + w2 + margin || y1 + h1 + margin < y2 || y1 > y2 + h2 + margin)
}

fun isLevelPassable(layout: LevelLayout): Boolean {
    val gridSize = 40
    val grid = Array(gridSize) { BooleanArray(gridSize) { true } }
    val ballMargin = 0.05f

    for (wall in layout.walls) {
        val minX = wall.x - if (wall.isMoving && wall.moveAxis == 'H') wall.moveRange else 0f
        val maxX = wall.x + wall.width + if (wall.isMoving && wall.moveAxis == 'H') wall.moveRange else 0f
        val minY = wall.y - if (wall.isMoving && wall.moveAxis == 'V') wall.moveRange else 0f
        val maxY = wall.y + wall.height + if (wall.isMoving && wall.moveAxis == 'V') wall.moveRange else 0f

        val startGx = ((minX - ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
        val endGx = ((maxX + ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
        val startGy = ((minY - ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)
        val endGy = ((maxY + ballMargin) * gridSize).toInt().coerceIn(0, gridSize - 1)

        for (gx in startGx..endGx) {
            for (gy in startGy..endGy) {
                grid[gx][gy] = false
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

fun buildSingleLevelCandidate(level: Int, seed: Long): LevelLayout {
    val rng = Random(seed)
    val walls = mutableListOf<Wall>()
    val hazards = mutableListOf<Hazard>()
    val bumpers = mutableListOf<PinballBumper>()

    val speedMultiplier = 1.0f + (level - 1) * 0.10f

    fun tryAddWall(baseW: Float, baseH: Float, isMoving: Boolean, axis: Char) {
        for (attempt in 0..40) {
            val wx = 0.12f + rng.nextFloat() * (0.7f - baseW)
            val wy = 0.12f + rng.nextFloat() * (0.7f - baseH)
            val range = if (isMoving) 0.12f else 0f
            val wallSpeed = (0.5f + rng.nextFloat() * 0.3f) * speedMultiplier

            val candidate = Wall(
                x = wx, y = wy, width = baseW, height = baseH,
                isMoving = isMoving, moveAxis = axis, moveRange = range,
                speed = wallSpeed
            )

            val cMinX = candidate.x - if (candidate.isMoving && candidate.moveAxis == 'H') candidate.moveRange else 0f
            val cMaxX = candidate.x + candidate.width + if (candidate.isMoving && candidate.moveAxis == 'H') candidate.moveRange else 0f
            val cMinY = candidate.y - if (candidate.isMoving && candidate.moveAxis == 'V') candidate.moveRange else 0f
            val cMaxY = candidate.y + candidate.height + if (candidate.isMoving && candidate.moveAxis == 'V') candidate.moveRange else 0f

            val hasOverlap = walls.any { existing ->
                val eMinX = existing.x - if (existing.isMoving && existing.moveAxis == 'H') existing.moveRange else 0f
                val eMaxX = existing.x + existing.width + if (existing.isMoving && existing.moveAxis == 'H') existing.moveRange else 0f
                val eMinY = existing.y - if (existing.isMoving && existing.moveAxis == 'V') existing.moveRange else 0f
                val eMaxY = existing.y + existing.height + if (existing.isMoving && existing.moveAxis == 'V') existing.moveRange else 0f

                isOverlapping(cMinX, cMinY, cMaxX - cMinX, cMaxY - cMinY, eMinX, eMinY, eMaxX - eMinX, eMaxY - eMinY)
            }

            if (!hasOverlap) {
                walls.add(candidate)
                break
            }
        }
    }

    tryAddWall(0.30f, 0.04f, false, 'H')
    tryAddWall(0.04f, 0.22f, false, 'V')

    if (level >= 2) tryAddWall(0.25f, 0.04f, false, 'H')

    val movingWallCount = if (level >= 2) (1 + (level - 2) / 2).coerceAtMost(3) else 0
    for (i in 0 until movingWallCount) {
        val axis = if (rng.nextBoolean()) 'H' else 'V'
        tryAddWall(0.18f, 0.04f, true, axis)
    }

    val hazardCount = (level + 1).coerceAtMost(7)
    for (i in 0 until hazardCount) {
        for (attempt in 0..40) {
            val isHazardMoving = level >= 2 && rng.nextBoolean()
            val hx = 0.15f + rng.nextFloat() * 0.7f
            val hy = 0.15f + rng.nextFloat() * 0.7f
            val pathR = if (isHazardMoving) 0.08f + rng.nextFloat() * 0.05f else 0f
            val hazardSize = 0.06f

            val overlapsWall = walls.any { w ->
                val wMinX = w.x - if (w.isMoving && w.moveAxis == 'H') w.moveRange else 0f
                val wMaxX = w.x + w.width + if (w.isMoving && w.moveAxis == 'H') w.moveRange else 0f
                val wMinY = w.y - if (w.isMoving && w.moveAxis == 'V') w.moveRange else 0f
                val wMaxY = w.y + w.height + if (w.isMoving && w.moveAxis == 'V') w.moveRange else 0f

                isOverlapping(hx - pathR, hy - pathR, hazardSize + pathR * 2, hazardSize + pathR * 2, wMinX, wMinY, wMaxX - wMinX, wMaxY - wMinY)
            }

            if (!overlapsWall) {
                hazards.add(
                    Hazard(
                        x = hx, y = hy, radius = 18f,
                        isMoving = isHazardMoving,
                        speed = (0.4f + rng.nextFloat() * 0.4f) * speedMultiplier,
                        pathRadius = pathR,
                        seedOffset = rng.nextFloat() * 10f
                    )
                )
                break
            }
        }
    }

    val bumperCount = (1 + level % 3)
    for (i in 0 until bumperCount) {
        bumpers.add(
            PinballBumper(
                x = 0.2f + rng.nextFloat() * 0.6f,
                y = 0.2f + rng.nextFloat() * 0.6f,
                radius = 16f + rng.nextFloat() * 8f
            )
        )
    }

    var goalX = 0.15f
    var goalY = 0.15f
    for (attempt in 0..40) {
        goalX = 0.15f + rng.nextFloat() * 0.6f
        goalY = 0.15f + rng.nextFloat() * 0.6f

        if (hypot(goalX - 0.85f, goalY - 0.85f) < 0.35f) continue

        val overlapsWall = walls.any { w ->
            goalX >= w.x - 0.08f && goalX <= w.x + w.width + 0.08f &&
                    goalY >= w.y - 0.08f && goalY <= w.y + w.height + 0.08f
        }
        if (!overlapsWall) break
    }

    return LevelLayout(walls, hazards, bumpers, goalX, goalY)
}

fun generateRandomizedAILevel(level: Int): LevelLayout {
    var seed = level * 31337L
    while (true) {
        val layout = buildSingleLevelCandidate(level, seed)
        if (isLevelPassable(layout)) {
            return layout
        }
        seed += 10007L
    }
}

@Composable
fun SteelBallGameScreen(
    pitch: Float,
    roll: Float,
    vibrator: Vibrator?,
    arcadeMusicPlayer: ArcadeMusicPlayer?,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("steel_ball_prefs", Context.MODE_PRIVATE) }

    var currentLevel by remember {
        mutableIntStateOf(prefs.getInt("current_level", 1).coerceAtLeast(1))
    }

    var maxLevelReached by remember {
        mutableIntStateOf(prefs.getInt("max_level", currentLevel).coerceAtLeast(currentLevel))
    }

    val theme = AppThemes[(currentLevel - 1) % AppThemes.size]

    var gameState by remember { mutableStateOf("PLAYING") }
    var ballX by remember { mutableStateOf(-1f) }
    var ballY by remember { mutableStateOf(-1f) }
    var velocityX by remember { mutableStateOf(0f) }
    var velocityY by remember { mutableStateOf(0f) }

    val congratulationTitles = remember {
        listOf("!!! GREAT !!!", "!!! AWESOME !!!", "!!! EXCELLENT !!!", "!!! VICTORY !!!", "!!! AMAZING !!!")
    }
    var winTitle by remember { mutableStateOf("!!! GREAT !!!") }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    val ballRadius = 22f
    val friction = 0.96f
    val accelerationFactor = 0.35f

    val levelLayout = remember(currentLevel) { generateRandomizedAILevel(currentLevel) }
    val walls = levelLayout.walls
    val hazards = levelLayout.hazards
    val bumpers = levelLayout.bumpers

    var elapsedTime by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "lava")
    val lavaOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "lavaOffset"
    )

    LaunchedEffect(currentLevel) {
        arcadeMusicPlayer?.currentLevel = currentLevel
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
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.caseOuterBg)
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                        border = BorderStroke(1.dp, theme.bezelBorderColor),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("◀", color = if (currentLevel > 1) Color.White else Color.DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "STAGE $currentLevel",
                        color = theme.arcadeAccentColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                        border = BorderStroke(1.dp, theme.bezelBorderColor),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("▶", color = if (currentLevel < maxLevelReached) Color.White else Color.DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = when (gameState) {
                        "WON" -> "🏆 STAGE CLEARED! ADVANCING..."
                        "DEAD" -> "🔥 MELTED! AUTO RESTARTING..."
                        else -> "🎵 STYLE: ${theme.name.uppercase()} (MAX: $maxLevelReached)"
                    },
                    color = when (gameState) {
                        "WON" -> Color.Green
                        "DEAD" -> Color.Red
                        else -> Color(0xFFCCCCCC)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(theme.caseInnerBg)
                .border(2.dp, theme.arcadeAccentColor, RoundedCornerShape(24.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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
                val goalRadius = 34f

                val gridSize = 40f
                var gx = 0f
                while (gx < width) {
                    drawLine(theme.bezelBorderColor.copy(alpha = 0.3f), Offset(gx, 0f), Offset(gx, height), strokeWidth = 1f)
                    gx += gridSize
                }
                var gy = 0f
                while (gy < height) {
                    drawLine(theme.bezelBorderColor.copy(alpha = 0.3f), Offset(0f, gy), Offset(width, gy), strokeWidth = 1f)
                    gy += gridSize
                }

                if (gameState == "PLAYING") {
                    val subSteps = 4
                    val subAx = (-roll / 30f).coerceIn(-1f, 1f) * accelerationFactor / subSteps
                    val subAy = (pitch / 30f).coerceIn(-1f, 1f) * accelerationFactor / subSteps

                    for (step in 0 until subSteps) {
                        velocityX = (velocityX + subAx) * (friction.pow(1f / subSteps))
                        velocityY = (velocityY + subAy) * (friction.pow(1f / subSteps))

                        var nextX = ballX + velocityX / subSteps
                        var nextY = ballY + velocityY / subSteps

                        if (nextX - ballRadius < 0f) { nextX = ballRadius; velocityX = -velocityX * 0.3f }
                        if (nextX + ballRadius > width) { nextX = width - ballRadius; velocityX = -velocityX * 0.3f }
                        if (nextY - ballRadius < 0f) { nextY = ballRadius; velocityY = -velocityY * 0.3f }
                        if (nextY + ballRadius > height) { nextY = height - ballRadius; velocityY = -velocityY * 0.3f }

                        for (wall in walls) {
                            val currentWx = if (wall.isMoving && wall.moveAxis == 'H') {
                                wall.x + sin(elapsedTime * wall.speed) * wall.moveRange
                            } else wall.x
                            val currentWy = if (wall.isMoving && wall.moveAxis == 'V') {
                                wall.y + cos(elapsedTime * wall.speed) * wall.moveRange
                            } else wall.y

                            val wx = currentWx * width
                            val wy = currentWy * height
                            val ww = wall.width * width
                            val wh = wall.height * height

                            if (nextX + ballRadius > wx && nextX - ballRadius < wx + ww &&
                                nextY + ballRadius > wy && nextY - ballRadius < wy + wh) {

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

                        for (bumper in bumpers) {
                            val bx = bumper.x * width
                            val by = bumper.y * height
                            val dist = hypot(nextX - bx, nextY - by)
                            if (dist < ballRadius + bumper.radius) {
                                val nx = (nextX - bx) / dist
                                val ny = (nextY - by) / dist
                                nextX = bx + nx * (ballRadius + bumper.radius)
                                nextY = by + ny * (ballRadius + bumper.radius)
                                val dot = velocityX * nx + velocityY * ny
                                velocityX = (velocityX - 2 * dot * nx) * 1.2f
                                velocityY = (velocityY - 2 * dot * ny) * 1.2f
                            }
                        }

                        ballX = nextX
                        ballY = nextY
                    }

                    for (hazard in hazards) {
                        val hx = (if (hazard.isMoving) hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius else hazard.x) * width
                        val hy = (if (hazard.isMoving) hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius else hazard.y) * height
                        val distToHazard = hypot(ballX - hx, ballY - hy)
                        if (distToHazard < ballRadius + hazard.radius * 0.6f) {
                            gameState = "DEAD"
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { @Suppress("DEPRECATION") vibrator?.vibrate(250) }
                            } catch (_: Exception) {}
                        }
                    }

                    val distToGoal = hypot(ballX - goalPos.x, ballY - goalPos.y)
                    if (distToGoal < goalRadius * 0.8f) {
                        winTitle = congratulationTitles.random()
                        gameState = "WON"
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else { @Suppress("DEPRECATION") vibrator?.vibrate(100) }
                        } catch (_: Exception) {}
                    }
                }

                drawCircle(color = Color.Green.copy(alpha = 0.25f), radius = 36f, center = startPos)
                drawCircle(color = Color.Green, radius = 36f, center = startPos, style = Stroke(width = 2f))

                drawCircle(brush = Brush.radialGradient(colors = listOf(theme.fluidCenterColor, theme.caseOuterBg), center = goalPos, radius = goalRadius), radius = goalRadius, center = goalPos)
                drawCircle(color = theme.fluidCenterColor, radius = goalRadius, center = goalPos, style = Stroke(width = 3f))

                for (hazard in hazards) {
                    val hx = (if (hazard.isMoving) hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius else hazard.x) * width
                    val hy = (if (hazard.isMoving) hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius else hazard.y) * height
                    val hCenter = Offset(hx, hy)

                    if (hazard.isMoving) {
                        for (trail in 1..4) {
                            val trailOffsetTime = elapsedTime - (trail * 0.05f)
                            val thx = (hazard.x + sin(trailOffsetTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius) * width
                            val thy = (hazard.y + cos(trailOffsetTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius) * height
                            drawCircle(
                                color = Color(0xFFFF4500).copy(alpha = 0.4f / trail),
                                radius = hazard.radius * (1.2f - trail * 0.15f),
                                center = Offset(thx, thy)
                            )
                        }
                    }

                    drawCircle(color = Color(0xFFFF2200).copy(alpha = 0.45f), radius = hazard.radius * 1.5f, center = hCenter)
                    drawCircle(
                        brush = Brush.radialGradient(colors = listOf(Color.Yellow, Color.Red, Color.Black), center = hCenter, radius = hazard.radius),
                        radius = hazard.radius, center = hCenter
                    )
                    drawCircle(color = Color.Yellow, radius = hazard.radius, center = hCenter, style = Stroke(width = 1.5f))
                }

                for (wall in walls) {
                    val currentWx = if (wall.isMoving && wall.moveAxis == 'H') {
                        wall.x + sin(elapsedTime * wall.speed) * wall.moveRange
                    } else wall.x
                    val currentWy = if (wall.isMoving && wall.moveAxis == 'V') {
                        wall.y + cos(elapsedTime * wall.speed) * wall.moveRange
                    } else wall.y

                    val wallTopLeft = Offset(currentWx * width, currentWy * height)
                    val wallSize = Size(wall.width * width, wall.height * height)

                    if (wall.isMoving) {
                        val trailShift = sin(elapsedTime * wall.speed * 2f) * 6f

                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF8C00).copy(alpha = 0.6f), Color.Transparent),
                                center = Offset(wallTopLeft.x + wallSize.width / 2, wallTopLeft.y + wallSize.height / 2),
                                radius = max(wallSize.width, wallSize.height) * 1.2f
                            ),
                            topLeft = Offset(wallTopLeft.x - 12f, wallTopLeft.y - 12f),
                            size = Size(wallSize.width + 24f, wallSize.height + 24f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )

                        val fireBrush = Brush.linearGradient(
                            colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red, Color(0xFFFF8C00)),
                            start = Offset(wallTopLeft.x + trailShift, wallTopLeft.y),
                            end = Offset(wallTopLeft.x + wallSize.width, wallTopLeft.y + wallSize.height)
                        )

                        drawRoundRect(brush = fireBrush, topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f))
                        drawRoundRect(color = Color.Yellow, topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 2f))
                    } else {
                        drawRoundRect(color = theme.bezelBorderColor, topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f))
                        drawRoundRect(color = theme.arcadeAccentColor.copy(alpha = 0.6f), topLeft = wallTopLeft, size = wallSize, cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 1.5f))
                    }
                }

                if (gameState != "DEAD") {
                    val ballCenter = Offset(ballX, ballY)
                    val highlightOffset = Offset(ballCenter.x - ballRadius * 0.35f, ballCenter.y - ballRadius * 0.35f)

                    drawCircle(
                        brush = Brush.radialGradient(colors = listOf(Color.White, Color(0xFFD0D0D0), Color(0xFF505050), Color(0xFF202020)), center = highlightOffset, radius = ballRadius * 1.3f),
                        radius = ballRadius, center = ballCenter
                    )
                    drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = ballRadius, center = ballCenter, style = Stroke(width = 1.5f))
                } else {
                    val puddleCenter = Offset(ballX, ballY + 10f)
                    drawOval(
                        brush = Brush.radialGradient(colors = listOf(Color(0xFFFF4500), Color(0xFF8B0000), Color.Transparent)),
                        topLeft = Offset(puddleCenter.x - 35f, puddleCenter.y - 15f),
                        size = Size(70f, 30f)
                    )
                }
            }

            if (gameState == "WON") {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val lavaBrush = Brush.linearGradient(
                            colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red, Color(0xFFFF8C00)),
                            start = Offset(0f, lavaOffset), end = Offset(300f, lavaOffset + 300f)
                        )
                        Text(text = winTitle, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, style = androidx.compose.ui.text.TextStyle(brush = lavaBrush))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "STAGE $currentLevel CLEARED!", color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "NEXT STAGE IN $countdownSeconds...", color = theme.arcadeAccentColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            if (gameState == "DEAD") {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val lavaBrush = Brush.linearGradient(
                            colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red, Color(0xFFFF8C00)),
                            start = Offset(0f, lavaOffset), end = Offset(300f, lavaOffset + 300f)
                        )
                        Text(text = "MELTED", fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, style = androidx.compose.ui.text.TextStyle(brush = lavaBrush))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "YOUR BALL LIQUEFIED IN THE LAVA", color = Color(0xFFFFB366), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "RESTARTING IN $countdownSeconds...", color = theme.arcadeAccentColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                border = BorderStroke(1.5.dp, theme.bezelBorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Exit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime ->
                elapsedTime = (frameTime - startTime) / 1_000_000_000f
            }
        }
    }
}