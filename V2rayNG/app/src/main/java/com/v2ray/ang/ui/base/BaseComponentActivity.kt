package com.v2ray.ang.ui.base

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.MyContextWrapper

abstract class BaseComponentActivity : ComponentActivity() {

    /**
     * Set to true by activities whose compact-mode content applies its own circular
     * safe area. Prevents double insetting. Has no effect on non-compact screens.
     */
    protected open val managesOwnCompactSafeArea: Boolean = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(applyCompactSafeArea = !managesOwnCompactSafeArea) {
                ScreenContent()
            }
        }
    }

    @Composable
    protected abstract fun ScreenContent()
}