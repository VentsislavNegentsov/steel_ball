package com.example.steel_ball

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var primarySensor: Sensor? = null
    private var vibrator: Vibrator? = null

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
}

data class ThemePalette(
    val name: String,
    val caseOuterBg: Color,
    val caseInnerBg: Color,
    val bezelBorderColor: Color,
    val fluidCenterColor: Color,
    val fluidEdgeColor: Color,
    val reticleColor: Color
)

val AppThemes = listOf(
    ThemePalette(
        name = "Amber",
        caseOuterBg = Color(0xFF12100E),
        caseInnerBg = Color(0xFF1E1A17),
        bezelBorderColor = Color(0xFF382E26),
        fluidCenterColor = Color(0xFFFFB300),
        fluidEdgeColor = Color(0xFFB26A00),
        reticleColor = Color(0xFF38230B)
    ),
    ThemePalette(
        name = "Cyan",
        caseOuterBg = Color(0xFF0A1215),
        caseInnerBg = Color(0xFF122026),
        bezelBorderColor = Color(0xFF1D353F),
        fluidCenterColor = Color(0xFF00E5FF),
        fluidEdgeColor = Color(0xFF00838F),
        reticleColor = Color(0xFF00363D)
    ),
    ThemePalette(
        name = "Magenta",
        caseOuterBg = Color(0xFF150A12),
        caseInnerBg = Color(0xFF20121D),
        bezelBorderColor = Color(0xFF3F1D35),
        fluidCenterColor = Color(0xFFFF007F),
        fluidEdgeColor = Color(0xFF8F0047),
        reticleColor = Color(0xFF3D001F)
    ),
    ThemePalette(
        name = "Matrix",
        caseOuterBg = Color(0xFF0A150D),
        caseInnerBg = Color(0xFF122015),
        bezelBorderColor = Color(0xFF1D3F26),
        fluidCenterColor = Color(0xFF00FF66),
        fluidEdgeColor = Color(0xFF008F38),
        reticleColor = Color(0xFF003D17)
    )
)

data class Wall(val x: Float, val y: Float, val width: Float, val height: Float)

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

    // Game state
    var gameState by remember { mutableStateOf("PLAYING") } // PLAYING, WON
    var ballX by remember { mutableStateOf(-1f) }
    var ballY by remember { mutableStateOf(-1f) }
    var velocityX by remember { mutableStateOf(0f) }
    var velocityY by remember { mutableStateOf(0f) }

    val ballRadius = 24f
    val friction = 0.96f
    val accelerationFactor = 0.35f

    val walls = remember {
        listOf(
            Wall(0.2f, 0.3f, 0.4f, 0.05f),
            Wall(0.4f, 0.6f, 0.05f, 0.3f),
            Wall(0.15f, 0.7f, 0.35f, 0.05f)
        )
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NIVELIR MAZE",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (gameState == "WON") "🏆 Level Cleared!" else "Guide the ball to the target",
                    color = if (gameState == "WON") theme.fluidCenterColor else Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
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

        // Game Playfield Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(theme.caseInnerBg)
                .border(2.dp, theme.bezelBorderColor, RoundedCornerShape(24.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@Canvas

                // Initialize start position if not set
                if (ballX < 0f || ballY < 0f) {
                    ballX = width * 0.15f
                    ballY = height * 0.85f
                }

                val startPos = Offset(width * 0.15f, height * 0.85f)
                val goalPos = Offset(width * 0.85f, height * 0.15f)
                val goalRadius = 36f

                // Physics Simulation
                if (gameState == "PLAYING") {
                    // Steel Ball Gravity Correction:
                    // Tilting right (+roll) rolls ball right (+ax)
                    // Tilting down/away (+pitch) rolls ball towards the top (-ay)
                    val ax = (-roll / 30f).coerceIn(-1f, 1f) * accelerationFactor
                    val ay = (pitch / 30f).coerceIn(-1f, 1f) * accelerationFactor

                    velocityX = (velocityX + ax) * friction
                    velocityY = (velocityY + ay) * friction

                    var nextX = ballX + velocityX
                    var nextY = ballY + velocityY

                    // Boundary collisions
                    if (nextX - ballRadius < 0f) { nextX = ballRadius; velocityX = -velocityX * 0.3f }
                    if (nextX + ballRadius > width) { nextX = width - ballRadius; velocityX = -velocityX * 0.3f }
                    if (nextY - ballRadius < 0f) { nextY = ballRadius; velocityY = -velocityY * 0.3f }
                    if (nextY + ballRadius > height) { nextY = height - ballRadius; velocityY = -velocityY * 0.3f }

                    // Wall collisions
                    for (wall in walls) {
                        val wx = wall.x * width
                        val wy = wall.y * height
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

                    ballX = nextX
                    ballY = nextY

                    // Check win condition
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
                drawCircle(
                    color = Color.Green.copy(alpha = 0.25f),
                    radius = 40f,
                    center = startPos
                )
                drawCircle(
                    color = Color.Green,
                    radius = 40f,
                    center = startPos,
                    style = Stroke(width = 2f)
                )

                // Goal Target
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(theme.fluidCenterColor, theme.caseOuterBg),
                        center = goalPos,
                        radius = goalRadius
                    ),
                    radius = goalRadius,
                    center = goalPos
                )
                drawCircle(
                    color = theme.fluidCenterColor,
                    radius = goalRadius,
                    center = goalPos,
                    style = Stroke(width = 3f)
                )

                // Walls
                for (wall in walls) {
                    drawRoundRect(
                        color = theme.bezelBorderColor,
                        topLeft = Offset(wall.x * width, wall.y * height),
                        size = Size(wall.width * width, wall.height * height),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }

                // Steel Ball Rendering (Metallic Radial Gradient)
                val ballCenter = Offset(ballX, ballY)
                val highlightOffset = Offset(ballCenter.x - ballRadius * 0.35f, ballCenter.y - ballRadius * 0.35f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFD0D0D0), Color(0xFF505050), Color(0xFF202020)),
                        center = highlightOffset,
                        radius = ballRadius * 1.3f
                    ),
                    radius = ballRadius,
                    center = ballCenter
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = ballRadius,
                    center = ballCenter,
                    style = Stroke(width = 1.5f)
                )
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
                border = BorderStroke(1.5.dp, theme.fluidCenterColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Theme: ${theme.name}",
                    color = theme.fluidCenterColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
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
                    fontSize = 14.sp
                )
            }
        }
    }

    // Smooth frame tick loop
    var frameTrigger by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime ->
                frameTrigger = frameTime
            }
        }
    }
}