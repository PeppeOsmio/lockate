package com.peppeosmio.lockate.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MIGRATION_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE anonymous_group_member ADD COLUMN lastLocationRecordId TEXT")
    }
}
