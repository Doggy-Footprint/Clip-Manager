package com.doggy.clip_manager.feature.sidelayer

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.ConflictPolicy
import com.doggy.clip_manager.core.data.operation.ConsentRequiredException
import com.doggy.clip_manager.core.data.operation.FileOperationRepository
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.data.operation.OperationDestination
import com.doggy.clip_manager.core.data.operation.OperationOutcome
import com.doggy.clip_manager.core.data.operation.TrashEntry
import com.doggy.clip_manager.core.data.operation.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private sealed interface PendingConsent {
    data object PasteRetry : PendingConsent
    data class DeleteRetry(val source: FileOperationSource, val remaining: List<FileOperationSource>) : PendingConsent
}

@HiltViewModel
class OperationViewModel @Inject constructor(
    private val fileOperationRepository: FileOperationRepository,
    private val archiveRepository: ArchiveRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selected = MutableStateFlow<List<FileOperationSource>>(emptyList())
    val selected: StateFlow<List<FileOperationSource>> = _selected.asStateFlow()

    private val _staged = MutableStateFlow<List<FileOperationSource>>(emptyList())
    val staged: StateFlow<List<FileOperationSource>> = _staged.asStateFlow()

    private val _stagedMove = MutableStateFlow(false)
    val stagedMove: StateFlow<Boolean> = _stagedMove.asStateFlow()

    private val _conflict = MutableStateFlow<FileOperationSource?>(null)
    val conflict: StateFlow<FileOperationSource?> = _conflict.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _trashEntries = MutableStateFlow<List<TrashEntry>>(emptyList())
    val trashEntries: StateFlow<List<TrashEntry>> = _trashEntries.asStateFlow()

    private val _showTrash = MutableStateFlow(false)
    val showTrash: StateFlow<Boolean> = _showTrash.asStateFlow()

    private val _consentRequest = MutableStateFlow<IntentSender?>(null)
    val consentRequest: StateFlow<IntentSender?> = _consentRequest.asStateFlow()

    // R14: screens observe this to reload their current listing after a mutating op completes;
    // it is a plain counter rather than an event so a late subscriber can't miss the signal.
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()
    private fun bumpRefresh() {
        _refreshTick.value++
    }

    // Pending paste queue state; not exposed because only `conflict`/`busy` are observable per spec.
    private val pasteQueue = ArrayDeque<FileOperationSource>()
    private var pasteDestination: OperationDestination? = null
    private var stickyPolicy: ConflictPolicy? = null
    private var pendingConsent: PendingConsent? = null
    private var pasteCompleted = 0
    private var pasteSkipped = 0
    private var pasteFailed = 0
    private var pasteLastError: String? = null

    fun enterSelection() {
        _selectionMode.value = true
    }

    fun clearSelection() {
        _selectionMode.value = false
        _selected.value = emptyList()
    }

    fun toggle(source: FileOperationSource) {
        _selected.value = if (_selected.value.contains(source)) {
            _selected.value - source
        } else {
            _selected.value + source
        }
    }

    /** Screen/folder/tab exit clears only the current selection; staged items persist across tabs (A8). */
    fun leaveScreen() {
        clearSelection()
    }

    fun stage(move: Boolean) {
        if (_selected.value.isEmpty()) return
        _staged.value = _selected.value
        _stagedMove.value = move
        clearSelection()
    }

    fun cancelStage() {
        _staged.value = emptyList()
        pasteQueue.clear()
        pasteDestination = null
        stickyPolicy = null
    }

    fun paste(destination: OperationDestination) {
        if (_staged.value.isEmpty() || _busy.value) return
        pasteQueue.clear()
        pasteQueue.addAll(_staged.value)
        pasteDestination = destination
        stickyPolicy = null
        pasteCompleted = 0
        pasteSkipped = 0
        pasteFailed = 0
        pasteLastError = null
        viewModelScope.launch { runPasteQueue(ConflictPolicy.ASK) }
    }

    private suspend fun runPasteQueue(forcedPolicy: ConflictPolicy?) {
        val destination = pasteDestination ?: return
        _busy.value = true
        var firstPolicy = forcedPolicy
        while (pasteQueue.isNotEmpty()) {
            val source = pasteQueue.first()
            val policy = firstPolicy ?: stickyPolicy ?: ConflictPolicy.ASK
            firstPolicy = null
            val outcome = try {
                fileOperationRepository.copyOrMove(listOf(source), destination, policy, _stagedMove.value).first()
            } catch (e: ConsentRequiredException) {
                _consentRequest.value = e.intentSender
                pendingConsent = PendingConsent.PasteRetry
                _busy.value = false
                return
            }
            if (outcome.conflict) {
                _conflict.value = source
                _busy.value = false
                return
            }
            pasteQueue.removeFirst()
            tallyOutcome(outcome)
        }
        finishPaste()
    }

    // D10: an error outcome must survive to the final message instead of being clobbered by the
    // batch's completion summary, so failures are tallied here and folded into that summary.
    private fun tallyOutcome(outcome: OperationOutcome) {
        when {
            outcome.error != null -> {
                pasteFailed++
                pasteLastError = outcome.error
            }
            outcome.skipped -> pasteSkipped++
            else -> pasteCompleted++
        }
    }

    private fun finishPaste() {
        _staged.value = emptyList()
        pasteDestination = null
        stickyPolicy = null
        _busy.value = false
        val parts = mutableListOf("완료 ${pasteCompleted}건")
        if (pasteSkipped > 0) parts += "건너뜀 ${pasteSkipped}건"
        if (pasteFailed > 0) parts += "실패 ${pasteFailed}건" + (pasteLastError?.let { ": $it" } ?: "")
        _message.value = parts.joinToString(", ")
        bumpRefresh()
    }

    fun resolveConflict(policy: ConflictPolicy, applyRemaining: Boolean) {
        val destination = pasteDestination ?: run { _conflict.value = null; return }
        val source = pasteQueue.firstOrNull() ?: run { _conflict.value = null; return }
        if (applyRemaining && (policy == ConflictPolicy.SKIP || policy == ConflictPolicy.AUTO_RENAME)) {
            stickyPolicy = policy
        }
        _conflict.value = null
        viewModelScope.launch {
            _busy.value = true
            val outcome = try {
                fileOperationRepository.copyOrMove(listOf(source), destination, policy, _stagedMove.value).first()
            } catch (e: ConsentRequiredException) {
                _consentRequest.value = e.intentSender
                pendingConsent = PendingConsent.PasteRetry
                _busy.value = false
                return@launch
            }
            pasteQueue.removeFirst()
            tallyOutcome(outcome)
            runPasteQueue(null)
        }
    }

    fun cancelConflict() {
        _conflict.value = null
        pasteQueue.clear()
        pasteDestination = null
        stickyPolicy = null
        _busy.value = false
    }

    fun delete(sources: List<FileOperationSource>) {
        if (sources.isEmpty()) return
        viewModelScope.launch { runDeleteQueue(sources) }
    }

    private suspend fun runDeleteQueue(sources: List<FileOperationSource>) {
        _busy.value = true
        var remaining = sources
        while (remaining.isNotEmpty()) {
            val source = remaining.first()
            val outcome = try {
                trashRepository.delete(listOf(source)).first()
            } catch (e: ConsentRequiredException) {
                _consentRequest.value = e.intentSender
                pendingConsent = PendingConsent.DeleteRetry(source, remaining.drop(1))
                _busy.value = false
                return
            }
            outcome.error?.let { _message.value = it }
            remaining = remaining.drop(1)
        }
        _busy.value = false
        clearSelection()
        bumpRefresh()
    }

    fun zip(sources: List<FileOperationSource>, target: File) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val result = fileOperationRepository.zip(sources, target)
            result.exceptionOrNull()?.let { _message.value = it.message ?: "ZIP 생성 실패" }
            _busy.value = false
            if (result.isSuccess) {
                clearSelection()
                bumpRefresh()
            }
        }
    }

    fun openTrash() {
        _showTrash.value = true
        viewModelScope.launch { _trashEntries.value = trashRepository.list() }
    }

    fun closeTrash() {
        _showTrash.value = false
    }

    fun restore(id: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = trashRepository.restore(id)
            result.exceptionOrNull()?.let { _message.value = it.message ?: "복원 실패" }
            _trashEntries.value = trashRepository.list()
            _busy.value = false
            if (result.isSuccess) bumpRefresh()
        }
    }

    /** v6: called once a shown `message` has been presented (e.g. as a Snackbar), so it isn't re-shown. */
    fun consumeMessage() {
        _message.value = null
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _busy.value = true
            val result = trashRepository.empty()
            result.exceptionOrNull()?.let { _message.value = it.message ?: "휴지통 비우기 실패" }
            _trashEntries.value = trashRepository.list()
            _busy.value = false
        }
    }

    fun onConsentResult(granted: Boolean) {
        _consentRequest.value = null
        val pending = pendingConsent ?: return
        pendingConsent = null
        if (!granted) {
            _message.value = "권한 동의가 거부되었습니다"
            when (pending) {
                is PendingConsent.PasteRetry -> {
                    pasteQueue.removeFirstOrNull()
                    viewModelScope.launch { runPasteQueue(null) }
                }
                is PendingConsent.DeleteRetry -> viewModelScope.launch { runDeleteQueue(pending.remaining) }
            }
            return
        }
        viewModelScope.launch {
            when (pending) {
                // The item already reached the destination before the consent-triggered delete
                // failed, so the retry forces REPLACE instead of re-asking about the same name.
                is PendingConsent.PasteRetry -> runPasteQueue(ConflictPolicy.REPLACE)
                is PendingConsent.DeleteRetry -> runDeleteQueue(listOf(pending.source) + pending.remaining)
            }
        }
    }
}
