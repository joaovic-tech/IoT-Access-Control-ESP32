package com.example.espcamapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.espcamapp.ui.theme.ESPCamAppTheme
import com.example.espcamapp.views.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ESPCamAppTheme {
                AppRoot()
            }
        }
    }
}
