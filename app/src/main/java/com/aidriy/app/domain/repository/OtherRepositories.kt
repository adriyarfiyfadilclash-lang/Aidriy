package com.aidriy.app.domain.repository

import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.RecentItems
import com.aidriy.app.domain.model.SearchResults
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.model.TaggableKind
import kotlinx.coroutines.flow.Flow

/** Tags are assigned through record drafts; this repository reads and maintains them. */
interface TagRepository {
    fun observeAll(): Flow<List<Tag>>
    /** Tags currently attached to at least one record of [kind]; used for filter rows. */
    fun observeUsedBy(kind: TaggableKind): Flow<List<Tag>>
    suspend fun deleteUnused(): DataResult<Int>
}

/** Offline search across notes (including archived), ideas and journal entries. */
interface SearchRepository {
    fun search(query: String, limitPerType: Int = 20): Flow<SearchResults>
}

/** Latest records for Beranda. */
fun interface RecentItemsSource {
    fun observe(): Flow<RecentItems>
}
