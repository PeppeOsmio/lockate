package com.peppeosmio.lockate.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lockate")

object ConfigSettings {
    val SELECTED_CONNECTION_SETTINGS_ID = longPreferencesKey("selected_connection_settings_id")
    val LAST_MAP_LOCATION_LATITUDE = doublePreferencesKey("last_map_location_latitude")
    val LAST_MAP_LOCATION_LONGITUDE = doublePreferencesKey("last_map_location_longitude")
}