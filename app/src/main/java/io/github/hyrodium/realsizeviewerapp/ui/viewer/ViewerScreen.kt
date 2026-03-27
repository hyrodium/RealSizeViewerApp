package io.github.hyrodium.realsizeviewerapp.ui.viewer

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.exp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyrodium.realsizeviewerapp.R
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val appMode by viewModel.appMode.collectAsState()
    val calibrationFactorX by viewModel.calibrationFactorX.collectAsState()
    val calibrationFactorY by viewModel.calibrationFactorY.collectAsState()
    val zoomFactor by viewModel.zoomFactor.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val svgOffset by viewModel.svgOffset.collectAsState()
    val svgRotation by viewModel.svgRotation.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val serverUpdateProposal by viewModel.serverUpdateProposal.collectAsState()
    val isAtMachineDpi by viewModel.isAtMachineDpi.collectAsState()
    val isServerCalibrationAvailable by viewModel.isServerCalibrationAvailable.collectAsState()
    val pdfViewportBitmap by viewModel.pdfViewportBitmap.collectAsState()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // 端末姿勢をコンポジション中に同期取得（LaunchedEffect の非同期遅延なし）
    val currentScreenRotation = remember(configuration) { getDisplayRotation(context) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            if (mimeType == "application/pdf") viewModel.loadPdfFromUri(it)
            else viewModel.loadSvgFromUri(it, currentScreenRotation)
        }
    }

    val displayMetrics = LocalContext.current.resources.displayMetrics

    // 全画面表示モード: システムバー（ステータスバー・ナビゲーションバー）の制御 + スリープ回避
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(appMode) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (appMode == AppMode.FULLSCREEN ||
            appMode == AppMode.CALIBRATION_X ||
            appMode == AppMode.CALIBRATION_Y
        ) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        // 全画面表示モードのときのみスリープ回避
        if (appMode == AppMode.FULLSCREEN) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 全画面モード中にシステムバーが表示されたら3秒後に自動隠し
    // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE は「コンテンツタッチで隠れる」仕様のため、時間制御を追加
    val density = LocalDensity.current
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    LaunchedEffect(appMode, navBarBottomPx, statusBarTopPx) {
        val isHidingMode = appMode == AppMode.FULLSCREEN ||
                appMode == AppMode.CALIBRATION_X ||
                appMode == AppMode.CALIBRATION_Y
        if (isHidingMode && (navBarBottomPx > 0 || statusBarTopPx > 0)) {
            delay(3000L)
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView)
                    .hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 全画面表示モードでの終了ボタン表示制御
    var showFullscreenExitButton by remember { mutableStateOf(false) }

    // 全画面表示モードに入ったときに一時的に表示するヒント
    var showFullscreenHint by remember { mutableStateOf(false) }
    LaunchedEffect(appMode) {
        if (appMode == AppMode.FULLSCREEN) {
            showFullscreenHint = true
            delay(3000)
            showFullscreenHint = false
        }
    }

    // 設定ドロップダウンの表示制御
    var showSettings by remember { mutableStateOf(false) }

    // 拡大率ドロップダウンの表示制御
    var showZoomMenu by remember { mutableStateOf(false) }

    // 送信同意ダイアログの表示制御
    var showUploadDialog by remember { mutableStateOf(false) }

    // デバッグモード用の状態（キャリブレーションボタン3連打で切替）
    var debugTapCount by remember { mutableStateOf(0) }
    var lastDebugTapTime by remember { mutableStateOf(0L) }
    var showDebugInfo by remember { mutableStateOf(false) }

    // デフォルトSVGを起動時に一度だけ読み込む
    LaunchedEffect(Unit) {
        viewModel.loadDefaultSvg(currentScreenRotation)
    }

    // 画面回転をViewModelに通知（PDFビューポートの再レンダリングに使用）
    LaunchedEffect(currentScreenRotation) {
        viewModel.setScreenRotation(currentScreenRotation)
    }

    // アップロード成功時にダイアログを閉じる
    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Success) {
            showUploadDialog = false
        }
    }

    // DocumentCanvasの共通パラメータをまとめるヘルパー
    @Composable
    fun DocumentCanvasWithState(
        document: ViewerDocument,
        currentAppMode: AppMode,
        onTripleTap: () -> Unit,
        onDoubleTap: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        DocumentCanvas(
            document = document,
            appMode = currentAppMode,
            offset = svgOffset,
            rotation = svgRotation,
            screenRotation = currentScreenRotation,
            calibrationFactorX = calibrationFactorX,
            calibrationFactorY = calibrationFactorY,
            zoomFactor = zoomFactor,
            backgroundColor = backgroundColor,
            onOffsetChange = { viewModel.updateSvgOffset(it) },
            onRotationChange = { viewModel.updateSvgRotation(it) },
            onCalibrationFactorChange = { viewModel.updateCalibrationFactor(it) },
            onTripleTap = onTripleTap,
            onDoubleTap = onDoubleTap,
            pdfViewportBitmap = pdfViewportBitmap,
            onScreenSizeChanged = { w, h -> viewModel.setScreenSize(w, h) },
            modifier = modifier
        )
    }

    when (appMode) {
        AppMode.FULLSCREEN -> {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is UiState.Success -> {
                        DocumentCanvasWithState(
                            document = state.document,
                            currentAppMode = appMode,
                            onTripleTap = { showFullscreenExitButton = !showFullscreenExitButton },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor)
                        )
                    }
                }

                // 全画面モード突入時のヒント（3秒後に自動消去）
                AnimatedVisibility(
                    visible = showFullscreenHint,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier.padding(bottom = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = stringResource(R.string.fullscreen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // 3回タップで表示される終了ボタン
                AnimatedVisibility(
                    visible = showFullscreenExitButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.setAppMode(AppMode.ALIGNMENT)
                                showFullscreenExitButton = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.fullscreen_exit_description),
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }

        AppMode.CALIBRATION_X, AppMode.CALIBRATION_Y -> {
            val isXMode = appMode == AppMode.CALIBRATION_X
            val currentFactor = if (isXMode) calibrationFactorX else calibrationFactorY

            // スライダー位置 (範囲 -1..1、離したとき0にリセット)
            var sliderPosition by remember(appMode) { mutableStateOf(0f) }
            // 乗数: exp(s^3/10)（s=±1 のとき ≈×0.905/×1.105）
            val multiplier = exp(sliderPosition * sliderPosition * sliderPosition / 10.0).toFloat()
            val effectiveFactor = currentFactor * multiplier

            // 数値直接入力の状態
            var isEditingValue by remember(appMode) { mutableStateOf(false) }
            var editText by remember(appMode) { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is UiState.Success -> {
                        DocumentCanvasWithState(
                            document = state.document,
                            currentAppMode = appMode,
                            onTripleTap = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_document_loaded),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 中央オーバーレイ: 方向表示 + スケール値 + スライダー
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 方向矢印 + ラベル
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isXMode) Icons.Filled.SwapHoriz else Icons.Filled.SwapVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(if (isXMode) R.string.calibration_x_label else R.string.calibration_y_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // スケール値の表示 / 直接入力
                        if (isEditingValue) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                label = { Text("Scale ${if (isXMode) "X" else "Y"}") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        editText.toFloatOrNull()?.let { value ->
                                            if (isXMode) viewModel.setCalibrationFactorX(value)
                                            else viewModel.setCalibrationFactorY(value)
                                        }
                                        sliderPosition = 0f
                                        isEditingValue = false
                                        focusManager.clearFocus()
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .width(200.dp)
                                    .focusRequester(focusRequester)
                            )
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            // タップで数値入力モードへ
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable {
                                        editText = "%.4f".format(currentFactor)
                                        isEditingValue = true
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Scale ${if (isXMode) "X" else "Y"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "%.4f".format(effectiveFactor),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = stringResource(R.string.calibration_tap_to_input),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // スライダー（離すと原点リセット、非線形乗数）
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Slider(
                                value = sliderPosition,
                                onValueChange = { sliderPosition = it },
                                valueRange = -1f..1f,
                                onValueChangeFinished = {
                                    val s = sliderPosition
                                    val newFactor = currentFactor * exp(s * s * s / 10.0).toFloat()
                                    if (isXMode) viewModel.setCalibrationFactorX(newFactor)
                                    else viewModel.setCalibrationFactorY(newFactor)
                                    sliderPosition = 0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "≈×0.905",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "×1.00",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "≈×1.105",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 完了ボタン
                SmallFloatingActionButton(
                    onClick = { viewModel.setAppMode(AppMode.ALIGNMENT) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = stringResource(R.string.done)
                    )
                }
            }
        }

        AppMode.ALIGNMENT, AppMode.CALIBRATION -> {
            val isCalibrating = appMode == AppMode.CALIBRATION

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is UiState.Success -> {
                        DocumentCanvasWithState(
                            document = state.document,
                            currentAppMode = appMode,
                            onTripleTap = {},
                            onDoubleTap = { if (!isCalibrating) viewModel.snapRotationBy15Degrees() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is UiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // デバッグ用: デバイス・DPI情報表示（デバッグモード有効時のみ）
                if (!isCalibrating && showDebugInfo) {
                    Text(
                        text = "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                            "xdpi=%.1f  ydpi=%.1f  densityDpi=%d  density=%.2f\n".format(
                                displayMetrics.xdpi,
                                displayMetrics.ydpi,
                                displayMetrics.densityDpi,
                                displayMetrics.density
                            ) +
                            "factorX=%.4f  factorY=%.4f  zoom=%.1f%%\n".format(
                                calibrationFactorX,
                                calibrationFactorY,
                                zoomFactor * 100,
                            ) +
                            "rotation=%.1f°  offsetX=%.2fmm  offsetY=%.2fmm\n".format(
                                svgRotation,
                                svgOffset.x,
                                svgOffset.y
                            ) +
                            "screenRotation=%d (0=自然 1=90CW 2=180 3=90CCW)".format(
                                currentScreenRotation
                            ),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 8.dp, bottom = 80.dp)
                    )
                }

                // 右下フローティングボタン群
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // 全画面表示ボタン
                    SmallFloatingActionButton(
                        onClick = { viewModel.setAppMode(AppMode.FULLSCREEN) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = stringResource(R.string.fab_fullscreen),
                            modifier = Modifier
                        )
                    }
                    // ファイルを開くボタン
                    SmallFloatingActionButton(
                        onClick = { filePickerLauncher.launch(arrayOf("image/svg+xml", "application/pdf")) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = stringResource(R.string.fab_open_file),
                            modifier = Modifier
                        )
                    }
                    // 拡大率ボタン
                    Box {
                        SmallFloatingActionButton(
                            onClick = { showZoomMenu = !showZoomMenu }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ZoomIn,
                                contentDescription = stringResource(R.string.fab_zoom),
                                modifier = Modifier
                            )
                        }
                        ZoomDropdown(
                            expanded = showZoomMenu,
                            onDismiss = { showZoomMenu = false },
                            zoomFactor = zoomFactor,
                            onZoomFactorChange = { viewModel.setZoomFactor(it) }
                        )
                    }
                    // 設定ボタン
                    Box {
                        SmallFloatingActionButton(
                            onClick = { showSettings = !showSettings }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = stringResource(R.string.fab_background_color),
                                modifier = Modifier
                            )
                        }
                        SettingsDropdown(
                            expanded = showSettings,
                            onDismiss = { showSettings = false },
                            backgroundColor = backgroundColor,
                            onBackgroundColorChange = { viewModel.setBackgroundColor(it) }
                        )
                    }
                    // キャリブレーションボタン（ポップアップ付き）
                    Box {
                        SmallFloatingActionButton(
                            onClick = {
                                // 3連打（2秒以内）でデバッグモード切替
                                val now = System.currentTimeMillis()
                                if (now - lastDebugTapTime > 2000L) {
                                    debugTapCount = 1
                                } else {
                                    debugTapCount++
                                }
                                lastDebugTapTime = now
                                if (debugTapCount >= 3) {
                                    showDebugInfo = !showDebugInfo
                                    debugTapCount = 0
                                }
                                viewModel.setAppMode(
                                    if (isCalibrating) AppMode.ALIGNMENT else AppMode.CALIBRATION
                                )
                            },
                            containerColor = if (isCalibrating) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                FloatingActionButtonDefaults.containerColor
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.fab_calibration),
                                modifier = Modifier
                            )
                        }
                        CalibrationDropdown(
                            expanded = isCalibrating,
                            onDismiss = { viewModel.setAppMode(AppMode.ALIGNMENT) },
                            calibrationFactorX = calibrationFactorX,
                            calibrationFactorY = calibrationFactorY,
                            isAtMachineDpi = isAtMachineDpi,
                            isServerCalibrationAvailable = isServerCalibrationAvailable,
                            onResetToMachineDpi = { viewModel.resetToMachineDpi() },
                            onResetToServerCalibration = { viewModel.resetToServerCalibration() },
                            onUpload = {
                                viewModel.setAppMode(AppMode.ALIGNMENT)
                                showUploadDialog = true
                            },
                            onStartXCalibration = {
                                viewModel.setAppMode(AppMode.CALIBRATION_X)
                            },
                            onStartYCalibration = {
                                viewModel.setAppMode(AppMode.CALIBRATION_Y)
                            }
                        )
                    }
                }
            }
        }
    }

    // キャリブレーション結果送信ダイアログ
    if (showUploadDialog) {
        UploadConsentDialog(
            uploading = uploadState is UploadState.Uploading,
            error = (uploadState as? UploadState.Error)?.message,
            onConfirm = { viewModel.uploadCalibration() },
            onDismiss = {
                showUploadDialog = false
                viewModel.resetUploadState()
            }
        )
    }

    // サーバー推奨値更新通知ダイアログ
    serverUpdateProposal?.let { proposal ->
        ServerUpdateDialog(
            proposal = proposal,
            currentFactorX = calibrationFactorX,
            currentFactorY = calibrationFactorY,
            displayXdpi = displayMetrics.xdpi,
            onApply = { viewModel.applyServerCalibration() },
            onDismiss = { viewModel.dismissServerUpdate() }
        )
    }
}

