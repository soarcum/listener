package com.ankilistener.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ankilistener.app.util.DownloadState
import com.ankilistener.app.util.UpdateInfo
import com.ankilistener.app.util.UpdateManager
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

        // Set up global crash handler first, before any initialization that might crash
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable?.stackTraceToString() ?: "no stack trace"
            AppLogger.e("CRASH", "Uncaught exception in ${thread.name}: ${throwable?.message}\n$stackTrace")
            // Give logger time to persist
            Thread.sleep(100)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        AppLogger.init(this)
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
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadState: DownloadState,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading) onDismiss()
        },
        title = {
            Text(
                text = if (downloadState is DownloadState.Downloading) "正在下载" else "发现新版本",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text("AnkiListener v${updateInfo.version}")

                when (downloadState) {
                    is DownloadState.Idle -> {
                        if (updateInfo.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateInfo.body,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is DownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { if (downloadState.progress >= 0) downloadState.progress / 100f else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val sizeText = if (downloadState.totalBytes > 0) {
                            val downloadedMB = "%.1f".format(downloadState.downloadedBytes / (1024.0 * 1024.0))
                            val totalMB = "%.1f".format(downloadState.totalBytes / (1024.0 * 1024.0))
                            "$downloadedMB / $totalMB MB"
                        } else {
                            val downloadedMB = "%.1f".format(downloadState.downloadedBytes / (1024.0 * 1024.0))
                            "$downloadedMB MB"
                        }
                        Text(
                            text = if (downloadState.progress >= 0) "${downloadState.progress}%  $sizeText" else sizeText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DownloadState.Installing -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "下载完成，正在启动安装...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is DownloadState.Error -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = downloadState.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Idle -> {
                    Button(onClick = onUpdate) {
                        Text("更新")
                    }
                }
                is DownloadState.Downloading -> {
                    // Disable during download
                }
                is DownloadState.Error -> {
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
                is DownloadState.Installing -> {
                    // No button needed
                }
            }
        },
        dismissButton = {
            if (downloadState !is DownloadState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text(when (downloadState) {
                        is DownloadState.Error -> "取消"
                        is DownloadState.Installing -> "关闭"
                        else -> "稍后"
                    })
                }
            }
        }
    )
}

@Composable
fun MainNavigation(reviewFactory: ViewModelProvider.Factory, settingsFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val viewModel: ReviewViewModel = viewModel(factory = reviewFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    LaunchedEffect(Unit) {
        try {
            val info = UpdateManager.checkForUpdate(context)
            if (info != null) {
                AppLogger.i("UpdateManager", "Update available: ${info.version}")
                updateInfo = info
            }
        } catch (e: Exception) {
            AppLogger.e("UpdateManager", "Update check failed: ${e.message}")
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            downloadState = downloadState,
            onUpdate = {
                downloadState = DownloadState.Idle
                scope.launch {
                    UpdateManager.downloadAndInstall(
                        context, info.downloadUrl, info.version
                    ) { state -> downloadState = state }
                }
            },
            onRetry = {
                downloadState = DownloadState.Idle
                scope.launch {
                    UpdateManager.downloadAndInstall(
                        context, info.downloadUrl, info.version
                    ) { state -> downloadState = state }
                }
            },
            onDismiss = {
                updateInfo = null
                downloadState = DownloadState.Idle
            }
        )
    }

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
