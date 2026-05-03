package com.peppeosmio.lockate.anonymous_group.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.peppeosmio.lockate.anonymous_group.dto.*;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.NotFoundException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.srp.InvalidSrpSessionException;

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

@Slf4j
@RestController
@RequestMapping("/api/anonymous-groups")
@Validated
public class AnonymousGroupController {

    private final AnonymousGroupService anonymousGroupService;

    public AnonymousGroupController(AnonymousGroupService anonymousGroupService) {
        this.anonymousGroupService = anonymousGroupService;
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    AGCreateResDto createAnonymousGroup(@RequestBody AGCreateReqDto dto) throws Base64Exception {
        return anonymousGroupService.createAnonymousGroup(dto);
    }

    @GetMapping("/{anonymousGroupId}/members/auth/srp/info")
    @ResponseStatus(HttpStatus.OK)
    AGGetMemberPasswordSrpInfoResDto getMemberSrpInfo(@PathVariable UUID anonymousGroupId)
            throws AGNotFoundException {
        return anonymousGroupService.getMemberSrpInfo(anonymousGroupId);
    }

    @PostMapping("/{anonymousGroupId}/members/auth/srp/start")
    AGMemberAuthStartResDto memberAuthStart(
            @PathVariable UUID anonymousGroupId, @RequestBody AGMemberAuthStartReqDto dto)
            throws UnauthorizedException,
                    Base64Exception,
                    AGNotFoundException,
                    InvalidSrpSessionException {
        return anonymousGroupService.startMemberSrpAuth(anonymousGroupId, dto);
    }

    @PostMapping("/{anonymousGroupId}/members/auth/srp/verify")
    @ResponseStatus(HttpStatus.OK)
    AGMemberAuthVerifyResDto memberAuthVerify(
            @PathVariable UUID anonymousGroupId, @RequestBody AGMemberAuthVerifyReqDto dto)
            throws UnauthorizedException,
                    NotFoundException,
                    InvalidSrpSessionException,
                    Base64Exception {
        return anonymousGroupService.verifyMemberSrpAuth(anonymousGroupId, dto);
    }

}
