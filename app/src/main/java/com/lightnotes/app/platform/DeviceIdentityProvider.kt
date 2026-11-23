package com.lightnotes.app.platform

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lightnotes.app.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

class DeviceIdentityProvider private constructor(private val context: Context) {

    private val KEY_DEVICE_ID = stringPreferencesKey("sync_device_id")

    @Volatile
    private var cachedDeviceId: String? = null

    suspend fun getOrGenerateDeviceId(): String {
        cachedDeviceId?.let { return it }

        val prefs = context.dataStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) {
            cachedDeviceId = existing
            return existing
        }

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_DEVICE_ID] = newId }
        cachedDeviceId = newId
        return newId
    }

    fun getDeviceIdSync(): String {
        cachedDeviceId?.let { return it }
        return runBlocking {
            getOrGenerateDeviceId()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DeviceIdentityProvider? = null

        fun getInstance(context: Context): DeviceIdentityProvider {
            return INSTANCE ?: synchronized(this) {
                val instance = DeviceIdentityProvider(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
