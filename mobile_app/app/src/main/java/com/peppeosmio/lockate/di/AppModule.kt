package com.peppeosmio.lockate.di

import android.util.Log
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import com.peppeosmio.lockate.AppDatabase
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.data.apiJson
import com.peppeosmio.lockate.platform_service.ActivityService
import com.peppeosmio.lockate.platform_service.ConnectivityService
import com.peppeosmio.lockate.platform_service.DeviceOrientationService
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.PlaceSearchService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.invite.InviteService
import com.peppeosmio.lockate.service.location.PositionProvider
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val WEB_SOCKET_CLIENT = "webSocket"

private val httpLogger =
    object : Logger {
        override fun log(message: String) {
            Log.d("HttpLogging", message)
        }
    }

val appModule = module {

    // Singleton AppDatabase
    single<AppDatabase> {
        Room.databaseBuilder(
                androidContext(),
                AppDatabase::class.java,
                "lockate.db",
            )
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
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
            engine {
                config {
                    // the SSE heartbeats come every 15 s
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            install(SSE)

            install(ContentNegotiation) {
                json(apiJson)
            }

            install(Logging) { logger = httpLogger }
        }
    }

    // The group sockets ping on their own, aligned to the minute, so no OkHttp ping here: its
    // timer would wake the radio at other times. The read timeout only backs up the pong timeout.
    single<HttpClient>(named(WEB_SOCKET_CLIENT)) {
        HttpClient(OkHttp) {
            engine { config { readTimeout(90, java.util.concurrent.TimeUnit.SECONDS) } }
            install(WebSockets)
            install(Logging) { logger = httpLogger }
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
            keyStoreService = get(),
        )
    }

    single<MapStyleService> {
        MapStyleService(context = androidContext(), httpClient = get())
    }

    single<PlaceSearchService> {
        PlaceSearchService(context = androidContext(), httpClient = get())
    }

    single<CryptoService> {
        CryptoService()
    }

    single<PayloadCodec> {
        PayloadCodec(cryptoService = get())
    }

    single<PermissionsService> {
        PermissionsService(context = androidContext())
    }

    single<SendLoopLog> {
        SendLoopLog(File(androidContext().getExternalFilesDir(null), "send-loop.log"))
    }

    single<ConnectivityService> {
        ConnectivityService(context = androidContext())
    }

    single<LocationService> {
        LocationService(
            context = androidContext(),
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(androidContext()),
            permissionsService = get<PermissionsService>(),
        )
    }

    single<ActivityService> {
        ActivityService(
            context = androidContext(),
            permissionsService = get(),
            sendLoopLog = get(),
        )
    }

    single<PositionProvider> {
        val locationService = get<LocationService>()
        val activityService = get<ActivityService>()
        PositionProvider(
            gpsFixes = locationService::getLocationUpdates,
            passiveFixes = locationService::getPassiveLocationUpdates,
            activities = activityService::getActivityUpdates,
        )
    }

    single<DeviceOrientationService> {
        DeviceOrientationService(
            context = androidContext(),
            fusedOrientationProviderClient =
                LocationServices.getFusedOrientationProviderClient(androidContext()),
        )
    }

    single<AnonymousGroupService> {
        AnonymousGroupService(
            anonymousGroupDao = get(),
            cryptoService = get(),
            connectionService = get(),
            httpClient = get(),
            webSocketClient = get(named(WEB_SOCKET_CLIENT)),
            locationService = get(),
            positionProvider = get(),
            connectivityService = get(),
            keyStoreService = get(),
            payloadCodec = get(),
            sendLoopLog = get(),
        )
    }

    single<InviteService> {
        InviteService(anonymousGroupService = get(), connectionService = get(), httpClient = get())
    }
}
