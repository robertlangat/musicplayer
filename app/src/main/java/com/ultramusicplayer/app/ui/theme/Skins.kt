package com.ultramusicplayer.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class SkinPattern { NONE, DOTS, STRIPES, GRID, WAVES, RINGS, STARS, DIAGONAL, HEX }

data class Skin(
    val id: String,
    val name: String,
    val colors: List<Color>,
    val pattern: SkinPattern = SkinPattern.NONE,
    val radial: Boolean = false,
)

private fun c(hex: Long) = Color(hex)

/** 24 built-in background skins: procedural gradients and textures (no third-party imagery). */
object Skins {
    const val PHOTO_ID = "photo"

    val all: List<Skin> = listOf(
        Skin("charcoal", "Charcoal", listOf(c(0xFF1C1D21), c(0xFF2A2C33))),
        Skin("midnight", "Midnight", listOf(c(0xFF0F2027), c(0xFF203A43), c(0xFF2C5364))),
        Skin("aurora", "Aurora", listOf(c(0xFF00C9A7), c(0xFF845EC2)), SkinPattern.WAVES),
        Skin("sunset", "Sunset", listOf(c(0xFFFF5F6D), c(0xFFFFC371))),
        Skin("ocean", "Ocean", listOf(c(0xFF2193B0), c(0xFF6DD5ED)), SkinPattern.WAVES),
        Skin("forest", "Forest", listOf(c(0xFF134E5E), c(0xFF71B280)), SkinPattern.DIAGONAL),
        Skin("ember", "Ember", listOf(c(0xFF3A1C71), c(0xFFD76D77), c(0xFFFFAF7B)), radial = true),
        Skin("neon", "Neon Grid", listOf(c(0xFF12C2E9), c(0xFFC471ED), c(0xFFF64F59)), SkinPattern.GRID),
        Skin("graphite", "Graphite", listOf(c(0xFF232526), c(0xFF414345)), SkinPattern.STRIPES),
        Skin("royal", "Royal Dots", listOf(c(0xFF141E30), c(0xFF243B55)), SkinPattern.DOTS),
        Skin("lavender", "Lavender", listOf(c(0xFFB993D6), c(0xFF8CA6DB))),
        Skin("mint", "Mint Night", listOf(c(0xFF43C6AC), c(0xFF191654)), radial = true),
        Skin("crimson", "Crimson", listOf(c(0xFF93291E), c(0xFFED213A)), SkinPattern.DIAGONAL),
        Skin("gold", "Gold Rings", listOf(c(0xFFF7971E), c(0xFFFFD200)), SkinPattern.RINGS),
        Skin("deepspace", "Deep Space", listOf(c(0xFF000000), c(0xFF29323C)), SkinPattern.STARS),
        Skin("cosmic", "Cosmic", listOf(c(0xFF1F1C2C), c(0xFF928DAB)), SkinPattern.STARS, radial = true),
        Skin("coral", "Coral Reef", listOf(c(0xFFFF9966), c(0xFFFF5E62)), SkinPattern.WAVES),
        Skin("arctic", "Arctic", listOf(c(0xFFE0EAFC), c(0xFFCFDEF3)), SkinPattern.DOTS),
        Skin("velvet", "Velvet", listOf(c(0xFF41295A), c(0xFF2F0743)), SkinPattern.STRIPES),
        Skin("indigo", "Indigo", listOf(c(0xFF005C97), c(0xFF363795)), SkinPattern.HEX),
        Skin("carbon", "Carbon Fibre", listOf(c(0xFF1E1E1E), c(0xFF2D2D2D)), SkinPattern.GRID),
        Skin("vinyl", "Vinyl", listOf(c(0xFF111111), c(0xFF2B2B2B)), SkinPattern.RINGS, radial = true),
        Skin("peach", "Peach", listOf(c(0xFFED4264), c(0xFFFFEDBC))),
        Skin("northern", "Northern Lights", listOf(c(0xFF43CEA2), c(0xFF185A9D)), SkinPattern.WAVES),
    )

    fun byId(id: String): Skin = all.firstOrNull { it.id == id } ?: all.first()

