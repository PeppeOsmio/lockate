package com.peppeosmio.lockate

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AGPlaceEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity
import com.peppeosmio.lockate.data.anonymous_group.database.MutedTargetEntity
import com.peppeosmio.lockate.data.anonymous_group.database.PushRegistrationEntity
import com.peppeosmio.lockate.data.anonymous_group.database.StoredServer
import com.peppeosmio.lockate.data.anonymous_group.database.mergeServers
import com.peppeosmio.lockate.platform_service.KeyStoreService

@Database(
    version = 9,
    entities =
        [
            ConnectionEntity::class,
            AnonymousGroupEntity::class,
            AGMemberEntity::class,
            AGPlaceEntity::class,
            MutedTargetEntity::class,
            PushRegistrationEntity::class,
        ],
    exportSchema = true,
    autoMigrations =
        [
            AutoMigration(from = 1, to = 2),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
            AutoMigration(from = 5, to = 6, spec = AppDatabase.DropPlaceSelfInside::class),
            AutoMigration(from = 6, to = 7),
            AutoMigration(from = 8, to = 9, spec = AppDatabase.RenameCirclesToPlaces::class),
        ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun anonymousGroupDao(): AnonymousGroupDao

    abstract fun connectionSettingsDao(): ConnectionDao

    // the other members' arrivals are worked out from their positions now, never our own
    @DeleteColumn(tableName = "ag_circle", columnName = "selfInside")
    class DropPlaceSelfInside : AutoMigrationSpec

    @RenameTable(fromTableName = "ag_circle", toTableName = "ag_place")
    class RenameCirclesToPlaces : AutoMigrationSpec

    companion object {
        // Groups used to be protected by a password and the servers dropped them all when invites
        // replaced it, so the local copies can't be used anymore
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DELETE FROM ag_member")
                    connection.execSQL("DELETE FROM anonymous_group")
                }
            }
    }

    /**
     * One server per address, with its API key encrypted: servers saved twice with the same address
     * are merged into the oldest, which takes their groups. The table is rebuilt, which is safe
     * because Room never turns foreign keys on, so dropping it cascades to nothing.
     */
    class Migration7To8(private val keyStoreService: KeyStoreService) : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            val stored =
                connection.prepare("SELECT id, name, url, apiKey FROM connection").use { stmt ->
                    buildList {
                        while (stmt.step()) {
                            add(
                                StoredServer(
                                    id = stmt.getLong(0),
                                    name = stmt.getText(1),
                                    url = stmt.getText(2),
                                    apiKey = if (stmt.isNull(3)) null else stmt.getText(3),
                                )
                            )
                        }
                    }
                }
            val merge = mergeServers(stored)
            merge.movedTo.forEach { (from, to) ->
                connection
                    .prepare("UPDATE anonymous_group SET connectionId = ? WHERE connectionId = ?")
                    .use {
                        it.bindLong(1, to)
                        it.bindLong(2, from)
                        it.step()
                    }
            }
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_connection` (`id` INTEGER PRIMARY KEY " +
                    "AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `url` TEXT NOT " +
                    "NULL, `apiKeyCipher` BLOB, `apiKeyIv` BLOB)"
            )
            merge.kept.forEach { server ->
                val apiKey =
                    server.apiKey?.let { keyStoreService.encryptBlocking(it.toByteArray()) }
                connection
                    .prepare(
                        "INSERT INTO `_new_connection` (id, name, url, apiKeyCipher, apiKeyIv) " +
                            "VALUES (?, ?, ?, ?, ?)"
                    )
                    .use {
                        it.bindLong(1, server.id)
                        it.bindText(2, server.name)
                        it.bindText(3, server.url)
                        if (apiKey == null) {
                            it.bindNull(4)
                            it.bindNull(5)
                        } else {
                            it.bindBlob(4, apiKey.cipherText)
                            it.bindBlob(5, apiKey.iv)
                        }
                        it.step()
                    }
            }
            connection.execSQL("DROP TABLE `connection`")
            connection.execSQL("ALTER TABLE `_new_connection` RENAME TO `connection`")
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `idx_connection_url` ON `connection` (`url`)"
            )
        }
    }
}
