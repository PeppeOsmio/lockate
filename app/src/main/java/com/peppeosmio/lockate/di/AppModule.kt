package com.peppeosmio.lockate.di

import android.util.Log
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import com.peppeosmio.lockate.AppDatabase
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionSettingsDao
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.location.LocationService
import com.peppeosmio.lockate.service.srp.SrpClientService
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    // Singleton AppDatabase
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(), AppDatabase::class.java, "lockate.db"
        ).build()
    }

    // DAO from the database
    single<AnonymousGroupDao> {
        get<AppDatabase>().anonymousGroupDao()
    }

    single<ConnectionSettingsDao> {
        get<AppDatabase>().connectionSettingsDao()
    }

    // Ktor HttpClient
    single<HttpClient> {
        HttpClient(Android) {
            install(SSE)

            install(ContentNegotiation) {
                json()
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("HttpLogging", message)
                    }
                }
            }
        }
    }

    single<ConnectionSettingsService> {
        ConnectionSettingsService(
            context = androidContext(),
            httpClient = get<HttpClient>(),
            connectionSettingsDao = get<ConnectionSettingsDao>()
        )
    }

    single<CryptoService> {
        CryptoService()
    }

    single<SrpClientService> {
        SrpClientService()
    }

    single<PermissionsService> {
        PermissionsService(context = androidContext())
    }

    single<LocationService> {
        LocationService(
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(
                androidContext()
            ), permissionsService = get<PermissionsService>()
        )
    }

    single<AnonymousGroupService> {
        AnonymousGroupService(
            anonymousGroupDao = get<AnonymousGroupDao>(),
            cryptoService = get<CryptoService>(),
            connectionSettingsService = get<ConnectionSettingsService>(),
            httpClient = get<HttpClient>(),
            srpClientService = get<SrpClientService>(),
            locationService = get<LocationService>()
        )
    }


}
