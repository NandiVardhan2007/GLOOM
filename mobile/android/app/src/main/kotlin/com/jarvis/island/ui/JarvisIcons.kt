package com.jarvis.island.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * SF Symbols–inspired icon set drawn for JARVIS.
 *
 * SF Symbols themselves are licensed by Apple for Apple platforms only, so the
 * shapes here are original vectors built to the same design language: a 24pt
 * grid, 1.8pt rounded strokes, geometric forms, and filled variants only where
 * the symbol carries a state (play, pause, bolt).
 */
object JarvisIcons {

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.line(path: String, width: Float = 1.8f) {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }

    private fun ImageVector.Builder.solid(path: String) {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }

    val House: ImageVector by lazy {
        build("house") {
            line("M4 10.7 12 4l8 6.7V19a1.6 1.6 0 0 1-1.6 1.6H5.6A1.6 1.6 0 0 1 4 19z")
            line("M9.4 20.6v-6.1h5.2v6.1")
        }
    }

    val Puzzle: ImageVector by lazy {
        build("puzzlepiece") {
            line("M9.5 4.2a2.1 2.1 0 0 1 4.2 0c0 .9-.4 1.3-.4 1.9h3.3a1 1 0 0 1 1 1v3.1c.6 0 1-.3 1.9-.3a2.1 2.1 0 0 1 0 4.2c-.9 0-1.3-.3-1.9-.3v3.1a1 1 0 0 1-1 1h-3.4c0 .6.4 1 .4 1.9a2.1 2.1 0 0 1-4.2 0c0-.9.4-1.3.4-1.9H6.4a1 1 0 0 1-1-1v-3.3c-.6 0-1 .3-1.9.3a2.1 2.1 0 0 1 0-4.2c.9 0 1.3.3 1.9.3V7.1a1 1 0 0 1 1-1h3.1c0-.6-.4-1-.4-1.9z")
        }
    }

    val Gear: ImageVector by lazy {
        build("gearshape") {
            line("M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8z")
            line("M12 2.6v2.6M12 18.8v2.6M2.6 12h2.6M18.8 12h2.6")
            line("M5.4 5.4l1.8 1.8M16.8 16.8l1.8 1.8M18.6 5.4l-1.8 1.8M7.2 16.8l-1.8 1.8")
            line("M12 4.4a7.6 7.6 0 0 0-5.4 2.2 7.6 7.6 0 0 0 0 10.8 7.6 7.6 0 0 0 10.8 0 7.6 7.6 0 0 0 0-10.8A7.6 7.6 0 0 0 12 4.4z")
        }
    }

    val Info: ImageVector by lazy {
        build("info.circle") {
            line("M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z")
            line("M12 10.6v6")
            solid("M12 7.1a1.15 1.15 0 1 0 0 2.3 1.15 1.15 0 0 0 0-2.3z")
        }
    }

    val ChevronRight: ImageVector by lazy {
        build("chevron.right") { line("M9.2 5.2 16 12l-6.8 6.8") }
    }

    val ChevronLeft: ImageVector by lazy {
        build("chevron.left") { line("M14.8 5.2 8 12l6.8 6.8") }
    }

    val ChevronUp: ImageVector by lazy {
        build("chevron.up") { line("M5.2 15 12 8.2l6.8 6.8") }
    }

    val ChevronDown: ImageVector by lazy {
        build("chevron.down") { line("M5.2 9 12 15.8 18.8 9") }
    }

    val Close: ImageVector by lazy {
        build("xmark") { line("M6.2 6.2 17.8 17.8M17.8 6.2 6.2 17.8", 2f) }
    }

    val CheckCircle: ImageVector by lazy {
        build("checkmark.circle") {
            line("M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z")
            line("M7.8 12.3l2.8 2.8 5.6-6")
        }
    }

    val CheckCircleFill: ImageVector by lazy {
        build("checkmark.circle.fill") {
            line("M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z", 2f)
            line("M7.6 12.2l3 3 5.8-6.2", 2.1f)
        }
    }

    val XCircleFill: ImageVector by lazy {
        build("xmark.circle.fill") {
            line("M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z", 2f)
            line("M8.6 8.6l6.8 6.8M15.4 8.6l-6.8 6.8", 2f)
        }
    }

