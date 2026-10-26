package com.gloom.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gloom.island.ui.Apple
import java.util.Locale

enum class ThemeFillType {
    SOLID,
    HORIZONTAL_GRADIENT,
    LINEAR_GRADIENT,
    RADIAL_GRADIENT,
    SWEEP_GRADIENT
}

/** Theme specification for the Dynamic Island supporting 51 catalog themes & 20 gradient pairs. */
data class IslandThemeColors(
    val id: String = "classic_black",
    val displayName: String = "Classic Black",
    val fillType: ThemeFillType = ThemeFillType.SOLID,
    val backgroundColors: List<Color> = listOf(Color.Black),
    val background: Color = backgroundColors.firstOrNull() ?: Color.Black,
    val cardBackground: Color = Color(0xFF1C1C1E),
    val primaryAccent: Color = Color(0xFF0A84FF),
    val secondaryAccent: Color = Color(0xFF30D158),
    val borderColor: Color = Color.Transparent,
    val borderWidthDp: Float = 0f,
    val borderBrush: Brush = SolidColor(Color(0x1FFFFFFF)),
    val textColor: Color = Color.White,
    val isPremium: Boolean = false
)

object IslandThemePalettes {
    private val hairline = SolidColor(Color(0x1FFFFFFF))

    val THEMES: Map<String, IslandThemeColors> = mapOf(
        "classic_black" to IslandThemeColors(
            id = "classic_black",
            displayName = "Classic Black",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF000000)),
            cardBackground = Color(0xFF1C1C1E),
            primaryAccent = Color(0xFF0A84FF),
            secondaryAccent = Color(0xFF30D158),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "minimal_gray" to IslandThemeColors(
            id = "minimal_gray",
            displayName = "Minimal Gray",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF2B2B2C)),
            cardBackground = Color(0xFF3A3A3C),
            primaryAccent = Color(0xFF8E8E93),
            secondaryAccent = Color(0xFF636366),
            borderColor = Color(0xFF3C3C3D),
            borderWidthDp = 0.5f
        ),
        "midnight_blue" to IslandThemeColors(
            id = "midnight_blue",
            displayName = "Midnight",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF131820)),
            cardBackground = Color(0xFF1B232E),
            primaryAccent = Color(0xFF0A84FF),
            secondaryAccent = Color(0xFF5E5CE6),
            borderColor = Color(0xFF232B35),
            borderWidthDp = 1.0f
        ),
        "amethyst" to IslandThemeColors(
            id = "amethyst",
            displayName = "Amethyst",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF8A2BE2), Color(0xFF5D3FD3)),
            cardBackground = Color(0xFF2C1542),
            primaryAccent = Color(0xFFBF5AF2),
            secondaryAccent = Color(0xFFDA70D6),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            isPremium = true
        ),
        "sunset" to IslandThemeColors(
            id = "sunset",
            displayName = "Sunset",
            fillType = ThemeFillType.LINEAR_GRADIENT,
            backgroundColors = listOf(Color(0xFFFF5722), Color(0xFFC2185B)),
            cardBackground = Color(0xFF3B1520),
            primaryAccent = Color(0xFFFF9500),
            secondaryAccent = Color(0xFFFF2D55),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            isPremium = true
        ),
        "neon_cyber" to IslandThemeColors(
            id = "neon_cyber",
            displayName = "Neon Cyber",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF000000)),
            cardBackground = Color(0xFF0B141E),
            primaryAccent = Color(0xFF00FFCC),
            secondaryAccent = Color(0xFF00E5FF),
            borderColor = Color(0xFF00FFCC),
            borderWidthDp = 1.0f,
            isPremium = true
        ),
        "royal_gold" to IslandThemeColors(
            id = "royal_gold",
            displayName = "Royal Gold",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFD4AF37), Color(0xFFFFD700), Color(0xFFC5A028)),
            cardBackground = Color(0xFF2B2208),
            primaryAccent = Color(0xFFFFD700),
            secondaryAccent = Color(0xFFFFA500),
            borderColor = Color(0xFFFFDF00),
            borderWidthDp = 1.0f,
            textColor = Color(0xFF1C1C1E),
            isPremium = true
        ),
        "ocean_breeze" to IslandThemeColors(
            id = "ocean_breeze",
            displayName = "Ocean Breeze",
            fillType = ThemeFillType.LINEAR_GRADIENT,
            backgroundColors = listOf(Color(0xFF007799), Color(0xFF004466)),
            cardBackground = Color(0xFF0A2230),
            primaryAccent = Color(0xFF64D2FF),
            secondaryAccent = Color(0xFF0A84FF),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            isPremium = true
        ),
        "crimson_fire" to IslandThemeColors(
            id = "crimson_fire",
            displayName = "Crimson Fire",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFD32F2F), Color(0xFFFF1744)),
            cardBackground = Color(0xFF350A10),
            primaryAccent = Color(0xFFFF3B30),
            secondaryAccent = Color(0xFFFF453A),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            isPremium = true
        ),
        "deep_space" to IslandThemeColors(
            id = "deep_space",
            displayName = "Deep Space",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF0B0C10)),
            cardBackground = Color(0xFF1F2833),
            primaryAccent = Color(0xFF64D2FF),
            secondaryAccent = Color(0xFF45A29E),
            borderColor = Color(0xFF1F2833),
            borderWidthDp = 1.0f,
            isPremium = true
        ),
        "forest_rain" to IslandThemeColors(
            id = "forest_rain",
            displayName = "Forest Rain",
            fillType = ThemeFillType.LINEAR_GRADIENT,
            backgroundColors = listOf(Color(0xFF0F2027), Color(0xFF203A43)),
            cardBackground = Color(0xFF132824),
            primaryAccent = Color(0xFF30D158),
            secondaryAccent = Color(0xFF34C759),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "purple_haze" to IslandThemeColors(
            id = "purple_haze",
            displayName = "Purple Haze",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF301934)),
            cardBackground = Color(0xFF421E48),
            primaryAccent = Color(0xFFBF5AF2),
            secondaryAccent = Color(0xFFAF52DE),
            borderColor = Color(0xFF58111A),
            borderWidthDp = 1.0f
        ),
        "cherry_blossom" to IslandThemeColors(
            id = "cherry_blossom",
            displayName = "Cherry Blossom",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFFFB7B2), Color(0xFFFF9AA2)),
            cardBackground = Color(0xFF3E1E24),
            primaryAccent = Color(0xFFFF6482),
            secondaryAccent = Color(0xFFFF2D55),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "golden_hour" to IslandThemeColors(
            id = "golden_hour",
            displayName = "Golden Hour",
            fillType = ThemeFillType.LINEAR_GRADIENT,
            backgroundColors = listOf(Color(0xFFFFA000), Color(0xFFFF6F00)),
            cardBackground = Color(0xFF3A1F05),
            primaryAccent = Color(0xFFFF9500),
            secondaryAccent = Color(0xFFFFB340),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "matrix" to IslandThemeColors(
            id = "matrix",
            displayName = "The Matrix",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF000000)),
            cardBackground = Color(0xFF041909),
            primaryAccent = Color(0xFF00FF00),
            secondaryAccent = Color(0xFF30D158),
            borderColor = Color(0xFF00FF00),
            borderWidthDp = 1.5f,
            isPremium = true
        ),
        "cyberpunk_pink" to IslandThemeColors(
            id = "cyberpunk_pink",
            displayName = "Cyber Pink",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF1E002B)),
            cardBackground = Color(0xFF2C053E),
            primaryAccent = Color(0xFFFF007F),
            secondaryAccent = Color(0xFFFF375F),
            borderColor = Color(0xFFFF007F),
            borderWidthDp = 1.0f,
            isPremium = true
        ),
        "mint_fresh" to IslandThemeColors(
            id = "mint_fresh",
            displayName = "Mint Fresh",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF00F260), Color(0xFF0575E6)),
            cardBackground = Color(0xFF0A2B20),
            primaryAccent = Color(0xFF00F260),
            secondaryAccent = Color(0xFF0575E6),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "silver_lining" to IslandThemeColors(
            id = "silver_lining",
            displayName = "Silver Lining",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFDCDCDC), Color(0xFFE8E8E8)),
            cardBackground = Color(0xFFF2F2F7),
            primaryAccent = Color(0xFF8E8E93),
            secondaryAccent = Color(0xFF636366),
            borderColor = Color(0xFFB0B0B0),
            borderWidthDp = 0.5f,
            textColor = Color(0xFF1C1C1E)
        ),
        "carbon_fiber" to IslandThemeColors(
            id = "carbon_fiber",
            displayName = "Carbon Fiber",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF1A1A1A)),
            cardBackground = Color(0xFF262626),
            primaryAccent = Color(0xFF8E8E93),
            secondaryAccent = Color(0xFFD1D1D6),
            borderColor = Color(0xFF333333),
            borderWidthDp = 1.0f
        ),
        "electric_blue" to IslandThemeColors(
            id = "electric_blue",
            displayName = "Electric Blue",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF001F3F)),
            cardBackground = Color(0xFF082E59),
            primaryAccent = Color(0xFF0074D9),
            secondaryAccent = Color(0xFF00BCD4),
            borderColor = Color(0xFF0074D9),
            borderWidthDp = 2.0f
        ),
        "sweet_candy" to IslandThemeColors(
            id = "sweet_candy",
            displayName = "Sweet Candy",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFFBC5EC), Color(0xFFA6C1EE)),
            cardBackground = Color(0xFF361F38),
            primaryAccent = Color(0xFFBF5AF2),
            secondaryAccent = Color(0xFF64D2FF),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "northern_lights" to IslandThemeColors(
            id = "northern_lights",
            displayName = "Northern Lights",
            fillType = ThemeFillType.SWEEP_GRADIENT,
            backgroundColors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
            cardBackground = Color(0xFF0A2B28),
            primaryAccent = Color(0xFF00C9FF),
            secondaryAccent = Color(0xFF92FE9D),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "pure_white" to IslandThemeColors(
            id = "pure_white",
            displayName = "Pure White",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFFFFFFFF)),
            cardBackground = Color(0xFFF2F2F7),
            primaryAccent = Color(0xFF0A84FF),
            secondaryAccent = Color(0xFF30D158),
            borderColor = Color(0xFFD1D1D6),
            borderWidthDp = 0.5f,
            textColor = Color(0xFF1C1C1E)
        ),
        "vampire_red" to IslandThemeColors(
            id = "vampire_red",
            displayName = "Vampire Red",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF2A0808)),
            cardBackground = Color(0xFF3F0B0B),
            primaryAccent = Color(0xFFFF3B30),
            secondaryAccent = Color(0xFFFF0000),
            borderColor = Color(0xFFFF0000),
            borderWidthDp = 1.0f
        ),
        "emerald_city" to IslandThemeColors(
            id = "emerald_city",
            displayName = "Emerald City",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF004B23), Color(0xFF007200)),
            cardBackground = Color(0xFF052B14),
            primaryAccent = Color(0xFF30D158),
            secondaryAccent = Color(0xFF34C759),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "cosmic_void" to IslandThemeColors(
            id = "cosmic_void",
            displayName = "Cosmic Void",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF080014)),
            cardBackground = Color(0xFF16032B),
            primaryAccent = Color(0xFF8A2BE2),
            secondaryAccent = Color(0xFFBF5AF2),
            borderColor = Color(0xFF8A2BE2),
            borderWidthDp = 0.5f
        ),
        "rose_gold" to IslandThemeColors(
            id = "rose_gold",
            displayName = "Rose Gold",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFF7CAC9), Color(0xFFDEC2BA)),
            cardBackground = Color(0xFF3A2422),
            primaryAccent = Color(0xFFFF6482),
            secondaryAccent = Color(0xFFDEC2BA),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "ice_cold" to IslandThemeColors(
            id = "ice_cold",
            displayName = "Ice Cold",
            fillType = ThemeFillType.LINEAR_GRADIENT,
            backgroundColors = listOf(Color(0xFF74EBD5), Color(0xFF9FACE6), Color(0xFFFFFFFF)),
            cardBackground = Color(0xFF142B38),
            primaryAccent = Color(0xFF64D2FF),
            secondaryAccent = Color(0xFF74EBD5),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "lemon_twist" to IslandThemeColors(
            id = "lemon_twist",
            displayName = "Lemon Twist",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFFFE000), Color(0xFF799F0C)),
            cardBackground = Color(0xFF2E2B08),
            primaryAccent = Color(0xFFFFD60A),
            secondaryAccent = Color(0xFF799F0C),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "twilight" to IslandThemeColors(
            id = "twilight",
            displayName = "Twilight",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            cardBackground = Color(0xFF14222B),
            primaryAccent = Color(0xFF64D2FF),
            secondaryAccent = Color(0xFF0A84FF),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "hot_lava" to IslandThemeColors(
            id = "hot_lava",
            displayName = "Hot Lava",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFFF4E50), Color(0xFFF9D423)),
            cardBackground = Color(0xFF38140E),
            primaryAccent = Color(0xFFFF6600),
            secondaryAccent = Color(0xFFFF4500),
            borderColor = Color(0xFFFF6600),
            borderWidthDp = 1.0f,
            textColor = Color(0xFF1C1C1E),
            isPremium = true
        ),
        "sea_foam" to IslandThemeColors(
            id = "sea_foam",
            displayName = "Sea Foam",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF1E3F3B)),
            cardBackground = Color(0xFF264F4A),
            primaryAccent = Color(0xFF40826D),
            secondaryAccent = Color(0xFF5ED0B3),
            borderColor = Color(0xFF40826D),
            borderWidthDp = 1.0f
        ),
        "nebula" to IslandThemeColors(
            id = "nebula",
            displayName = "Nebula",
            fillType = ThemeFillType.RADIAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2)),
            cardBackground = Color(0xFF24064E),
            primaryAccent = Color(0xFFBF5AF2),
            secondaryAccent = Color(0xFF8E2DE2),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "bumblebee" to IslandThemeColors(
            id = "bumblebee",
            displayName = "Bumblebee",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFFFFCC00)),
            cardBackground = Color(0xFFE5B700),
            primaryAccent = Color(0xFF000000),
            secondaryAccent = Color(0xFF2C2C2E),
            borderColor = Color(0xFF000000),
            borderWidthDp = 2.0f,
            textColor = Color(0xFF000000)
        ),
        "slate" to IslandThemeColors(
            id = "slate",
            displayName = "Slate",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF2E3440)),
            cardBackground = Color(0xFF3B4252),
            primaryAccent = Color(0xFF88C0D0),
            secondaryAccent = Color(0xFF81A1C1),
            borderColor = Color(0xFF4C566A),
            borderWidthDp = 1.0f
        ),
        "rainbow" to IslandThemeColors(
            id = "rainbow",
            displayName = "Rainbow",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF)),
            cardBackground = Color(0xFF2C2C2E),
            primaryAccent = Color(0xFFFF375F),
            secondaryAccent = Color(0xFFBF5AF2),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "stormy_night" to IslandThemeColors(
            id = "stormy_night",
            displayName = "Stormy Night",
            fillType = ThemeFillType.LINEAR_GRADIENT,
            backgroundColors = listOf(Color(0xFF232526), Color(0xFF414345)),
            cardBackground = Color(0xFF2C2E30),
            primaryAccent = Color(0xFF8E8E93),
            secondaryAccent = Color(0xFFAEAEB2),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "bubblegum" to IslandThemeColors(
            id = "bubblegum",
            displayName = "Bubblegum",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFFFF69B4)),
            cardBackground = Color(0xFFD64D92),
            primaryAccent = Color(0xFFFF1493),
            secondaryAccent = Color(0xFFFF375F),
            borderColor = Color(0xFFFF1493),
            borderWidthDp = 1.5f,
            textColor = Color(0xFF1C1C1E)
        ),
        "toxic_green" to IslandThemeColors(
            id = "toxic_green",
            displayName = "Toxic Green",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF0D1F0D)),
            cardBackground = Color(0xFF153315),
            primaryAccent = Color(0xFF39FF14),
            secondaryAccent = Color(0xFF30D158),
            borderColor = Color(0xFF39FF14),
            borderWidthDp = 1.0f
        ),
        "ruby_red" to IslandThemeColors(
            id = "ruby_red",
            displayName = "Ruby Red",
            fillType = ThemeFillType.RADIAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF870000), Color(0xFF190A05)),
            cardBackground = Color(0xFF380808),
            primaryAccent = Color(0xFFFF3B30),
            secondaryAccent = Color(0xFFFF453A),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "deep_sea" to IslandThemeColors(
            id = "deep_sea",
            displayName = "Deep Sea",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF001122)),
            cardBackground = Color(0xFF08223E),
            primaryAccent = Color(0xFF007AFF),
            secondaryAccent = Color(0xFF0055B3),
            borderColor = Color(0xFF003366),
            borderWidthDp = 0.5f
        ),
        "pumpkin_spice" to IslandThemeColors(
            id = "pumpkin_spice",
            displayName = "Pumpkin",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFFD35400)),
            cardBackground = Color(0xFFB84500),
            primaryAccent = Color(0xFFFF9500),
            secondaryAccent = Color(0xFFFF6F00),
            borderColor = Color(0xFF873600),
            borderWidthDp = 1.0f
        ),
        "titanium" to IslandThemeColors(
            id = "titanium",
            displayName = "Titanium",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF3A3D40), Color(0xFF181719)),
            cardBackground = Color(0xFF26282B),
            primaryAccent = Color(0xFF8E8E93),
            secondaryAccent = Color(0xFFD1D1D6),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "insta_style" to IslandThemeColors(
            id = "insta_style",
            displayName = "Influencer",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045)),
            cardBackground = Color(0xFF320E1E),
            primaryAccent = Color(0xFFFD1D1D),
            secondaryAccent = Color(0xFFFCB045),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "glitch" to IslandThemeColors(
            id = "glitch",
            displayName = "Glitch",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF111122)),
            cardBackground = Color(0xFF1E1E3A),
            primaryAccent = Color(0xFF00FFFF),
            secondaryAccent = Color(0xFFFF007F),
            borderColor = Color(0xFF00FFFF),
            borderWidthDp = 1.0f
        ),
        "copper" to IslandThemeColors(
            id = "copper",
            displayName = "Copper",
            fillType = ThemeFillType.HORIZONTAL_GRADIENT,
            backgroundColors = listOf(Color(0xFFB87333), Color(0xFFE59866)),
            cardBackground = Color(0xFF3B2014),
            primaryAccent = Color(0xFFB87333),
            secondaryAccent = Color(0xFFE59866),
            borderColor = Color.Transparent,
            borderWidthDp = 0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "lavender" to IslandThemeColors(
            id = "lavender",
            displayName = "Lavender",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFFE6E6FA)),
            cardBackground = Color(0xFFD4D4F5),
            primaryAccent = Color(0xFF9370DB),
            secondaryAccent = Color(0xFF7B52C4),
            borderColor = Color(0xFF9370DB),
            borderWidthDp = 1.0f,
            textColor = Color(0xFF1C1C1E)
        ),
        "stealth" to IslandThemeColors(
            id = "stealth",
            displayName = "Stealth",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF1C1C1E)),
            cardBackground = Color(0xFF2C2C2E),
            primaryAccent = Color(0xFF8E8E93),
            secondaryAccent = Color(0xFF636366),
            borderColor = Color(0xFF2C2C2E),
            borderWidthDp = 0.5f
        ),
        "miami_vice" to IslandThemeColors(
            id = "miami_vice",
            displayName = "Miami Vice",
            fillType = ThemeFillType.SWEEP_GRADIENT,
            backgroundColors = listOf(Color(0xFF00F0FF), Color(0xFFFF007F)),
            cardBackground = Color(0xFF1B0F2A),
            primaryAccent = Color(0xFF00F0FF),
            secondaryAccent = Color(0xFFFF007F),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "miami_vice_real" to IslandThemeColors(
            id = "miami_vice_real",
            displayName = "Miami Vibes",
            fillType = ThemeFillType.SWEEP_GRADIENT,
            backgroundColors = listOf(Color(0xFFFF1493), Color(0xFF00CED1)),
            cardBackground = Color(0xFF251028),
            primaryAccent = Color(0xFFFF1493),
            secondaryAccent = Color(0xFF00CED1),
            borderColor = Color.Transparent,
            borderWidthDp = 0f
        ),
        "zen_garden" to IslandThemeColors(
            id = "zen_garden",
            displayName = "Zen Garden",
            fillType = ThemeFillType.SOLID,
            backgroundColors = listOf(Color(0xFF2D3A27)),
            cardBackground = Color(0xFF3B4C33),
            primaryAccent = Color(0xFF4B5320),
            secondaryAccent = Color(0xFF85994C),
            borderColor = Color(0xFF4B5320),
            borderWidthDp = 1.0f
        )
    )

    val CUSTOM_GRADIENT_PAIRS: List<Pair<Color, Color>> = listOf(
        Pair(Color(0xFFFF5733), Color(0xFFC70039)), // 01
        Pair(Color(0xFF6A0572), Color(0xFFAB83A1)), // 02
        Pair(Color(0xFF1B1464), Color(0xFF5C2E91)), // 03
        Pair(Color(0xFF009688), Color(0xFF4CAF50)), // 04
        Pair(Color(0xFFF44336), Color(0xFFE91E63)), // 05
        Pair(Color(0xFF3F51B5), Color(0xFF2196F3)), // 06
        Pair(Color(0xFFFFC107), Color(0xFFFF9800)), // 07
        Pair(Color(0xFF8BC34A), Color(0xFFCDDC39)), // 08
        Pair(Color(0xFF673AB7), Color(0xFF9C27B0)), // 09
        Pair(Color(0xFF795548), Color(0xFFFF5722)), // 10
        Pair(Color(0xFF00BCD4), Color(0xFF009688)), // 11
        Pair(Color(0xFFE91E63), Color(0xFFF06292)), // 12
        Pair(Color(0xFF3F51B5), Color(0xFF7986CB)), // 13
        Pair(Color(0xFFFFEB3B), Color(0xFFFFC107)), // 14
        Pair(Color(0xFFFF9800), Color(0xFFFF5722)), // 15
        Pair(Color(0xFFCDDC39), Color(0xFF8BC34A)), // 16
        Pair(Color(0xFF9E9E9E), Color(0xFF607D8B)), // 17
        Pair(Color(0xFF2196F3), Color(0xFF03A9F4)), // 18
        Pair(Color(0xFF4CAF50), Color(0xFF8BC34A)), // 19
        Pair(Color(0xFFB71C1C), Color(0xFFF44336))  // 20
    )

    fun getTheme(nameOrId: String): IslandThemeColors {
        val trimmed = nameOrId.trim()
        val normalized = trimmed.lowercase().replace(" ", "_")
        THEMES[normalized]?.let { return it }

        THEMES.values.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }?.let { return it }

        when (trimmed) {
            "Obsidian Black" -> return THEMES["classic_black"]!!
            "Cyber Cyan" -> return THEMES["neon_cyber"]!!
            "Emerald Matrix", "Neon Green" -> return THEMES["matrix"]!!
            "Material Purple" -> return THEMES["amethyst"]!!
        }

        if (normalized.startsWith("gradient_") || normalized.startsWith("pair_")) {
            val numStr = normalized.substringAfter("_")
            val index = (numStr.toIntOrNull() ?: 1) - 1
            if (index in CUSTOM_GRADIENT_PAIRS.indices) {
                val pair = CUSTOM_GRADIENT_PAIRS[index]
                return IslandThemeColors(
                    id = "custom_gradient_${index + 1}",
                    displayName = "Gradient ${index + 1}",
                    fillType = ThemeFillType.LINEAR_GRADIENT,
                    backgroundColors = listOf(pair.first, pair.second),
                    primaryAccent = pair.first,
                    secondaryAccent = pair.second
                )
            }
        }

        return THEMES["classic_black"] ?: IslandThemeColors()
    }
}

