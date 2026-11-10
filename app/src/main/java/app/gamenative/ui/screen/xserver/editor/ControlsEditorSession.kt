package app.gamenative.ui.screen.xserver.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.winlator.core.FileUtils
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.widget.InputControlsView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * One state of the profile in the undo history: the profile file text and which element (index) is selected.
 * Whole-profile snapshots cover every change alike (move, size, settings, add, delete, duplicate, radial menus).
 */
data class EditorSnapshot(val profileText: String, val selectedIndex: Int)

/**
 * State and actions of one run of the controls editor (edit mode of the on-screen controls). Holds the undo history
 * (a session only), the selection, and the settings-sheet state for the Compose UI; implements [EditorListener] for the
 * gesture results of the view.
 *
 * Every committed change is saved to the profile file right away, so leaving the app does not lose work. [cancel] writes
 * the original file back.
 */
class ControlsEditorSession : EditorListener {
    private val history = UndoHistory<EditorSnapshot>()
    private var view: InputControlsView? = null
    private var originalText: String? = null
    private var firstText: String? = null

    /** The selected element (drives the Compose UI). */
    var selected by mutableStateOf<ControlElement?>(null)
        private set

    /** Counts every committed change; the UI re-reads element values when it changes. */
    var revision by mutableIntStateOf(0)
        private set

    /** Counts changes that did not come from the open sheet itself (undo, pinch, binding picker): the sheet reloads its fields. */
    var externalRevision by mutableIntStateOf(0)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** The settings sheet of the selected element is open. */
    var sheetOpen by mutableStateOf(false)

    /** Counter of "element deleted" notices; 0 = none shown. */
    var deleteNoticeId by mutableIntStateOf(0)
        private set
    var deleteNoticeVisible by mutableStateOf(false)
        private set

    val isActive: Boolean get() = view != null

    /**
     * Set by the open sheet: commits what is still being typed or slid (one undo step). Called before an undo or redo, so they
     * act on the finished change and a pending one is not lost or applied afterwards on top of the restored state.
     */
    var flushPending: (() -> Unit)? = null

    /**
     * Starts the session on [inputView] once its profile and elements are loaded (waits briefly for that).
     * Returns false if there is no profile.
     */
    suspend fun begin(inputView: InputControlsView): Boolean {
        delay(60)
        var tries = 0
        while ((inputView.profile == null || inputView.width == 0 || inputView.height == 0) && tries < 60) {
            delay(50)
            tries++
        }
        val profile = inputView.profile ?: return false
        if (!profile.isElementsLoaded) profile.loadElements(inputView)

        view = inputView
        inputView.setEditorListener(this)
        val file = ControlsProfile.getProfileFile(inputView.context, profile.id)
        originalText = if (file.isFile) FileUtils.readString(file) else null
        profile.save()
        val text = readProfileText(profile) ?: ""
        firstText = text
        history.reset(EditorSnapshot(text, -1))
        selected = inputView.selectedElement
        sheetOpen = false
        deleteNoticeVisible = false
        refreshHistoryFlags()
        return true
    }

    // ---- listener from the view ------------------------------------------------------------------------------------

    override fun onSelectionChanged(element: ControlElement?) {
        selected = element
        if (element == null) sheetOpen = false
    }

    override fun onEditRequested(element: ControlElement) {
        selected = element
        sheetOpen = true
    }

    override fun onGestureFinished(changed: Boolean) {
        if (changed) {
            commit()
            externalRevision++
        }
    }

    // ---- change handling -------------------------------------------------------------------------------------------

    /**
     * Saves the profile and records the new state in the undo history (nothing happens if the profile text did not change).
     * [selectionBefore] is the index of the element the change was about, restored when the change is undone.
     */
    fun commit(selectionBefore: Int = selectedIndex()) {
        val inputView = view ?: return
        val profile = inputView.profile ?: return
        profile.save()
        val text = readProfileText(profile) ?: return
        val current = history.current
        if (current != null && current.profileText == text) {
            revision++
            return
        }
        if (current != null) history.replaceCurrent(current.copy(selectedIndex = selectionBefore))
        history.record(EditorSnapshot(text, selectedIndex()))
        revision++
        refreshHistoryFlags()
    }

    /** The full element dialog was closed, or the profile was replaced by "copy from profile". */
    fun onExternalEdit() {
        val inputView = view ?: return
        val profile = inputView.profile ?: return
        val current = selected
        if (current != null && profile.elements.none { it === current }) inputView.editorSelect(null)
        commit()
        externalRevision++
        inputView.invalidate()
    }

    fun undo() {
        flushPending?.invoke()
        val snapshot = history.undo() ?: return
        apply(snapshot)
    }

    fun redo() {
        flushPending?.invoke()
        val snapshot = history.redo() ?: return
        apply(snapshot)
    }

    private fun apply(snapshot: EditorSnapshot) {
        val inputView = view ?: return
        val profile = inputView.profile ?: return
        loadProfileText(inputView, profile, snapshot.profileText)
        inputView.editorSelect(profile.elements.getOrNull(snapshot.selectedIndex))
        inputView.invalidate()
        revision++
        externalRevision++
        deleteNoticeVisible = false
        refreshHistoryFlags()
    }

    // ---- element actions -------------------------------------------------------------------------------------------