@Composable
private fun UploadConsentDialog(
    uploading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!uploading) onDismiss() },
        title = { Text(stringResource(R.string.upload_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.upload_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.upload_dialog_data_list),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        text = stringResource(R.string.error_prefix, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (uploading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.sending), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !uploading
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.send))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !uploading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ServerUpdateDialog(
    proposal: ServerUpdateProposal,
    currentFactorX: Float,
    currentFactorY: Float,
    displayXdpi: Float,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val response = proposal.response
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.server_update_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.server_update_factor_x, currentFactorX, response.medianFactorX),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = stringResource(R.string.server_update_factor_y, currentFactorY, response.medianFactorY),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = if (response.lowSampleWarning) {
                        stringResource(R.string.server_update_sample_count_low, response.sampleCount)
                    } else {
                        stringResource(R.string.server_update_sample_count, response.sampleCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (response.lowSampleWarning) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (proposal.xdpiMismatch) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.server_update_xdpi_mismatch, displayXdpi, response.medianReportedXdpi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.skip))
            }
        }
    )
}

@Composable
private fun ZoomDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    zoomFactor: Float,
    onZoomFactorChange: (Float) -> Unit
) {
    val focusManager = LocalFocusManager.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(200.dp)
    ) {
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.zoom_label), modifier = Modifier.weight(1f))
                    val percentDisplay = zoomFactor * 100f
                    var textValue by remember(zoomFactor) {
                        val s = "%.2f".format(percentDisplay).trimEnd('0').trimEnd('.')
                        mutableStateOf(s)
                    }
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            textValue = newValue.filter { it.isDigit() || it == '.' }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                textValue.toFloatOrNull()?.let { onZoomFactorChange(it / 100f) }
                                focusManager.clearFocus()
                            }
                        ),
                        suffix = { Text("%") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true
                    )
                }
            },
            onClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    calibrationFactorX: Float,
    calibrationFactorY: Float,
    isAtMachineDpi: Boolean,
    isServerCalibrationAvailable: Boolean,
    onResetToMachineDpi: () -> Unit,
    onResetToServerCalibration: () -> Unit,
    onUpload: () -> Unit,
    onStartXCalibration: () -> Unit,
    onStartYCalibration: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(260.dp)
    ) {
        // 補正係数の表示
        DropdownMenuItem(
            text = {
                Text(
                    text = "factorX=%.4f\nfactorY=%.4f".format(
                        calibrationFactorX, calibrationFactorY
                    ),
                    fontSize = 12.sp
                )
            },
            onClick = {},
            enabled = false
        )

        // X方向キャリブレーション
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calibration_x_label)) },
            onClick = onStartXCalibration,
            leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) }
        )
        // Y方向キャリブレーション
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calibration_y_label)) },
            onClick = onStartYCalibration,
            leadingIcon = { Icon(Icons.Filled.SwapVert, contentDescription = null) }
        )

        // リセット・送信ボタン
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calibration_reset_machine_dpi)) },
            onClick = onResetToMachineDpi,
            leadingIcon = { Icon(Icons.Filled.Memory, contentDescription = null) },
            enabled = !isAtMachineDpi
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calibration_reset_server)) },
            onClick = onResetToServerCalibration,
            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
            enabled = isServerCalibrationAvailable
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calibration_upload)) },
            onClick = onUpload,
            leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) }
        )
    }
}

// 背景色プリセット
private val backgroundColorPresets = listOf(
    Color.White to "白",
    Color.Black to "黒",
    Color(0xFFF5F5F5) to "ライトグレー",
    Color(0xFF333333) to "ダークグレー",
    Color(0xFFFFF8E1) to "クリーム",
    Color(0xFFE3F2FD) to "ライトブルー",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    backgroundColor: Color,
    onBackgroundColorChange: (Color) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(280.dp)
    ) {
        // 背景色
        DropdownMenuItem(
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.background_color_label))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        backgroundColorPresets.forEach { (color, _) ->
                            val isSelected = backgroundColor == color
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier.border(1.dp, Color.Gray, CircleShape)
                                        }
                                    )
                                    .clickable { onBackgroundColorChange(color) }
                            )
                        }
                    }
                }
            },
            onClick = {}
        )

    }
}

/** Display.getRotation() の値 (0=自然, 1=90°CW, 2=180°, 3=90°CCW) を返す */
@Suppress("DEPRECATION")
private fun getDisplayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: 0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