    fun photoFile(dir: File) = File(dir, "background_photo.jpg")
}

private val starField: List<Offset> = Random(42).let { r -> List(140) { Offset(r.nextFloat(), r.nextFloat()) } }

private fun DrawScope.drawPattern(pattern: SkinPattern) {
    val w = size.width
    val h = size.height
    val ink = Color.White.copy(alpha = 0.07f)
    when (pattern) {
        SkinPattern.NONE -> Unit
        SkinPattern.DOTS -> {
            val step = 28f
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) {
                    drawCircle(ink, radius = 2.2f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        SkinPattern.STRIPES -> {
            var x = 0f
            while (x < w) {
                drawLine(ink, Offset(x, 0f), Offset(x, h), strokeWidth = 6f)
                x += 22f
            }
        }
        SkinPattern.DIAGONAL -> {
            var x = -h
            while (x < w) {
                drawLine(ink, Offset(x, h), Offset(x + h, 0f), strokeWidth = 3f)
                x += 26f
            }
        }
        SkinPattern.GRID -> {
            val step = 24f
            var x = 0f
            while (x < w) {
                drawLine(ink, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f)
                x += step
            }
            var y = 0f
            while (y < h) {
                drawLine(ink, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
                y += step
            }
        }
        SkinPattern.WAVES -> {
            for (k in 0 until 6) {
                val path = Path()
                val baseY = h * (0.35f + k * 0.11f)
                path.moveTo(0f, baseY)
                var x = 0f
                while (x <= w) {
                    path.lineTo(x, baseY + sin((x / w) * 2 * PI * 1.5 + k).toFloat() * h * 0.03f)
                    x += 8f
                }
                drawPath(path, Color.White.copy(alpha = 0.06f + k * 0.01f), style = Stroke(width = 3f))
            }
        }
        SkinPattern.RINGS -> {
            val center = Offset(w * 0.5f, h * 0.38f)
            var r = 20f
            while (r < maxOf(w, h)) {
                drawCircle(ink, radius = r, center = center, style = Stroke(width = 1.5f))
                r += 14f
            }
        }
        SkinPattern.STARS -> {
            drawPoints(
                starField.map { Offset(it.x * w, it.y * h) },
                PointMode.Points,
                Color.White.copy(alpha = 0.55f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
        SkinPattern.HEX -> {
            val r = 22f
            val dx = r * 1.732f
            var row = 0
            var y = 0f
            while (y < h + r) {
                var x = if (row % 2 == 0) 0f else dx / 2
                while (x < w + r) {
                    val p = Path()
                    for (i in 0 until 6) {
                        val a = PI / 3 * i + PI / 6
                        val px = x + r * kotlin.math.cos(a).toFloat()
                        val py = y + r * kotlin.math.sin(a).toFloat()
                        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                    p.close()
                    drawPath(p, ink, style = Stroke(width = 1.2f))
                    x += dx
                }
                y += r * 1.5f
                row++
            }
        }
    }
}

/** Draws a skin (or the user's photo) with a readability overlay, then the content on top. */
@Composable
fun SkinBackground(
    skinId: String,
    photoFile: File?,
    overlay: Float,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = remember(skinId) { Skins.byId(skinId) }
    val usePhoto = skinId == Skins.PHOTO_ID && photoFile != null && photoFile.exists()
    Box(modifier.fillMaxSize()) {
        if (usePhoto) {
            AsyncImage(
                model = photoFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            SkinSwatch(skin, Modifier.matchParentSize())
        }
        val scrim = if (darkTheme) Color.Black else Color.White
        Box(Modifier.matchParentSize().background(scrim.copy(alpha = overlay.coerceIn(0f, 0.9f))))
        content()
    }
}

@Composable
fun SkinSwatch(skin: Skin, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val brush = if (skin.radial) {
                Brush.radialGradient(skin.colors, center = Offset(size.width * 0.3f, size.height * 0.2f), radius = maxOf(size.width, size.height))
            } else {
                Brush.linearGradient(skin.colors, start = Offset.Zero, end = Offset(size.width, size.height))
            }
            drawRect(brush)
            drawPattern(skin.pattern)
        },
    )
}
