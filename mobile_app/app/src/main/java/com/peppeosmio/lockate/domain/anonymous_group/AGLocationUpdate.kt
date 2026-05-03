package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.LocationRecord

data class AGLocationUpdate(val locationRecord: LocationRecord, val agMemberId: String)