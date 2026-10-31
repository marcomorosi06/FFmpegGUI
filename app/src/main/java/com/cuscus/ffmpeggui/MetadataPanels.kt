@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.CustomTag
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.TagKind
import com.cuscus.ffmpeggui.core.model.tagKind
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint

/*
 * Every tag of the file besides the usual ones: the EXIF of a photo (when and
 * with which camera it was taken, its settings, where), the tags of a video
 * or a song (the place and the phone it was recorded on, its date, anything a
 * program left). Grouped by what they say, the sensitive groups (place,
 * device) marked; each one edited in place or taken away, new ones added, and
 * two buttons that take away what is sensitive, or everything.
 */

/** What each kind of tag is called and shown with. */
private val TagKind.labelRes: Int
    @StringRes get() = when (this) {
        TagKind.LOCATION -> R.string.tag_kind_location
        TagKind.DEVICE -> R.string.tag_kind_device
        TagKind.DATE -> R.string.tag_kind_date
        TagKind.CAMERA -> R.string.tag_kind_camera
        TagKind.OTHER -> R.string.tag_kind_other
    }

private val TagKind.icon: ImageVector
    get() = when (this) {
        TagKind.LOCATION -> Icons.Rounded.LocationOn
        TagKind.DEVICE -> Icons.Rounded.PhoneAndroid
        TagKind.DATE -> Icons.Rounded.Event
        TagKind.CAMERA -> Icons.Rounded.PhotoCamera
        TagKind.OTHER -> Icons.AutoMirrored.Rounded.Label
    }

/**
 * A tag's name as people read it: an EXIF name split into words
 * ("DateTimeOriginal" is "Date time original", "GPSLatitude" "GPS latitude");
 * a name with dots or underscores as the file has it.
 */
internal fun readableTagName(key: String): String {
    if (key.any { it == '.' || it == '_' || it == '-' || it == ' ' }) return key
    val words = key.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")).filter { it.isNotEmpty() }
    return words.mapIndexed { index, word ->
        when {
            word.length > 1 && word.all { it.isUpperCase() || it.isDigit() } -> word
            index == 0 -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word.lowercase()
        }
    }.joinToString(" ")
}

/** Every other tag of the file ([MetadataTags.custom]), grouped; [picture]: EXIF names. */
@Composable
internal fun AllTagsCard(
    tags: MetadataTags,
    onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit,
    picture: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val groups = tags.custom.groupBy { tagKind(it.key) }
    val sensitive = tags.custom.count { tagKind(it.key).sensitive }
    PanelCard(modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.SoftBurst,
                morphed = sensitive == 0,
                color = if (sensitive > 0) scheme.errorContainer else scheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (sensitive > 0) Icons.Rounded.DataObject else Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = if (sensitive > 0) scheme.onErrorContainer else scheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tags_all), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    text = when {
                        tags.custom.isEmpty() && tags.stripAll -> stringResource(R.string.tags_removed_all)
                        tags.custom.isEmpty() -> stringResource(R.string.tags_none)
                        sensitive > 0 -> pluralStringResource(R.plurals.tags_count_sensitive, tags.custom.size, tags.custom.size, sensitive)
                        else -> pluralStringResource(R.plurals.tags_count, tags.custom.size, tags.custom.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sensitive > 0) scheme.error else scheme.onSurfaceVariant,
                )
            }
        }
        // Taking away: what is sensitive, or everything. Two halves of one connected pair.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTags(null) { it.withoutSensitive() }
                },
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    pressedShape = RoundedCornerShape(12.dp),
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.tags_remove_sensitive), maxLines = 1)
            }
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    onTags(null) { it.cleared() }
                },
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                    pressedShape = RoundedCornerShape(12.dp),
                ),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.tags_remove_all), maxLines = 1)
            }
        }
        // What was taken away, as pills.
        AnimatedVisibility(
            visible = tags.stripSensitive || tags.stripAll,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tags.stripAll) StatusPill(Icons.Rounded.DeleteSweep, stringResource(R.string.tags_removed_original), scheme.errorContainer, scheme.onErrorContainer)
                if (tags.stripSensitive) StatusPill(Icons.Rounded.Shield, stringResource(R.string.tags_removed_sensitive), scheme.tertiaryContainer, scheme.onTertiaryContainer)
            }
        }
        // The tags, by what they say: the sensitive groups first.
        TagKind.entries.sortedBy { if (it.sensitive) 0 else 1 }.forEach { kind ->
            val group = groups[kind].orEmpty()
            if (group.isNotEmpty()) TagGroup(kind, group, onTags)
        }
        AddTagRow(picture, onTags)
        if (picture) PanelHint(stringResource(R.string.tags_exif_hint))
    }
}

/** A small pill saying what was taken away. */
@Composable
private fun StatusPill(icon: ImageVector, text: String, container: Color, content: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** One group of tags: its badge and name (marked when sensitive), a button that takes it away, its fields. */
@Composable
private fun TagGroup(kind: TagKind, group: List<CustomTag>, onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val container = if (kind.sensitive) scheme.errorContainer.copy(alpha = 0.35f) else scheme.surfaceContainerHigh
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.Cookie6Sided,
                morphed = kind.sensitive,
                color = if (kind.sensitive) scheme.error else scheme.secondary,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(kind.icon, contentDescription = null, tint = if (kind.sensitive) scheme.onError else scheme.onSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(kind.labelRes), style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f, fill = false))
            if (kind.sensitive) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(Icons.Rounded.Shield, stringResource(R.string.tag_sensitive), scheme.error, scheme.onError)
            }
            Spacer(modifier = Modifier.weight(1f))
            val removeGroup = stringResource(R.string.tags_group_remove, stringResource(kind.labelRes))
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                val keys = group.map { it.key }.toSet()
                onTags(null) { t -> t.copy(custom = t.custom.filter { it.key !in keys }) }
            }) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = removeGroup)
            }
        }
        group.forEach { tag ->
            val name = readableTagName(tag.key)
            OutlinedTextField(
                value = tag.value,
                onValueChange = { text -> onTags("custom:${tag.key}") { t -> t.copy(custom = t.custom.map { if (it.key == tag.key) it.copy(value = text) else it }) } },
                label = { Text(name) },
                trailingIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onTags(null) { t -> t.copy(custom = t.custom.filter { it.key != tag.key }) }
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tag_remove, name))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A new tag: its name and value, added with the button (or the keyboard's done key). */
@Composable
private fun AddTagRow(picture: Boolean, onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var key by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    val canAdd = key.isNotBlank() && value.isNotBlank() && key.trim() !in MetadataTags.KNOWN_KEYS
    fun add() {
        if (!canAdd) return
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        val k = key.trim()
        val v = value.trim()
        onTags(null) { t -> t.copy(custom = t.custom.filter { it.key != k } + CustomTag(k, v)) }
        key = ""
        value = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.filter { c -> !c.isWhitespace() || !picture } },
            label = { Text(stringResource(if (picture) R.string.tag_add_name_exif else R.string.tag_add_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.weight(0.42f),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(stringResource(R.string.tag_add_value)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(0.58f),
        )
        FilledTonalIconButton(onClick = { add() }, enabled = canAdd) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tag_add))
        }
    }
}
