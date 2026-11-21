package com.ide.git.bridge

/**
 * Các callback/dịch vụ mà Git Module gọi ngược lại để IDE Core xử lý trên UI/Editor.
 */
interface IdeGitHost {
    /**
     * Yêu cầu Editor tải lại nội dung file từ đĩa (vd: sau checkout/pull/reset).
     * Trả về true nếu IDE đã reload thành công, false nếu từ chối (vd: có thay đổi chưa lưu).
     */
    suspend fun requestFileReload(filePath: String): Boolean

    fun openFileAt(filePath: String, line: Int)

    fun openDiff(
        filePath: String,
        oldContent: String?,
        newContent: String?
    )

    fun showGitError(
        title: String,
        message: String
    )

    fun updateGitStatuses(
        statuses: Map<String, GitFileStatus>
    )
}
