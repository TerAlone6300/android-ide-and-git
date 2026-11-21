package com.ide.core

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class OpenEditorFile(
    val relativePath: String,
    val content: String,
    val isDirty: Boolean,
    val cursorLine: Int = 1
)

class WorkspaceEditorController(
    private val projectRoot: String
) {
    private val rootPath: Path = Path(projectRoot).toAbsolutePath().normalize()
    private val openFiles = linkedMapOf<String, OpenEditorFile>()

    fun toRelativePath(pathOrRelative: String): String {
        val candidate = Path(pathOrRelative)
        val normalized = if (candidate.isAbsolute) {
            rootPath.relativize(candidate.toAbsolutePath().normalize())
        } else {
            candidate.normalize()
        }
        return normalized.toString().replace('\\', '/')
    }

    fun resolveFile(relativePath: String): Path {
        return rootPath.resolve(toRelativePath(relativePath)).normalize()
    }

    fun openOrFocus(relativePath: String, line: Int = 1): OpenEditorFile? {
        val rel = toRelativePath(relativePath)
        val target = resolveFile(rel)
        if (!target.exists()) return null

        val existing = openFiles[rel]
        val updated = if (existing != null) {
            existing.copy(cursorLine = line.coerceAtLeast(1))
        } else {
            OpenEditorFile(
                relativePath = rel,
                content = target.readText(),
                isDirty = false,
                cursorLine = line.coerceAtLeast(1)
            )
        }
        openFiles[rel] = updated
        return updated
    }

    fun editContent(relativePath: String, newContent: String) {
        val rel = toRelativePath(relativePath)
        val current = openFiles[rel] ?: return
        openFiles[rel] = current.copy(content = newContent, isDirty = true)
    }

    fun saveFile(relativePath: String): Boolean {
        val rel = toRelativePath(relativePath)
        val current = openFiles[rel] ?: return false
        val target = resolveFile(rel)
        target.writeText(current.content)
        openFiles[rel] = current.copy(isDirty = false)
        return true
    }

    /**
     * Chỉ reload từ đĩa nếu file không có thay đổi chưa lưu.
     * Trả về true nếu đã reload, false nếu từ chối do đang dirty.
     */
    fun reloadFromDiskIfClean(relativePath: String): Boolean {
        val rel = toRelativePath(relativePath)
        val current = openFiles[rel] ?: return false
        if (current.isDirty) return false
        val target = resolveFile(rel)
        if (!target.exists()) return false

        openFiles[rel] = current.copy(
            content = target.readText(),
            isDirty = false
        )
        return true
    }

    fun isDirty(relativePath: String): Boolean {
        return openFiles[toRelativePath(relativePath)]?.isDirty == true
    }
}