    val Bell: ImageVector by lazy {
        build("bell") {
            line("M12 3.8a5.2 5.2 0 0 0-5.2 5.2v3.3L4.6 15.4h14.8l-2.2-3.1V9A5.2 5.2 0 0 0 12 3.8z")
            line("M10 18.4a2.1 2.1 0 0 0 4 0")
        }
    }

    val Display: ImageVector by lazy {
        build("display") {
            line("M5.4 4.8h13.2a1.6 1.6 0 0 1 1.6 1.6v7.2a1.6 1.6 0 0 1-1.6 1.6H5.4a1.6 1.6 0 0 1-1.6-1.6V6.4a1.6 1.6 0 0 1 1.6-1.6z")
            line("M9 19.2h6M12 15.2v4")
        }
    }

    val HandTap: ImageVector by lazy {
        build("hand.tap") {
            line("M9.2 11.4V6.6a1.5 1.5 0 0 1 3 0v4.1")
            line("M12.2 10.7V5.8a1.5 1.5 0 0 1 3 0v5")
            line("M15.2 11V8a1.5 1.5 0 0 1 3 0v6.2a6.2 6.2 0 0 1-6.2 6.2h-.7a5.6 5.6 0 0 1-4.4-2.1l-2.6-3.3a1.6 1.6 0 0 1 2.5-2l2 2")
        }
    }

    val Island: ImageVector by lazy {
        build("dynamic.island") {
            line("M5.4 8h13.2a4 4 0 0 1 0 8H5.4a4 4 0 0 1 0-8z")
            solid("M8.4 10.6a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8z")
            solid("M13.2 10.6a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8z")
        }
    }

    val BoltFill: ImageVector by lazy {
        build("bolt.fill") {
            solid("M13.4 2.4 4.8 13.2h5.6l-1 8.4L19.2 10.6h-5.6z")
        }
    }

    val MusicNote: ImageVector by lazy {
        build("music.note") {
            line("M9.4 18.2a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z")
            line("M12 15.6V5.2l7.2-2v9.8")
        }
    }

    val Clipboard: ImageVector by lazy {
        build("doc.on.doc") {
            line("M8.2 7.4V5.6A1.6 1.6 0 0 1 9.8 4h6.6a1.6 1.6 0 0 1 1.6 1.6v8.8a1.6 1.6 0 0 1-1.6 1.6h-1.8")
            line("M6 7.4h6.6a1.6 1.6 0 0 1 1.6 1.6v9.4a1.6 1.6 0 0 1-1.6 1.6H6a1.6 1.6 0 0 1-1.6-1.6V9A1.6 1.6 0 0 1 6 7.4z")
        }
    }

    val Beam: ImageVector by lazy {
        build("arrow.up.doc") {
            line("M6.2 9.2h2.4a1.6 1.6 0 0 1 1.6 1.6v7.6a1.6 1.6 0 0 1-1.6 1.6H6.2a1.6 1.6 0 0 1-1.6-1.6v-7.6a1.6 1.6 0 0 1 1.6-1.6z")
            line("M16.4 16.4V4.2M13.2 7.4l3.2-3.2 3.2 3.2")
        }
    }

    val DownloadDoc: ImageVector by lazy {
        build("arrow.down.doc") {
            line("M6.2 9.2h2.4a1.6 1.6 0 0 1 1.6 1.6v7.6a1.6 1.6 0 0 1-1.6 1.6H6.2a1.6 1.6 0 0 1-1.6-1.6v-7.6a1.6 1.6 0 0 1 1.6-1.6z")
            line("M16.4 4.2v12.2M13.2 13.2l3.2 3.2 3.2-3.2")
        }
    }

    val Mouse: ImageVector by lazy {
        build("computer.mouse") {
            line("M8 3.6h8a2.4 2.4 0 0 1 2.4 2.4v12a2.4 2.4 0 0 1-2.4 2.4H8a2.4 2.4 0 0 1-2.4-2.4V6A2.4 2.4 0 0 1 8 3.6z")
            line("M12 3.6v5.2")
        }
    }

    val Timer: ImageVector by lazy {
        build("timer") {
            line("M12 4.2a7.8 7.8 0 1 0 0 15.6 7.8 7.8 0 0 0 0-15.6z")
            line("M12 8.2v4.2l2.8 1.7")
            line("M9.4 2.8h5.2")
        }
    }

