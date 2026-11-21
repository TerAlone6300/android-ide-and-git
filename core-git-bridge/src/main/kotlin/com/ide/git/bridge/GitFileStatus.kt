package com.ide.git.bridge

enum class GitStatus {
    UNTRACKED,
    MODIFIED,
    ADDED,
    DELETED,
    RENAMED,
    CONFLICTED,
    IGNORED,
    CLEAN
}

data class GitFileStatus(
    val path: String,
    val indexStatus: Char = ' ',
    val workTreeStatus: Char = ' ',
    val isConflict: Boolean = false,
    val status: GitStatus = resolveStatus(indexStatus, workTreeStatus, isConflict)
) {
    companion object {
        fun resolveStatus(
            indexStatus: Char,
            workTreeStatus: Char,
            isConflict: Boolean
        ): GitStatus {
            if (isConflict || indexStatus == 'U' || workTreeStatus == 'U') {
                return GitStatus.CONFLICTED
            }
            if (indexStatus == '?' && workTreeStatus == '?') {
                return GitStatus.UNTRACKED
            }
            if (indexStatus == '!' && workTreeStatus == '!') {
                return GitStatus.IGNORED
            }
            if (indexStatus == 'R' || workTreeStatus == 'R') {
                return GitStatus.RENAMED
            }
            if (indexStatus == 'D' || workTreeStatus == 'D') {
                return GitStatus.DELETED
            }
            if (indexStatus == 'A') {
                return GitStatus.ADDED
            }
            if (indexStatus == 'M' || workTreeStatus == 'M') {
                return GitStatus.MODIFIED
            }
            return GitStatus.CLEAN
        }
    }
}
