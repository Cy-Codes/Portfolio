package codes.cy.portfolio.admin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AdminIcons {
    private const val VIEWPORT_SIZE: Float = 24f
    private const val STROKE_WIDTH: Float = 1.9f

    val Stats: ImageVector by lazy {
        strokeIcon("AdminStats") {
            moveTo(4f, 19.5f)
            lineTo(20f, 19.5f)
            moveTo(6.5f, 16f)
            lineTo(6.5f, 11f)
            moveTo(12f, 16f)
            lineTo(12f, 6f)
            moveTo(17.5f, 16f)
            lineTo(17.5f, 9f)
        }
    }

    val Upload: ImageVector by lazy {
        strokeIcon("AdminUpload") {
            moveTo(12f, 15.5f)
            lineTo(12f, 4f)
            moveTo(7.5f, 8.5f)
            lineTo(12f, 4f)
            lineTo(16.5f, 8.5f)
            moveTo(5f, 19.5f)
            lineTo(19f, 19.5f)
        }
    }

    val Plus: ImageVector by lazy {
        strokeIcon("AdminPlus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Trash: ImageVector by lazy {
        strokeIcon("AdminTrash") {
            moveTo(5f, 6.5f)
            lineTo(19f, 6.5f)
            moveTo(9.5f, 6.5f)
            lineTo(9.5f, 4f)
            lineTo(14.5f, 4f)
            lineTo(14.5f, 6.5f)
            moveTo(7f, 6.5f)
            lineTo(8f, 19.5f)
            lineTo(16f, 19.5f)
            lineTo(17f, 6.5f)
        }
    }

    val Lock: ImageVector by lazy {
        strokeIcon("AdminLock") {
            moveTo(6f, 11f)
            lineTo(18f, 11f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
            moveTo(8.5f, 11f)
            lineTo(8.5f, 7.5f)
            curveTo(8.5f, 5.2f, 10f, 4f, 12f, 4f)
            curveTo(14f, 4f, 15.5f, 5.2f, 15.5f, 7.5f)
            lineTo(15.5f, 11f)
        }
    }

    val Logout: ImageVector by lazy {
        strokeIcon("AdminLogout") {
            moveTo(10f, 4.5f)
            lineTo(5f, 4.5f)
            lineTo(5f, 19.5f)
            lineTo(10f, 19.5f)
            moveTo(10f, 12f)
            lineTo(19.5f, 12f)
            moveTo(15.5f, 8f)
            lineTo(19.5f, 12f)
            lineTo(15.5f, 16f)
        }
    }

    val Save: ImageVector by lazy {
        strokeIcon("AdminSave") {
            moveTo(5f, 4.5f)
            lineTo(16f, 4.5f)
            lineTo(19.5f, 8f)
            lineTo(19.5f, 19.5f)
            lineTo(5f, 19.5f)
            close()
            moveTo(8f, 4.5f)
            lineTo(8f, 9.5f)
            lineTo(15f, 9.5f)
            lineTo(15f, 4.5f)
            moveTo(8f, 19.5f)
            lineTo(8f, 14f)
            lineTo(16f, 14f)
            lineTo(16f, 19.5f)
        }
    }

    val Eye: ImageVector by lazy {
        strokeIcon("AdminEye") {
            moveTo(3f, 12f)
            curveTo(5.5f, 7.5f, 8.5f, 6f, 12f, 6f)
            curveTo(15.5f, 6f, 18.5f, 7.5f, 21f, 12f)
            curveTo(18.5f, 16.5f, 15.5f, 18f, 12f, 18f)
            curveTo(8.5f, 18f, 5.5f, 16.5f, 3f, 12f)
            close()
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
            curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
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
