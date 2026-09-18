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
            var fontScale by remember { mutableStateOf(prefs.getFloat("font_scale", 1f)) }

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
