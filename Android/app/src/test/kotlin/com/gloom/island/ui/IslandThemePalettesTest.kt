package com.gloom.island.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandThemePalettesTest {

    @Test
    fun resolvesClassicBlackDefaultTheme() {
        val theme = IslandThemePalettes.getTheme("classic_black")
        assertEquals("classic_black", theme.id)
        assertEquals("Classic Black", theme.displayName)
        assertEquals(ThemeFillType.SOLID, theme.fillType)
        assertEquals(Color(0xFF000000), theme.backgroundColors.first())
        assertEquals(0f, theme.borderWidthDp, 0.001f)
    }

    @Test
    fun resolvesLegacyAliases() {
        val obsidian = IslandThemePalettes.getTheme("Obsidian Black")
        assertEquals("classic_black", obsidian.id)

        val cyber = IslandThemePalettes.getTheme("Cyber Cyan")
        assertEquals("neon_cyber", cyber.id)
        assertEquals(1.0f, cyber.borderWidthDp, 0.001f)
        assertEquals(Color(0xFF00FFCC), cyber.borderColor)

        val matrix = IslandThemePalettes.getTheme("Emerald Matrix")
        assertEquals("matrix", matrix.id)
        assertEquals(1.5f, matrix.borderWidthDp, 0.001f)
        assertEquals(Color(0xFF00FF00), matrix.borderColor)

        val neonGreen = IslandThemePalettes.getTheme("Neon Green")
        assertEquals("matrix", neonGreen.id)

        val purple = IslandThemePalettes.getTheme("Material Purple")
        assertEquals("amethyst", purple.id)
        assertEquals(ThemeFillType.HORIZONTAL_GRADIENT, purple.fillType)
    }

    @Test
    fun all51ThemesAreDefinedAndResolvable() {
        val expectedThemeIds = listOf(
            "classic_black", "minimal_gray", "midnight_blue", "amethyst", "sunset",
            "neon_cyber", "royal_gold", "ocean_breeze", "crimson_fire", "deep_space",
            "forest_rain", "purple_haze", "cherry_blossom", "golden_hour", "matrix",
            "cyberpunk_pink", "mint_fresh", "silver_lining", "carbon_fiber", "electric_blue",
            "sweet_candy", "northern_lights", "pure_white", "vampire_red", "emerald_city",
            "cosmic_void", "rose_gold", "ice_cold", "lemon_twist", "twilight",
            "hot_lava", "sea_foam", "nebula", "bumblebee", "slate",
            "rainbow", "stormy_night", "bubblegum", "toxic_green", "ruby_red",
            "deep_sea", "pumpkin_spice", "titanium", "insta_style", "glitch",
            "copper", "lavender", "stealth", "miami_vice", "miami_vice_real",
            "zen_garden"
        )

        assertEquals(51, expectedThemeIds.size)
        assertEquals(51, IslandThemePalettes.THEMES.size)

        for (id in expectedThemeIds) {
            val theme = IslandThemePalettes.getTheme(id)
            assertNotNull("Theme '$id' should not be null", theme)
            assertEquals("Theme '$id' should resolve to itself", id, theme.id)
            assertTrue("Theme '$id' must have at least one background color", theme.backgroundColors.isNotEmpty())
        }
    }

    @Test
    fun resolvesSweepAndRadialGradientThemes() {
        val aurora = IslandThemePalettes.getTheme("northern_lights")
        assertEquals(ThemeFillType.SWEEP_GRADIENT, aurora.fillType)
        assertEquals(2, aurora.backgroundColors.size)

        val nebula = IslandThemePalettes.getTheme("nebula")
        assertEquals(ThemeFillType.RADIAL_GRADIENT, nebula.fillType)
        assertEquals(2, nebula.backgroundColors.size)

        val ruby = IslandThemePalettes.getTheme("ruby_red")
        assertEquals(ThemeFillType.RADIAL_GRADIENT, ruby.fillType)
        assertEquals(2, ruby.backgroundColors.size)
    }

    @Test
    fun customGradientPairsResolveCorrectly() {
        assertEquals(20, IslandThemePalettes.CUSTOM_GRADIENT_PAIRS.size)

        val pair01 = IslandThemePalettes.getTheme("gradient_1")
        assertEquals("custom_gradient_1", pair01.id)
        assertEquals(ThemeFillType.LINEAR_GRADIENT, pair01.fillType)
        assertEquals(Color(0xFFFF5733), pair01.backgroundColors[0])
        assertEquals(Color(0xFFC70039), pair01.backgroundColors[1])

        val pair20 = IslandThemePalettes.getTheme("pair_20")
        assertEquals("custom_gradient_20", pair20.id)
        assertEquals(Color(0xFFB71C1C), pair20.backgroundColors[0])
        assertEquals(Color(0xFFF44336), pair20.backgroundColors[1])
    }

    @Test
    fun unknownThemeFallsBackToClassicBlack() {
        val fallback = IslandThemePalettes.getTheme("unknown_nonexistent_theme_123")
        assertEquals("classic_black", fallback.id)
        assertEquals("Classic Black", fallback.displayName)
    }
}
