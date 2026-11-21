package com.ide.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WorkspaceEditorAndHostTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reload clean file updates content from disk`() = runBlocking {
        val file = tempDir.resolve("Main.kt").toFile().apply { writeText("v1") }
        val controller = WorkspaceEditorController(tempDir.toString())
        val host = IdeGitHostImpl(controller)

        controller.openOrFocus("Main.kt")
        file.writeText("v2_from_git")

        val reloaded = host.requestFileReload("Main.kt")
        assertTrue(reloaded)
        assertEquals("v2_from_git", controller.getOpenFile("Main.kt")?.content)
        assertFalse(controller.isDirty("Main.kt"))
    }

    @Test
    fun `dirty file is not overwritten when user declines`() = runBlocking {
        val file = tempDir.resolve("Main.kt").toFile().apply { writeText("initial") }
        val controller = WorkspaceEditorController(tempDir.toString())
        val host = IdeGitHostImpl(
            editorController = controller,
            onConfirmOverwriteDirty = { false }
        )

        controller.openOrFocus("Main.kt")
        controller.editContent("Main.kt", "local_unsaved_edit")
        file.writeText("remote_checkout_content")

        val reloaded = host.requestFileReload("Main.kt")
        assertFalse(reloaded)
        assertEquals("local_unsaved_edit", controller.getOpenFile("Main.kt")?.content)
        assertTrue(controller.isDirty("Main.kt"))
    }

    @Test
    fun `dirty file is force reloaded and clears dirty flag when user confirms`() = runBlocking {
        val file = tempDir.resolve("Main.kt").toFile().apply { writeText("initial") }
        val controller = WorkspaceEditorController(tempDir.toString())
        val host = IdeGitHostImpl(
            editorController = controller,
            onConfirmOverwriteDirty = { true }
        )

        controller.openOrFocus("Main.kt")
        controller.editContent("Main.kt", "local_unsaved_edit")
        file.writeText("remote_checkout_content")

        val reloaded = host.requestFileReload("Main.kt")
        assertTrue(reloaded)
        assertEquals("remote_checkout_content", controller.getOpenFile("Main.kt")?.content)
        assertFalse(controller.isDirty("Main.kt"))
    }
}
