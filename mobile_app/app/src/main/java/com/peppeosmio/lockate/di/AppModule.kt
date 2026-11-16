package com.peppeosmio.lockate.di

import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import com.peppeosmio.lockate.AppDatabase
import com.peppeosmio.lockate.android_service.CircleNotifier
import com.peppeosmio.lockate.android_service.HealthNotifier
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.data.apiJson
import com.peppeosmio.lockate.platform_service.ActivityService
import com.peppeosmio.lockate.platform_service.ConnectivityService
import com.peppeosmio.lockate.platform_service.DeviceOrientationService
import com.peppeosmio.lockate.platform_service.FirebasePushTokenSource
import com.peppeosmio.lockate.platform_service.KeyStoreService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.platform_service.PushTokenSource
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.PlaceSearchService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec
import com.peppeosmio.lockate.service.crypto.CryptoService
import com.peppeosmio.lockate.service.dataStore
import com.peppeosmio.lockate.service.health.HealthCheck
import com.peppeosmio.lockate.service.invite.InviteService
import com.peppeosmio.lockate.service.location.PositionProvider
import com.peppeosmio.lockate.service.location.STILL_FIX_INTERVAL_MILLIS
import com.peppeosmio.lockate.service.push.PushRegistrationService
import com.peppeosmio.lockate.service.version.apiCompatibility
import com.peppeosmio.lockate.utils.SendLoopLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

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
            stillFixes = { locationService.getStillLocationUpdates(STILL_FIX_INTERVAL_MILLIS) },
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
            locationService = get(),
            positionProvider = get(),
            connectivityService = get(),
            keyStoreService = get(),
            payloadCodec = get(),
            sendLoopLog = get(),
        )
    }

    single<PushTokenSource> { FirebasePushTokenSource() }

    single<CircleNotifier> { CircleNotifier(androidContext()) }

    single<HealthNotifier> { HealthNotifier(androidContext()) }

    single<HealthCheck> {
        val anonymousGroupDao = get<AnonymousGroupDao>()
        val anonymousGroupService = get<AnonymousGroupService>()
        val connectionService = get<ConnectionService>()
        val healthNotifier = get<HealthNotifier>()
        val dataStore = androidContext().dataStore
        HealthCheck(
            listGroups = {
                anonymousGroupDao.listMemberAGs().map {
                    HealthCheck.Group(it.connectionId, it.internalId)
                }
            },
            compatibility = { connectionId ->
                try {
                    val connection = connectionService.getConnectionSettingsById(connectionId)
                    apiCompatibility(
                        connectionService.getServerVersion(connection.url, connection.apiKey)?.apis
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            },
            verify = { group ->
                // marks the group itself when we were removed or it was deleted
                try {
                    anonymousGroupService.verifyMemberAuth(
                        group.connectionId,
                        group.anonymousGroupInternalId,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("HealthCheck", "Can't verify group ${group.anonymousGroupInternalId}", e)
                }
            },
            registerPushes = { get<PushRegistrationService>().registerAll() },
            state =
                object : HealthCheck.State {
                    private fun incompatibleKey(connectionId: Long) =
                        booleanPreferencesKey("health_incompatible_$connectionId")

                    override suspend fun incompatibleNotified(connectionId: Long) =
                        dataStore.data.first()[incompatibleKey(connectionId)] ?: false

                    override suspend fun setIncompatibleNotified(
                        connectionId: Long,
                        notified: Boolean,
                    ) {
                        dataStore.edit { it[incompatibleKey(connectionId)] = notified }
                    }
                },
            notifyIncompatible = { connectionId, compatibility ->
                healthNotifier.showIncompatible(
                    connectionId,
                    connectionService.getConnectionSettingsById(connectionId).name,
                    compatibility,
                )
            },
            clearIncompatible = { healthNotifier.clearIncompatible(it) },
        )
    }

    single<PushRegistrationService> {
        PushRegistrationService(
            anonymousGroupDao = get(),
            connectionService = get(),
            keyStoreService = get(),
            httpClient = get(),
            pushTokenSource = get(),
            sendLoopLog = get(),
        )
    }

    single<InviteService> {
        InviteService(anonymousGroupService = get(), connectionService = get(), httpClient = get())
    }
}
