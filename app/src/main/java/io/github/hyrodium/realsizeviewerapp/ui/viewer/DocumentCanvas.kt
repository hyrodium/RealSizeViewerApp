package io.github.hyrodium.realsizeviewerapp.ui.viewer

import android.graphics.Bitmap
import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext

@Composable
fun DocumentCanvas(
    document: ViewerDocument,
    appMode: AppMode,
    offset: Offset,       // ワールド座標（画面センター相対、mm単位）
    rotation: Float,      // 物理回転角（端末姿勢によらず不変）
    screenRotation: Int,  // Display.getRotation() の値 (0/1/2/3)
    calibrationFactorX: Float,
    calibrationFactorY: Float,
    zoomFactor: Float,
    backgroundColor: Color,
    onOffsetChange: (Offset) -> Unit,
    onRotationChange: (Float) -> Unit,
    onCalibrationFactorChange: (Float) -> Unit,
    onTripleTap: () -> Unit,
    onDoubleTap: () -> Unit = {},
    pdfViewportBitmap: Bitmap? = null,
    onScreenSizeChanged: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val xdpi = displayMetrics.xdpi
    val ydpi = displayMetrics.ydpi

    // 全画面表示モード用: 3回連続タップ検出
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // SVGをPictureに事前レンダリング（PDF時はnull）
    val svgDoc = document as? ViewerDocument.SvgDocument
    val picture = remember(svgDoc?.svg) {
        svgDoc?.svg?.let { svg ->
            val pic = Picture()
            val svgWidth = if (svg.documentWidth > 0) svg.documentWidth else 300f
            val svgHeight = if (svg.documentHeight > 0) svg.documentHeight else 300f
            val canvas = pic.beginRecording(svgWidth.toInt(), svgHeight.toInt())
            svg.renderToCanvas(canvas)
            pic.endRecording()
            pic
        }
    }

    // スクリーン座標系での実効回転角（物理回転角 - 端末姿勢補正）
    val effectiveScreenRotation = rotation - screenRotation * 90f

    // pointerInputコルーチンはappMode変更時にしか再起動しないため、
    // 最新の値を参照するためにrememberUpdatedStateを使う
    val currentRotation by rememberUpdatedState(effectiveScreenRotation)
    // スクリーンピクセル ↔ mm 変換係数（ドラッグ量のmm換算に使用）
    val currentMmToPixelX by rememberUpdatedState(xdpi * calibrationFactorX * zoomFactor / 25.4f)
    val currentMmToPixelY by rememberUpdatedState(ydpi * calibrationFactorY * zoomFactor / 25.4f)

    val inputModifier = when (appMode) {
        AppMode.ALIGNMENT -> {
            modifier
                .pointerInput(appMode) {
                    detectTransformGestures { _, pan, _, rotationChange ->
                        // スクリーン座標のドラッグを、回転の逆変換で補正してmm単位に換算
                        val angleRad = -currentRotation * Math.PI.toFloat() / 180f
                        val cos = kotlin.math.cos(angleRad)
                        val sin = kotlin.math.sin(angleRad)
                        val adjustedPanMm = Offset(
                            (pan.x * cos - pan.y * sin) / currentMmToPixelX,
                            (pan.x * sin + pan.y * cos) / currentMmToPixelY,
                        )
                        onOffsetChange(adjustedPanMm)
                        onRotationChange(rotationChange)
                    }
                }
                .pointerInput(appMode) {
                    // ダブルタップで15°スナップ回転
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
        }
        AppMode.CALIBRATION -> {
            modifier
                .pointerInput(appMode) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            val delta = (zoom - 1.0f) * 0.01f
                            onCalibrationFactorChange(delta)
                        }
                    }
                }
        }
        AppMode.CALIBRATION_X, AppMode.CALIBRATION_Y -> {
            // ジェスチャー不要。スライダー・数値入力は ViewerScreen 側オーバーレイで処理
            modifier
        }
        AppMode.FULLSCREEN -> {
            modifier
                .pointerInput(appMode) {
                    detectTapGestures {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 500) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = now
                        if (tapCount >= 3) {
                            tapCount = 0
                            onTripleTap()
                        }
                    }
                }
        }
    }

    Canvas(
        modifier = inputModifier.fillMaxSize().onSizeChanged { onScreenSizeChanged(it.width, it.height) }
    ) {
        drawRect(color = backgroundColor)

        when (document) {
            is ViewerDocument.SvgDocument -> {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()

                    // 画面センターを原点にして回転し、ワールドオフセット（mm）をpxに変換して適用
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val mmToPixelX = xdpi * calibrationFactorX * zoomFactor / 25.4f
                    val mmToPixelY = ydpi * calibrationFactorY * zoomFactor / 25.4f
                    canvas.nativeCanvas.translate(centerX, centerY)
                    canvas.nativeCanvas.rotate(effectiveScreenRotation)
                    canvas.nativeCanvas.translate(offset.x * mmToPixelX, offset.y * mmToPixelY)

                    // AndroidSVGは96dpi換算でPictureをレンダリングするため、
                    // 実寸表示にはデバイスDPI/96の係数が必要
                    val scaleX = xdpi * calibrationFactorX * zoomFactor / 96f
                    val scaleY = ydpi * calibrationFactorY * zoomFactor / 96f
                    canvas.nativeCanvas.scale(scaleX, scaleY)
                    picture?.let { canvas.nativeCanvas.drawPicture(it) }

                    canvas.nativeCanvas.restore()
                }
            }
            is ViewerDocument.PdfDocument -> {
                // ビューポートビットマップはすでにスクリーン座標で描かれているため
                // 変換を適用せず (0,0) に描画する
                pdfViewportBitmap?.let { bmp ->
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(bmp, 0f, 0f, null)
                    }
                }
            }
        }
    }
}
