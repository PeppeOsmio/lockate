package com.peppeosmio.lockate.anonymous_group.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.peppeosmio.lockate.anonymous_group.dto.AGGetMembersCountDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGGetMembersResDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGLocationSaveReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SecuredAGMember
@Slf4j
@RestController
@RequestMapping("/api/anonymous-groups")
@Validated
public class SecuredAGController {
    private final AnonymousGroupService anonymousGroupService;

    public SecuredAGController(AnonymousGroupService anonymousGroupService) {
        this.anonymousGroupService = anonymousGroupService;
    }

    @GetMapping("/{anonymousGroupId}/members/auth/verify")
    @ResponseStatus(HttpStatus.OK)
    void verifyMemberAuth(@PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication) {
    }

    @GetMapping("/{anonymousGroupId}/members")
    @ResponseStatus(HttpStatus.OK)
    AGGetMembersResDto getAGMembers(
            @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws AGNotFoundException, UnauthorizedException {
        return anonymousGroupService.getMembers(anonymousGroupId,
                (AGMemberAuthentication) authentication);
    }

    @PostMapping("/{anonymousGroupId}/members/leave")
    @ResponseStatus(HttpStatus.OK)
    void memberLeave(@PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws AGNotFoundException, UnauthorizedException {
        anonymousGroupService.deleteMember(anonymousGroupId,
                (AGMemberAuthentication) authentication);
    }

    @GetMapping("/{anonymousGroupId}/members/count")
    @ResponseStatus(HttpStatus.OK)
    AGGetMembersCountDto getMembersCount(
            @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws UnauthorizedException, AGNotFoundException {
        return anonymousGroupService.getMembersCount(anonymousGroupId,
                (AGMemberAuthentication) authentication);
    }

    @PostMapping("/{anonymousGroupId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    void saveAnonymousLocation(
            @PathVariable UUID anonymousGroupId,
            @RequestBody AGLocationSaveReqDto dto,
            AGMemberAuthentication authentication)
            throws UnauthorizedException, AGNotFoundException, JsonProcessingException {
        anonymousGroupService.saveLocation(anonymousGroupId,
                (AGMemberAuthentication) authentication, dto, null);
    }

    @DeleteMapping("/{anonymousGroupId}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteAnonymousGroup(
            @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws UnauthorizedException, AGNotFoundException, AGMemberNotAdminException {
        anonymousGroupService.deleteAnonymousGroup(anonymousGroupId,
                (AGMemberAuthentication) authentication);
    }

    @GetMapping("/{anonymousGroupId}/locations")
    @ResponseStatus(HttpStatus.OK)
    public SseEmitter streamLocations(
            @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication) {
        try {
            SseEmitter emitter = new SseEmitter(0L);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().comment("heartbeat"));
                        } catch (Exception e) {
                            scheduler.shutdown();
                        }
                    },
                    15,
                    15,
                    TimeUnit.SECONDS);
            var unsubscribe =
                    anonymousGroupService.streamLocations(
                            anonymousGroupId,
                            (location) -> {
                                try {
                                    emitter.send(SseEmitter.event().name("location").data(location));
                                } catch (Exception ignored) {
                                }
                            }, authentication);
            emitter.onCompletion(unsubscribe);
            return emitter;
        } catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }
}
