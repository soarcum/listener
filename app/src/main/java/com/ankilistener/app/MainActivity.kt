package com.ankilistener.app

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
import com.ankilistener.app.data.AiAnswerApiClient
import com.ankilistener.app.data.AiReviewRepository
import com.ankilistener.app.data.AnkiRepository
import com.ankilistener.app.data.ConceptScheduleRepository
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.ui.screens.DeckSelectionScreen
import com.ankilistener.app.ui.screens.LogViewerScreen
import com.ankilistener.app.ui.screens.PermissionScreen
import com.ankilistener.app.ui.screens.ReviewScreen
import com.ankilistener.app.ui.screens.SettingsScreen
import com.ankilistener.app.ui.viewmodel.ReviewViewModel
import com.ankilistener.app.ui.viewmodel.SettingsViewModel
import com.ankilistener.app.util.AudioAnswerRecorder
import com.ankilistener.app.util.AppLogger
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.VibrateManager

class MainActivity : ComponentActivity() {

    private lateinit var repository: AnkiRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var vibrateManager: VibrateManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioAnswerRecorder: AudioAnswerRecorder
    private lateinit var aiAnswerApiClient: AiAnswerApiClient
    private lateinit var aiReviewRepository: AiReviewRepository
    private lateinit var conceptScheduleRepository: ConceptScheduleRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.i("App", "MainActivity.onCreate()")
        
        repository = AnkiRepository(this)
        ttsManager = TtsManager(this)
        vibrateManager = VibrateManager(this)
        settingsRepository = SettingsRepository(this)
        audioAnswerRecorder = AudioAnswerRecorder(this)
        aiAnswerApiClient = AiAnswerApiClient()
        aiReviewRepository = AiReviewRepository(this)
        conceptScheduleRepository = ConceptScheduleRepository(this)

        // Apply saved TTS settings
        ttsManager.provider = settingsRepository.getTtsProvider()
        ttsManager.updateApiConfig(
            baseUrl = settingsRepository.getTtsBaseUrl(),
            speed = settingsRepository.getTtsSpeed(),
            delay = settingsRepository.getTtsDelay(),
            voice = settingsRepository.getTtsVoice()
        )

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
                        AppLogger.i("App", "Permission granted, API available")
                        val reviewFactory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ReviewViewModel(
                                    repository,
                                    ttsManager,
                                    vibrateManager,
                                    settingsRepository,
                                    audioAnswerRecorder,
                                    aiAnswerApiClient,
                                    aiReviewRepository,
                                    conceptScheduleRepository
                                ) as T
                            }
                        }
                        val settingsFactory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return SettingsViewModel(settingsRepository, ttsManager) as T
                            }
                        }
                        MainNavigation(reviewFactory, settingsFactory)
                    }
                }
            }
        }
    }
}

@Composable
fun MainNavigation(reviewFactory: ViewModelProvider.Factory, settingsFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val viewModel: ReviewViewModel = viewModel(factory = reviewFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)

    NavHost(navController = navController, startDestination = "deck_selection") {
        composable("deck_selection") {
            DeckSelectionScreen(
                viewModel = viewModel,
                onDeckClick = { deckId ->
                    viewModel.startReview(deckId)
                    navController.navigate("review")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onLogClick = {
                    navController.navigate("logs")
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
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("logs") {
            LogViewerScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
