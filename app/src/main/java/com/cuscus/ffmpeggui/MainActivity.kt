package com.cuscus.ffmpeggui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cuscus.ffmpeggui.ui.theme.LocalSystemBars
import com.cuscus.ffmpeggui.ui.theme.background
import com.cuscus.ffmpeggui.ui.theme.rememberSystemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import com.cuscus.ffmpeggui.ui.adaptive.ProvideWindowLayout
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.cuscus.ffmpeggui.hdr.LocalStillGainMaps
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.conversion.ConversionViewModel
import com.cuscus.ffmpeggui.conversion.CutoutStatus
import com.cuscus.ffmpeggui.preview.soundWordsKey
import com.cuscus.ffmpeggui.speech.SpeechModelStore
import com.cuscus.ffmpeggui.onboarding.WelcomeScreen
import com.cuscus.ffmpeggui.core.preview.normalizationGainDb
import com.cuscus.ffmpeggui.settings.AboutScreen
import com.cuscus.ffmpeggui.settings.AppSettings
import com.cuscus.ffmpeggui.settings.ExportsUsage
import com.cuscus.ffmpeggui.settings.SettingsCategory
import com.cuscus.ffmpeggui.settings.SettingsCategoryScreen
import com.cuscus.ffmpeggui.settings.SettingsScreen
import com.cuscus.ffmpeggui.settings.SmartFeaturesUi
import com.cuscus.ffmpeggui.conversion.SpeechStatus
import com.cuscus.ffmpeggui.settings.CutoutFeatureUi
import com.cuscus.ffmpeggui.settings.RefineFeatureUi
import com.cuscus.ffmpeggui.ui.theme.AppIcon
import com.cuscus.ffmpeggui.ui.theme.FFmpegGUITheme
import com.cuscus.ffmpeggui.ui.theme.LocalAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SPLASH_CHOREOGRAPHY_MS = 900L
private const val SPLASH_HANDOFF_MS = 160L

