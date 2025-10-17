package com.peppeosmio.lockate.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object MIGRATION_4_5 : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            DROP INDEX idx_anonymous_group_member_anonymousGroupInternalId
        """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX idx_ag_member_anonymousGroupInternalId_id
            ON ag_member(anonymousGroupInternalId, id)
        """.trimIndent()
        )
    }
}