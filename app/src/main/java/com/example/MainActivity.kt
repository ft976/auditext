package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.tts.TtsManager
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.StudioViewModelFactory
import com.example.ui.theme.CharcoalBackground

class MainActivity : ComponentActivity() {
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ttsManager = TtsManager(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val factory = StudioViewModelFactory(database.historyDao(), ttsManager, applicationContext)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CharcoalBackground
                ) {
                    val viewModel: StudioViewModel = viewModel(factory = factory)
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }
}
