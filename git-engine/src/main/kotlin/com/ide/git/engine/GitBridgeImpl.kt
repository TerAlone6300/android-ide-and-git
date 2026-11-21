package com.ide.git.engine

import com.ide.git.bridge.GitBridge
import com.ide.git.bridge.GitEngine
import com.ide.git.bridge.GitEngineClient
import com.ide.git.bridge.GitFileStatus
import com.ide.git.bridge.GitOperationResult
import com.ide.git.bridge.IdeGitHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Owns one repository session, one status client and the host-aware engine.
 * Git actions automatically publish status; pull/checkout also request editor
 * reloads for paths changed between the previous and resulting HEAD.
 */
class GitBridgeImpl(
    private val jgitEngine: JGitGitEngine = JGitGitEngine(),
    override val client: JGitGitEngineClient = JGitGitEngineClient()
) : GitBridge {

    private val lifecycleMutex = Mutex()
    private val operationMutex = Mutex()

    @Volatile
    private var host: IdeGitHost? = null

    @Volatile
    private var projectRoot: File? = null

    override val engine: GitEngine = HostAwareGitEngine(
        delegate = jgitEngine,
        projectRoot = { projectRoot },
        host = { host },
        operationMutex = operationMutex,
        refreshStatuses = { refreshStatuses() }
    )

    override fun attachHost(host: IdeGitHost) {
        this.host = host
    }

    override fun detachHost() {
        host = null
    }

    suspend fun openProject(rootPath: String): GitOperationResult<Unit> =
        lifecycleMutex.withLock {
            operationMutex.withLock {
                val result = jgitEngine.openRepository(rootPath)
                if (result is GitOperationResult.Success) {
                    projectRoot = File(rootPath).canonicalFile
                    client.onProjectOpened(rootPath)
                    refreshStatuses()
                } else if (result is GitOperationResult.Failure) {
                    host?.showGitError("Open repository failed", result.message)
                }
                result
            }
        }

    suspend fun closeProject() {
        lifecycleMutex.withLock {
            operationMutex.withLock {
                projectRoot = null
                client.onProjectClosed()
                jgitEngine.closeRepository()
                host?.updateGitStatuses(emptyMap())
            }
        }
    }

    /** Reads status on IO and publishes it to the attached IDE host. */
    suspend fun refreshStatuses(): Map<String, GitFileStatus> {
        return try {
            val statuses = client.getFileStatuses()
            host?.updateGitStatuses(statuses)
            statuses
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            host?.showGitError(
                "Git status failed",
                failure.message ?: "Unable to read repository status"
            )
            emptyMap()
        }
    }

    /** Call from a coroutine after the IDE saves a file. */
    fun notifyFileSaved(relativePath: String) {
        client.onFileSaved(relativePath)
    }

    fun notifyFileTreeChanged() {
        client.onFileTreeChanged()
    }
}
