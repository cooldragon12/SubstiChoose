package com.substichoose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.substichoose.ai.ModelState
import com.substichoose.ui.SubstiChooseRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SubstiChooseApp).container
        // Warm up Gemma in the background so the first scan is fast (plan: "warm up at app start").
        // No model on the phone yet: fetch it (Wi-Fi only unless the user allows mobile data).
        container.appScope.launch {
            if (!container.gemma.ensureLoaded() && container.gemma.state.value is ModelState.Missing) {
                container.modelDownloader.autoStart()
            }
        }
        setContent { SubstiChooseRoot(container) }
    }
}
