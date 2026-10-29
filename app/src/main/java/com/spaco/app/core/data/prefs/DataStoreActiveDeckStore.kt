package com.spaco.app.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.spaco.app.core.domain.repository.ActiveDeckStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreActiveDeckStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActiveDeckStore {

    override val activeDeckId: Flow<String?> =
        context.settingsDataStore.data.map { preferences -> preferences[ACTIVE_DECK_ID] }

    override suspend fun setActiveDeck(deckId: String) {
        context.settingsDataStore.edit { preferences -> preferences[ACTIVE_DECK_ID] = deckId }
    }

    private companion object {
        val ACTIVE_DECK_ID = stringPreferencesKey("active_deck_id")
    }
}
