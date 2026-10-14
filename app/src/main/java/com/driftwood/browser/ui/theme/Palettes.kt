package com.driftwood.browser.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The four themes. Hex codes are copied from the official sources:
 * - Gruvbox: https://github.com/morhetz/gruvbox (colors/gruvbox.vim)
 * - Rosé Pine: https://github.com/rose-pine/neovim (lua/rose-pine/palette.lua)
 */
data class Palette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val subtle: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
)

enum class ThemeId(val label: String, val palette: Palette) {
    GRUVBOX_DARK(
        "Gruvbox Dark",
        Palette(
            isDark = true,
            background = Color(0xFF282828), // dark0
            surface = Color(0xFF3C3836), // dark1
            surfaceHigh = Color(0xFF504945), // dark2
            text = Color(0xFFEBDBB2), // light1
            subtle = Color(0xFFA89984), // light4
            muted = Color(0xFF928374), // gray
            accent = Color(0xFFFABD2F), // bright_yellow
            onAccent = Color(0xFF282828),
            danger = Color(0xFFFB4934), // bright_red
        ),
    ),
    GRUVBOX_LIGHT(
        "Gruvbox Light",
        Palette(
            isDark = false,
            background = Color(0xFFFBF1C7), // light0
            surface = Color(0xFFEBDBB2), // light1
            surfaceHigh = Color(0xFFD5C4A1), // light2
            text = Color(0xFF3C3836), // dark1
            subtle = Color(0xFF7C6F64), // dark4
            muted = Color(0xFF928374), // gray
            accent = Color(0xFFAF3A03), // faded_orange
            onAccent = Color(0xFFFBF1C7),
            danger = Color(0xFF9D0006), // faded_red
        ),
    ),
    ROSE_PINE(
        "Rosé Pine",
        Palette(
            isDark = true,
            background = Color(0xFF191724), // base
            surface = Color(0xFF1F1D2E), // surface
            surfaceHigh = Color(0xFF26233A), // overlay
            text = Color(0xFFE0DEF4), // text
            subtle = Color(0xFF908CAA), // subtle
            muted = Color(0xFF6E6A86), // muted
            accent = Color(0xFFC4A7E7), // iris
            onAccent = Color(0xFF191724),
            danger = Color(0xFFEB6F92), // love
        ),
    ),
    ROSE_PINE_DAWN(
        "Rosé Pine Dawn",
        Palette(
            isDark = false,
            background = Color(0xFFFAF4ED), // base
            surface = Color(0xFFFFFAF3), // surface
            surfaceHigh = Color(0xFFF2E9E1), // overlay
            text = Color(0xFF464261), // text
            subtle = Color(0xFF797593), // subtle
            muted = Color(0xFF9893A5), // muted
            accent = Color(0xFF286983), // pine
            onAccent = Color(0xFFFAF4ED),
            danger = Color(0xFFB4637A), // love
        ),
    ),
}
