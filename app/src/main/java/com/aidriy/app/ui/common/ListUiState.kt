package com.aidriy.app.ui.common

/** Explicit list states shared by Catatan, Ide and Journal. */
sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>

    /** [isFiltered] distinguishes "nothing stored yet" from "nothing matches the filter". */
    data class Empty(val isFiltered: Boolean) : ListUiState<Nothing>

    data class Success<out T>(val items: List<T>) : ListUiState<T>

    data class Error(val message: UiText) : ListUiState<Nothing>
}

fun <T> List<T>.toListState(isFiltered: Boolean): ListUiState<T> =
    if (isEmpty()) ListUiState.Empty(isFiltered) else ListUiState.Success(this)
