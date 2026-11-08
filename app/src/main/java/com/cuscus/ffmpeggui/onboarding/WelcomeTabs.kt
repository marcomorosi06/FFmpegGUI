@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.media.HardwareCalibration
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.settings.AppSettings
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup

/*
 * The welcome's pages that hold more than one thing: a heading, a row of
 * connected buttons to pick one of its parts, and that part (the pages they
 * were before, see WelcomePages.kt). Seven pages instead of eleven, each
 * still short enough to read at a glance; the summary at the end goes back
 * to the very part a choice was made on.
 */

/** The parts of "What it does". */
internal enum class MakesPart { FORMATS, EDITOR, PHOTOS }

/** The parts of "Make it yours". */
internal enum class YoursPart { LOOK, FEEL }

/** The parts of "Your work". */
internal enum class WorkPart { EXPORTS, PROJECTS, NOTIFY }

/** Formats, the video editor and the photo editor. */
@Composable
internal fun MakesPage(motion: PageMotion, part: MakesPart, onPart: (MakesPart) -> Unit) {
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_makes_kicker),
            title = stringResource(R.string.welcome_makes_title),
            text = stringResource(R.string.welcome_makes_text),
        )
        Parts(
            motion = motion,
            parts = MakesPart.entries,
            part = part,
            label = { stringResource(it.label) },
            onPart = onPart,
        ) { shown ->
            when (shown) {
                MakesPart.FORMATS -> FormatsSection(motion)
                MakesPart.EDITOR -> EditorSection(motion)
                MakesPart.PHOTOS -> PhotosSection(motion)
            }
        }
    }
}

/** The look and the feel. */
@Composable
internal fun YoursPage(motion: PageMotion, part: YoursPart, onPart: (YoursPart) -> Unit, settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_yours_kicker),
            title = stringResource(R.string.welcome_look_title),
            text = stringResource(R.string.welcome_yours_text),
        )
        Parts(
            motion = motion,
            parts = YoursPart.entries,
            part = part,
            label = { stringResource(it.label) },
            onPart = onPart,
        ) { shown ->
            when (shown) {
                YoursPart.LOOK -> LookSection(motion, settings, onChange)
                YoursPart.FEEL -> FeelSection(motion, settings, onChange)
            }
        }
    }
}

/** Exports, projects and notifications. */
@Composable
internal fun WorkPage(
    motion: PageMotion,
    part: WorkPart,
    onPart: (WorkPart) -> Unit,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    calibration: HardwareCalibration.State,
    canCalibrate: Boolean,
    onCalibrate: () -> Unit,
    onStopCalibration: () -> Unit,
    projects: List<ProjectCard>,
    onDeleteAllProjects: () -> Unit,
    onKeepProjects: (Int) -> Unit,
    notifications: NotificationAccess,
) {
    PageColumn {
        PageHeading(
            motion,
            kicker = stringResource(R.string.welcome_work_kicker),
            title = stringResource(R.string.welcome_work_title),
            text = stringResource(R.string.welcome_work_text),
        )
        Parts(
            motion = motion,
            parts = WorkPart.entries,
            part = part,
            label = { stringResource(it.label) },
            onPart = onPart,
        ) { shown ->
            when (shown) {
                WorkPart.EXPORTS -> ExportsSection(motion, settings, onChange, calibration, canCalibrate, onCalibrate, onStopCalibration)
                WorkPart.PROJECTS -> ProjectsSection(motion, settings, onChange, projects, onDeleteAllProjects, onKeepProjects)
                WorkPart.NOTIFY -> NotifySection(motion, settings, onChange, notifications)
            }
        }
    }
}

/**
 * The row of connected buttons, and the part chosen under it, sliding in from
 * the side it is on (the next part from the right).
 */
@Composable
private fun <T : Enum<T>> Parts(
    motion: PageMotion,
    parts: List<T>,
    part: T,
    label: @Composable (T) -> String,
    onPart: (T) -> Unit,
    content: @Composable (T) -> Unit,
) {
    ConnectedChoiceGroup(
        options = parts,
        selected = part,
        label = label,
        onSelect = onPart,
        modifier = Modifier.enter(motion, 3),
    )
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = part,
        transitionSpec = {
            val forwards = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(spatial) { if (forwards) it / 4 else -it / 4 } + fadeIn(effects)) togetherWith
                (slideOutHorizontally(spatial) { if (forwards) -it / 4 else it / 4 } + fadeOut(effects))
        },
        label = "welcomePart",
        modifier = Modifier.fillMaxWidth(),
    ) { shown ->
        Column(modifier = Modifier.fillMaxWidth()) { content(shown) }
    }
}

private val MakesPart.label: Int
    get() = when (this) {
        MakesPart.FORMATS -> R.string.welcome_formats_kicker
        MakesPart.EDITOR -> R.string.welcome_editor_kicker
        MakesPart.PHOTOS -> R.string.welcome_photos_kicker
    }

private val YoursPart.label: Int
    get() = when (this) {
        YoursPart.LOOK -> R.string.welcome_look_kicker
        YoursPart.FEEL -> R.string.welcome_feel_kicker
    }

private val WorkPart.label: Int
    get() = when (this) {
        WorkPart.EXPORTS -> R.string.welcome_exports_kicker
        WorkPart.PROJECTS -> R.string.welcome_projects_kicker
        WorkPart.NOTIFY -> R.string.welcome_notify_kicker
    }
