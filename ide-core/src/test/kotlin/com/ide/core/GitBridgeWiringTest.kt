package com.ide.core

import com.ide.git.bridge.DefaultGitBridge
import com.ide.git.bridge.GitBridge
import com.ide.git.bridge.GitEngineClient
import com.ide.git.bridge.GitFileStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class GitBridgeWiringTest {

    @Test
    fun `wire ide core and git bridge`() = runBlocking {
        val projectRootPath = "."

        // 1. Khởi tạo IDE Core
        val editorController = WorkspaceEditorController(projectRootPath)
        val ideGitHost = IdeGitHostImpl(editorController)

        // 2. Tạm dùng stub cho GitEngineClient nếu đội Git chưa implement xong
        val gitClient: GitEngineClient = object : GitEngineClient {
            override fun onProjectOpened(rootPath: String) = Unit
            override fun onProjectClosed() = Unit
            override fun onFileSaved(filePath: String) = Unit
            override fun onFileTreeChanged() = Unit
            override suspend fun getFileStatuses(): Map<String, GitFileStatus> = emptyMap()
        }

        // 3. Nối qua DefaultGitBridge
        // Khi có implementation thật từ :git-engine (như JGitGitEngine), truyền vào tham số `engine`
        val gitBridge: GitBridge = DefaultGitBridge(
            engine = TODO("Truyền instance GitEngine thật từ :git-engine khi ghép nối"),
            client = gitClient
        ).apply {
            attachHost(ideGitHost)
        }

        println("Attached host: ${gitBridge.client}")
    }
}

