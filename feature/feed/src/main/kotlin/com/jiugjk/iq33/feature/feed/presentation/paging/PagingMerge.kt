package com.jiugjk.iq33.feature.feed.presentation.paging

internal fun <T> mergeUniqueById(
    existing: List<T>,
    incoming: List<T>,
    idOf: (T) -> Long,
): Pair<List<T>, Boolean> {
    val existingIds = existing.map(idOf).toSet()
    val actuallyNew = incoming.filterNot { idOf(it) in existingIds }

    return (existing + actuallyNew) to actuallyNew.isNotEmpty()
}
