package com.lightnotes.app.editor

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.ArrayDeque

data class EditSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class EditHistoryManager(
    private val editText: EditText,
    private val onStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit
) {
    private val maxHistorySize = 100
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()

    private var isRestoring = false
    private var lastSnapshotTime = 0L
    private val coalesceThresholdMs = 450L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCoalesceRunnable: Runnable? = null
    private var previousText: String = ""

    init {
        previousText = editText.text.toString()

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isRestoring) {
                    previousText = s?.toString() ?: ""
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isRestoring) return

                val currentText = s?.toString() ?: ""
                if (currentText == previousText) return

                val now = System.currentTimeMillis()
                val isSignificant = (currentText.contains("\n") && !previousText.contains("\n")) ||
                        Math.abs(currentText.length - previousText.length) > 2

                if (isSignificant || (now - lastSnapshotTime > coalesceThresholdMs)) {
                    commitPendingSnapshot()
                    pushUndoState(previousText, editText.selectionStart, editText.selectionEnd)
                    lastSnapshotTime = now
                } else {
                    scheduleCoalescedSnapshot(previousText, editText.selectionStart, editText.selectionEnd)
                }
                previousText = currentText
            }
        })
    }

    private fun scheduleCoalescedSnapshot(text: String, selStart: Int, selEnd: Int) {
        if (pendingCoalesceRunnable == null) {
            pendingCoalesceRunnable = Runnable {
                pushUndoState(text, selStart, selEnd)
                pendingCoalesceRunnable = null
            }
            handler.postDelayed(pendingCoalesceRunnable!!, coalesceThresholdMs)
        }
    }

    private fun commitPendingSnapshot() {
        pendingCoalesceRunnable?.let {
            handler.removeCallbacks(it)
            it.run()
            pendingCoalesceRunnable = null
        }
    }

    fun initHistory(initialText: String) {
        commitPendingSnapshot()
        undoStack.clear()
        redoStack.clear()
        previousText = initialText
        undoStack.push(EditSnapshot(initialText, initialText.length, initialText.length))
        notifyState()
    }

    fun commitImmediateSnapshot() {
        commitPendingSnapshot()
        val current = editText.text.toString()
        pushUndoState(previousText, editText.selectionStart, editText.selectionEnd)
        previousText = current
        lastSnapshotTime = System.currentTimeMillis()
    }

    private fun pushUndoState(text: String, selStart: Int, selEnd: Int) {
        if (undoStack.isNotEmpty() && undoStack.peek().text == text) {
            return
        }
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeLast()
        }
        undoStack.push(EditSnapshot(text, selStart, selEnd))
        redoStack.clear()
        notifyState()
    }

    fun canUndo(): Boolean = undoStack.size > 1
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        commitPendingSnapshot()
        if (!canUndo()) return

        val current = undoStack.pop()
        redoStack.push(EditSnapshot(editText.text.toString(), editText.selectionStart, editText.selectionEnd))

        val target = undoStack.peek() ?: return
        restoreSnapshot(target)
    }

    fun redo() {
        if (!canRedo()) return

        val target = redoStack.pop()
        undoStack.push(EditSnapshot(editText.text.toString(), editText.selectionStart, editText.selectionEnd))

        restoreSnapshot(target)
    }

    private fun restoreSnapshot(snapshot: EditSnapshot) {
        isRestoring = true
        try {
            previousText = snapshot.text
            editText.setText(snapshot.text)
            val selStart = snapshot.selectionStart.coerceIn(0, snapshot.text.length)
            val selEnd = snapshot.selectionEnd.coerceIn(0, snapshot.text.length)
            editText.setSelection(selStart, selEnd)
        } finally {
            isRestoring = false
            notifyState()
        }
    }

    fun notifyState() {
        onStateChanged(canUndo(), canRedo())
    }
}
