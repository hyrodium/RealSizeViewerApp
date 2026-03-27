package io.github.hyrodium.realsizeviewerapp.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

class PdfRendererHolder(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizesPt: List<Pair<Int, Int>>,  // (width, height) in points (1pt = 1/72 inch)
) : Closeable {

    private val mutex = Mutex()

    suspend fun renderViewport(
        screenWidth: Int,
        screenHeight: Int,
        offset: Offset,
        effectiveRotation: Float,
        xdpi: Float,
        ydpi: Float,
        calibrationFactorX: Float,
        calibrationFactorY: Float,
        zoomFactor: Float,
        backgroundColor: Color,
    ): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(backgroundColor.toArgb())

        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val sx = xdpi * calibrationFactorX * zoomFactor / 72f
        val sy = ydpi * calibrationFactorY * zoomFactor / 72f
        val gapPx = 5f / 25.4f * xdpi * calibrationFactorX * zoomFactor

        var pageOriginX = 0f
        for (i in 0 until renderer.pageCount) {
            val (widthPt, heightPt) = pageSizesPt[i]

            // ページ座標 → スクリーンピクセルへの変換行列
            val m = Matrix()
            m.setScale(sx, sy)
            m.postTranslate(pageOriginX + offset.x, offset.y)
            m.postRotate(effectiveRotation, 0f, 0f)
            m.postTranslate(centerX, centerY)

            val destClip = computeDestClip(m, widthPt, heightPt, screenWidth, screenHeight)
            if (!destClip.isEmpty) {
                mutex.withLock {
                    renderer.openPage(i).use { page ->
                        page.render(bitmap, destClip, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }

            pageOriginX += widthPt * sx + gapPx
        }
        bitmap
    }

    private fun computeDestClip(
        matrix: Matrix,
        widthPt: Int,
        heightPt: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Rect {
        val corners = floatArrayOf(
            0f, 0f,
            widthPt.toFloat(), 0f,
            widthPt.toFloat(), heightPt.toFloat(),
            0f, heightPt.toFloat(),
        )
        matrix.mapPoints(corners)
        val xs = floatArrayOf(corners[0], corners[2], corners[4], corners[6])
        val ys = floatArrayOf(corners[1], corners[3], corners[5], corners[7])
        val rect = Rect(
            xs.min().toInt(), ys.min().toInt(),
            xs.max().toInt() + 1, ys.max().toInt() + 1,
        )
        val screen = Rect(0, 0, screenWidth, screenHeight)
        return if (rect.intersect(screen)) rect else Rect()
    }

    override fun close() {
        renderer.close()
        pfd.close()
    }
}
