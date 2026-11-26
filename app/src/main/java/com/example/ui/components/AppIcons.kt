package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight custom ImageVectors so BlackSMS does not need the ~18MB
 * material-icons-extended dependency.
 */
object AppIcons {
    val Shield: ImageVector by lazy {
        ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
                moveTo(10.94f, 15.54f)
                lineTo(7.4f, 12f)
                lineTo(8.81f, 10.59f)
                lineTo(10.94f, 12.71f)
                lineTo(15.18f, 8.47f)
                lineTo(16.59f, 9.88f)
                lineTo(10.94f, 15.54f)
                close()
            }
        }.build()
    }

    val Block: ImageVector by lazy {
        ImageVector.Builder("Block", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(4f, 12f)
                curveToRelative(0f, -4.42f, 3.58f, -8f, 8f, -8f)
                curveToRelative(1.85f, 0f, 3.55f, 0.63f, 4.9f, 1.69f)
                lineTo(5.69f, 16.9f)
                curveTo(4.63f, 15.55f, 4f, 13.85f, 4f, 12f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-1.85f, 0f, -3.55f, -0.63f, -4.9f, -1.69f)
                lineTo(18.31f, 7.1f)
                curveTo(19.37f, 8.45f, 20f, 10.15f, 20f, 12f)
                curveToRelative(0f, 4.42f, -3.58f, 8f, -8f, 8f)
                close()
            }
        }.build()
    }

    val Inbox: ImageVector by lazy {
        ImageVector.Builder("Inbox", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 15f)
                horizontalLineToRelative(-4f)
                curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
                reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                close()
            }
        }.build()
    }

    val ChatBubble: ImageVector by lazy {
        ImageVector.Builder("ChatBubble", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(18f)
                lineToRelative(4f, -4f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
        }.build()
    }

    val Archive: ImageVector by lazy {
        ImageVector.Builder("Archive", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.54f, 5.23f)
                lineToRelative(-1.39f, -1.68f)
                curveTo(18.88f, 3.21f, 18.47f, 3f, 18f, 3f)
                horizontalLineTo(6f)
                curveToRelative(-0.47f, 0f, -0.88f, 0.21f, -1.16f, 0.55f)
                lineTo(3.46f, 5.23f)
                curveTo(3.17f, 5.57f, 3f, 6.02f, 3f, 6.5f)
                verticalLineTo(19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6.5f)
                curveToRelative(0f, -0.48f, -0.17f, -0.93f, -0.46f, -1.27f)
                close()
                moveTo(12f, 17.5f)
                lineTo(6.5f, 12f)
                horizontalLineTo(10f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3.5f)
                lineTo(12f, 17.5f)
                close()
                moveTo(5.12f, 5f)
                lineToRelative(0.81f, -1f)
                horizontalLineToRelative(12f)
                lineToRelative(0.94f, 1f)
                horizontalLineTo(5.12f)
                close()
            }
        }.build()
    }

    val Restore: ImageVector by lazy {
        ImageVector.Builder("Restore", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, true, false, 3f, -6.7f)
                moveTo(3f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(4f)
            }
        }.build()
    }

    val Attach: ImageVector by lazy {
        ImageVector.Builder("Attach", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21.44f, 11.05f)
                lineToRelative(-9.19f, 9.19f)
                arcToRelative(6f, 6f, 0f, true, true, -8.49f, -8.49f)
                lineToRelative(9.19f, -9.19f)
                arcToRelative(4f, 4f, 0f, true, true, 5.66f, 5.66f)
                lineToRelative(-9.2f, 9.19f)
                arcToRelative(2f, 2f, 0f, true, true, -2.83f, -2.83f)
                lineToRelative(8.49f, -8.48f)
            }
        }.build()
    }

    val Language: ImageVector by lazy {
        ImageVector.Builder("Language", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, -20f)
                moveTo(2f, 12f)
                horizontalLineToRelative(20f)
                moveTo(12f, 2f)
                curveToRelative(2.8f, 3f, 4f, 6.5f, 4f, 10f)
                reflectiveCurveToRelative(-1.2f, 7f, -4f, 10f)
                moveTo(12f, 2f)
                curveToRelative(-2.8f, 3f, -4f, 6.5f, -4f, 10f)
                reflectiveCurveToRelative(1.2f, 7f, 4f, 10f)
            }
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder("Palette", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
                curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
                curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(16f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
                close()
                moveTo(6.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
                reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
                reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
                close()
                moveTo(9.5f, 8f)
                curveTo(8.67f, 8f, 8f, 7.33f, 8f, 6.5f)
                reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
                reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
                reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
                close()
                moveTo(14.5f, 8f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
                reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
                reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
                close()
                moveTo(17.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
                reflectiveCurveTo(19f, 9.67f, 19f, 10.5f)
                reflectiveCurveTo(18.33f, 12f, 17.5f, 12f)
                close()
            }
        }.build()
    }

    val Spark: ImageVector by lazy {
        ImageVector.Builder("Spark", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(14.3f, 8.7f)
                lineTo(21f, 11f)
                lineTo(14.3f, 13.3f)
                lineTo(12f, 20f)
                lineTo(9.7f, 13.3f)
                lineTo(3f, 11f)
                lineTo(9.7f, 8.7f)
                close()
            }
        }.build()
    }

    val Tag: ImageVector by lazy {
        ImageVector.Builder("Tag", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 9f)
                horizontalLineToRelative(16f)
                moveTo(4f, 15f)
                horizontalLineToRelative(16f)
                moveTo(10f, 3f)
                lineTo(8f, 21f)
                moveTo(16f, 3f)
                lineTo(14f, 21f)
            }
        }.build()
    }

    val Pin: ImageVector by lazy {
        ImageVector.Builder("Pin", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 12f)
                verticalLineTo(4f)
                horizontalLineTo(17f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
                verticalLineTo(12f)
                lineTo(6f, 14f)
                verticalLineTo(16f)
                horizontalLineTo(11.2f)
                verticalLineTo(22f)
                horizontalLineTo(12.8f)
                verticalLineTo(16f)
                horizontalLineTo(18f)
                verticalLineTo(14f)
                lineTo(16f, 12f)
                close()
            }
        }.build()
    }

    val Copy: ImageVector by lazy {
        ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(12f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(11f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
        }.build()
    }

    val DoneAll: ImageVector by lazy {
        ImageVector.Builder("DoneAll", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 13f)
                lineToRelative(4f, 4f)
                lineToRelative(9f, -9f)
                moveTo(9f, 16f)
                lineToRelative(1.5f, 1.5f)
                lineToRelative(9.5f, -9.5f)
            }
        }.build()
    }
}
