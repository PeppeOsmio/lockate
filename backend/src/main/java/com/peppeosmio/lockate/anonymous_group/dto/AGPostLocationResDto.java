package com.peppeosmio.lockate.anonymous_group.dto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * The answer to a location post: the version of the group's places, which goes up whenever one is
 * created, edited or deleted, so a phone sharing in the background learns when to fetch them again.
 */
@RegisterReflectionForBinding
public record AGPostLocationResDto(long placesVersion) {}
