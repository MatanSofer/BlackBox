package com.blackbox.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.blackbox.ui.BlackBoxApp
import com.blackbox.ui.theme.BlackBoxTheme

/**
 * Single activity for the BlackBox app.
 *
 * Sets up edge-to-edge display, applies [BlackBoxTheme], and delegates
 * to the shared [BlackBoxApp] composable for all UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BlackBoxTheme {
                BlackBoxApp()
            }
        }
    }
}