enum class IslandCompactKind {
    MEDIA, TIMER, TRANSFER, NOTIFICATION, CHARGING, DROP, REEL_COUNTER, LOCK, VOICE_ASSISTANT, IDLE
}

/** Small 4-bar equalizer in the SF Symbols waveform spirit. */
@Composable
fun AppleEqualizer(
    isPlaying: Boolean,
    color: Color = Apple.systemGreen,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "apple-eq")
    val finals = listOf(0.42f, 0.92f, 0.58f, 1f)
    val bars = finals.mapIndexed { index, factor ->
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = if (isPlaying) factor else 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isPlaying) 820 + index * 120 else 180,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar-$index"
        )
    }

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { value ->
            val barHeight = (height * value.value).coerceAtLeast(3.dp)
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.25.dp))
                    .background(color)
            )
        }
    }
}

/** 22dp artwork for the compact wings. */
@Composable
fun AppleCompactArtwork(track: MediaTrackInfo) {
    val size = AppleIslandSpec.compactArtwork
    val shape = RoundedCornerShape(6.5.dp)
    if (track.albumArt != null) {
        Image(
            bitmap = track.albumArt.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Apple.systemPink, Color(0xFFB3123F))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            GloomIcon(GloomIcons.MusicNote, Color.White, size * 0.58f)
        }
    }
}