    val Palette: ImageVector by lazy {
        build("paintpalette") {
            line("M12 3.2a8.8 8.8 0 0 0 0 17.6c1.4 0 2.1-.9 2.1-1.9 0-1.4-1.2-1.7-1.2-2.9 0-.9.8-1.6 1.8-1.6h1.9c2.3 0 4.2-1.9 4.2-4.2C20.8 6.3 17 3.2 12 3.2z")
            solid("M7.6 9.2a1.15 1.15 0 1 0 0 2.3 1.15 1.15 0 0 0 0-2.3z")
            solid("M11.4 6.7a1.15 1.15 0 1 0 0 2.3 1.15 1.15 0 0 0 0-2.3z")
            solid("M15.6 8.1a1.15 1.15 0 1 0 0 2.3 1.15 1.15 0 0 0 0-2.3z")
        }
    }

    val ArrowsUpDown: ImageVector by lazy {
        build("arrow.up.arrow.down") {
            line("M8.4 4.6v14.8M4.9 8.1l3.5-3.5 3.5 3.5")
            line("M15.6 19.4V4.6M12.1 15.9l3.5 3.5 3.5-3.5")
        }
    }

    val Move: ImageVector by lazy {
        build("arrow.up.and.down.and.arrow.left.and.right") {
            line("M12 3.6v16.8M3.6 12h16.8")
            line("M9.4 6.2 12 3.6l2.6 2.6M9.4 17.8 12 20.4l2.6-2.6")
            line("M6.2 9.4 3.6 12l2.6 2.6M17.8 9.4l2.6 2.6-2.6 2.6")
        }
    }

    val Apps: ImageVector by lazy {
        build("square.grid.2x2") {
            line("M4.8 4.8h5.4v5.4H4.8zM13.8 4.8h5.4v5.4h-5.4zM4.8 13.8h5.4v5.4H4.8zM13.8 13.8h5.4v5.4h-5.4z")
        }
    }

    val Sliders: ImageVector by lazy {
        build("slider.horizontal.3") {
            line("M4 6.4h16M4 12h16M4 17.6h16")
            solid("M8.2 4.2a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z")
            solid("M15.8 9.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z")
            solid("M10.6 15.4a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z")
        }
    }

    val Sparkles: ImageVector by lazy {
        build("sparkles") {
            solid("M9.6 3.4l1.1 3 3 1.1-3 1.1-1.1 3-1.1-3-3-1.1 3-1.1z")
            solid("M17.2 11.6l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9z")
        }
    }

    val Shield: ImageVector by lazy {
        build("lock.shield") {
            line("M12 3.2 5.4 5.8v5.4c0 4 2.7 7.6 6.6 9.1 3.9-1.5 6.6-5.1 6.6-9.1V5.8z")
            line("M9.4 11.8l1.9 1.9 3.5-3.8")
        }
    }

    val Globe: ImageVector by lazy {
        build("globe") {
            line("M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z")
            line("M3.4 12h17.2")
            line("M12 3.2c2.3 2.4 3.5 5.4 3.5 8.8S14.3 18.4 12 20.8c-2.3-2.4-3.5-5.4-3.5-8.8S9.7 5.6 12 3.2z")
        }
    }

    val Waveform: ImageVector by lazy {
        build("waveform") {
            line("M4 10.4v3.2M7.2 7.6v8.8M10.4 5.4v13.2M13.6 8.6v6.8M16.8 6.6v10.8M20 10.4v3.2", 1.9f)
        }
    }

    val Speaker: ImageVector by lazy {
        build("speaker.wave.2") {
            line("M4.4 9.4h3.2L12 5.8v12.4l-4.4-3.6H4.4a.9.9 0 0 1-.9-.9v-3.4a.9.9 0 0 1 .9-.9z")
            line("M15.2 9.2a4 4 0 0 1 0 5.6")
            line("M17.8 6.6a7.6 7.6 0 0 1 0 10.8")
        }
    }

    val Moon: ImageVector by lazy {
        build("moon.fill") {
            solid("M20.4 14.8A8.8 8.8 0 0 1 9.2 3.6a8.9 8.9 0 1 0 11.2 11.2z")
        }
    }

    val Wifi: ImageVector by lazy {
        build("wifi") {
            line("M3 9.4a13.5 13.5 0 0 1 18 0")
            line("M6.2 12.8a9 9 0 0 1 11.6 0")
            line("M9.3 16.1a4.4 4.4 0 0 1 5.4 0")
            solid("M12 18.4a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z")
        }
    }

