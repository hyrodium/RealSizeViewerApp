package io.github.hyrodium.realsizeviewerapp.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caverock.androidsvg.SVG
import io.github.hyrodium.realsizeviewerapp.BuildConfig
import io.github.hyrodium.realsizeviewerapp.R
import io.github.hyrodium.realsizeviewerapp.data.CalibrationApiService
import io.github.hyrodium.realsizeviewerapp.data.CalibrationDataStore
import io.github.hyrodium.realsizeviewerapp.data.CalibrationRequest
import io.github.hyrodium.realsizeviewerapp.data.PdfRendererHolder
import io.github.hyrodium.realsizeviewerapp.data.PdfRepository
import io.github.hyrodium.realsizeviewerapp.data.RecommendedCalibrationResponse
import io.github.hyrodium.realsizeviewerapp.data.SvgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.floor

sealed class ViewerDocument {
    data class SvgDocument(val svg: SVG) : ViewerDocument()
    data class PdfDocument(
        val pageSizesPt: List<Pair<Int, Int>>,
        val renderXdpi: Float,
        val renderYdpi: Float,
    ) : ViewerDocument()
}

sealed interface UiState {
    data object Loading : UiState
    data class Success(val document: ViewerDocument) : UiState
    data class Error(val message: String) : UiState
}

sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data object Success : UploadState
    data class Error(val message: String) : UploadState
}

/**
 * サーバーからの推奨値更新提案
 * @param response サーバーレスポンス
 * @param xdpiMismatch 実機のxdpiとDBのrecommended_xdpiが2%超乖離している場合true
 * @param deviceXdpi 実機が報告するxdpi
 */
data class ServerUpdateProposal(
    val response: RecommendedCalibrationResponse,
    val xdpiMismatch: Boolean,
    val deviceXdpi: Float,
)

enum class AppMode {
    FULLSCREEN,
    ALIGNMENT,
    CALIBRATION,
    CALIBRATION_X,
    CALIBRATION_Y
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val svgRepository: SvgRepository,
    private val pdfRepository: PdfRepository,
    private val calibrationDataStore: CalibrationDataStore,
    private val calibrationApiService: CalibrationApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _appMode = MutableStateFlow(AppMode.ALIGNMENT)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _calibrationFactorX = MutableStateFlow(1.0f)
    val calibrationFactorX: StateFlow<Float> = _calibrationFactorX.asStateFlow()

    private val _calibrationFactorY = MutableStateFlow(1.0f)
    val calibrationFactorY: StateFlow<Float> = _calibrationFactorY.asStateFlow()

    private val _zoomFactor = MutableStateFlow(1.0f)
    val zoomFactor: StateFlow<Float> = _zoomFactor.asStateFlow()

    private val _backgroundColor = MutableStateFlow(Color.White)
    val backgroundColor: StateFlow<Color> = _backgroundColor.asStateFlow()

    private val _svgOffset = MutableStateFlow(Offset.Zero)  // mm単位（画面センター相対）
    val svgOffset: StateFlow<Offset> = _svgOffset.asStateFlow()

    private val _svgRotation = MutableStateFlow(0f)
    val svgRotation: StateFlow<Float> = _svgRotation.asStateFlow()