/**
 * Apple's compact presentation: leading and trailing elements on either side
 * of the camera band, with no padding between content and the camera.
 */
@Composable
fun AppleCompactContent(
    kind: IslandCompactKind,
    track: MediaTrackInfo,
    transfer: ActiveTransferInfo,
    notification: NotificationIslandEvent?,
    batteryLevel: Int,
    isCharging: Boolean,
    timerText: String,
    wingWidth: Dp,
    cameraGap: Dp,
    inset: Dp,
    reelCount: Int = 0,
    lockEvent: LockIslandEvent? = null,
    modifier: Modifier = Modifier
) {
    val contentTransition = (
        fadeIn(tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = spring(1f, 320f))
        ).togetherWith(fadeOut(tween(100)))

    val timeText = remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val fmt = java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault())
        while (true) {
            timeText.value = fmt.format(java.util.Date())
            delay(10000)
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(wingWidth)
                .fillMaxHeight()
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.padding(start = inset)) {
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = { contentTransition },
                    label = "compactLeading"
                ) { state ->
                    when (state) {
                        IslandCompactKind.MEDIA -> AppleCompactArtwork(track)
                        IslandCompactKind.TIMER -> CompactGlyphCircle(
                            icon = GloomIcons.Timer,
                            tint = Apple.systemOrange,
                            background = Apple.systemOrange.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.TRANSFER -> CompactGlyphCircle(
                            icon = if (transfer.isUpload) GloomIcons.Beam else GloomIcons.DownloadDoc,
                            tint = Apple.systemBlue,
                            background = Apple.systemBlue.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.NOTIFICATION -> {
                            if (notification?.isCall == true) {
                                CompactGlyphCircle(
                                    icon = GloomIcons.Phone,
                                    tint = Apple.systemGreen,
                                    background = Apple.systemGreen.copy(alpha = 0.25f)
                                )
                            } else {
                                val notifStyle = NotificationStyleRegistry.getStyleForPackage(notification?.packageName)
                                val notifAccent = notifStyle.accentColor
                                val context = LocalContext.current
                                val officialAppIcon = notification?.appIcon ?: remember(notification?.packageName) {
                                    if (!notification?.packageName.isNullOrBlank()) {
                                        try {
                                            val d = context.packageManager.getApplicationIcon(notification!!.packageName)
                                            if (d is BitmapDrawable && d.bitmap != null) d.bitmap
                                            else {
                                                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 48
                                                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 48
                                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                val c = android.graphics.Canvas(b)
                                                d.setBounds(0, 0, c.width, c.height)
                                                d.draw(c)
                                                b
                                            }
                                        } catch (_: Exception) { null }
                                    } else null
                                }

                                if (officialAppIcon != null) {
                                    Image(
                                        bitmap = officialAppIcon.asImageBitmap(),
                                        contentDescription = "Official App Icon",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(AppleIslandSpec.compactArtwork)
                                            .clip(CircleShape)
                                            .border(0.5.dp, notifAccent.copy(alpha = 0.5f), CircleShape)
                                    )
                                } else if (notification?.largeIcon != null) {
                                    Image(
                                        bitmap = notification.largeIcon.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(AppleIslandSpec.compactArtwork)
                                            .clip(CircleShape)
                                            .border(0.5.dp, notifAccent.copy(alpha = 0.5f), CircleShape)
                                    )
                                } else {
                                    CompactGlyphCircle(
                                        icon = GloomIcons.Bell,
                                        tint = notifAccent,
                                        background = notifAccent.copy(alpha = 0.18f)
                                    )
                                }
                            }
                        }
                        IslandCompactKind.CHARGING -> GloomIcon(
                            icon = GloomIcons.BoltFill,
                            tint = Apple.systemGreen,
                            size = 16.dp
                        )
                        IslandCompactKind.DROP -> CompactGlyphCircle(
                            icon = GloomIcons.Beam,
                            tint = Apple.systemBlue,
                            background = Apple.systemBlue.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.REEL_COUNTER -> CompactGlyphCircle(
                            icon = GloomIcons.Instagram,
                            tint = Color.White,
                            background = Color(0xFFE1306C)
                        )
                        IslandCompactKind.LOCK -> {
                            val unlocked = lockEvent?.isUnlocked == true
                            AppleAnimatedLockIcon(
                                isUnlocked = unlocked,
                                size = 16.dp,
                                tint = if (unlocked) Apple.systemGreen else Color.White
                            )
                        }
                        IslandCompactKind.VOICE_ASSISTANT -> CompactGlyphCircle(
                            icon = GloomIcons.Sparkles,
                            tint = Apple.systemPurple,
                            background = Apple.systemPurple.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.IDLE -> {
                            if (com.gloom.island.settings.GloomIslandSettings.instance.showIdleStatus && timeText.value.isNotEmpty()) {
                                Text(
                                    text = timeText.value,
                                    style = IslandType.compactNumber.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 1
                                )
                            } else {
                                Spacer(Modifier.size(0.dp))
                            }
                        }
                    }
                }
            }
        }

        // TrueDepth camera band. Never place content here.
        Spacer(Modifier.width(cameraGap).height(1.dp))

        Box(
            modifier = Modifier
                .width(wingWidth)
                .fillMaxHeight()
                .clipToBounds(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(modifier = Modifier.padding(end = inset)) {
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = { contentTransition },
                    label = "compactTrailing"
                ) { state ->
                    when (state) {
                        IslandCompactKind.MEDIA -> if (track.isPlaying) {
                            AppleEqualizer(isPlaying = true, height = 13.dp)
                        } else {
                            GloomIcon(GloomIcons.Waveform, Apple.secondaryLabel, 15.dp)
                        }
                        IslandCompactKind.TIMER -> Text(
                            text = timerText,
                            style = IslandType.compactNumber,
                            color = Apple.systemOrange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IslandCompactKind.TRANSFER -> Text(
                            text = "${(transfer.progress * 100).toInt()}%",
                            style = IslandType.compactNumber,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IslandCompactKind.NOTIFICATION -> {
                            if (notification?.isCall == true) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    GloomIcon(GloomIcons.Phone, Apple.systemGreen, 13.dp)
                                    Text(
                                        "Call",
                                        style = IslandType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        color = Apple.systemGreen
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Apple.systemBlue)
                                )
                            }
                        }
                        IslandCompactKind.CHARGING -> Text(
                            text = "$batteryLevel%",
                            style = IslandType.compactNumber,
                            color = Apple.systemGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IslandCompactKind.DROP -> Text(
                            text = "Beam",
                            style = IslandType.compactNumber.copy(fontSize = 12.sp),
                            color = Apple.systemBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IslandCompactKind.REEL_COUNTER -> Text(
                            text = "$reelCount",
                            style = IslandType.compactNumber,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IslandCompactKind.LOCK -> {
                            val unlocked = lockEvent?.isUnlocked == true
                            val density = LocalDensity.current
                            val fontScale = density.fontScale.coerceAtLeast(0.5f)
                            // Scale-independent font units ensure "Unlocked" never clips or ellipsizes at system font scale 1.0, 1.3, or 2.0
                            val lockFontSize = (9.5f / fontScale).sp
                            val lockLetterSpacing = (-0.35f / fontScale).sp
                            Text(
                                text = if (unlocked) "Unlocked" else "Locked",
                                style = IslandType.compactNumber.copy(
                                    fontSize = lockFontSize,
                                    letterSpacing = lockLetterSpacing
                                ),
                                color = if (unlocked) Apple.systemGreen else Apple.secondaryLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = (wingWidth - inset).coerceAtLeast(20.dp))
                            )
                        }
                        IslandCompactKind.VOICE_ASSISTANT -> AppleEqualizer(
                            isPlaying = true,
                            color = Apple.systemPurple,
                            height = 13.dp
                        )
                        IslandCompactKind.IDLE -> {
                            if (com.gloom.island.settings.GloomIslandSettings.instance.showIdleStatus) {
                                Text(
                                    text = "$batteryLevel%",
                                    style = IslandType.compactNumber.copy(fontSize = 11.sp),
                                    color = if (isCharging) Apple.systemGreen else Apple.secondaryLabel,
                                    maxLines = 1
                                )
                            } else {
                                Spacer(Modifier.size(0.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactGlyphCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color
) {
    Box(
        modifier = Modifier
            .size(AppleIslandSpec.compactArtwork)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        GloomIcon(icon, tint, 13.dp)
    }
}

/**
 * Apple's expanded music Live Activity, 400 x 180.
 */
@Composable
fun AppleMusicActivity(
    track: MediaTrackInfo,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: ((Float) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 32.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val artworkShape = RoundedCornerShape(10.dp)
            if (track.albumArt != null) {
                Image(
                    bitmap = track.albumArt.asImageBitmap(),
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppleIslandSpec.artwork)
                        .clip(artworkShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(AppleIslandSpec.artwork)
                        .clip(artworkShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Apple.systemPink, Color(0xFFB3123F))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    GloomIcon(GloomIcons.MusicNote, Color.White, 18.dp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { "Nothing Playing" },
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Start playback in any app" },
                    style = IslandType.subtitle,
                    color = Apple.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AppleEqualizer(
                isPlaying = track.isPlaying,
                color = if (track.isPlaying) Apple.systemGreen else Apple.tertiaryLabel,
                height = 14.dp
            )
            ActivityCloseButton(onClose)
        }

        Spacer(Modifier.height(6.dp))

        AppleSeekBar(
            progress = track.progress,
            positionMs = track.positionMs,
            durationMs = track.durationMs,
            onSeek = onSeek
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ActivityTransportButton(
                label = "Previous track",
                onClick = onPrev
            ) {
                GloomIcon(GloomIcons.SkipBackFill, Color.White, 19.dp)
            }
            Spacer(Modifier.width(26.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                GloomIcon(
                    icon = if (track.isPlaying) GloomIcons.PauseFill else GloomIcons.PlayFill,
                    tint = Color.Black,
                    size = 18.dp
                )
            }
            Spacer(Modifier.width(26.dp))
            ActivityTransportButton(
                label = "Next track",
                onClick = onNext
            ) {
                GloomIcon(GloomIcons.SkipForwardFill, Color.White, 19.dp)
            }
        }
    }
}

/**
 * Apple's expanded transfer Live Activity.
 */
@Composable
fun AppleTransferActivity(
    transfer: ActiveTransferInfo,
    accentColor: Color,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val done = transfer.progress >= 1f || transfer.speedMbps.equals("Completed", ignoreCase = true)
    val failed = transfer.speedMbps.startsWith("Failed", ignoreCase = true)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 34.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(AppleIslandSpec.artwork)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (failed) Apple.systemRed.copy(alpha = 0.18f)
                        else if (done) Apple.systemGreen.copy(alpha = 0.18f)
                        else Apple.systemBlue.copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GloomIcon(
                    icon = if (failed) GloomIcons.XCircleFill
                    else if (done) GloomIcons.CheckCircleFill
                    else if (transfer.isUpload) GloomIcons.Beam else GloomIcons.DownloadDoc,
                    tint = if (failed) Apple.systemRed else if (done) Apple.systemGreen else Apple.systemBlue,
                    size = 22.dp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.fileName.ifBlank { "Transfer" },
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (failed) "Transfer failed"
                    else if (done) "Transfer complete"
                    else if (transfer.isUpload) "Sending to GLOOM PC" else "Receiving from GLOOM PC",
                    style = IslandType.subtitle,
                    color = if (failed) Apple.systemRed else Apple.secondaryLabel,
                    maxLines = 1
                )
            }

            Text(
                text = if (failed) "Failed" else if (done) "Complete" else transfer.speedMbps,
                style = IslandType.time,
                color = if (failed) Apple.systemRed else if (done) Apple.systemGreen else Apple.secondaryLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1FFFFFFF))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            ActivityCloseButton(onClose)
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x24FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(transfer.progress.coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (failed) Apple.systemRed else if (done) Apple.systemGreen else accentColor)
            )
        }

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "%.1f MB of %.1f MB",
                    transfer.fileSizeMb * transfer.progress,
                    transfer.fileSizeMb
                ),
                style = IslandType.time,
                color = Apple.secondaryLabel
            )
            Text(
                text = "${(transfer.progress * 100).toInt()}%",
                style = IslandType.time,
                color = if (done) Apple.systemGreen else Apple.secondaryLabel
            )
        }
    }
}

/**
 * Apple's expanded notification presentation with per-app styling, distinct cards, and quick actions.
 */
@Composable
fun AppleNotificationActivity(
    event: NotificationIslandEvent,
    accentColor: Color,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit = {},
    onAnswerCall: (Boolean) -> Unit = {},
    onDeclineCall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }
    val appStyle = remember(event.packageName) {
        NotificationStyleRegistry.getStyleForPackage(event.packageName)
    }
    val effectiveAccent = appStyle.accentColor
    var copied by remember(event.id) { mutableStateOf(false) }
    var replyText by remember(event.id) { mutableStateOf("") }
    var replySent by remember(event.id) { mutableStateOf(false) }
    var replyError by remember(event.id) { mutableStateOf(false) }
    val otpCode = event.otpCode
    val replyAction = event.replyAction

    val isKeyguardLocked = remember {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.isKeyguardLocked == true
    }

    val appVectorIcon = remember(event.packageName) {
        val pkg = event.packageName.lowercase()
        when {
            pkg.contains("instagram") -> GloomIcons.Instagram
            pkg.contains("whatsapp") -> GloomIcons.WhatsApp
            pkg.contains("snapchat") -> GloomIcons.Snapchat
            pkg.contains("gm") || pkg.contains("gmail") -> GloomIcons.Mail
            pkg.contains("outlook") -> GloomIcons.Mail
            pkg.contains("telegram") -> GloomIcons.PaperPlane
            pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms") -> GloomIcons.MessageBubble
            else -> GloomIcons.Bell
        }
    }

    fun copyCode() {
        val code = otpCode ?: return
        clipboard.setText(AnnotatedString(code))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        copied = true
    }

    androidx.compose.runtime.LaunchedEffect(replySent) {
        if (replySent) {
            onInputFocusChanged(false)
            delay(900)
            onDismiss()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 28.dp,
                bottom = 8.dp
            )
    ) {
        // Tapping the upper card section instantly opens the app
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onInputFocusChanged(false)
                    onOpen()
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val officialAppIcon = event.appIcon ?: remember(event.packageName) {
                    if (event.packageName.isNotBlank()) {
                        try {
                            val d = context.packageManager.getApplicationIcon(event.packageName)
                            if (d is BitmapDrawable && d.bitmap != null) d.bitmap
                            else {
                                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 48
                                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 48
                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val c = android.graphics.Canvas(b)
                                d.setBounds(0, 0, c.width, c.height)
                                d.draw(c)
                                b
                            }
                        } catch (_: Exception) { null }
                    } else null
                }

                if (event.largeIcon != null) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Image(
                            bitmap = event.largeIcon.asImageBitmap(),
                            contentDescription = "Sender avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(AppleIslandSpec.artwork)
                                .clip(CircleShape)
                                .border(1.5.dp, effectiveAccent.copy(alpha = 0.6f), CircleShape)
                        )
                        if (officialAppIcon != null) {
                            Image(
                                bitmap = officialAppIcon.asImageBitmap(),
                                contentDescription = "Official App Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.Black, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(effectiveAccent)
                                    .border(1.dp, Color.Black, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                GloomIcon(appVectorIcon, Color.White, 9.dp)
                            }
                        }
                    }
                } else if (officialAppIcon != null) {
                    Image(
                        bitmap = officialAppIcon.asImageBitmap(),
                        contentDescription = "Official App Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(AppleIslandSpec.artwork)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(AppleIslandSpec.artwork)
                            .clip(CircleShape)
                            .background(effectiveAccent.copy(alpha = 0.22f))
                            .border(1.5.dp, effectiveAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        GloomIcon(appVectorIcon, effectiveAccent, 22.dp)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.appName.ifBlank { appStyle.brandName }.uppercase(),
                            style = IslandType.caption.copy(letterSpacing = 1.sp),
                            color = effectiveAccent,
                            maxLines = 1
                        )
                        if (event.conversationTitle.isNotBlank() && event.conversationTitle != event.title) {
                            Text(
                                text = "• ${event.conversationTitle}",
                                style = IslandType.caption,
                                color = Apple.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = event.title.ifBlank { "New message" },
                        style = IslandType.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ActivityCloseButton {
                    onInputFocusChanged(false)
                    onDismiss()
                }
            }

            Spacer(Modifier.height(6.dp))

            // App-specific body rendering
            when (appStyle.layoutKind) {
                NotificationLayoutKind.EMAIL -> {
                    // Email tailored layout (Gmail / Outlook)
                    if (event.subText.isNotBlank()) {
                        Text(
                            text = event.subText,
                            style = IslandType.caption.copy(color = effectiveAccent.copy(alpha = 0.9f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = event.text,
                        style = IslandType.subtitle.copy(lineHeight = 15.sp),
                        color = Apple.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                NotificationLayoutKind.SOCIAL -> {
                    // Social tailored layout (Instagram / Snapchat)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(effectiveAccent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = event.text,
                            style = IslandType.subtitle.copy(lineHeight = 15.sp),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> {
                    // Messaging (WhatsApp, Telegram, SMS) & Standard
                    if (otpCode != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1FFFFFFF))
                                .clickable { copyCode() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = otpCode,
                                style = IslandType.title.copy(letterSpacing = 3.sp),
                                color = Color.White
                            )
                            Text(
                                text = if (copied) "Copied" else "Copy",
                                style = IslandType.caption,
                                color = if (copied) Apple.systemGreen else effectiveAccent
                            )
                        }
                    } else {
                        Text(
                            text = event.text,
                            style = IslandType.subtitle.copy(lineHeight = 15.sp),
                            color = Apple.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Direct Inline Reply / Actions section
        if (event.isCall) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Decline (Red)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Apple.systemRed)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onInputFocusChanged(false)
                            onDeclineCall()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GloomIcon(GloomIcons.PhoneDown, Color.White, 16.dp)
                        Text(
                            text = "Decline",
                            style = IslandType.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                // 2. Speaker (Blue)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Apple.systemBlue)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onInputFocusChanged(false)
                            onAnswerCall(true)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GloomIcon(GloomIcons.Speaker, Color.White, 16.dp)
                        Text(
                            text = "Speaker",
                            style = IslandType.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                // 3. Answer (Green)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Apple.systemGreen)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onInputFocusChanged(false)
                            onAnswerCall(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GloomIcon(GloomIcons.Phone, Color.White, 16.dp)
                        Text(
                            text = "Answer",
                            style = IslandType.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }
        } else if (replySent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2EFFFFFF)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GloomIcon(GloomIcons.CheckCircleFill, Apple.systemGreen, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Reply Sent", style = IslandType.caption, color = Color.White)
            }
        } else if (replyError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Apple.systemRed.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GloomIcon(GloomIcons.XCircleFill, Apple.systemRed, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Could not send reply", style = IslandType.caption, color = Color.White)
                }
                Text(
                    text = "Open App",
                    style = IslandType.caption,
                    color = effectiveAccent,
                    modifier = Modifier.clickable {
                        onInputFocusChanged(false)
                        onOpen()
                    }
                )
            }
        } else if (otpCode != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppleActivityButton(
                    text = if (copied) "Copied" else "Copy Code",
                    background = effectiveAccent,
                    foreground = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { copyCode() }
                )
                AppleActivityButton(
                    text = appStyle.openActionLabel,
                    background = Color(0x24FFFFFF),
                    foreground = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onInputFocusChanged(false)
                        onOpen()
                    }
                )
            }
        } else if (replyAction != null) {
            if (isKeyguardLocked) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppleActivityButton(
                        text = "Unlock to Reply",
                        background = effectiveAccent,
                        foreground = Color.White,
                        modifier = Modifier.weight(1.2f),
                        onClick = {
                            onInputFocusChanged(false)
                            onOpen()
                        }
                    )
                    AppleActivityButton(
                        text = "Dismiss",
                        background = Color(0x24FFFFFF),
                        foreground = Color.White,
                        modifier = Modifier.weight(0.8f),
                        onClick = {
                            onInputFocusChanged(false)
                            onDismiss()
                        }
                    )
                }
            } else {
                // Direct inline reply field visible right below the notification text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0x2EFFFFFF))
                        .border(
                            width = if (isInputFocused) 1.5.dp else 1.dp,
                            color = if (isInputFocused) effectiveAccent else effectiveAccent.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(19.dp)
                        )
                        .clickable {
                            isInputFocused = true
                            onInputFocusChanged(true)
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(80)
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = replyText,
                        onValueChange = {
                            replyText = it
                            if (!isInputFocused) {
                                isInputFocused = true
                                onInputFocusChanged(true)
                            }
                        },
                        textStyle = IslandType.subtitle.copy(color = Color.White),
                        cursorBrush = SolidColor(effectiveAccent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Text
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (replyText.isNotBlank()) {
                                    val ok = replyAction.send(context, replyText)
                                    if (ok) {
                                        replySent = true
                                        replyError = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isInputFocused = false
                                        onInputFocusChanged(false)
                                    } else {
                                        replyError = true
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    isInputFocused = true
                                    onInputFocusChanged(true)
                                }
                            },
                        decorationBox = { innerTextField ->
                            if (replyText.isEmpty()) {
                                Text(
                                    text = "Reply to ${event.title.take(14)}...",
                                    style = IslandType.caption,
                                    color = Apple.secondaryLabel
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (replyText.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(effectiveAccent)
                                .clickable {
                                    val ok = replyAction.send(context, replyText)
                                    if (ok) {
                                        replySent = true
                                        replyError = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isInputFocused = false
                                        onInputFocusChanged(false)
                                    } else {
                                        replyError = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GloomIcon(GloomIcons.ChevronUp, Color.White, 16.dp)
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppleActivityButton(
                    text = appStyle.openActionLabel,
                    background = effectiveAccent,
                    foreground = Color.White,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        onInputFocusChanged(false)
                        onOpen()
                    }
                )
                AppleActivityButton(
                    text = "Dismiss",
                    background = Color(0x24FFFFFF),
                    foreground = Color.White,
                    modifier = Modifier.weight(0.8f),
                    onClick = {
                        onInputFocusChanged(false)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Short-lived Apple Dynamic Island Hardware HUD (Ringer switch, Charging liquid fill, Bluetooth connect).
 * The center camera gap is permanently protected as an empty spacer so no text or glyphs overflow the punch hole.
 */
@Composable
fun AppleSystemAlertActivity(
    event: SystemAlertIslandEvent,
    onDismiss: () -> Unit,
    cameraGap: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    androidx.compose.runtime.LaunchedEffect(event.timestampMs) {
        val duration = if (event.type == SystemAlertIslandEvent.AlertType.CHARGING) 3000L
        else if (event.type == SystemAlertIslandEvent.AlertType.VOLUME) 2000L
        else 2500L
        delay(duration)
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Wing (Left of camera cutout)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                when (event.type) {
                    SystemAlertIslandEvent.AlertType.RINGER -> {
                        val isSilent = event.isSilent
                        val bellRotation by animateFloatAsState(
                            targetValue = if (isSilent) -12f else 12f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                            label = "bellRotation"
                        )
                        val accent = if (isSilent) Apple.systemRed else Apple.systemOrange

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            GloomIcon(
                                if (isSilent) GloomIcons.BellSlash else GloomIcons.Bell,
                                accent,
                                17.dp,
                                modifier = Modifier.graphicsLayer { rotationZ = bellRotation }
                            )
                        }
                    }

                    SystemAlertIslandEvent.AlertType.CHARGING -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Apple.systemGreen.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                GloomIcon(
                                    GloomIcons.BoltFill,
                                    Apple.systemGreen,
                                    15.dp,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                )
                            }
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "${event.level}%",
                                    style = IslandType.compactNumber.copy(fontSize = 13.sp),
                                    color = Apple.systemGreen
                                )
                                if (event.subtitle.isNotEmpty() && event.subtitle != "Charging" && event.subtitle != "Unplugged") {
                                    Text(
                                        text = event.subtitle,
                                        style = IslandType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                        color = Apple.systemGreen.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    SystemAlertIslandEvent.AlertType.BLUETOOTH -> {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Apple.systemCyan.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            GloomIcon(
                                GloomIcons.Headphones,
                                Apple.systemCyan,
                                16.dp
                            )
                        }
                    }

                    SystemAlertIslandEvent.AlertType.VOLUME -> {
                        val isMuted = event.isSilent || event.level == 0
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color(0x33FF453A) else Color(0x330A84FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            GloomIcon(
                                GloomIcons.Speaker,
                                if (isMuted) Apple.systemRed else Apple.systemBlue,
                                16.dp
                            )
                        }
                    }

                    else -> Spacer(Modifier.size(0.dp))
                }
            }

            // Camera Cutout Band - GUARANTEED 100% CLEAR (NO words, NO content)
            Spacer(Modifier.width(cameraGap).height(1.dp))

            // Trailing Wing (Right of camera cutout)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd
            ) {
                when (event.type) {
                    SystemAlertIslandEvent.AlertType.RINGER -> {
                        val isSilent = event.isSilent
                        val accent = if (isSilent) Apple.systemRed else Apple.systemOrange
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSilent) "SILENT" else "RING",
                                style = IslandType.caption.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                                color = accent
                            )
                        }
                    }

                    SystemAlertIslandEvent.AlertType.CHARGING -> {
                        val fillRatio = (event.level / 100f).coerceIn(0f, 1f)
                        val animatedFill by animateFloatAsState(
                            targetValue = fillRatio,
                            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            label = "batteryFill"
                        )
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x28FFFFFF))
                                .border(1.dp, Apple.systemGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedFill)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF28CD41),
                                                Color(0xFF34C759),
                                                Color(0xFF30D158)
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    SystemAlertIslandEvent.AlertType.BLUETOOTH -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Apple.systemCyan.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (event.isConnected) "CONNECTED" else "DISCONNECTED",
                                style = IslandType.caption.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold),
                                color = if (event.isConnected) Apple.systemCyan else Apple.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    SystemAlertIslandEvent.AlertType.VOLUME -> {
                        val fillRatio = (event.level / 100f).coerceIn(0f, 1f)
                        val animatedFill by animateFloatAsState(
                            targetValue = fillRatio,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                            label = "volumeFill"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0x28FFFFFF))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedFill)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Apple.systemBlue,
                                                    Color(0xFF64D2FF)
                                                )
                                            )
                                        )
                                )
                            }
                            Text(
                                text = "${event.level}%",
                                style = IslandType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    else -> Spacer(Modifier.size(0.dp))
                }
            }
        }
    }
}

/**
 * Category B Live Activity: Apple Timer presentation with circular countdown ring and transport controls.
 */
@Composable
fun AppleTimerActivity(
    timer: TimerIslandEvent,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val progress = timer.progress

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 28.dp,
                bottom = 14.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Timer title and digital readout
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timer.title.ifBlank { "Timer" },
                style = IslandType.caption,
                color = Apple.secondaryLabel
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = timer.formattedTime,
                style = IslandType.compactNumber.copy(fontSize = 38.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
                color = if (timer.isPaused) Apple.secondaryLabel else Apple.systemOrange
            )
        }

        // Right Section: Progress Ring + Control Buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    drawCircle(
                        color = Apple.systemOrange.copy(alpha = 0.25f),
                        style = Stroke(stroke)
                    )
                    drawArc(
                        color = Apple.systemOrange,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                GloomIcon(
                    icon = if (timer.isPaused) GloomIcons.PlayFill else GloomIcons.Timer,
                    tint = Apple.systemOrange,
                    size = 20.dp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Apple.fillSecondary)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCancel()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    GloomIcon(GloomIcons.Close, Color.White, 14.dp)
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (timer.isPaused) Apple.systemOrange else Apple.systemOrange.copy(alpha = 0.35f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPauseResume()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    GloomIcon(
                        icon = if (timer.isPaused) GloomIcons.PlayFill else GloomIcons.PauseFill,
                        tint = if (timer.isPaused) Color.Black else Apple.systemOrange,
                        size = 14.dp
                    )
                }
            }
        }
    }
}

/**
 * Expanded continuity card used when no Live Activity is active.
 */
@Composable
fun AppleContinuityActivity(
    device: BridgeDeviceInfo,
    batteryLevel: Int,
    isCharging: Boolean,
    accentColor: Color,
    secondaryColor: Color,
    onBeamFile: () -> Unit,
    onSyncClipboard: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 28.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(AppleIslandSpec.artwork)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                GloomIcon(GloomIcons.Laptop, Color.White, 22.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (device.isConnected) "GLOOM Ecosystem" else "GLOOM Standby",
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = if (device.isConnected) "Live Continuity Active" else "Ready to Connect",
                    style = IslandType.subtitle,
                    color = Apple.secondaryLabel,
                    maxLines = 1
                )
            }
            ActivityCloseButton(onClose)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0x10FFFFFF))
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCharging) Apple.systemGreen
                            else if (device.isConnected) secondaryColor
                            else Apple.systemRed
                        )
                )
                Text(
                    text = if (isCharging) "Charging · $batteryLevel%"
                    else if (device.isConnected) "High-Speed Link" else "Local Wireless Standby",
                    style = IslandType.subtitle.copy(fontSize = 12.sp),
                    color = Apple.secondaryLabel,
                    maxLines = 1
                )
            }
            Text(
                text = if (device.isConnected) "Synced" else "Ready",
                style = IslandType.time,
                color = if (device.isConnected) Apple.systemGreen else Apple.tertiaryLabel
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContinuityButton(
                icon = GloomIcons.Beam,
                label = "File Sharing",
                modifier = Modifier.weight(1f),
                onClick = onBeamFile
            )
            ContinuityButton(
                icon = GloomIcons.Clipboard,
                label = "Clipboard",
                modifier = Modifier.weight(1f),
                onClick = onSyncClipboard
            )
        }
    }
}

