package com.meapps.scadenzespese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.meapps.scadenzespese.catalogui.CatalogRoot
import com.meapps.scadenzespese.catalogui.CatalogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(15, 23, 42)
        window.navigationBarColor = android.graphics.Color.rgb(55, 65, 81)
        setContent {
            val prefs = remember { getSharedPreferences("me_apps_settings", MODE_PRIVATE) }
            val initialFontScale = remember {
                if (!prefs.getBoolean("compact_font_v1", false)) {
                    val compact = (prefs.getFloat("font_scale", 1f) * 0.90f).coerceIn(0.80f, 1.40f)
                    prefs.edit()
                        .putFloat("font_scale", compact)
                        .putBoolean("compact_font_v1", true)
                        .apply()
                    compact
                } else {
                    prefs.getFloat("font_scale", 0.90f)
                }
            }
            var fontScale by remember { mutableStateOf(initialFontScale) }

            CatalogTheme(fontScale = fontScale) {
                CatalogRoot(
                    fontScale = fontScale,
                    onFontScaleChange = { value ->
                        val normalized = value.coerceIn(0.80f, 1.40f)
                        fontScale = normalized
                        prefs.edit().putFloat("font_scale", normalized).apply()
                    }
                )
            }
        }
    }
}
