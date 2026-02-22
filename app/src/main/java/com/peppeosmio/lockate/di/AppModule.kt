package com.peppeosmio.lockate.di

import android.util.Log
import androidx.room.Room
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.LocationServices
import com.peppeosmio.lockate.AppDatabase
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.srp.SrpClientService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    // Singleton AppDatabase
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(), AppDatabase::class.java, "lockate.db"
        ).addMigrations().build()
    }

    // DAO from the database
    single<AnonymousGroupDao> {
        get<AppDatabase>().anonymousGroupDao()
    }

    single<ConnectionDao> {
        get<AppDatabase>().connectionSettingsDao()
    }

    // Ktor HttpClient
    single<HttpClient> {
        HttpClient(OkHttp) {
            install(SSE)
            install(WebSockets)

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

    single<KeyStoreService> {
        KeyStoreService()
    }

    single<ConnectionService> {
        ConnectionService(
            context = androidContext(),
            httpClient = get(),
            connectionDao = get(),
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
            fusedOrientationProviderClient = LocationServices.getFusedOrientationProviderClient(
                androidContext()
            ),
            permissionsService = get<PermissionsService>()
        )
    }

    single<AnonymousGroupService> {
        AnonymousGroupService(
            anonymousGroupDao = get(),
            cryptoService = get(),
            connectionService = get(),
            httpClient = get(),
            srpClientService = get(),
            locationService = get(),
            keyStoreService = get()
        )
    }
}
