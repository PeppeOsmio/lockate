package com.peppeosmio.lockate

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionDao
import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity

@Database(
    version = 2,
    entities = [ConnectionEntity::class, AnonymousGroupEntity::class, AGMemberEntity::class],
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun anonymousGroupDao(): AnonymousGroupDao
    abstract fun connectionSettingsDao(): ConnectionDao
}
