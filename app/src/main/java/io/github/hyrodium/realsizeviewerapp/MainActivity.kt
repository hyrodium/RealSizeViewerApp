package io.github.hyrodium.realsizeviewerapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.hyrodium.realsizeviewerapp.ui.theme.RealSizeViewerTheme
import io.github.hyrodium.realsizeviewerapp.ui.viewer.ViewerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 画面回転時のOSアニメーションを即切り替えに変更（クロスフェード無効）
        window.attributes = window.attributes.also {
            it.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
        }

        setContent {
            RealSizeViewerTheme {
                ViewerScreen()
            }
        }
    }
}