class MainActivity : ComponentActivity() {
    override fun onStop() {
        super.onStop()
        // The launcher icon follows the colours setting once the app is out of sight:
        // some launchers redraw the entry when it changes.
        val settings = (application as FfmpegGuiApp).container.settingsRepository.settings.value
        if (settings != null && !isChangingConfigurations) AppIcon.sync(this, settings.dynamicColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val splashStartedAt = SystemClock.uptimeMillis()
        val container = (application as FfmpegGuiApp).container
        val settingsRepository = container.settingsRepository
        val projectStore = container.projectStore
        splash.setKeepOnScreenCondition {
            settingsRepository.settings.value == null ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SystemClock.uptimeMillis() - splashStartedAt < SPLASH_CHOREOGRAPHY_MS)
        }
        splash.setOnExitAnimationListener { provider ->
            val finish = { runCatching { provider.remove() }.let { } }
            val root = runCatching { provider.view }.getOrNull()
            if (root == null) {
                finish()
            } else {
                runCatching {
                    ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).apply {
                        duration = SPLASH_HANDOFF_MS
                        interpolator = AccelerateInterpolator(1.6f)
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) = finish()
                            override fun onAnimationCancel(animation: Animator) = finish()
                        })
                        start()
                    }
                }.onFailure { finish() }
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

        setContent {
            val storedSettings by settingsRepository.settings.collectAsState()
            val appSettings = storedSettings ?: AppSettings()
            val loaded = storedSettings != null
            LaunchedEffect(loaded, appSettings.dynamicColor) {
                if (loaded) AppIcon.update(appSettings.dynamicColor)
                if (loaded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getSplashScreen().setSplashScreenTheme(
                        if (appSettings.dynamicColor) R.style.Theme_FFmpegGUI_Splash else R.style.Theme_FFmpegGUI_Splash_Teal,
                    )
                }
            }
            FFmpegGUITheme(settings = appSettings) {
                // Vibration off: every haptic of the app does nothing. The setting is read when a
                // haptic is played, not when the screen was made: a gesture handler or an effect
                // that keeps the feedback it started with (the welcome's pages, the timeline)
                // goes quiet as soon as the switch is turned off, and speaks as soon as it is on.
                val platformHaptics = LocalHapticFeedback.current
                val hapticsOn = rememberUpdatedState(appSettings.haptics)
                val haptics = remember(platformHaptics) { SettingsHaptics(platformHaptics) { hapticsOn.value } }
                // Behind the status bar and the navigation bar: the background of the screen shown.
                val systemBars = rememberSystemBars()
                CompositionLocalProvider(
                    LocalHapticFeedback provides haptics,
                    LocalAppSettings provides appSettings,
                    LocalExportFonts provides container.subtitleFonts,
                    LocalSystemBars provides systemBars,
                    LocalStillGainMaps provides container.stillGainMaps,
                ) {
                // The window as the screens get it (inside the system bars and a camera's
                // notch at the side), measured once for all of them (see WindowLayout).
                ProvideWindowLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(systemBars.background(MaterialTheme.colorScheme.background))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Nothing until the settings are read (the splash screen is up meanwhile):
                    // whether the welcome shows first depends on them.
                    if (!loaded) return@Surface
                    val navController = rememberNavController()
                    val vm: ConversionViewModel = viewModel(factory = ConversionViewModel.Factory)

                    // Top-level state
                    val inputUri by vm.inputUri.collectAsState()
                    val inputFileName by vm.inputFileName.collectAsState()
                    val mediaInfo by vm.mediaInfo.collectAsState()
                    val selectedFormat by vm.selectedFormat.collectAsState()
                    val conversionState by vm.conversionState.collectAsState()
                    val commandPreview by vm.commandPreview.collectAsState()
                    val canUndo by vm.canUndo.collectAsState()
                    val canRedo by vm.canRedo.collectAsState()

                    // All editing settings in one flow
                    val s by vm.settings.collectAsState()

                    // The recent projects (kept only on this phone, see ProjectStore).
                    val projectCards by projectStore.cards.collectAsState()
                    val openFailed = stringResource(R.string.projects_open_failed)
                    val duplicateName = stringResource(R.string.projects_copy_name, "%s")

                    // An export shows its progress in a notification: Android 13 and later ask first.
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    // [adjust] changes what this one export makes, not the project.
                    val startExportOf: ((ConversionSettings) -> ConversionSettings) -> Unit = { adjust ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.startConversion(adjust)
                        navController.navigate(Screen.Processing.route)
                    }
                    val startExport: () -> Unit = { startExportOf { it } }

                    // Data the live preview needs beyond the settings themselves
                    val subtitleCues by vm.subtitleCues.collectAsState()
                    val subtitleFileStyled by vm.subtitleFileStyled.collectAsState()
                    // Subtitles made from what is said: where the run is, the words it found so far.
                    val speech by vm.speech.collectAsState()
                    val liveWords by vm.liveWords.collectAsState()
                    val speechModel by container.smartFeatures.speechState.collectAsState()
                    // The subject cut-out of the key panels: its model, and where cutting a picture out is.
                    val cutoutModel by container.smartFeatures.cutoutState.collectAsState()
                    val cutout by vm.cutout.collectAsState()
                    val refineModel by container.smartFeatures.refineState.collectAsState()
                    val subjectCut = SubjectCut(
                        ready = cutoutModel is SpeechModelStore.State.Ready,
                        refine = container.smartFeatures.refineEngine.takeIf { refineModel is SpeechModelStore.State.Ready },
                        status = cutout,
                        onCut = vm::cutOutSubject,
                        onStop = vm::stopCutout,
                        onOpenSmartFeatures = { navController.navigate(Screen.settingsCategory(SettingsCategory.SMART)) },
                    )
                    val sourceLoudness by vm.sourceLoudnessLufs.collectAsState()

                    LaunchedEffect(Unit) {
                        // Only on a fresh launch: after a configuration change the view
                        // model already holds the file and the user's edits.
                        if (savedInstanceState == null) {
                            openedFile(intent, packageName)?.let { uri ->
                                vm.setInputUri(uri)
                                navController.navigate(Screen.FormatPicker.route)
                            }
                        }
                    }

                    // The welcome on the very first launch; a file opened from another app goes first.
                    val startRoute = remember {
                        val opening = savedInstanceState == null && openedFile(intent, packageName) != null
                        if (appSettings.onboarded || opening) Screen.Home.route else Screen.Welcome.route
                    }

                    CompositionLocalProvider(LocalSubjectCut provides subjectCut) {
                    NavHost(
                        navController = navController,
                        startDestination = startRoute,
                    ) {
                        composable(Screen.Welcome.route) {
                            val calibration by container.hardwareCalibration.state.collectAsState()
                            WelcomeScreen(
                                settings = appSettings,
                                onChange = settingsRepository::update,
                                calibration = calibration,
                                // Not while an export uses FFmpeg and the chip.
                                canCalibrate = conversionState !is ConversionState.Processing,
                                onCalibrate = container.hardwareCalibration::start,
                                onStopCalibration = container.hardwareCalibration::stop,
                                speechReady = speechModel is SpeechModelStore.State.Ready,
                                cutoutReady = cutoutModel is SpeechModelStore.State.Ready,
                                projects = projectCards.orEmpty(),
                                onDeleteAllProjects = projectStore::deleteAll,
                                onKeepProjects = projectStore::keepAtMost,
                                onFinish = { openSmart ->
                                    settingsRepository.update { it.copy(onboarded = true) }
                                    // Home becomes the bottom of the stack: back from it leaves the app.
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                    if (openSmart) navController.navigate(Screen.settingsCategory(SettingsCategory.SMART))
                                },
                            )
                        }

                        composable(Screen.Home.route) {
                            // Back home: a file of a project may have gone meanwhile.
                            LaunchedEffect(Unit) { projectStore.refresh() }
                            HomeScreen(
                                onFileSelected = { uri ->
                                    vm.setInputUri(uri)
                                    navController.navigate(Screen.FormatPicker.route)
                                },
                                onNewRecording = vm::newRecordingUri,
                                onRecordingDiscarded = vm::discardRecording,
                                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                                projects = if (appSettings.saveProjects) projectCards.orEmpty() else emptyList(),
                                onOpenProject = { project ->
                                    vm.openProject(
                                        project.id,
                                        onOpened = {
                                            // As if the format had just been picked: back goes to the formats.
                                            navController.navigate(Screen.FormatPicker.route)
                                            navController.navigate(Screen.Config.route)
                                        },
                                        onFailed = {
                                            android.widget.Toast.makeText(this@MainActivity, openFailed, android.widget.Toast.LENGTH_SHORT).show()
                                            projectStore.refresh()
                                        },
                                    )
                                },
                                onDuplicateProject = { project -> projectStore.duplicate(project.id) { duplicateName.replace("%s", it) } },
                                onDeleteProject = { project -> projectStore.delete(project.id) },
                            )
                        }

                        composable(Screen.FormatPicker.route) {
                            FormatPickerScreen(
                                fileName = inputFileName,
                                mediaInfo = mediaInfo,
                                onFormatSelected = { format ->
                                    vm.selectFormat(format)
                                    when {
                                        // Listened to and shown beside the player, to be read through and corrected.
                                        format.isTranscript -> navController.navigate(Screen.Transcript.route)
                                        // A picture of a video: its exact frame is chosen first.
                                        format.isImage && mediaInfo?.isVideo == true && s.playingClips.firstOrNull()?.let { s.source(it.sourceId) }?.isStill != true ->
                                            navController.navigate(Screen.FramePicker.route)
                                        else -> navController.navigate(Screen.Config.route)
                                    }
                                },
                                onBack = { navController.popBackStack() },
                                transcriptReady = speechModel is SpeechModelStore.State.Ready,
                                onOpenSmartFeatures = { navController.navigate(Screen.settingsCategory(SettingsCategory.SMART)) },
                            )
                        }

                        composable(Screen.FramePicker.route) {
                            val format = selectedFormat
                            val first = s.playingClips.firstOrNull()
                            val source = first?.let { s.source(it.sourceId) }
                            if (format == null) {
                                navController.popBackStack()
                                return@composable
                            }
                            // The file is still being looked at: in a moment.
                            if (first == null || source == null || source.info == null) {
                                FramePickerLoading()
                                return@composable
                            }
                            // A picture has one frame: straight to the editor.
                            if (source.isStill) {
                                LaunchedEffect(Unit) {
                                    navController.navigate(Screen.Config.route) { popUpTo(Screen.FramePicker.route) { inclusive = true } }
                                }
                                return@composable
                            }
                            // The clip the picture comes from, in its file.
                            val clipEnd = first.endMs ?: source.durationMs
                            FramePickerScreen(
                                uri = source.uri,
                                fileName = inputFileName,
                                info = source.info,
                                format = format,
                                rangeStartMs = first.startMs,
                                rangeEndMs = clipEnd.coerceAtLeast(first.startMs + 1),
                                initialMs = first.startMs + s.imageEdit.frameMs,
                                onEditAsPhoto = { fileMs ->
                                    vm.chooseFrame(fileMs - first.startMs)
                                    navController.navigate(Screen.Config.route)
                                },
                                onExportFrames = { framesPerSecond, fromMs, toMs ->
                                    // The first clip starts the sequence: its clock is the sequence's.
                                    startExportOf { settings ->
                                        settings.copy(
                                            frameExtractionMode = FrameExtractionMode.SEQUENCE,
                                            frameExtractionRate = framesPerSecond,
                                            frameRangeStartMs = (fromMs - first.startMs).coerceAtLeast(0L),
                                            frameRangeEndMs = (toMs - first.startMs).takeIf { it > 0L },
                                        )
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(Screen.Transcript.route) {
                            val format = selectedFormat
                            val uri = inputUri
                            if (format == null || uri == null || !format.isTranscript) {
                                navController.popBackStack()
                                return@composable
                            }
                            val info = mediaInfo
                            // Joined once per change of either, not at every recomposition (hours of words).
                            val keptWords = s.spokenWords[EditorSource.MAIN_ID]
                            val foundWords = liveWords[EditorSource.MAIN_ID]
                            val transcriptWords = remember(keptWords, foundWords) {
                                when {
                                    foundWords.isNullOrEmpty() -> keptWords.orEmpty()
                                    keptWords.isNullOrEmpty() -> foundWords
                                    else -> keptWords + foundWords
                                }
                            }
                            TranscriptScreen(
                                fileName = inputFileName,
                                uri = uri,
                                isVideo = info?.isVideo == true,
                                videoRatio = if (info != null && info.width > 0 && info.height > 0) info.width.toFloat() / info.height else 0f,
                                format = format,
                                words = transcriptWords,
                                speech = speech,
                                heard = EditorSource.MAIN_ID in s.spokenWords,
                                style = s.subtitleStyle,
                                onListen = { vm.generateSubtitles() },
                                onStop = vm::stopSpeech,
                                onEditLine = { start, end, text -> vm.editSpokenLine(EditorSource.MAIN_ID, start, end, text) },
                                onSave = startExport,
                                onBack = {
                                    vm.stopSpeech()
                                    navController.popBackStack()
                                },
                            )
                        }

                        composable(Screen.Config.route) {
                            val format = selectedFormat ?: run {
                                navController.popBackStack()
                                return@composable
                            }
                            if (format.isImage) {
                                ImageEditorScreen(
                                    inputUri = inputUri,
                                    format = format,
                                    fileName = inputFileName,
                                    sources = s.sources,
                                    playingClips = s.playingClips,
                                    imageEdit = s.imageEdit,
                                    onImageEdit = vm::updateImageEdit,
                                    onAddStroke = vm::addStroke,
                                    onUndoStroke = vm::removeLastStroke,
                                    onClearStrokes = vm::clearStrokes,
                                    wholeEffects = s.wholeEffects,
                                    onUpdateEffects = vm::updateEffects,
                                    textOverlays = s.textOverlays,
                                    imageOverlays = s.imageOverlays,
                                    onAddText = vm::addText,
                                    onRemoveTextOverlay = vm::removeTextOverlay,
                                    onUpdateText = vm::updateText,
                                    onAddOverlay = vm::addOverlay,
                                    onReplaceOverlay = vm::replaceOverlay,
                                    onRemoveImageOverlay = vm::removeImageOverlay,
                                    onUpdateImage = vm::updateImage,
                                    onArrangeImage = vm::arrangeImage,
                                    onDuplicateOverlay = vm::duplicateOverlay,
                                    tags = s.tags,
                                    onTags = vm::updateTags,
                                    commandPreview = commandPreview,
                                    isManualMode = s.isManualMode,
                                    manualCommand = s.manualCommandOverride,
                                    onManualCommandChange = vm::setManualCommand,
                                    onResetManualMode = vm::resetManualMode,
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onUndo = vm::undo,
                                    onRedo = vm::redo,
                                    onStart = startExport,
                                    onBack = { navController.popBackStack() },
                                )
                                return@composable
                            }
                            ConfigScreen(
                                inputUri = inputUri,
                                format = format,
                                fileName = inputFileName,
                                // Settings - unpacked from the single flow
                                timeline = s.timeline,
                                sources = s.sources,
                                trimStartMs = s.trimStartMs,
                                trimEndMs = s.trimEndMs,
                                resolution = s.resolution,
                                framerate = s.framerate,
                                qualityLevel = s.qualityLevel,
                                keepHdr = s.keepHdr,
                                wholeEffects = s.wholeEffects,
                                onUpdateEffects = vm::updateEffects,
                                onTransitionForAll = vm::setTransitionForAll,
                                projectRatio = s.projectRatio,
                                customCommand = s.customCommand,
                                commandPreview = commandPreview,
                                isManualMode = s.isManualMode,
                                manualCommand = s.manualCommandOverride,
                                normalizeAudio = s.normalizeAudio,
                                audioEffects = s.audioEffects,
                                onAudioEffects = vm::updateAudioEffects,
                                gif = s.gif,
                                onGif = vm::updateGif,
                                audioEncoding = s.audioEncoding,
                                onAudioEncoding = vm::updateAudioEncoding,
                                tags = s.tags,
                                onTags = vm::updateTags,
                                coverUri = s.coverUri,
                                onSetCover = vm::setCover,
                                splitMode = s.splitMode,
                                splitValue = s.splitValue,
                                onSplit = vm::setSplit,
                                audioClips = s.audioClips,
                                textOverlays = s.textOverlays,
                                imageOverlays = s.imageOverlays,
                                subtitles = SubtitleEditing(
                                    fileUri = s.subtitleUri,
                                    fileName = s.subtitleName,
                                    fileCues = subtitleCues,
                                    fileStyled = subtitleFileStyled,
                                    words = s.spokenWords,
                                    liveWords = liveWords,
                                    style = s.subtitleStyle,
                                    speech = speech,
                                    modelReady = speechModel is SpeechModelStore.State.Ready,
                                    unheardFiles = s.playingClips.map { it.sourceId }.distinct().mapNotNull(s::source)
                                        .count { !it.isStill && it.info?.hasAudio != false && it.id !in s.spokenWords } +
                                        // The sounds placed on the video are listened to as well.
                                        s.audioClips.filter { !it.muted }.distinctBy { it.uri }.count { soundWordsKey(it.uri) !in s.spokenWords },
                                    fonts = container.subtitleFonts,
                                    sourceNames = s.sources.associate { it.id to it.fileName } + s.audioClips.associate { soundWordsKey(it.uri) to it.name },
                                    onSetFile = vm::setSubtitle,
                                    onGenerate = vm::generateSubtitles,
                                    onStop = vm::stopSpeech,
                                    onDismissProblem = vm::dismissSpeechProblem,
                                    onStyle = vm::updateSubtitleStyle,
                                    onClearSpoken = vm::clearSpokenSubtitles,
                                    onToTexts = vm::spokenSubtitlesToTexts,
                                    onEditLine = vm::editSpokenLine,
                                    srt = vm::spokenSubtitlesSrt,
                                    onOpenSmartFeatures = { navController.navigate(Screen.settingsCategory(SettingsCategory.SMART)) },
                                ),
                                useStreamCopy = s.useStreamCopy,
                                frameExtractionMode = s.frameExtractionMode,
                                frameExtractionRate = s.frameExtractionRate,
                                frameExtractionTimecode = s.frameExtractionTimecode,
                                // Callbacks
                                onAddText = vm::addText,
                                onRemoveTextOverlay = vm::removeTextOverlay,
                                onUpdateText = vm::updateText,
                                onAddClip = vm::addClip,
                                onSplitClip = vm::splitClipAt,
                                onFreezeFrame = vm::insertFreezeFrame,
                                onRemoveClip = vm::removeClip,
                                onDuplicateClip = vm::duplicateClip,
                                onMoveClip = vm::moveClip,
                                onTrimClip = vm::trimClip,
                                onTrimWindow = vm::setTrimWindow,
                                onResolution = vm::setResolution,
                                onFramerate = vm::setFramerate,
                                onQualityLevel = vm::setQualityLevel,
                                onKeepHdr = vm::setKeepHdr,
                                onProjectRatioChange = vm::setProjectRatio,
                                onCustomCommand = vm::setCustomCommand,
                                onManualCommandChange = vm::setManualCommand,
                                onResetManualMode = vm::resetManualMode,
                                onNormalizeAudio = vm::setNormalizeAudio,
                                onAddAudioClip = vm::addAudioClip,
                                onUpdateAudioClip = vm::updateAudioClip,
                                onRemoveAudioClip = vm::removeAudioClip,
                                onDuplicateAudioClip = vm::duplicateAudioClip,
                                onSplitAudioClip = vm::splitAudioClip,
                                onReplaceAudioClip = vm::replaceAudioClip,
                                onAudioClipNormalize = vm::setAudioClipNormalize,
                                onStart = startExport,
                                onAddOverlay = vm::addOverlay,
                                onReplaceOverlay = vm::replaceOverlay,
                                onRemoveImageOverlay = vm::removeImageOverlay,
                                onUpdateImage = vm::updateImage,
                                onArrangeImage = vm::arrangeImage,
                                onSetOverlaySpan = vm::setOverlaySpan,
                                onDuplicateOverlay = vm::duplicateOverlay,
                                onSplitOverlay = vm::splitOverlay,
                                onUseStreamCopy = vm::setUseStreamCopy,
                                onFrameExtractionMode = vm::setFrameExtractionMode,
                                onFrameExtractionRate = vm::setFrameExtractionRate,
                                onFrameExtractionTimecode = vm::setFrameExtractionTimecode,
                                onBack = { navController.popBackStack() },
                                normalizationGainDb = sourceLoudness?.let(::normalizationGainDb) ?: 0f,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                onUndo = vm::undo,
                                onRedo = vm::redo,
                            )
                        }

                        composable(Screen.Processing.route) {
                            // Cancelled from the notification: back to the editor.
                            LaunchedEffect(conversionState) {
                                if (conversionState == ConversionState.Idle &&
                                    navController.currentDestination?.route == Screen.Processing.route
                                ) {
                                    navController.popBackStack()
                                }
                            }
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
                                onBackToEditor = {
                                    vm.resetConversionState()
                                    navController.popBackStack()
                                },
                                fileName = inputFileName,
                                format = selectedFormat,
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                settings = appSettings,
                                speechReady = speechModel is SpeechModelStore.State.Ready,
                                cutoutReady = cutoutModel is SpeechModelStore.State.Ready,
                                versionName = versionName,
                                onOpenCategory = { category -> navController.navigate(Screen.settingsCategory(category)) },
                                onOpenAbout = { navController.navigate(Screen.About.route) },
                                onOpenWelcome = { navController.navigate(Screen.Welcome.route) },
                                onReset = settingsRepository::reset,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        SettingsCategory.entries.forEach { category ->
                            composable(Screen.settingsCategory(category)) {
                                var usageRefresh by remember { mutableIntStateOf(0) }
                                // The video chip's test: where it is, and what it found.
                                val calibration by container.hardwareCalibration.state.collectAsState()
                                // The speech model of the smart features, and its speed test.
                                val smart = container.smartFeatures
                                val speechLoaded by smart.engine.loaded.collectAsState()
                                val speedTest by smart.test.collectAsState()
                                val cutoutLoaded by smart.cutoutEngine.inMemory.collectAsState()
                                val refineLoaded by smart.refineEngine.inMemory.collectAsState()
                                val cutoutTest by smart.cutoutTest.collectAsState()
                                // Which build of each model is chosen, and where every build is.
                                val speechVariant by smart.speechVariant.collectAsState()
                                val cutoutVariant by smart.cutoutVariant.collectAsState()
                                val speechVariants = smart.speechStores.mapValues { (_, store) -> store.state.collectAsState().value }
                                val cutoutVariants = smart.cutoutStores.mapValues { (_, store) -> store.state.collectAsState().value }
                                val usage by produceState<ExportsUsage?>(null, usageRefresh) {
                                    value = withContext(Dispatchers.IO) { container.exportedFiles.usage() }
                                }
                                val scope = rememberCoroutineScope()
                                SettingsCategoryScreen(
                                    category = category,
                                    settings = appSettings,
                                    onChange = settingsRepository::update,
                                    exportsUsage = usage,
                                    // Not while an export is being written into the same folder.
                                    canDeleteExports = conversionState !is ConversionState.Processing,
                                    onDeleteExports = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { container.exportedFiles.deleteAll() }
                                            usageRefresh++
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                    projects = projectCards,
                                    onDeleteProject = projectStore::delete,
                                    onDeleteAllProjects = projectStore::deleteAll,
                                    onKeepProjects = projectStore::keepAtMost,
                                    calibration = calibration,
                                    onCalibrate = container.hardwareCalibration::start,
                                    onStopCalibration = container.hardwareCalibration::stop,
                                    // Not while an export uses FFmpeg and the chip.
                                    canCalibrate = conversionState !is ConversionState.Processing,
                                    smart = SmartFeaturesUi(
                                        variant = speechVariant,
                                        variants = speechVariants,
                                        onChoose = smart::chooseSpeech,
                                        model = speechModel,
                                        loaded = speechLoaded,
                                        test = speedTest,
                                        // Not while the editor is making subtitles with the model.
                                        idle = speech !is SpeechStatus.Working,
                                        onImport = { uris -> smart.speechStores.getValue(speechVariant).import(uris) },
                                        onStopImport = { smart.speechStores.getValue(speechVariant).stop() },
                                        onVerify = { smart.speechStores.getValue(speechVariant).verify() },
                                        onDelete = { smart.deleteModel(speechVariant) },
                                        onTest = smart::runTest,
                                        onStopTest = smart::stopTest,
                                        onUnload = smart.engine::unload,
                                        cutout = CutoutFeatureUi(
                                            variant = cutoutVariant,
                                            variants = cutoutVariants,
                                            onChoose = smart::chooseCutout,
                                            model = cutoutModel,
                                            loaded = cutoutLoaded,
                                            test = cutoutTest,
                                            // Not while a picture is being cut out with the model.
                                            idle = cutout !is CutoutStatus.Working,
                                            onImport = { uri -> smart.cutoutStores.getValue(cutoutVariant).import(uri) },
                                            onStopImport = { smart.cutoutStores.getValue(cutoutVariant).stop() },
                                            onVerify = { smart.cutoutStores.getValue(cutoutVariant).verify() },
                                            onDelete = { smart.deleteCutoutModel(cutoutVariant) },
                                            onTest = smart::runCutoutTest,
                                            onStopTest = smart::stopCutoutTest,
                                            onUnload = smart.cutoutEngine::unload,
                                            refine = RefineFeatureUi(
                                                model = refineModel,
                                                loaded = refineLoaded,
                                                onImport = smart.refineStore::import,
                                                onStopImport = smart.refineStore::stop,
                                                onVerify = smart.refineStore::verify,
                                                onDelete = smart::deleteRefineModel,
                                                onUnload = smart.refineEngine::unload,
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }

                        composable(Screen.About.route) {
                            AboutScreen(versionName = versionName, onBack = { navController.popBackStack() })
                        }
                    }
                    }
                }
                }
                }
            }
        }
    }
}

/**
 * The file another app gave: opened with it (VIEW, the file in the data) or
 * shared to it (SEND, the file in EXTRA_STREAM or the clip). Null otherwise,
 * and null for any URI that is not safe to take from another app (see
 * [isForeignContentUri]).
 */
private fun openedFile(intent: android.content.Intent?, ownPackage: String): android.net.Uri? {
    intent ?: return null
    val uri = when (intent.action) {
        android.content.Intent.ACTION_VIEW -> intent.data
        android.content.Intent.ACTION_SEND -> {
            val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM) as? android.net.Uri
            }
            stream ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        }
        else -> null
    }
    return uri?.takeIf { isForeignContentUri(it, ownPackage) }
}

/**
 * True only for a content URI served by another app. Any app can start this
 * activity, so what it hands over must not make the app open its own private
 * files on the sender's behalf: a file URI (the app can read its own data
 * folder, which the sender cannot) or a URI of one of the app's own providers
 * (the FileProvider serves the cache, the exports and the projects) is refused.
 */
internal fun isForeignContentUri(uri: android.net.Uri, ownPackage: String): Boolean {
    if (!uri.scheme.equals(android.content.ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return false
    val authority = uri.authority?.lowercase(java.util.Locale.ROOT) ?: return false
    val own = ownPackage.lowercase(java.util.Locale.ROOT)
    // An authority may list several names separated by ';' and may carry a user id ("10@...").
    return authority.split(';').map { it.substringAfterLast('@') }.none { it == own || it.startsWith("$own.") }
}

/** The phone's haptic feedback, played only while vibration is on in the settings ([enabled]). */
private class SettingsHaptics(private val phone: HapticFeedback, private val enabled: () -> Boolean) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (enabled()) phone.performHapticFeedback(hapticFeedbackType)
    }
}