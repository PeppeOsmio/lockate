package com.peppeosmio.lockate.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object MIGRATION_3_4 : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {

        connection.execSQL("ALTER TABLE connection_settings RENAME TO connection")

        // migrate anonymous_group to use the internalId
        connection.execSQL("ALTER TABLE anonymous_group RENAME TO old_anonymous_group")

        connection.execSQL(
            """
            CREATE TABLE anonymous_group (
                internalId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                joinedAt INTEGER NOT NULL,
                isMember INTEGER NOT NULL,
                existsRemote INTEGER NOT NULL,
                sendLocation INTEGER NOT NULL,
                connectionId INTEGER NOT NULL,
                memberName TEXT NOT NULL,
                memberId TEXT NOT NULL,
                memberTokenCipher BLOB NOT NULL,
                memberTokenIv BLOB NOT NULL,
                adminTokenCipher BLOB,
                adminTokenIv BLOB,
                keyCipher BLOB NOT NULL,
                keyIv BLOB NOT NULL,
                FOREIGN KEY(connectionId) REFERENCES connection(id) ON DELETE CASCADE
            )
        """.trimIndent()
        )

        connection.execSQL(
            """
            INSERT INTO anonymous_group (
                id, name, createdAt, joinedAt,
                isMember, existsRemote, sendLocation,
                connectionId,
                memberName, memberId, memberTokenCipher, memberTokenIv,
                adminTokenCipher, adminTokenIv,
                keyCipher, keyIv
            )
            SELECT 
                id, name, createdAt, joinedAt,
                isMember, existsRemote, sendLocation,
                connectionSettingsId,
                memberName, memberId, memberTokenCipher, memberTokenIv,
                adminTokenCipher, adminTokenIv,
                keyCipher, keyIv
            FROM old_anonymous_group
        """.trimIndent()
        )

        connection.execSQL("DROP TABLE old_anonymous_group")

        connection.execSQL("CREATE INDEX idx_anonymous_group_connectionId ON anonymous_group(connectionId)")

        // migrate anonymous_group_member to use the internalId and rename to ag_member
        connection.execSQL("ALTER TABLE anonymous_group_member RENAME TO old_anonymous_group_member")

        connection.execSQL(
            """
            CREATE TABLE ag_member (
                internalId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                lastLatitude REAL,
                lastLongitude REAL,
                lastSeen INTEGER,
                anonymousGroupInternalId INTEGER NOT NULL,
                FOREIGN KEY(anonymousGroupInternalId) REFERENCES anonymous_group(internalId)
                ON DELETE CASCADE
            )
        """.trimIndent()
        )

        connection.execSQL(
            """
            INSERT INTO ag_member (
                id, name, createdAt, lastLatitude, lastLongitude, lastSeen, anonymousGroupInternalId
            )
            SELECT 
                old_anonymous_group_member.id,
                old_anonymous_group_member.name,
                old_anonymous_group_member.createdAt,
                old_anonymous_group_member.lastLatitude,
                old_anonymous_group_member.lastLongitude,
                old_anonymous_group_member.lastSeen,
                anonymous_group.internalId
            FROM old_anonymous_group_member
            JOIN anonymous_group 
            ON anonymous_group.id = old_anonymous_group_member.anonymousGroupId
            """.trimIndent()
        )

        connection.execSQL("DROP TABLE old_anonymous_group_member")

        connection.execSQL(
            """
            CREATE INDEX idx_anonymous_group_member_anonymousGroupInternalId
            ON ag_member(anonymousGroupInternalId)
            """.trimIndent()
        )
    }
}