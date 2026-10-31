package com.cuscus.ffmpeggui.blank

import android.content.Context
import android.util.Log
import com.cuscus.ffmpeggui.core.model.BlankPresetsJson
import com.cuscus.ffmpeggui.core.model.BlankSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The user's own blanks (see [BlankSpec]), shown on the home screen under the
 * recent projects: neither settings nor projects, a small JSON file of their
 * own in the app's files. Read once at start, written after every change.
 */
class BlankPresetStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _presets = MutableStateFlow<List<BlankSpec>>(emptyList())

    /** The presets, the newest first. */
    val presets: StateFlow<List<BlankSpec>> = _presets.asStateFlow()

    init {
        scope.launch {
            val read = runCatching { if (file.isFile) BlankPresetsJson.read(file.readText()) else emptyList() }
                .onFailure { Log.w(TAG, "Could not read the blanks: ${it.message}") }
                .getOrDefault(emptyList())
            _presets.update { current -> if (current.isEmpty()) read else current }
        }
    }

    /** Keeps [spec] first; one of the same name takes its place. */
    fun save(spec: BlankSpec) = change { list -> listOf(spec) + list.filter { it.name != spec.name } }

    /** Forgets the preset called [name]. */
    fun delete(name: String) = change { list -> list.filter { it.name != name } }

    private fun change(edit: (List<BlankSpec>) -> List<BlankSpec>) {
        _presets.update(edit)
        val snapshot = _presets.value
        scope.launch {
            runCatching {
                val temp = File(file.parentFile, "$FILE_NAME.tmp")
                temp.writeText(BlankPresetsJson.write(snapshot))
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }.onFailure { Log.w(TAG, "Could not write the blanks: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "BlankPresetStore"
        const val FILE_NAME = "blank_presets.json"
    }
}
