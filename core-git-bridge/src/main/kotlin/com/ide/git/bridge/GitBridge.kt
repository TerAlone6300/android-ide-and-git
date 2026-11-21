package com.ide.git.bridge

/**
 * Thao tác Git bất đồng bộ, độc lập với UI framework.
 */
interface GitEngine {
    suspend fun commit(message: String): GitOperationResult<String>
    suspend fun pull(): GitOperationResult<Unit>
    suspend fun push(): GitOperationResult<Unit>
    suspend fun checkout(branch: String): GitOperationResult<Unit>
}

/**
 * Điểm kết nối tổng hợp giữa IDE Core và Git Module.
 */
interface GitBridge {
    val engine: GitEngine
    val client: GitEngineClient
    fun attachHost(host: IdeGitHost)
    fun detachHost()
}
