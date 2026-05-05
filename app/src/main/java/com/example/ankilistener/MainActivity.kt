package com.example.ankilistener

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ankilistener.data.AnkiRepository
import com.example.ankilistener.ui.screens.DeckSelectionScreen
import com.example.ankilistener.ui.screens.PermissionScreen
import com.example.ankilistener.ui.screens.ReviewScreen
import com.example.ankilistener.ui.viewmodel.ReviewViewModel
import com.example.ankilistener.util.TtsManager
import com.example.ankilistener.util.VibrateManager

class MainActivity : ComponentActivity() {

    private lateinit var repository: AnkiRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var vibrateManager: VibrateManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        repository = AnkiRepository(this)
        ttsManager = TtsManager(this)
        vibrateManager = VibrateManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this, "com.ichi2.anki.permission.READ_WRITE_DATABASE"
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPermission || !repository.isApiAvailable()) {
                        PermissionScreen(
                            isInstalled = repository.isApiAvailable(),
                            onGrantClick = {
                                requestPermissionLauncher.launch("com.ichi2.anki.permission.READ_WRITE_DATABASE")
                            }
                        )
                    } else {
                        val factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ReviewViewModel(repository, ttsManager, vibrateManager) as T
                            }
                        }
                        MainNavigation(factory)
                    }
                }
            }
        }
    }
}

@Composable
fun MainNavigation(factory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val viewModel: ReviewViewModel = viewModel(factory = factory)

    NavHost(navController = navController, startDestination = "deck_selection") {
        composable("deck_selection") {
            DeckSelectionScreen(
                viewModel = viewModel,
                onDeckClick = { deckId ->
                    viewModel.startReview(deckId)
                    navController.navigate("review")
                }
            )
        }
        composable("review") {
            ReviewScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.popBackStack()
                }
            )
        }
    }
}
