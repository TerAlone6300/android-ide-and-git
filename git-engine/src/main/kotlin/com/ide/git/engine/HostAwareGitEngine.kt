package com.ide.git.engine

import com.ide.git.bridge.GitEngine
import com.ide.git.bridge.GitOperationResult
import com.ide.git.bridge.IdeGitHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File

/**
 * Decorates Git commands with IDE notifications.
 *
 * Pull/checkout compare HEAD before and after the operation, then ask the IDE
 * to reload only paths whose committed tree entries changed. Dirty buffers are
 * never overwritten unless the host explicitly confirms the reload.
 */
internal class HostAwareGitEngine(
    private val delegate: GitEngine,
    private val projectRoot: () -> File?,
    private val host: () -> IdeGitHost?,
    private val operationMutex: Mutex,
    private val refreshStatuses: suspend () -> Unit
) : GitEngine {

    override suspend fun commit(message: String): GitOperationResult<String> =
        runOperation("Commit", reloadChangedFiles = false) {
            delegate.commit(message)
        }

    override suspend fun pull(): GitOperationResult<Unit> =
        runOperation("Pull", reloadChangedFiles = true) {
            delegate.pull()
        }

    override suspend fun push(): GitOperationResult<Unit> =
        runOperation("Push", reloadChangedFiles = false) {
            delegate.push()
        }

    override suspend fun checkout(branch: String): GitOperationResult<Unit> =
        runOperation("Checkout", reloadChangedFiles = true) {
            delegate.checkout(branch)
        }

    private suspend fun <T> runOperation(
        title: String,
        reloadChangedFiles: Boolean,
        action: suspend () -> GitOperationResult<T>
    ): GitOperationResult<T> = operationMutex.withLock {
        val previousHead = if (reloadChangedFiles) {
            try {
                readHead()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val result = try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            GitOperationResult.Failure(
                message = failure.message ?: "$title failed",
                cause = failure,
                code = "GIT_BRIDGE_OPERATION_FAILED"
            )
        }

        when (result) {
            is GitOperationResult.Success -> {
                if (reloadChangedFiles) {
                    val changedPaths = changedPaths(previousHead)
                    for (path in changedPaths) {
                        val reloaded = try {
                            host()?.requestFileReload(path) ?: true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            host()?.showGitError(
                                "$title reload failed",
                                "$path: ${failure.message ?: "Unable to reload file"}"
                            )
                            false
                        }

                        if (!reloaded) {
                            host()?.showGitError(
                                "$title needs editor attention",
                                "IDE declined to reload '$path'. Unsaved editor content was kept."
                            )
                        }
                    }
                }
                refreshStatuses()
            }

            is GitOperationResult.Failure -> {
                host()?.showGitError(title, result.message)
                refreshStatuses()
            }

            is GitOperationResult.Cancelled -> Unit
        }

        result
    }

    private suspend fun readHead(): ObjectId? = withContext(Dispatchers.IO) {
        val root = projectRoot() ?: return@withContext null
        Git.open(root).use { git ->
            git.repository.resolve("HEAD")?.copy()
        }
    }

    private suspend fun changedPaths(previousHead: ObjectId?): List<String> =
        withContext(Dispatchers.IO) {
            val root = projectRoot() ?: return@withContext emptyList()
            Git.open(root).use { git ->
                val repository = git.repository
                val newHeadId = repository.resolve("HEAD")
                    ?: return@withContext emptyList()
                if (previousHead == null || previousHead == newHeadId) {
                    return@withContext emptyList()
                }

                RevWalk(repository).use { revWalk ->
                    val oldCommit = revWalk.parseCommit(previousHead)
                    val newCommit = revWalk.parseCommit(newHeadId)
                    val reader = repository.newObjectReader()
                    reader.use {
                        val oldTree = CanonicalTreeParser().apply {
                            reset(it, oldCommit.tree)
                        }
                        val newTree = CanonicalTreeParser().apply {
                            reset(it, newCommit.tree)
                        }
                        DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                            formatter.setRepository(repository)
                            formatter.scan(oldTree, newTree)
                                .flatMap { entry ->
                                    listOf(entry.oldPath, entry.newPath)
                                        .filter { it != org.eclipse.jgit.diff.DiffEntry.DEV_NULL }
                                }
                                .distinct()
                                .sorted()
                        }
                    }
                }
            }
        }
}
