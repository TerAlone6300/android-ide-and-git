package com.ide.core

import com.ide.git.bridge.GitFileStatus
import com.ide.git.bridge.IdeGitHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DiffRequest(
    val filePath: String,
    val oldContent: String?,
    val newContent: String?
)

data class GitErrorBanner(
    val title: String,
    val message: String
)

class IdeGitHostImpl(
    private val editorController: WorkspaceEditorController,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onConfirmOverwriteDirty: (suspend (filePath: String) -> Boolean)? = null
) : IdeGitHost {

    private val _fileStatuses = MutableStateFlow>(emptyMap())
    val fileStatuses: StateFlow> = _fileStatuses.asStateFlow()

    private val _activeDiff = MutableStateFlow<DiffRequest?>(null)
    val activeDiff: StateFlow<DiffRequest?> = _activeDiff.asStateFlow()

    private val _lastError = MutableStateFlow<GitErrorBanner?>(null)
    val lastError: StateFlow<GitErrorBanner?> = _lastError.asStateFlow()

    override suspend fun requestFileReload(filePath: String): Boolean = withContext(callbackDispatcher) {
        val rel = editorController.toRelativePath(filePath)
        if (editorController.reloadFromDiskIfClean(rel)) {
            return@withContext true
        }
        if (!editorController.isDirty(rel)) {
            return@withContext false
        }
        val allowOverwrite = onConfirmOverwriteDirty?.invoke(rel) ?: false
        if (!allowOverwrite) return@withContext false

        editorController.forceReloadFromDisk(rel)
    }

    override fun openFileAt(filePath: String, line: Int) {
        editorController.openOrFocus(filePath, line)
    }

    override fun openDiff(
        filePath: String,
        oldContent: String?,
        newContent: String?
    ) {
        _activeDiff.value = DiffRequest(
            filePath = editorController.toRelativePath(filePath),
            oldContent = oldContent,
            newContent = newContent
        )
    }

    override fun showGitError(title: String, message: String) {
        _lastError.value = GitErrorBanner(title = title, message = message)
    }

    override fun updateGitStatuses(statuses: Map<String, GitFileStatus>) {
        _fileStatuses.value = statuses.mapKeys { (k, _) -> editorController.toRelativePath(k) }
    }
}
