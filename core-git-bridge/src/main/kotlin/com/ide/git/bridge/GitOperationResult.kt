package com.ide.git.bridge

sealed interface GitOperationResult<out T> {
    data class Success<T>(val data: T) : GitOperationResult<T>

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val code: String? = null
    ) : GitOperationResult<Nothing>

    data class Cancelled(
        val reason: String? = null
    ) : GitOperationResult<Nothing>
}