@Composable
private fun ContinuityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GloomIcon(icon, Color.White, 14.dp)
            Text(
                text = label,
                style = IslandType.button.copy(fontSize = 11.5.sp),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AppleSeekBar(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "seek-progress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .pointerInput(onSeek) {
                    detectTapGestures { offset ->
                        onSeek?.invoke((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(onSeek) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onSeek?.invoke((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            onSeek?.invoke((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                val stroke = 4.dp.toPx()
                drawLine(
                    color = Color(0x29FFFFFF),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                val x = size.width * animatedProgress
                if (x > 0f) {
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, midY),
                        end = Offset(x, midY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatClock(positionMs),
                style = IslandType.time,
                color = Apple.tertiaryLabel
            )
            Text(
                text = if (durationMs > 0) "-${formatClock((durationMs - positionMs).coerceAtLeast(0))}" else "--:--",
                style = IslandType.time,
                color = Apple.tertiaryLabel
            )
        }
    }
}

@Composable
private fun ActivityTransportButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semanticsLabel(label),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ActivityCloseButton(onClose: (() -> Unit)?) {
    if (onClose == null) return
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        GloomIcon(GloomIcons.Close, Apple.secondaryLabel, 10.dp)
    }
}

@Composable
fun AppleActivityButton(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = IslandType.button, color = foreground, maxLines = 1)
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(
        Modifier.semantics { contentDescription = label }
    )

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

@Composable
fun LockScreenSiriTrigger(
    onOpenSiri: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "lockSiriScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color(0xE6121214))
                .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onOpenSiri
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MiniSiriOrb(modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Unlock",
                style = AppleType.caption1,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun MiniSiriOrb(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "miniSiri")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "miniRotation"
    )
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.45f
        val blue = Color(0xFF0A84FF)
        val purple = Color(0xFFBF5AF2)
        val pink = Color(0xFFFF375F)

        fun blob(color: Color, angleDeg: Float, distance: Float, blobRadius: Float, alpha: Float) {
            val angle = Math.toRadians((angleDeg + rotation).toDouble())
            val offset = Offset(
                center.x + (Math.cos(angle) * distance).toFloat(),
                center.y + (Math.sin(angle) * distance).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = offset,
                    radius = blobRadius
                ),
                radius = blobRadius,
                center = offset
            )
        }
        blob(blue, 0f, radius * 0.25f, radius * 0.9f, 0.8f)
        blob(purple, 120f, radius * 0.25f, radius * 0.85f, 0.7f)
        blob(pink, 240f, radius * 0.25f, radius * 0.85f, 0.65f)
    }
}
