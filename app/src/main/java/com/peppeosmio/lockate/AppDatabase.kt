package com.peppeosmio.lockate

import androidx.room.Database
import androidx.room.RoomDatabase
import com.peppeosmio.lockate.dao.AnonymousGroupDao
import com.peppeosmio.lockate.dao.ConnectionSettingsDao
import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionSettingsEntity

@Database(
    version = 3,
    entities = [ConnectionSettingsEntity::class, AnonymousGroupEntity::class, AGMemberEntity::class],
    exportSchema = true,
    autoMigrations = []
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun anonymousGroupDao(): AnonymousGroupDao
    abstract fun connectionSettingsDao(): ConnectionSettingsDao
}
