package dev.mattramotar.storex.pager

/**
 * Aggregates the [LoadState] for all pagination operations: [refresh], [prepend], and [append].
 *
 * By examining this combined state, UI components can determine the overall loading/error state and
 * provide appropriate feedback (e.g., show a global loading spinner if any load is ongoing).
 *
 * @property refresh The load state for the refresh operation.
 * @property prepend The load state for loading data before the currently loaded set.
 * @property append The load state for loading data after the currently loaded set.
 */
data class LoadStates(
    val refresh: LoadState,
    val prepend: LoadState,
    val append: LoadState,
) {
    companion object {
        val initial: LoadStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            append = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = false)
        )
    }
}