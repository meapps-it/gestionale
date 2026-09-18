package com.meapps.scadenzespese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.meapps.scadenzespese.catalogui.CatalogRoot
import com.meapps.scadenzespese.catalogui.CatalogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(15, 23, 42)
        window.navigationBarColor = android.graphics.Color.rgb(55, 65, 81)
        setContent {
            CatalogTheme {
                CatalogRoot()
            }
        }
    }
}
