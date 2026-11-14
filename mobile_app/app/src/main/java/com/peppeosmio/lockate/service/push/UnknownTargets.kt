package com.peppeosmio.lockate.service.push

import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame

/**
 * Whether a pushed [frame] names a member, or for a circle event a circle, that the phone hasn't
 * saved: then it can't be described until the group's snapshot is fetched.
 */
fun namesUnknown(frame: AGChannelFrame, members: Set<String>, circles: Set<String>): Boolean =
    when (frame) {
        is AGChannelFrame.CircleEvent ->
            frame.memberId !in members || frame.transition.circleId !in circles
        is AGChannelFrame.MemberPresence -> frame.memberId !in members
        else -> false
    }
