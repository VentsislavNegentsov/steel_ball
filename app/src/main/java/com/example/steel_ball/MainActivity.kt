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
                NivelirGameScreen(
                    pitch = pitch,
                    roll = roll,
                    vibrator = vibrator,
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

// Gentle, 60+ second looping ambient synth-chime melody (sine waves with smooth attack/decay envelope)
class ArcadeMusicPlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var thread: Thread? = null

    fun start() {
        if (isPlaying) return
        isPlaying = true
        thread = Thread {
            val sampleRate = 11025
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

            // Gentle soothing pentatonic and chord progression notes (frequencies in Hz)
            val melody = listOf(
                261.63, 293.66, 329.63, 392.00, 440.00, 392.00, 329.63, 293.66, // C4 D4 E4 G4 A4 G4 E4 D4
                220.00, 246.94, 293.66, 329.63, 392.00, 329.63, 293.66, 246.94, // A3 B3 D4 E4 G4 E4 D4 B3
                196.00, 220.00, 261.63, 293.66, 329.63, 293.66, 261.63, 220.00, // G3 A3 C4 D4 E4 D4 C4 A3
                174.61, 196.00, 220.00, 261.63, 293.66, 261.63, 220.00, 196.00  // F3 G3 A3 C4 D4 C4 A3 G3
            )
            // Each note lasts ~0.375s -> 32 notes * 0.375s = 12 seconds per loop.
            // Repeating for 6 full loops yields >72 seconds (>1 min) before looping sequence.

            val noteDurationSamples = sampleRate * 3 / 8 // ~0.375 seconds per note
            val totalNotesInMelody = melody.size * 6
            val buffer = ShortArray(noteDurationSamples)

            var globalNoteCounter = 0
            while (isPlaying) {
                val noteIndex = globalNoteCounter % melody.size
                val freq = melody[noteIndex]
                globalNoteCounter++

                if (globalNoteCounter > totalNotesInMelody) {
                    globalNoteCounter = 0 // loop melody seamlessly
                }

                for (i in 0 until noteDurationSamples) {
                    val t = i.toDouble() / sampleRate
                    // Smooth Sine wave with gentle envelope (attack & release) to avoid harsh clicking/alarm sound
                    val envelope = sin((i.toDouble() / noteDurationSamples) * Math.PI)
                    val sampleValue = (sin(2.0 * Math.PI * freq * t) * envelope * 4500.0).toInt().toShort()
                    buffer[i] = sampleValue
                }

                try {
                    audioTrack?.write(buffer, 0, buffer.size)
                } catch (_: Exception) {
                    break
                }
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
    ThemePalette(
        name = "Outrun 84",
        caseOuterBg = Color(0xFF140727),
        caseInnerBg = Color(0xFF220945),
        bezelBorderColor = Color(0xFF6B117A),
        fluidCenterColor = Color(0xFFFF0055),
        fluidEdgeColor = Color(0xFF990033),
        arcadeAccentColor = Color(0xFF00FFFF)
    ),
    ThemePalette(
        name = "Cyberpunk",
        caseOuterBg = Color(0xFF0B0F19),
        caseInnerBg = Color(0xFF131C2E),
        bezelBorderColor = Color(0xFF1F3A60),
        fluidCenterColor = Color(0xFFFEE715),
        fluidEdgeColor = Color(0xFFC7830A),
        arcadeAccentColor = Color(0xFFFF0055)
    ),
    ThemePalette(
        name = "Arcade Amber",
        caseOuterBg = Color(0xFF1A0D00),
        caseInnerBg = Color(0xFF2E1700),
        bezelBorderColor = Color(0xFF663300),
        fluidCenterColor = Color(0xFFFF8800),
        fluidEdgeColor = Color(0xFFB34700),
        arcadeAccentColor = Color(0xFFFFFF00)
    ),
    ThemePalette(
        name = "Laser Matrix",
        caseOuterBg = Color(0xFF021508),
        caseInnerBg = Color(0xFF072B12),
        bezelBorderColor = Color(0xFF0F5927),
        fluidCenterColor = Color(0xFF00FF66),
        fluidEdgeColor = Color(0xFF008F38),
        arcadeAccentColor = Color(0xFF00E5FF)
    )
)

data class Wall(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val isMoving: Boolean = false,
    val moveAxis: Char = 'H',
    val moveRange: Float = 0.12f,
    val speed: Float = 2.0f
)

data class Hazard(
    val x: Float,
    val y: Float,
    val radius: Float,
    val isFire: Boolean = false,
    val isMoving: Boolean = false,
    val speed: Float = 1.5f,
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

// Procedural Fully Randomized AI Level Generator ensuring non-overlapping walls and random goal on every single level pass
fun generateRandomizedAILevel(level: Int): LevelLayout {
    val rng = Random(level * 31337L)
    val walls = mutableListOf<Wall>()
    val hazards = mutableListOf<Hazard>()
    val bumpers = mutableListOf<PinballBumper>()

    fun tryAddWall(baseW: Float, baseH: Float, isMoving: Boolean, axis: Char) {
        for (attempt in 0..30) {
            val margin = 0.04f
            val wx = 0.12f + rng.nextFloat() * (0.7f - baseW)
            val wy = 0.12f + rng.nextFloat() * (0.7f - baseH)
            val range = if (isMoving) 0.12f else 0f

            val candidate = Wall(
                x = wx,
                y = wy,
                width = baseW,
                height = baseH,
                isMoving = isMoving,
                moveAxis = axis,
                moveRange = range,
                speed = 1.8f + rng.nextFloat() * 1.5f
            )

            val hasOverlap = walls.any { existing ->
                val eMinX = existing.x - if (existing.isMoving && existing.moveAxis == 'H') existing.moveRange else 0f
                val eMaxX = existing.x + existing.width + if (existing.isMoving && existing.moveAxis == 'H') existing.moveRange else 0f
                val eMinY = existing.y - if (existing.isMoving && existing.moveAxis == 'V') existing.moveRange else 0f
                val eMaxY = existing.y + existing.height + if (existing.isMoving && existing.moveAxis == 'V') existing.moveRange else 0f

                val cMinX = candidate.x - if (candidate.isMoving && candidate.moveAxis == 'H') candidate.moveRange else 0f
                val cMaxX = candidate.x + candidate.width + if (candidate.isMoving && candidate.moveAxis == 'H') candidate.moveRange else 0f
                val cMinY = candidate.y - if (candidate.isMoving && candidate.moveAxis == 'V') candidate.moveRange else 0f
                val cMaxY = candidate.y + candidate.height + if (candidate.isMoving && candidate.moveAxis == 'V') candidate.moveRange else 0f

                !(cMaxX + margin < eMinX || cMinX > eMaxX + margin || cMaxY + margin < eMinY || cMinY > eMaxY + margin)
            }

            if (!hasOverlap) {
                walls.add(candidate)
                break
            }
        }
    }

    tryAddWall(0.32f, 0.04f, false, 'H')
    tryAddWall(0.04f, 0.22f, false, 'V')

    if (level >= 2) {
        tryAddWall(0.25f, 0.04f, false, 'H')
    }

    if (level >= 4) {
        val movingCount = 1 + ((level - 4) / 2).coerceAtMost(2)
        for (i in 0 until movingCount) {
            val axis = if (rng.nextBoolean()) 'H' else 'V'
            tryAddWall(0.2f, 0.04f, true, axis)
        }
    }

    val bumperCount = 2 + (level % 3)
    for (i in 0 until bumperCount) {
        bumpers.add(
            PinballBumper(
                x = 0.2f + rng.nextFloat() * 0.6f,
                y = 0.2f + rng.nextFloat() * 0.6f,
                radius = 16f + rng.nextFloat() * 8f
            )
        )
    }

    if (level >= 2) {
        val hazardCount = 1 + (level / 2).coerceAtMost(3)
        for (i in 0 until hazardCount) {
            val isHazardMoving = level >= 3 && rng.nextBoolean()
            hazards.add(
                Hazard(
                    x = 0.15f + rng.nextFloat() * 0.7f,
                    y = 0.15f + rng.nextFloat() * 0.7f,
                    radius = 18f,
                    isFire = false,
                    isMoving = isHazardMoving,
                    speed = 1.2f + rng.nextFloat() * 1.5f,
                    pathRadius = 0.08f + rng.nextFloat() * 0.08f,
                    seedOffset = rng.nextFloat() * 10f
                )
            )
        }
    }

    if (level >= 5) {
        val fireCount = 1 + ((level - 5) / 2).coerceAtMost(3)
        for (i in 0 until fireCount) {
            hazards.add(
                Hazard(
                    x = 0.15f + rng.nextFloat() * 0.7f,
                    y = 0.15f + rng.nextFloat() * 0.7f,
                    radius = 20f,
                    isFire = true,
                    isMoving = true,
                    speed = 1.5f + rng.nextFloat() * 1.5f,
                    pathRadius = 0.1f + rng.nextFloat() * 0.1f,
                    seedOffset = rng.nextFloat() * 10f
                )
            )
        }
    }

    var goalX = 0.15f
    var goalY = 0.15f
    for (attempt in 0..30) {
        goalX = 0.15f + rng.nextFloat() * 0.6f
        goalY = 0.15f + rng.nextFloat() * 0.6f

        if (hypot(goalX - 0.85f, goalY - 0.85f) < 0.35f) continue

        val overlapsWall = walls.any { w ->
            goalX >= w.x - 0.06f && goalX <= w.x + w.width + 0.06f &&
                    goalY >= w.y - 0.06f && goalY <= w.y + w.height + 0.06f
        }
        if (!overlapsWall) break
    }

    return LevelLayout(walls, hazards, bumpers, goalX, goalY)
}

@Composable
fun NivelirGameScreen(
    pitch: Float,
    roll: Float,
    vibrator: Vibrator?,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nivelir_game_prefs", Context.MODE_PRIVATE) }
    var currentThemeIndex by remember {
        mutableIntStateOf(prefs.getInt("theme_index", 0).coerceIn(0, AppThemes.size - 1))
    }
    val theme = AppThemes[currentThemeIndex]

    var currentLevel by remember {
        mutableIntStateOf(prefs.getInt("current_level", 1).coerceAtLeast(1))
    }

    // Game state
    var gameState by remember { mutableStateOf("PLAYING") } // PLAYING, WON, DEAD
    var ballX by remember { mutableStateOf(-1f) }
    var ballY by remember { mutableStateOf(-1f) }
    var velocityX by remember { mutableStateOf(0f) }
    var velocityY by remember { mutableStateOf(0f) }

    val ballRadius = 22f
    val friction = 0.96f
    val accelerationFactor = 0.35f

    val levelLayout = remember(currentLevel) { generateRandomizedAILevel(currentLevel) }
    val walls = levelLayout.walls
    val hazards = levelLayout.hazards
    val bumpers = levelLayout.bumpers

    var elapsedTime by remember { mutableFloatStateOf(0f) }

    // Lava animation infinite transition for "MELTED" title screen
    val infiniteTransition = rememberInfiniteTransition(label = "lava")
    val lavaOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lavaOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.caseOuterBg)
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Arcade Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "STAGE $currentLevel",
                    color = theme.arcadeAccentColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = when (gameState) {
                        "WON" -> "🏆 STAGE CLEARED!"
                        "DEAD" -> "🔥 MELTED! TAP RESET"
                        else -> "🎵 GENTLE AMBIENT CHIME - STAGE $currentLevel"
                    },
                    color = when (gameState) {
                        "WON" -> Color.Green
                        "DEAD" -> Color.Red
                        else -> Color(0xFFCCCCCC)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gameState == "WON") {
                    Button(
                        onClick = {
                            currentLevel++
                            prefs.edit().putInt("current_level", currentLevel).apply()
                            gameState = "PLAYING"
                            ballX = -1f
                            ballY = -1f
                            velocityX = 0f
                            velocityY = 0f
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.arcadeAccentColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Next", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        gameState = "PLAYING"
                        ballX = -1f
                        ballY = -1f
                        velocityX = 0f
                        velocityY = 0f
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                    border = BorderStroke(1.dp, theme.bezelBorderColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Reset", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Retro Arcade Pinball Playfield Canvas
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

                // --- RETRO ARCADE PINBALL BACKGROUND GRAPHICS ---
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

                drawArc(
                    color = theme.arcadeAccentColor.copy(alpha = 0.15f),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(width * 0.1f, height * 0.1f),
                    size = Size(width * 0.8f, height * 0.8f),
                    style = Stroke(width = 2f)
                )

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
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(250)
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    val distToGoal = hypot(ballX - goalPos.x, ballY - goalPos.y)
                    if (distToGoal < goalRadius * 0.8f) {
                        gameState = "WON"
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(100)
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Start Zone
                drawCircle(color = Color.Green.copy(alpha = 0.25f), radius = 36f, center = startPos)
                drawCircle(color = Color.Green, radius = 36f, center = startPos, style = Stroke(width = 2f))

                // Goal Target
                drawCircle(
                    brush = Brush.radialGradient(colors = listOf(theme.fluidCenterColor, theme.caseOuterBg), center = goalPos, radius = goalRadius),
                    radius = goalRadius,
                    center = goalPos
                )
                drawCircle(color = theme.fluidCenterColor, radius = goalRadius, center = goalPos, style = Stroke(width = 3f))

                // Bumpers
                for (bumper in bumpers) {
                    val bCenter = Offset(bumper.x * width, bumper.y * height)
                    drawCircle(
                        brush = Brush.radialGradient(colors = listOf(theme.arcadeAccentColor, theme.caseOuterBg), center = bCenter, radius = bumper.radius),
                        radius = bumper.radius,
                        center = bCenter
                    )
                    drawCircle(color = Color.White, radius = bumper.radius, center = bCenter, style = Stroke(width = 2f))
                }

                // Hazards & Fire Glow
                for (hazard in hazards) {
                    val hx = (if (hazard.isMoving) hazard.x + sin(elapsedTime * hazard.speed + hazard.seedOffset) * hazard.pathRadius else hazard.x) * width
                    val hy = (if (hazard.isMoving) hazard.y + cos(elapsedTime * (hazard.speed * 0.8f) + hazard.seedOffset) * hazard.pathRadius else hazard.y) * height
                    val hCenter = Offset(hx, hy)

                    if (hazard.isFire) {
                        val firePulse = (sin(elapsedTime * 14f) * 0.3f + 0.7f)
                        drawCircle(color = Color(0xFFFF4500).copy(alpha = 0.3f), radius = hazard.radius * firePulse * 1.8f, center = hCenter)
                        drawCircle(
                            brush = Brush.radialGradient(colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red), center = hCenter, radius = hazard.radius * firePulse),
                            radius = hazard.radius * firePulse,
                            center = hCenter
                        )
                    } else {
                        drawCircle(color = Color.Red.copy(alpha = 0.35f), radius = hazard.radius * 1.4f, center = hCenter)
                        drawCircle(
                            brush = Brush.radialGradient(colors = listOf(Color.Red.copy(alpha = 0.9f), Color.DarkGray), center = hCenter, radius = hazard.radius),
                            radius = hazard.radius,
                            center = hCenter
                        )
                        drawCircle(color = Color.Red, radius = hazard.radius, center = hCenter, style = Stroke(width = 2f))
                    }
                }

                // Walls
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
                        drawRoundRect(
                            color = theme.arcadeAccentColor.copy(alpha = 0.3f),
                            topLeft = Offset(wallTopLeft.x - 4f, wallTopLeft.y - 4f),
                            size = Size(wallSize.width + 8f, wallSize.height + 8f),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }

                    drawRoundRect(
                        color = if (wall.isMoving) theme.arcadeAccentColor else theme.bezelBorderColor,
                        topLeft = wallTopLeft,
                        size = wallSize,
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }

                // Ball Rendering (or Melted state overlay if DEAD)
                if (gameState != "DEAD") {
                    val ballCenter = Offset(ballX, ballY)
                    val highlightOffset = Offset(ballCenter.x - ballRadius * 0.35f, ballCenter.y - ballRadius * 0.35f)

                    drawCircle(
                        brush = Brush.radialGradient(colors = listOf(Color.White, Color(0xFFD0D0D0), Color(0xFF505050), Color(0xFF202020)), center = highlightOffset, radius = ballRadius * 1.3f),
                        radius = ballRadius,
                        center = ballCenter
                    )
                    drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = ballRadius, center = ballCenter, style = Stroke(width = 1.5f))
                } else {
                    // Melted puddle effect on death
                    val puddleCenter = Offset(ballX, ballY + 10f)
                    drawOval(
                        brush = Brush.radialGradient(colors = listOf(Color(0xFFFF4500), Color(0xFF8B0000), Color.Transparent)),
                        topLeft = Offset(puddleCenter.x - 35f, puddleCenter.y - 15f),
                        size = Size(70f, 30f)
                    )
                }
            }

            // --- BIG "MELTED" TITLE WITH LAVA ANIMATION OVERLAY WHEN DEAD ---
            if (gameState == "DEAD") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Animated Lava Gradient Text for "MELTED"
                        val lavaBrush = Brush.linearGradient(
                            colors = listOf(Color.Yellow, Color(0xFFFF4500), Color.Red, Color(0xFFFF8C00)),
                            start = Offset(0f, lavaOffset),
                            end = Offset(300f, lavaOffset + 300f)
                        )

                        Text(
                            text = "MELTED",
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            style = androidx.compose.ui.text.TextStyle(brush = lavaBrush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "YOUR BALL LIQUEFIED IN THE LAVA",
                            color = Color(0xFFFFB366),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    currentThemeIndex = (currentThemeIndex + 1) % AppThemes.size
                    prefs.edit().putInt("theme_index", currentThemeIndex).apply()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                border = BorderStroke(1.5.dp, theme.arcadeAccentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Theme: ${theme.name}",
                    color = theme.arcadeAccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = onExit,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = theme.caseInnerBg),
                border = BorderStroke(1.5.dp, theme.bezelBorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Exit",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
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