package com.ide.git.bridge

/**
 * Các sự kiện và truy vấn mà IDE Core gọi sang phía Git Module.
 * Lưu ý: filePath trao đổi trạng thái/sự kiện nên chuẩn hóa tương đối so với projectRootPath.
 */
interface GitEngineClient {
    fun onProjectOpened(rootPath: String)
    fun onProjectClosed()

    fun onFileSaved(filePath: String)
    fun onFileTreeChanged()

    suspend fun getFileStatuses(): Map<String, GitFileStatus>
}
