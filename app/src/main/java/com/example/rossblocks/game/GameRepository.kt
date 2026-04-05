package com.example.rossblocks.game

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "ross_blocks")

class GameRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val keyHighScore = intPreferencesKey("high_score")
    private val keySavedGame = stringPreferencesKey("saved_game")

    val highScoreFlow: Flow<Int> = context.gameDataStore.data.map { prefs ->
        prefs[keyHighScore] ?: 0
    }

    suspend fun getHighScore(): Int =
        context.gameDataStore.data.map { it[keyHighScore] ?: 0 }.first()

    suspend fun setHighScore(value: Int) {
        context.gameDataStore.edit { it[keyHighScore] = value }
    }

    suspend fun maybeUpdateHighScore(score: Int) {
        val current = getHighScore()
        if (score > current) setHighScore(score)
    }

    suspend fun hasSavedGame(): Boolean {
        val raw = context.gameDataStore.data.map { it[keySavedGame] }.first()
        return !raw.isNullOrBlank()
    }

    suspend fun loadSavedGame(): SavedGame? {
        val raw = context.gameDataStore.data.map { it[keySavedGame] }.first() ?: return null
        return runCatching { json.decodeFromString<SavedGame>(raw) }.getOrNull()
    }

    suspend fun saveGame(game: SavedGame) {
        val encoded = json.encodeToString(game)
        context.gameDataStore.edit { it[keySavedGame] = encoded }
    }

    suspend fun clearSavedGame() {
        context.gameDataStore.edit { it.remove(keySavedGame) }
    }
}
