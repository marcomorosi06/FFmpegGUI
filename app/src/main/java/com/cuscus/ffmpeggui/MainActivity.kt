package com.cuscus.ffmpeggui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cuscus.ffmpeggui.ui.theme.FFmpegGUITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FFmpegGUITheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    val navController = rememberNavController()
                    val vm: ConversionViewModel = viewModel()

                    // ── Top-level state ────────────────────────────────────────
                    val inputUri by vm.inputUri.collectAsState()
                    val inputFileName by vm.inputFileName.collectAsState()
                    val mediaInfo by vm.mediaInfo.collectAsState()
                    val selectedFormat by vm.selectedFormat.collectAsState()
                    val conversionState by vm.conversionState.collectAsState()
                    val commandPreview by vm.commandPreview.collectAsState()

                    // ── All editing settings in one flow ───────────────────────
                    val s by vm.settings.collectAsState()

                    LaunchedEffect(Unit) {
                        intent?.data?.let { uri ->
                            if (intent.action == android.content.Intent.ACTION_VIEW) {
                                vm.setInputUri(uri)
                                navController.navigate(Screen.FormatPicker.route)
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onFileSelected = { uri ->
                                    vm.setInputUri(uri)
                                    navController.navigate(Screen.FormatPicker.route)
                                },
                            )
                        }

                        composable(Screen.FormatPicker.route) {
                            FormatPickerScreen(
                                fileName = inputFileName,
                                mediaInfo = mediaInfo,
                                onFormatSelected = { format ->
                                    vm.selectFormat(format)
                                    navController.navigate(Screen.Config.route)
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(Screen.Config.route) {
                            val format = selectedFormat ?: run {
                                navController.popBackStack()
                                return@composable
                            }
                            ConfigScreen(
                                inputUri = inputUri,
                                format = format,
                                fileName = inputFileName,
                                mediaDurationFormatted = mediaInfo?.durationFormatted,
                                // Settings — unpacked from the single flow
                                startTime = s.startTime,
                                endTime = s.endTime,
                                resolution = s.resolution,
                                framerate = s.framerate,
                                qualityLevel = s.qualityLevel,
                                rotation = s.rotation,
                                flipHorizontal = s.flipHorizontal,
                                flipVertical = s.flipVertical,
                                projectRatio = s.projectRatio,
                                cropW = s.cropW,
                                cropH = s.cropH,
                                cropX = s.cropX,
                                cropY = s.cropY,
                                customCommand = s.customCommand,
                                commandPreview = commandPreview,
                                isManualMode = s.isManualMode,
                                manualCommand = s.manualCommandOverride,
                                removeAudio = s.removeAudio,
                                volumeLevel = s.volumeLevel,
                                normalizeAudio = s.normalizeAudio,
                                audioTrackUri = s.audioTrackUri,
                                audioTrackName = s.audioTrackName,
                                audioTrackDurationMs = s.audioTrackDurationMs,
                                audioTrackTrimStart = s.audioTrackTrimStart,
                                audioTrackTrimEnd = s.audioTrackTrimEnd,
                                audioTrackDelay = s.audioTrackDelay,
                                audioTrackVolume = s.audioTrackVolume,
                                videoSpeed = s.videoSpeed,
                                brightness = s.brightness,
                                contrast = s.contrast,
                                saturation = s.saturation,
                                fadeInDuration = s.fadeInDuration,
                                fadeOutDuration = s.fadeOutDuration,
                                isReversed = s.isReversed,
                                textOverlays = s.textOverlays,
                                // Callbacks
                                onAddTextOverlay = vm::addTextOverlay,
                                onRemoveTextOverlay = vm::removeTextOverlay,
                                onUpdateTextOverlay = vm::updateTextOverlay,
                                onStartTime = vm::setStartTime,
                                onEndTime = vm::setEndTime,
                                onResolution = vm::setResolution,
                                onFramerate = vm::setFramerate,
                                onQualityLevel = vm::setQualityLevel,
                                onRotation = vm::setRotation,
                                onFlipHorizontal = vm::setFlipHorizontal,
                                onFlipVertical = vm::setFlipVertical,
                                onProjectRatioChange = vm::setProjectRatio,
                                onCropW = vm::setCropW,
                                onCropH = vm::setCropH,
                                onCropX = vm::setCropX,
                                onCropY = vm::setCropY,
                                onCustomCommand = vm::setCustomCommand,
                                onManualCommandChange = vm::setManualCommand,
                                onResetManualMode = vm::resetManualMode,
                                onRemoveAudio = vm::setRemoveAudio,
                                onVolumeLevel = vm::setVolumeLevel,
                                onNormalizeAudio = vm::setNormalizeAudio,
                                onSetAudioTrack = vm::setAudioTrack,
                                onAudioTrackTrimStart = vm::setAudioTrackTrimStart,
                                onAudioTrackTrimEnd = vm::setAudioTrackTrimEnd,
                                onAudioTrackDelay = vm::setAudioTrackDelay,
                                onAudioTrackVolume = vm::setAudioTrackVolume,
                                onVideoSpeed = vm::setVideoSpeed,
                                onBrightness = vm::setBrightness,
                                onContrast = vm::setContrast,
                                onSaturation = vm::setSaturation,
                                onFadeIn = vm::setFadeIn,
                                onFadeOut = vm::setFadeOut,
                                onIsReversed = vm::setIsReversed,
                                onStart = {
                                    vm.startConversion()
                                    navController.navigate(Screen.Processing.route)
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(Screen.Processing.route) {
                            ProcessingScreen(
                                state = conversionState,
                                onCancel = {
                                    vm.cancelConversion()
                                    navController.popBackStack()
                                },
                                onNewConversion = {
                                    vm.resetConversionState()
                                    navController.popBackStack(Screen.Home.route, false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}