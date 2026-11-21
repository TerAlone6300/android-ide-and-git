package com.ide.git.engine

import com.ide.git.bridge.GitEngineClient
import com.ide.git.bridge.GitFileStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File

/**
 * JGit implementation of the IDE -> Git status/query contract.
 *
 * Paths returned by JGit are relative to the repository work tree and use
 * Git's '/' separator, which is also the bridge's canonical path format.
 */
class JGitGitEngineClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GitEngineClient {

    @Volatile
    private var repositoryRoot: File? = null

    override fun onProjectOpened(rootPath: String) {
        repositoryRoot = File(rootPath).canonicalFile
    }

    override fun onProjectClosed() {
        repositoryRoot = null
    }

    override fun onFileSaved(filePath: String) {
        // Status is intentionally queried on demand.
    }

    override fun onFileTreeChanged() {
        // Status is intentionally queried on demand.
    }

    override suspend fun getFileStatuses(): Map<String, GitFileStatus> =
        withContext(ioDispatcher) {
            val root = repositoryRoot
                ?: return@withContext emptyMap()

            Git.open(root).use { git ->
                val status = git.status().call()
                val files = linkedMapOf<String, GitFileStatus>()

                fun add(
                    paths: Set<String>,
                    index: Char = ' ',
                    workTree: Char = ' '
                ) {
                    for (path in paths) {
                        val previous = files[path]
                        val indexStatus = if (index != ' ') {
                            index
                        } else {
                            previous?.indexStatus ?: ' '
                        }
                        val workTreeStatus = if (workTree != ' ') {
                            workTree
                        } else {
                            previous?.workTreeStatus ?: ' '
                        }
                        val conflict = indexStatus == 'U' || workTreeStatus == 'U'

                        files[path] = GitFileStatus(
                            path = path,
                            indexStatus = indexStatus,
                            workTreeStatus = workTreeStatus,
                            isConflict = conflict
                        )
                    }
                }

                add(status.added, index = 'A')
                add(status.changed, index = 'M')
                add(status.removed, index = 'D')
                add(status.missing, workTree = 'D')
                add(status.modified, workTree = 'M')
                add(status.conflicting, index = 'U', workTree = 'U')
                add(status.untracked, index = '?', workTree = '?')
                add(status.ignoredNotInIndex, index = '!', workTree = '!')

                files
            }
        }
}
