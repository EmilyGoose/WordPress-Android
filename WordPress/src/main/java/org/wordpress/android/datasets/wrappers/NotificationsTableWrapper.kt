package org.wordpress.android.datasets.wrappers

import dagger.Reusable
import org.wordpress.android.datasets.NotificationsTable
import org.wordpress.android.models.Note
import javax.inject.Inject

@Reusable
class NotificationsTableWrapper @Inject constructor() {
    fun getNoteById(noteId: String): Note? =
            NotificationsTable.getNoteById(noteId)

    fun updateNote(note: Note): Boolean =
            NotificationsTable.saveNote(note)
}
