package desktopkit.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import desktopkit.components.DeskIcons

/**
 * The kit's Error, Warning and Info icons as paths a view can draw: the gutter is drawn by the
 * editor's view, in step with its text, and shows the same icons as the rest of the kit.
 */
internal object MarkerIcons {
    private const val GRID = 16f
    private const val STROKE = 1.6f
    private val error by lazy { paths(DeskIcons.Error) }
    private val warning by lazy { paths(DeskIcons.Warning) }
    private val info by lazy { paths(DeskIcons.Info) }

    /** The stroked paths of [icon] on the kit's 16 x 16 grid. */
    private fun paths(icon: ImageVector): List<Path> = icon.root.filterIsInstance<VectorPath>().map { PathParser().addPathNodes(it.pathData).toPath().asAndroidPath() }

    /** Draws the icon of [severity] in [color], [size] pixels square, with its top left at ([x], [y]); [paint] is a stroke paint. */
    fun draw(canvas: Canvas, severity: MarkerSeverity, x: Float, y: Float, size: Float, color: Int, paint: Paint) {
        val scale = size / GRID
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        paint.color = color
        paint.strokeWidth = STROKE
        val icon = when (severity) {
            MarkerSeverity.Error -> error
            MarkerSeverity.Warning -> warning
            MarkerSeverity.Info -> info
        }
        for (path in icon) canvas.drawPath(path, paint)
        canvas.restore()
    }
}
