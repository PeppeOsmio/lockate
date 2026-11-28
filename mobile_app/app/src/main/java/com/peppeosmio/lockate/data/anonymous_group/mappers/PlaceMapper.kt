package com.peppeosmio.lockate.data.anonymous_group.mappers

import com.peppeosmio.lockate.data.anonymous_group.database.AGPlaceEntity
import com.peppeosmio.lockate.data.anonymous_group.remote.PlaceDto
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.EncryptedPlace
import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec

object PlaceMapper {
    suspend fun dtoToDomain(
        dto: PlaceDto,
        payloadCodec: PayloadCodec,
        key: ByteArray,
        groupId: String,
    ): Place =
        payloadCodec.decryptPlace(
            place =
                EncryptedPlace(
                    coordinates = EncryptedDataMapper.toDomain(dto.encryptedCoordinates),
                    radius = EncryptedDataMapper.toDomain(dto.encryptedRadius),
                    name = EncryptedDataMapper.toDomain(dto.encryptedName),
                ),
            key = key,
            groupId = groupId,
            placeId = dto.id,
        )

    fun entityToDomain(entity: AGPlaceEntity) =
        Place(
            id = entity.id,
            name = entity.name,
            center = Coordinates(entity.latitude, entity.longitude),
            radiusMeters = entity.radiusMeters,
        )

    fun domainToEntity(place: Place, anonymousGroupInternalId: Long) =
        AGPlaceEntity(
            internalId = 0,
            id = place.id,
            name = place.name,
            latitude = place.center.latitude,
            longitude = place.center.longitude,
            radiusMeters = place.radiusMeters,
            anonymousGroupInternalId = anonymousGroupInternalId,
        )
}
