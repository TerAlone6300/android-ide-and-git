package com.ide.git.engine

import com.ide.git.bridge.GitBridge
import com.ide.git.bridge.GitEngine
import com.ide.git.bridge.GitEngineClient
import com.ide.git.bridge.GitFileStatus
import com.ide.git.bridge.GitOperationResult
import com.ide.git.bridge.IdeGitHost
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the single Git engine/client pair for one open project.
 *
 * Call openProject() when the IDE opens a workspace and closeProject() before
 * discarding the bridge. UI code can call refreshStatuses() after Git actions.
 */
class GitBridgeImpl(
    override val engine: JGitGitEngine = JGitGitEngine(),
    override val client: JGitGitEngineClient = JGitGitEngineClient()
) : GitBridge {

    private val lifecycleMutex = Mutex()

    @Volatile
    private var host: IdeGitHost? = null

    override fun attachHost(host: IdeGitHost) {
        this.host = host
    }

    override fun detachHost() {
        host = null
    }

    suspend fun openProject(rootPath: String): GitOperationResult<Unit> =
        lifecycleMutex.withLock {
            val result = engine.openRepository(rootPath)
            if (result is GitOperationResult.Success) {
                client.onProjectOpened(rootPath)
            }
            result
        }

    suspend fun closeProject() {
        lifecycleMutex.withLock {
            client.onProjectClosed()
            engine.closeRepository()
        }
    }

    /**
     * Reads current status and publishes it to the attached IDE host.
     */
    suspend fun refreshStatuses(): Map<String, GitFileStatus> {
        val statuses = client.getFileStatuses()
        host?.updateGitStatuses(statuses)
        return statuses
    }

    /**
     * Convenience hook for IDE events. This does not perform I/O; the caller
     * decides when to request refreshStatuses() from a coroutine.
     */
    fun notifyFileSaved(relativePath: String) {
        client.onFileSaved(relativePath)
    }

    fun notifyFileTreeChanged() {
        client.onFileTreeChanged()
    }
}