    fun addElement(kind: PaletteKind, bar: Int = 1) {
        val inputView = view ?: return
        val profile = inputView.profile ?: return
        val selectionBefore = selectedIndex()
        val element = ElementPalette.create(kind, inputView, profile, inputView.context, bar)

        val step = inputView.snappingSize.toFloat()
        val areaW = inputView.maxWidth.toFloat()
        val areaH = inputView.maxHeight.toFloat()
        val occupied = profile.elements.map { it.x.toFloat() to it.y.toFloat() }
        val (cx, cy) = ElementPlacement.findFreeCenter(areaW / 2f, areaH / 2f, step, areaW, areaH, occupied)
        element.setX(snapToGrid(cx, step))
        element.setY(snapToGrid(cy, step))

        profile.addElement(element)
        inputView.editorSelect(element)
        sheetOpen = true
        commit(selectionBefore)
        externalRevision++
        inputView.invalidate()
    }

    fun duplicateSelected() {
        val inputView = view ?: return
        val profile = inputView.profile ?: return
        val element = selected ?: return
        val index = profile.elements.indexOfFirst { it === element }
        if (index < 0) return

        profile.save()
        val text = readProfileText(profile) ?: return
        val step = inputView.snappingSize.toFloat()
        val areaW = inputView.maxWidth.toFloat()
        val areaH = inputView.maxHeight.toFloat()
        val occupied = profile.elements.map { it.x.toFloat() to it.y.toFloat() }
        val (cx, cy) = ElementPlacement.duplicateCenter(element.x.toFloat(), element.y.toFloat(), step, areaW, areaH, occupied)
        val duplicate = ProfileJson.duplicateElement(
            text,
            index,
            ((cx - element.x) / areaW).toDouble(),
            ((cy - element.y) / areaH).toDouble(),
        ) ?: return

        loadProfileText(inputView, profile, duplicate.json)
        inputView.editorSelect(profile.elements.getOrNull(duplicate.newIndex))
        commit(index)
        externalRevision++
        inputView.invalidate()
    }

    fun deleteSelected() {
        val inputView = view ?: return
        val profile = inputView.profile ?: return
        val element = selected ?: return
        val index = profile.elements.indexOfFirst { it === element }
        profile.removeElement(element)
        inputView.editorSelect(null)
        sheetOpen = false
        commit(index)
        externalRevision++
        deleteNoticeId++
        deleteNoticeVisible = true
        inputView.invalidate()
    }

    fun dismissDeleteNotice() {
        deleteNoticeVisible = false
    }

    /** Makes the open sheet re-read the values of its element (after a change that did not come from the sheet's own fields). */
    fun refreshSheet() {
        externalRevision++
    }

    // ---- leaving ---------------------------------------------------------------------------------------------------

    /** "Fertig": the profile is saved, the session ends. */
    fun finish() {
        val inputView = view
        val profile = inputView?.profile
        if (inputView != null && profile != null) {
            profile.save()
            inputView.onControlsProfileContentChanged(false)
        }
        end()
    }

    /** "Abbrechen": the profile file as it was before editing is written back and loaded again. */
    fun cancel() {
        val inputView = view
        val profile = inputView?.profile
        if (inputView != null && profile != null) {
            val text = originalText ?: firstText
            if (text != null) loadProfileText(inputView, profile, text)
            inputView.onControlsProfileContentChanged(false)
        }
        end()
    }

    /** The UI left the screen without finish/cancel: everything committed so far is already saved. */
    fun detach() = end()

    private fun end() {
        view?.setEditorListener(null)
        view = null
        selected = null
        sheetOpen = false
        deleteNoticeVisible = false
        originalText = null
        firstText = null
        canUndo = false
        canRedo = false
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    fun selectedIndex(): Int {
        val profile = view?.profile ?: return -1
        val element = selected ?: return -1
        return profile.elements.indexOfFirst { it === element }
    }

    fun invalidateView() {
        view?.invalidate()
    }

    private fun refreshHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    private fun readProfileText(profile: ControlsProfile): String? {
        val inputView = view ?: return null
        val file = ControlsProfile.getProfileFile(inputView.context, profile.id)
        return if (file.isFile) FileUtils.readString(file) else null
    }

    /** Writes [text] as the profile file and loads the elements and radial menus from it again. */
    private fun loadProfileText(inputView: InputControlsView, profile: ControlsProfile, text: String) {
        val file = ControlsProfile.getProfileFile(inputView.context, profile.id)
        FileUtils.writeString(file, text)
        profile.loadElements(inputView)
        profile.loadRadialMenus()
        roundPositions(inputView, profile, text)
        inputView.editorSelect(null)
    }

    /**
     * The profile stores positions as fractions of the view size and loading cuts them off; this puts every element on the
     * nearest pixel instead, so undo and restore never move elements by a pixel.
     */
    private fun roundPositions(inputView: InputControlsView, profile: ControlsProfile, text: String) {
        val elements = profile.elements
        val count = runCatching { ProfileJson.elementCount(text) }.getOrDefault(-1)
        if (count != elements.size) return
        val w = inputView.maxWidth
        val h = inputView.maxHeight
        for (i in elements.indices) {
            val position = runCatching { ProfileJson.positionOf(text, i) }.getOrNull() ?: continue
            elements[i].setX((position.first * w).roundToInt())
            elements[i].setY((position.second * h).roundToInt())
        }
    }

    private fun snapToGrid(value: Float, step: Float): Int =
        if (step > 0f) (Math.floor((value / step + 0.5f).toDouble()) * step).toInt() else value.toInt()
}
