package com.peppeosmio.lockate.di

import android.util.Log
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import com.peppeosmio.lockate.AppDatabase
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionSettingsDao
import com.peppeosmio.lockate.migrations.MIGRATION_1_2
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.platform_service.LocationService
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
        ).addMigrations(MIGRATION_1_2).build()
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

//            install(Logging) {
//                logger = object : Logger {
//                    override fun log(message: String) {
//                        Log.d("HttpLogging", message)
//                    }
//                }
//            }
        }
    }

    single<KeyStoreService> {
        KeyStoreService()
    }

    single<ConnectionSettingsService> {
        ConnectionSettingsService(
            context = androidContext(),
            httpClient = get(),
            connectionSettingsDao = get(),
            keyStoreService = get()
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
            context = androidContext(),
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(
                androidContext()
            ),
            permissionsService = get<PermissionsService>()
        )
    }

    single<AnonymousGroupService> {
        AnonymousGroupService(
            anonymousGroupDao = get(),
            cryptoService = get(),
            connectionSettingsService = get(),
            httpClient = get(),
            srpClientService = get(),
            locationService = get(),
            keyStoreService = get()
        )
    }
}
