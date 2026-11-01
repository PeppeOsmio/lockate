package com.peppeosmio.lockate

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.data.anonymous_group.database.AGCircleEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.database.MutedTargetEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity

@Database(
    version = 5,
    entities = [
        ConnectionEntity::class,
        AnonymousGroupEntity::class,
        AGMemberEntity::class,
        AGCircleEntity::class,
        MutedTargetEntity::class
    ],
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun anonymousGroupDao(): AnonymousGroupDao
    abstract fun connectionSettingsDao(): ConnectionDao

    companion object {
        // Groups used to be protected by a password and the servers dropped them all when invites
        // replaced it, so the local copies can't be used anymore
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("DELETE FROM ag_member")
                connection.execSQL("DELETE FROM anonymous_group")
            }
        }
    }
}