    // 機械DPI（factorX=1.0, factorY=1.0）が既に設定済みか
    val isAtMachineDpi: StateFlow<Boolean> = combine(
        _calibrationFactorX, _calibrationFactorY
    ) { x, y ->
        x == 1.0f && y == 1.0f
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // サーバー推奨値が存在し、かつ現在値と異なる場合にtrue（ボタンのenabled制御用）
    val isServerCalibrationAvailable: StateFlow<Boolean> = combine(
        _calibrationFactorX,
        _calibrationFactorY,
        calibrationDataStore.lastServerFactorX,
        calibrationDataStore.lastServerFactorY,
    ) { currentX, currentY, serverX, serverY ->
        serverX != null && serverY != null && (currentX != serverX || currentY != serverY)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // アップロード状態
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // サーバー推奨値更新提案（非nullの場合にダイアログ表示）
    private val _serverUpdateProposal = MutableStateFlow<ServerUpdateProposal?>(null)
    val serverUpdateProposal: StateFlow<ServerUpdateProposal?> = _serverUpdateProposal.asStateFlow()

    // PDF ビューポートレンダリング関連
    private val _pdfRendererHolder = MutableStateFlow<PdfRendererHolder?>(null)

    private val _pdfViewportBitmap = MutableStateFlow<Bitmap?>(null)
    val pdfViewportBitmap: StateFlow<Bitmap?> = _pdfViewportBitmap.asStateFlow()

    private val _screenWidth = MutableStateFlow(0)
    private val _screenHeight = MutableStateFlow(0)
    private val _screenRotation = MutableStateFlow(0)

    fun setScreenSize(w: Int, h: Int) {
        _screenWidth.value = w
        _screenHeight.value = h
    }

    fun setScreenRotation(r: Int) {
        _screenRotation.value = r
    }

    init {
        viewModelScope.launch {
            // DataStore から保存済みキャリブレーション値を読み込む
            val savedX = calibrationDataStore.calibrationFactorX.first()
            val savedY = calibrationDataStore.calibrationFactorY.first()
            if (savedX != null) _calibrationFactorX.value = savedX
            if (savedY != null) _calibrationFactorY.value = savedY

            // バックグラウンドでサーバーから推奨値を取得
            fetchRecommendedCalibration()
        }

        // いずれかの状態変化でPDFビューポートを再レンダリング
        viewModelScope.launch {
            merge(
                _pdfRendererHolder.map { },
                _screenWidth.map { },
                _screenHeight.map { },
                _screenRotation.map { },
                _svgOffset.map { },
                _svgRotation.map { },
                _appMode.map { },
                _calibrationFactorX.map { },
                _calibrationFactorY.map { },
                _zoomFactor.map { },
                _backgroundColor.map { },
            ).conflate()
                .collect { doRenderPdf() }
        }
    }

    private suspend fun doRenderPdf() {
        val holder = _pdfRendererHolder.value ?: return
        val w = _screenWidth.value
        val h = _screenHeight.value
        if (w == 0 || h == 0) return
        val dm = context.resources.displayMetrics
        val effectiveRot = _svgRotation.value - _screenRotation.value * 90f
        val pxPerMmX = dm.xdpi / 25.4f * _calibrationFactorX.value * _zoomFactor.value
        val pxPerMmY = dm.ydpi / 25.4f * _calibrationFactorY.value * _zoomFactor.value
        val offsetPx = Offset(
            x = _svgOffset.value.x * pxPerMmX,
            y = _svgOffset.value.y * pxPerMmY,
        )
        val bitmap = withContext(Dispatchers.IO) {
            holder.renderViewport(
                screenWidth = w,
                screenHeight = h,
                offset = offsetPx,
                effectiveRotation = effectiveRot,
                xdpi = dm.xdpi,
                ydpi = dm.ydpi,
                calibrationFactorX = _calibrationFactorX.value,
                calibrationFactorY = _calibrationFactorY.value,
                zoomFactor = _zoomFactor.value,
                backgroundColor = _backgroundColor.value,
            )
        }
        _pdfViewportBitmap.value = bitmap
    }

    fun loadDefaultSvg(screenRotation: Int) {
        viewModelScope.launch {
            try {
                val svg = svgRepository.loadSvgFromAssets("background.svg")
                _uiState.value = UiState.Success(ViewerDocument.SvgDocument(svg))
                // loadSvgFromUri と同様に、端末姿勢に合わせて物理回転角を設定
                _svgRotation.value = when (screenRotation) {
                    1 -> 90f
                    2 -> 180f
                    3 -> -90f
                    else -> 0f
                }
                // SVG座標 (45mm, 955mm) が画面左下に来るようにデフォルトオフセットをmm単位で計算
                // 画面中央を原点として、SVG原点(左上)の位置をmmで表す
                val dm = context.resources.displayMetrics
                val pxPerMmX = dm.xdpi / 25.4f * _calibrationFactorX.value * _zoomFactor.value
                val pxPerMmY = dm.ydpi / 25.4f * _calibrationFactorY.value * _zoomFactor.value
                _svgOffset.value = Offset(
                    x = -dm.widthPixels / 2f / pxPerMmX - 45f,
                    y = dm.heightPixels / 2f / pxPerMmY - 955f,
                )
            } catch (_: Exception) {
                // assetsが存在しない場合はEmptyのまま
            }
        }
    }

    private fun fetchRecommendedCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recommended = calibrationApiService.getRecommended(
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                )
                if (recommended != null) {
                    calibrationDataStore.saveServerValues(
                        recommended.medianFactorX.toFloat(),
                        recommended.medianFactorY.toFloat(),
                    )
                    // 現在値と比較して1%超の差があればダイアログ提案
                    val currentX = _calibrationFactorX.value
                    val currentY = _calibrationFactorY.value
                    val serverX = recommended.medianFactorX.toFloat()
                    val serverY = recommended.medianFactorY.toFloat()
                    val changeX = abs(serverX - currentX) / currentX
                    val changeY = abs(serverY - currentY) / currentY
                    if (changeX > 0.01f || changeY > 0.01f) {
                        // 実機のxdpiとDBのrecommended_xdpiを比較（2%超で不一致とみなす）
                        val deviceXdpi = context.resources.displayMetrics.xdpi
                        val xdpiMismatch = abs(
                            recommended.medianReportedXdpi.toFloat() - deviceXdpi
                        ) / deviceXdpi > 0.02f
                        _serverUpdateProposal.value = ServerUpdateProposal(
                            response = recommended,
                            xdpiMismatch = xdpiMismatch,
                            deviceXdpi = deviceXdpi,
                        )
                    }
                }
            } catch (_: Exception) {
                // ネットワークエラーはサイレントに無視
            }
        }
    }

    fun setAppMode(mode: AppMode) {
        val wasCalibrating = _appMode.value in listOf(
            AppMode.CALIBRATION, AppMode.CALIBRATION_X, AppMode.CALIBRATION_Y
        )
        _appMode.value = mode
        // キャリブレーション終了時にDataStoreへ保存
        if (wasCalibrating && mode == AppMode.ALIGNMENT) {
            saveCalibrationToDataStore()
        }
    }

    fun updateCalibrationFactor(delta: Float) {
        when (_appMode.value) {
            AppMode.CALIBRATION_X -> _calibrationFactorX.value += delta
            AppMode.CALIBRATION_Y -> _calibrationFactorY.value += delta
            else -> {}
        }
    }

    fun setCalibrationFactorX(value: Float) {
        _calibrationFactorX.value = value
    }

    fun setCalibrationFactorY(value: Float) {
        _calibrationFactorY.value = value
    }

    // 機械DPI採用: 補正係数をすべて1.0にリセット
    fun resetToMachineDpi() {
        _calibrationFactorX.value = 1.0f
        _calibrationFactorY.value = 1.0f
        saveCalibrationToDataStore()
    }

    // DataStoreに保存済みのサーバー推奨値を適用する
    fun resetToServerCalibration() {
        viewModelScope.launch {
            val serverX = calibrationDataStore.lastServerFactorX.first() ?: return@launch
            val serverY = calibrationDataStore.lastServerFactorY.first() ?: return@launch
            _calibrationFactorX.value = serverX
            _calibrationFactorY.value = serverY
            saveCalibrationToDataStore()
        }
    }

    // サーバー推奨値を適用する
    fun applyServerCalibration() {
        val proposal = _serverUpdateProposal.value ?: return
        _calibrationFactorX.value = proposal.response.medianFactorX.toFloat()
        _calibrationFactorY.value = proposal.response.medianFactorY.toFloat()
        _serverUpdateProposal.value = null
        saveCalibrationToDataStore()
    }

    fun dismissServerUpdate() {
        _serverUpdateProposal.value = null
    }

    fun setZoomFactor(factor: Float) {
        _zoomFactor.value = factor.coerceIn(0.01f, 10f)
    }

    fun setBackgroundColor(color: Color) {
        _backgroundColor.value = color
    }

    fun updateSvgOffset(delta: Offset) {
        _svgOffset.value += delta
    }

    fun updateSvgRotation(delta: Float) {
        _svgRotation.value += delta
    }

    // 現在の回転角に15°加えた値を15°刻みにスナップする
    fun snapRotationBy15Degrees() {
        _svgRotation.value = floor((_svgRotation.value + 15f) / 15f) * 15f
    }

    fun loadSvgFromUri(uri: Uri, screenRotation: Int = 0) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // PDFレンダラーを閉じる
            _pdfRendererHolder.value?.close()
            _pdfRendererHolder.value = null
            _pdfViewportBitmap.value = null
            // ファイルを開くタイミングでUIの向きに合わせてSVGを自動回転
            _svgRotation.value = when (screenRotation) {
                1 -> 90f
                2 -> 180f
                3 -> -90f
                else -> 0f
            }
            val dmSvg = context.resources.displayMetrics
            val pxPerMmXSvg = dmSvg.xdpi / 25.4f * _calibrationFactorX.value * _zoomFactor.value
            val pxPerMmYSvg = dmSvg.ydpi / 25.4f * _calibrationFactorY.value * _zoomFactor.value
            _svgOffset.value = Offset(
                x = -dmSvg.widthPixels / 2f / pxPerMmXSvg,
                y = -dmSvg.heightPixels / 2f / pxPerMmYSvg,
            )
            try {
                val svg = svgRepository.loadSvgFromUri(uri)
                _uiState.value = UiState.Success(ViewerDocument.SvgDocument(svg))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: context.getString(R.string.error_load_svg))
            }
        }
    }

    fun loadPdfFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val dmPdf = context.resources.displayMetrics
            val pxPerMmXPdf = dmPdf.xdpi / 25.4f * _calibrationFactorX.value * _zoomFactor.value
            val pxPerMmYPdf = dmPdf.ydpi / 25.4f * _calibrationFactorY.value * _zoomFactor.value
            _svgOffset.value = Offset(
                x = -dmPdf.widthPixels / 2f / pxPerMmXPdf,
                y = -dmPdf.heightPixels / 2f / pxPerMmYPdf,
            )
            _svgRotation.value = 0f
            _pdfRendererHolder.value?.close()
            _pdfRendererHolder.value = null
            _pdfViewportBitmap.value = null
            try {
                val holder = pdfRepository.openPdf(uri)
                val dm = context.resources.displayMetrics
                _uiState.value = UiState.Success(
                    ViewerDocument.PdfDocument(holder.pageSizesPt, dm.xdpi, dm.ydpi)
                )
                // _pdfRendererHolder への代入が merge を発火させ初回レンダリングをトリガー
                _pdfRendererHolder.value = holder
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: context.getString(R.string.error_load_pdf))
            }
        }
    }

    // キャリブレーション結果をサーバーに送信する
    fun uploadCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uploadState.value = UploadState.Uploading
            try {
                val displayMetrics = context.resources.displayMetrics
                val request = CalibrationRequest(
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                    reportedXdpi = displayMetrics.xdpi.toDouble(),
                    reportedYdpi = displayMetrics.ydpi.toDouble(),
                    calibrationFactorX = _calibrationFactorX.value.toDouble(),
                    calibrationFactorY = _calibrationFactorY.value.toDouble(),
                    appVersion = BuildConfig.VERSION_NAME,
                    buildSource = BuildConfig.BUILD_FLAVOR,
                )
                calibrationApiService.postCalibration(request)
                _uploadState.value = UploadState.Success
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: context.getString(R.string.error_upload))
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    private fun saveCalibrationToDataStore() {
        viewModelScope.launch {
            calibrationDataStore.saveCalibration(
                _calibrationFactorX.value,
                _calibrationFactorY.value,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        _pdfRendererHolder.value?.close()
    }
}
