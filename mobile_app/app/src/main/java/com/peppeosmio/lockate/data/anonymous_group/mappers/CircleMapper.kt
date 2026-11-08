package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AGCircleEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.CircleDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedCircle
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec

object CircleMapper {
    suspend fun dtoToDomain(
        dto: CircleDto,
        payloadCodec: PayloadCodec,
        key: ByteArray,
        groupId: String,
    ): Circle =
        payloadCodec.decryptCircle(
            circle =
                EncryptedCircle(
                    coordinates = EncryptedDataMapper.toDomain(dto.encryptedCoordinates),
                    radius = EncryptedDataMapper.toDomain(dto.encryptedRadius),
                    name = EncryptedDataMapper.toDomain(dto.encryptedName),
                ),
            key = key,
            groupId = groupId,
            circleId = dto.id,
        )

    fun entityToDomain(entity: AGCircleEntity) =
        Circle(
            id = entity.id,
            name = entity.name,
            center = Coordinates(entity.latitude, entity.longitude),
            radiusMeters = entity.radiusMeters,
        )

    fun domainToEntity(circle: Circle, anonymousGroupInternalId: Long) =
        AGCircleEntity(
            internalId = 0,
            id = circle.id,
            name = circle.name,
            latitude = circle.center.latitude,
            longitude = circle.center.longitude,
            radiusMeters = circle.radiusMeters,
            anonymousGroupInternalId = anonymousGroupInternalId,
        )
}
