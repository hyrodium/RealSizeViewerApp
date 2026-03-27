package io.github.hyrodium.realsizeviewerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "calibration")

@Singleton
class CalibrationDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FACTOR_X = floatPreferencesKey("calibration_factor_x")
        val FACTOR_Y = floatPreferencesKey("calibration_factor_y")
        val SERVER_FACTOR_X = floatPreferencesKey("last_server_factor_x")
        val SERVER_FACTOR_Y = floatPreferencesKey("last_server_factor_y")
    }

    // 現在のキャリブレーション値（ユーザーが設定したもの）
    val calibrationFactorX: Flow<Float?> = context.dataStore.data.map { it[Keys.FACTOR_X] }
    val calibrationFactorY: Flow<Float?> = context.dataStore.data.map { it[Keys.FACTOR_Y] }

    // サーバーから最後に取得した推奨値
    val lastServerFactorX: Flow<Float?> = context.dataStore.data.map { it[Keys.SERVER_FACTOR_X] }
    val lastServerFactorY: Flow<Float?> = context.dataStore.data.map { it[Keys.SERVER_FACTOR_Y] }

    suspend fun saveCalibration(factorX: Float, factorY: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FACTOR_X] = factorX
            prefs[Keys.FACTOR_Y] = factorY
        }
    }

    suspend fun saveServerValues(factorX: Float, factorY: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_FACTOR_X] = factorX
            prefs[Keys.SERVER_FACTOR_Y] = factorY
        }
    }
}
