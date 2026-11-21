package codes.cy.portfolio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight Compose vector icons to avoid pulling multi-MB Material icon packs into `wasmJs`.
 */
object PortfolioIcons {
    private const val VIEWPORT_SIZE: Float = 24f
    private const val STROKE_WIDTH: Float = 1.9f

    val Profile: ImageVector by lazy {
        strokeIcon("Profile") {
            // Head circle + shoulders arc
            moveTo(12f, 4f)
            curveTo(9.8f, 4f, 8f, 5.8f, 8f, 8f)
            curveTo(8f, 10.2f, 9.8f, 12f, 12f, 12f)
            curveTo(14.2f, 12f, 16f, 10.2f, 16f, 8f)
            curveTo(16f, 5.8f, 14.2f, 4f, 12f, 4f)
            close()
            moveTo(5f, 20f)
            curveTo(5f, 16.7f, 8.1f, 14.5f, 12f, 14.5f)
            curveTo(15.9f, 14.5f, 19f, 16.7f, 19f, 20f)
        }
    }

    val Projects: ImageVector by lazy {
        strokeIcon("Projects") {
            moveTo(4f, 4f)
            lineTo(10.5f, 4f)
            lineTo(10.5f, 10.5f)
            lineTo(4f, 10.5f)
            close()
            moveTo(13.5f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 10.5f)
            lineTo(13.5f, 10.5f)
            close()
            moveTo(4f, 13.5f)
            lineTo(10.5f, 13.5f)
            lineTo(10.5f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(13.5f, 13.5f)
            lineTo(20f, 13.5f)
            lineTo(20f, 20f)
            lineTo(13.5f, 20f)
            close()
        }
    }

    val Writeups: ImageVector by lazy {
        strokeIcon("Writeups") {
            moveTo(5f, 3.5f)
            lineTo(15f, 3.5f)
            lineTo(19f, 7.5f)
            lineTo(19f, 20.5f)
            lineTo(5f, 20.5f)
            close()
            moveTo(8.5f, 11.5f)
            lineTo(15.5f, 11.5f)
            moveTo(8.5f, 15.5f)
            lineTo(15.5f, 15.5f)
            moveTo(8.5f, 7.5f)
            lineTo(12f, 7.5f)
        }
    }

    val Resume: ImageVector by lazy {
        strokeIcon("Resume") {
            moveTo(4f, 6.5f)
            lineTo(20f, 6.5f)
            lineTo(20f, 19.5f)
            lineTo(4f, 19.5f)
            close()
            moveTo(9f, 6.5f)
            lineTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 6.5f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
        }
    }

    val ArrowBack: ImageVector by lazy {
        strokeIcon("ArrowBack") {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    val ExternalLink: ImageVector by lazy {
        strokeIcon("ExternalLink") {
            moveTo(14f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 10f)
            moveTo(10f, 14f)
            lineTo(20f, 4f)
            moveTo(18f, 13.5f)
            lineTo(18f, 19.5f)
            lineTo(4.5f, 19.5f)
            lineTo(4.5f, 6f)
            lineTo(10.5f, 6f)
        }
    }

    val Download: ImageVector by lazy {
        strokeIcon("Download") {
            moveTo(12f, 4f)
            lineTo(12f, 15.5f)
            moveTo(7.5f, 11.5f)
            lineTo(12f, 16f)
            lineTo(16.5f, 11.5f)
            moveTo(5f, 19.5f)
            lineTo(19f, 19.5f)
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            moveTo(11f, 4.5f)
            curveTo(7.4f, 4.5f, 4.5f, 7.4f, 4.5f, 11f)
            curveTo(4.5f, 14.6f, 7.4f, 17.5f, 11f, 17.5f)
            curveTo(14.6f, 17.5f, 17.5f, 14.6f, 17.5f, 11f)
            curveTo(17.5f, 7.4f, 14.6f, 4.5f, 11f, 4.5f)
            close()
            moveTo(16f, 16f)
            lineTo(20f, 20f)
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            moveTo(19f, 12f)
            curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f)
            curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f)
            curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f)
            curveTo(14.6f, 5f, 16.9f, 6.4f, 18.1f, 8.5f)
            moveTo(19f, 4.5f)
            lineTo(19f, 9f)
            lineTo(14.5f, 9f)
        }
    }

    val Clear: ImageVector by lazy {
        strokeIcon("Clear") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Toc: ImageVector by lazy {
        strokeIcon("Toc") {
            moveTo(4f, 6f)
            lineTo(14f, 6f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 18f)
            lineTo(16f, 18f)
        }
    }

    val Code: ImageVector by lazy {
        strokeIcon("Code") {
            moveTo(8.5f, 7.5f)
            lineTo(4f, 12f)
            lineTo(8.5f, 16.5f)
            moveTo(15.5f, 7.5f)
            lineTo(20f, 12f)
            lineTo(15.5f, 16.5f)
            moveTo(13.5f, 5.5f)
            lineTo(10.5f, 18.5f)
        }
    }

    val Sun: ImageVector by lazy {
        strokeIcon("LightMode") {
            moveTo(12f, 8f)
            curveTo(9.8f, 8f, 8f, 9.8f, 8f, 12f)
            curveTo(8f, 14.2f, 9.8f, 16f, 12f, 16f)
            curveTo(14.2f, 16f, 16f, 14.2f, 16f, 12f)
            curveTo(16f, 9.8f, 14.2f, 8f, 12f, 8f)
            close()
            moveTo(12f, 3f)
            lineTo(12f, 5f)
            moveTo(12f, 19f)
            lineTo(12f, 21f)
            moveTo(3f, 12f)
            lineTo(5f, 12f)
            moveTo(19f, 12f)
            lineTo(21f, 12f)
        }
    }

    val Moon: ImageVector by lazy {
        strokeIcon("DarkMode") {
            moveTo(19.5f, 14.5f)
            curveTo(18.5f, 17.7f, 15.5f, 20f, 12f, 20f)
            curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
            curveTo(4f, 8.5f, 6.3f, 5.5f, 9.5f, 4.5f)
            curveTo(8.4f, 7.8f, 9.7f, 11.4f, 12.6f, 13.4f)
            curveTo(14.7f, 14.8f, 17.2f, 15.1f, 19.5f, 14.5f)
            close()
        }
    }

    private fun strokeIcon(
        name: String,
        build: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = VIEWPORT_SIZE,
            viewportHeight = VIEWPORT_SIZE,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
                pathBuilder = build,
            )
        }.build()
}