    val Bluetooth: ImageVector by lazy {
        build("bluetooth") {
            line("M7.2 7.2 16.8 16.8 12 21V3l4.8 4.2L7.2 16.8")
        }
    }

    val PlayFill: ImageVector by lazy {
        build("play.fill") { solid("M8 4.8v14.4L19.6 12z") }
    }

    val PauseFill: ImageVector by lazy {
        build("pause.fill") {
            solid("M7.2 4.6h3.4v14.8H7.2zM13.4 4.6h3.4v14.8h-3.4z")
        }
    }

    val SkipBackFill: ImageVector by lazy {
        build("backward.fill") {
            solid("M11.4 12 20 5.6v12.8zM4 5.6h2.4v12.8H4z")
        }
    }

    val SkipForwardFill: ImageVector by lazy {
        build("forward.fill") {
            solid("M12.6 12 4 18.4V5.6zM17.6 5.6H20v12.8h-2.4z")
        }
    }

    val ArrowClockwise: ImageVector by lazy {
        build("arrow.clockwise") {
            line("M19.6 12a7.6 7.6 0 1 1-2.3-5.4")
            line("M19.8 4.4v4.8h-4.8")
        }
    }

    val Plus: ImageVector by lazy {
        build("plus") { line("M12 5v14M5 12h14", 2f) }
    }

    val Minus: ImageVector by lazy {
        build("minus") { line("M5 12h14", 2f) }
    }

    val Laptop: ImageVector by lazy {
        build("laptopcomputer") {
            line("M5.4 5.4h13.2a1.4 1.4 0 0 1 1.4 1.4v8H4V6.8a1.4 1.4 0 0 1 1.4-1.4z")
            line("M2.8 17.2h18.4")
        }
    }

    val QrCode: ImageVector by lazy {
        build("qrcode") {
            line("M4.4 4.4h5.2v5.2H4.4zM14.4 4.4h5.2v5.2h-5.2zM4.4 14.4h5.2v5.2H4.4z")
            line("M14.8 14.8h2v2h-2zM18.4 14.8h1.2v5.2h-3.2v-1.6M14.4 18h1.6v1.6")
        }
    }

    val HeartFill: ImageVector by lazy {
        build("heart.fill") {
            solid("M12 20.2S3.6 15 3.6 9.2A4.6 4.6 0 0 1 12 6.4a4.6 4.6 0 0 1 8.4 2.8c0 5.8-8.4 11-8.4 11z")
        }
    }

    val Note: ImageVector by lazy {
        build("note.text") {
            line("M6.2 3.8h8.2L18.6 8v11.3a1 1 0 0 1-1 1H6.2a1 1 0 0 1-1-1V4.8a1 1 0 0 1 1-1z")
            line("M14.2 3.9V8h4.3")
            line("M8.4 12h7.2M8.4 15.4h5.2", 1.6f)
        }
    }

    val Trash: ImageVector by lazy {
        build("trash") {
            line("M4.6 6.6h14.8")
            line("M9.2 6.6V4.4h5.6v2.2")
            line("M6.4 6.6l.9 13.2a1 1 0 0 0 1 .9h7.4a1 1 0 0 0 1-.9l.9-13.2")
            line("M10.2 10.4v6.4M13.8 10.4v6.4", 1.6f)
        }
    }

    val Alarm: ImageVector by lazy {
        build("alarm") {
            line("M12 21a7.6 7.6 0 1 0 0-15.2A7.6 7.6 0 0 0 12 21z")
            line("M12 9.4v3.8l2.6 1.6")
            line("M5.2 4.2 3 6.4M18.8 4.2 21 6.4", 1.7f)
        }
    }

    val Grid: ImageVector by lazy {
        build("square.grid.3x3") {
            line("M4.4 4.4h4v4h-4zM10 4.4h4v4h-4zM15.6 4.4h4v4h-4z")
            line("M4.4 10h4v4h-4zM10 10h4v4h-4zM15.6 10h4v4h-4z")
            line("M4.4 15.6h4v4h-4zM10 15.6h4v4h-4zM15.6 15.6h4v4h-4z")
        }
    }

    val Person: ImageVector by lazy {
        build("person") {
            line("M12 11.4a3.7 3.7 0 1 0 0-7.4 3.7 3.7 0 0 0 0 7.4z")
            line("M5 20.4c.7-3.5 3.5-5.4 7-5.4s6.3 1.9 7 5.4")
        }
    }
}
