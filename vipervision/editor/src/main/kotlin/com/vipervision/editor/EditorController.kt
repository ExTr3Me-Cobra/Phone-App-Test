package com.vipervision.editor

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A one-tap look in the Filters tab: a preset set of adjustments. */
data class Look(val name: String, val adjustments: Adjustments)

val looks = listOf(
    Look("Original", Adjustments()),
    Look(
        "Vivid", Adjustments()
            .with(Adjustment.Saturation, 35).with(Adjustment.Contrast, 15)
    ),
    Look(
        "Warm", Adjustments()
            .with(Adjustment.Warmth, 35).with(Adjustment.Saturation, 10)
    ),
    Look(
        "Cool", Adjustments()
            .with(Adjustment.Warmth, -35).with(Adjustment.Tint, -5)
    ),
    Look(
        "Fade", Adjustments()
            .with(Adjustment.Contrast, -30).with(Adjustment.Shadows, 25).with(Adjustment.Saturation, -20)
    ),
    Look(
        "Dramatic", Adjustments()
            .with(Adjustment.Contrast, 40).with(Adjustment.Highlights, -30)
            .with(Adjustment.Shadows, -20).with(Adjustment.Vignette, 35)
    ),
    Look("Mono", Adjustments(monochrome = true)),
    Look(
        "Noir", Adjustments(monochrome = true)
            .with(Adjustment.Contrast, 45).with(Adjustment.Vignette, 30)
    )
)

/** Everything the user has changed. Undo and redo step through these. */
data class EditState(
    val geometry: Geometry = Geometry(),
    val adjustments: Adjustments = Adjustments(),
    val lookName: String = "Original"
) {
    val isUnchanged: Boolean get() = geometry.isIdentity && adjustments.isIdentity
}

enum class EditorTab { Transform, Filters, Adjust }

/** Holds the editor's state for the screen; survives rotation because the activity handles it. */
class EditorController {
    var state by mutableStateOf(EditState())
        private set
    var tab by mutableStateOf(EditorTab.Adjust)
    var selected by mutableStateOf(Adjustment.Brightness)

    /** Screen-sized copy of the photo, upright, before any edits. */
    var basePreview by mutableStateOf<Bitmap?>(null)

    /** [basePreview] with the current edits applied, for display. */
    var editedPreview by mutableStateOf<Bitmap?>(null)

    var isBusy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Last state pushed to history; slider drags only commit once released. */
    private var committed = EditState()

    /** Live change (e.g. while dragging a slider); call [commit] when the gesture ends. */
    fun preview(newState: EditState) {
        state = newState
    }

    fun commit() {
        if (state == committed) return
        undoStack.addLast(committed)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        committed = state
    }

    fun change(newState: EditState) {
        preview(newState)
        commit()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(committed)
        committed = previous
        state = previous
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(committed)
        committed = next
        state = next
    }

    fun revert() = change(EditState())

    fun setAdjustment(adjustment: Adjustment, value: Int) =
        preview(state.copy(adjustments = state.adjustments.with(adjustment, value)))

    fun rotate(clockwise: Boolean) {
        val g = state.geometry
        change(state.copy(geometry = g.copy(quarterTurns = (g.quarterTurns + if (clockwise) 1 else 3) % 4)))
    }

    /**
     * Mirrors the photo as it's shown. The mirror is applied before the rotation, so mirroring a
     * rotated photo also reverses the rotation (flip then turn back equals turn then flip).
     */
    fun flip() {
        val g = state.geometry
        change(state.copy(geometry = Geometry(quarterTurns = (4 - g.quarterTurns) % 4, flipped = !g.flipped)))
    }

    fun applyLook(look: Look) = change(state.copy(adjustments = look.adjustments, lookName = look.name))

    fun autoEnhance() {
        val preview = basePreview ?: return
        val auto = autoAdjust(preview)
        // Keep any look's black-and-white setting; replace the sliders with the automatic values.
        change(state.copy(adjustments = auto.copy(monochrome = state.adjustments.monochrome)))
        tab = EditorTab.Adjust
    }
}
