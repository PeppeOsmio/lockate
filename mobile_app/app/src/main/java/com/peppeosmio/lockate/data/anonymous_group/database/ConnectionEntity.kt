package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A server. Its normalized address is unique, so the same server is never saved twice. The API key
 * is encrypted with the Keystore, like the group keys.
 */
@Entity(
    tableName = "connection",
    indices = [Index(value = ["url"], unique = true, name = "idx_connection_url")],
)
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(defaultValue = "") val name: String,
    val url: String,
    val apiKeyCipher: ByteArray?,
    val apiKeyIv: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectionEntity) return false
        return id == other.id &&
            name == other.name &&
            url == other.url &&
            apiKeyCipher.contentEquals(other.apiKeyCipher) &&
            apiKeyIv.contentEquals(other.apiKeyIv)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + (apiKeyCipher?.contentHashCode() ?: 0)
        result = 31 * result + (apiKeyIv?.contentHashCode() ?: 0)
        return result
    }
}
