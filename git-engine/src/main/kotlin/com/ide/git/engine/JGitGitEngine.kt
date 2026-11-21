package com.ide.git.engine

import com.ide.git.bridge.GitEngine
import com.ide.git.bridge.GitOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.CredentialsProvider
import java.io.File
import java.io.IOException

/**
 * JGit-backed implementation of the IDE Git contract.
 *
 * Repository access and every Git command run on [ioDispatcher].
 * One mutex serializes operations because JGit Repository instances are
 * shared mutable resources and must not be operated on concurrently.
 */
class JGitGitEngine(
    private val credentialsProvider: CredentialsProvider? = null,
    private val remoteName: String = "origin",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GitEngine, AutoCloseable {

    private val mutex = Mutex()
    @Volatile
    private var repository: Repository? = null

    /**
     * Opens an existing Git working tree. The path may point to the project
     * root or to a directory inside that working tree.
     */
    suspend fun openRepository(rootPath: String): GitOperationResult<Unit> =
        mutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val opened = Git.open(File(rootPath)).repository
                    repository?.close()
                    repository = opened
                    GitOperationResult.Success(Unit)
                } catch (error: Exception) {
                    GitOperationResult.Failure(
                        message = error.message ?: "Unable to open Git repository",
                        cause = error,
                        code = "REPOSITORY_OPEN_FAILED"
                    )
                }
            }
        }

    /**
     * Closes the current repository session.
     */
    suspend fun closeRepository() {
        mutex.withLock {
            withContext(ioDispatcher) {
                repository?.close()
                repository = null
            }
        }
    }

    override fun close() {
        repository?.close()
        repository = null
    }

    override suspend fun commit(message: String): GitOperationResult<String> {
        if (message.isBlank()) {
            return GitOperationResult.Failure(
                message = "Commit message cannot be empty",
                code = "EMPTY_COMMIT_MESSAGE"
            )
        }

        return execute { git ->
            // Stage additions/modifications, then stage removals of tracked files.
            git.add()
                .addFilepattern(".")
                .call()
            git.add()
                .setUpdate(true)
                .addFilepattern(".")
                .call()

            git.commit()
                .setMessage(message.trim())
                .call()
                .name
        }
    }

    override suspend fun pull(): GitOperationResult<Unit> = execute { git ->
        val command = git.pull().setRemote(remoteName)
        credentialsProvider?.let(command::setCredentialsProvider)

        val result = command.call()
        if (!result.isSuccessful) {
            val detail = result.mergeResult?.let {
                "Merge status: ${it.mergeStatus}"
            } ?: "The remote changes could not be integrated"

            throw GitOperationFailure("Pull failed. $detail", "PULL_FAILED")
        }

        Unit
    }

    override suspend fun push(): GitOperationResult<Unit> = execute { git ->
        val command = git.push().setRemote(remoteName)
        credentialsProvider?.let(command::setCredentialsProvider)

        val results = command.call().toList()
        val rejected = results
            .flatMap { it.remoteUpdates }
            .filter { update ->
                update.status !in setOf(
                    org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK,
                    org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE
                )
            }

        if (rejected.isNotEmpty()) {
            val detail = rejected.joinToString { update ->
                "${update.remoteName}: ${update.status} ${update.message.orEmpty()}".trim()
            }
            throw GitOperationFailure("Push was rejected: $detail", "PUSH_REJECTED")
        }

        Unit
    }

    override suspend fun checkout(branch: String): GitOperationResult<Unit> {
        if (branch.isBlank()) {
            return GitOperationResult.Failure(
                message = "Branch name cannot be empty",
                code = "EMPTY_BRANCH_NAME"
            )
        }

        return execute { git ->
            git.checkout()
                .setName(branch.trim())
                .call()
            Unit
        }
    }

    private suspend fun <T> execute(
        action: (Git) -> T
    ): GitOperationResult<T> = mutex.withLock {
        withContext(ioDispatcher) {
            val currentRepository = repository
                ?: return@withContext GitOperationResult.Failure(
                    message = "No Git repository is currently open",
                    code = "REPOSITORY_NOT_OPEN"
                )

            try {
                // Do not close this wrapper: it shares the managed Repository.
                action(Git.wrap(currentRepository)).let(GitOperationResult::Success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: GitOperationFailure) {
                GitOperationResult.Failure(
                    message = failure.message ?: "Git operation failed",
                    cause = failure,
                    code = failure.code
                )
            } catch (failure: GitAPIException) {
                GitOperationResult.Failure(
                    message = failure.message ?: "Git operation failed",
                    cause = failure,
                    code = "GIT_API_ERROR"
                )
            } catch (failure: IOException) {
                GitOperationResult.Failure(
                    message = failure.message ?: "Git I/O operation failed",
                    cause = failure,
                    code = "GIT_IO_ERROR"
                )
            } catch (failure: Exception) {
                GitOperationResult.Failure(
                    message = failure.message ?: "Unexpected Git operation failure",
                    cause = failure,
                    code = "GIT_OPERATION_ERROR"
                )
            }
        }
    }

    private class GitOperationFailure(
        message: String,
        val code: String
    ) : RuntimeException(message)
}
