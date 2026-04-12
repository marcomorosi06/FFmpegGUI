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
                        .navigationBarsPadding()
                ) {
                    val navController = rememberNavController()
                    val vm: ConversionViewModel = viewModel()

                    val projectRatio by vm.projectRatio.collectAsState()
                    val flipHorizontal by vm.flipHorizontal.collectAsState()
                    val flipVertical by vm.flipVertical.collectAsState()
                    val inputUri by vm.inputUri.collectAsState()
                    val inputFileName by vm.inputFileName.collectAsState()
                    val mediaInfo by vm.mediaInfo.collectAsState()
                    val selectedFormat by vm.selectedFormat.collectAsState()
                    val startTime by vm.startTime.collectAsState()
                    val endTime by vm.endTime.collectAsState()
                    val resolution by vm.resolution.collectAsState()
                    val framerate by vm.framerate.collectAsState()
                    val qualityLevel by vm.qualityLevel.collectAsState()
                    val rotation by vm.rotation.collectAsState()
                    val cropW by vm.cropW.collectAsState()
                    val cropH by vm.cropH.collectAsState()
                    val cropX by vm.cropX.collectAsState()
                    val cropY by vm.cropY.collectAsState()
                    val customCommand by vm.customCommand.collectAsState()
                    val commandPreview by vm.commandPreview.collectAsState()
                    val conversionState by vm.conversionState.collectAsState()
                    val isManualMode by vm.isManualMode.collectAsState()
                    val manualCommand by vm.manualCommand.collectAsState()
                    val removeAudio by vm.removeAudio.collectAsState()
                    val volumeLevel by vm.volumeLevel.collectAsState()
                    val normalizeAudio by vm.normalizeAudio.collectAsState()
                    val audioTrackUri by vm.audioTrackUri.collectAsState()
                    val audioTrackName by vm.audioTrackName.collectAsState()
                    val audioTrackDurationMs by vm.audioTrackDurationMs.collectAsState()
                    val audioTrackTrimStart by vm.audioTrackTrimStart.collectAsState()
                    val audioTrackTrimEnd by vm.audioTrackTrimEnd.collectAsState()
                    val audioTrackDelay by vm.audioTrackDelay.collectAsState()
                    val audioTrackVolume by vm.audioTrackVolume.collectAsState()
                    val videoSpeed by vm.videoSpeed.collectAsState()

                    // Nuovi stati avanzati aggiunti
                    val brightness by vm.brightness.collectAsState()
                    val contrast by vm.contrast.collectAsState()
                    val saturation by vm.saturation.collectAsState()
                    val fadeInDuration by vm.fadeInDuration.collectAsState()
                    val fadeOutDuration by vm.fadeOutDuration.collectAsState()
                    val isReversed by vm.isReversed.collectAsState()
                    val textOverlays by vm.textOverlays.collectAsState()

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
                                startTime = startTime,
                                endTime = endTime,
                                resolution = resolution,
                                framerate = framerate,
                                qualityLevel = qualityLevel,
                                rotation = rotation,
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                                projectRatio = projectRatio,
                                cropW = cropW,
                                cropH = cropH,
                                cropX = cropX,
                                cropY = cropY,
                                customCommand = customCommand,
                                commandPreview = commandPreview,
                                isManualMode = isManualMode,
                                manualCommand = manualCommand,
                                removeAudio = removeAudio,
                                volumeLevel = volumeLevel,
                                normalizeAudio = normalizeAudio,
                                audioTrackUri = audioTrackUri,
                                audioTrackName = audioTrackName,
                                audioTrackDurationMs = audioTrackDurationMs,
                                audioTrackTrimStart = audioTrackTrimStart,
                                audioTrackTrimEnd = audioTrackTrimEnd,
                                audioTrackDelay = audioTrackDelay,
                                audioTrackVolume = audioTrackVolume,
                                videoSpeed = videoSpeed,
                                brightness = brightness,
                                contrast = contrast,
                                saturation = saturation,
                                fadeInDuration = fadeInDuration,
                                fadeOutDuration = fadeOutDuration,
                                isReversed = isReversed,
                                textOverlays = textOverlays,
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