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

    /** All paths crossing the Git bridge are normalized project-relative paths. */
    fun toRelativePath(pathOrRelative: String): String {
        val candidate = Path(pathOrRelative)
        val normalized = if (candidate.isAbsolute) {
            val absolute = candidate.toAbsolutePath().normalize()
            require(absolute.startsWith(rootPath)) {
                "Path is outside the project root: $pathOrRelative"
            }
            rootPath.relativize(absolute)
        } else {
            candidate.normalize()
        }
        val relative = normalized.toString().replace('\\', '/')
        require(relative != ".." && !relative.startsWith("../")) {
            "Path is outside the project root: $pathOrRelative"
        }
        return relative
    }

    fun resolveFile(relativePath: String): Path {
        val resolved = rootPath.resolve(toRelativePath(relativePath)).normalize()
        require(resolved.startsWith(rootPath)) {
            "Path is outside the project root: $relativePath"
        }
        return resolved
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

    /** Reload an open buffer only when it is clean; close it if Git deleted the file. */
    fun reloadFromDiskIfClean(relativePath: String): Boolean =
        reloadFromDisk(relativePath, allowDirty = false)

    /** Used only after the user explicitly confirms overwriting a dirty buffer. */
    fun forceReloadFromDisk(relativePath: String): Boolean =
        reloadFromDisk(relativePath, allowDirty = true)

    private fun reloadFromDisk(relativePath: String, allowDirty: Boolean): Boolean {
        val rel = toRelativePath(relativePath)
        val current = openFiles[rel] ?: return true
        if (current.isDirty && !allowDirty) return false

        val target = resolveFile(rel)
        if (!target.exists()) {
            openFiles.remove(rel)
            return true
        }

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
