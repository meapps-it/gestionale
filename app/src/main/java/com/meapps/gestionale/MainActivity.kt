package com.meapps.gestionale

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.meapps.gestionale.ui.GestionaleApp
import com.meapps.gestionale.ui.theme.GestionaleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            var theme by remember { mutableStateOf(prefs.getString("theme", "Sistema") ?: "Sistema") }
            GestionaleTheme(theme) {
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                GestionaleApp(theme) { value -> theme=value; prefs.edit().putString("theme",value).apply() }
            }
        }
    }
}
