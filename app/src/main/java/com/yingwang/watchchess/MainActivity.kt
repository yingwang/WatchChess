package com.yingwang.watchchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yingwang.watchchess.ui.GameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch screen (the app icon on black) has to be in place before the activity is
        // created; it then hands over to the app's own theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            GameScreen()
        }
    }
}
