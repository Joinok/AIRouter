package com.airouter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.airouter.ui.navigation.AppNavigation
import com.airouter.ui.theme.AiRouterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AiRouterTheme {
                AppNavigation()
            }
        }
    }
}

/**
 * 在 View 层读取 IME 高度，绕过 NavHost 对 insets 的消费。
 */
@Composable
fun rememberImeHeight(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val imeHeightPx = remember { mutableStateOf(0) }

    SideEffect {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            imeHeightPx.value = ime.bottom
            ViewCompat.onApplyWindowInsets(v, insets)
        }
        view.requestApplyInsets()
    }

    return with(density) { imeHeightPx.value.toDp() }
}
