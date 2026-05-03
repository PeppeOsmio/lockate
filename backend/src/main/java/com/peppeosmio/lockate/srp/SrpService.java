package com.peppeosmio.lockate.srp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.exceptions.SrpSessionNotFoundException;
import com.peppeosmio.lockate.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class SrpService {
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public SrpService(RedisService redisService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    private StatelessSRP6Server getSrpServer(BigInteger verifier) {
        // SRP
        // standard group params (2048-bit)
        var params = SRP6StandardGroups.rfc5054_2048;

        // Initialize SRP server
        var random = new SecureRandom();
        var server = new StatelessSRP6Server();
        server.init(params, verifier, new SHA256Digest(), random);
        return server;
    }

    public SrpSessionResult startSrp(BigInteger A, BigInteger verifier)
            throws CryptoException, InvalidSrpSessionException {
        var server = getSrpServer(verifier);
        var B = server.generateServerCredentials();
        var encoder = Base64.getEncoder();
        var session = new SrpSession(encoder.encodeToString(A.toByteArray()),
                encoder.encodeToString(server.getb().toByteArray()),
                encoder.encodeToString(B.toByteArray()), LocalDateTime.now(ZoneOffset.UTC));
        var sessionId = "srp:" + UUID.randomUUID();
        String sessionJson;
        try {
            sessionJson = objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new InvalidSrpSessionException();
        }
        redisService.saveValue(sessionId, sessionJson, Duration.ofMinutes(5));
        return new SrpSessionResult(sessionId, session);

    }

    public boolean verifySrp(String sessionId, BigInteger verifier, BigInteger M1)
            throws CryptoException, InvalidSrpSessionException {
        var srpSessionJson = this.redisService.getValue(sessionId)
                .orElseThrow(() -> new SrpSessionNotFoundException(sessionId));
        SrpSession srpSession;
        try {
            srpSession = objectMapper.readValue(srpSessionJson, SrpSession.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new InvalidSrpSessionException();
        }
        var decoder = Base64.getDecoder();
        var srpServer = getSrpServer(verifier);

        srpServer.setInitialState(new BigInteger(decoder.decode(srpSession.b())),
                new BigInteger(decoder.decode(srpSession.B())));
        var S = srpServer.calculateSecret(
                new BigInteger(decoder.decode(srpSession.A())));
        redisService.deleteValue(sessionId);
        return srpServer.verifyClientEvidenceMessage(M1);
    }
}
