package com.peppeosmio.lockate.relay;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Forwards a push to FCM with our project's credentials. No authentication: knowing the token is
 * the permission, since only the app and the servers it registered with know it, and without the
 * key the app shares with its server a push only wakes the app, which drops it. The rate limits
 * bound what a leaked token allows.
 */
@Path("/v1/push")
public class PushResource {
  static final int MAX_TOKEN_LENGTH = 4096;
  static final int MAX_DATA_ENTRIES = 8;
  // FCM takes 4 KB; the backend's payloads are well under it
  static final int MAX_DATA_CHARS = 3072;

  private final FcmClient fcmClient;
  private final RateLimiter rateLimiter;
  private final int perTokenPerMinute;
  private final int perIpPerMinute;

  public PushResource(
      FcmClient fcmClient,
      RateLimiter rateLimiter,
      @ConfigProperty(name = "relay.limits.per-token-per-minute") int perTokenPerMinute,
      @ConfigProperty(name = "relay.limits.per-ip-per-minute") int perIpPerMinute) {
    this.fcmClient = fcmClient;
    this.rateLimiter = rateLimiter;
    this.perTokenPerMinute = perTokenPerMinute;
    this.perIpPerMinute = perIpPerMinute;
  }

  /**
   * 200 when FCM took it, 404 when FCM says the token is gone (the backend then drops the
   * registration), 400 for a bad request, 429 over a limit, 502 when FCM failed otherwise.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response push(PushRequest request, @Context HttpServerRequest http) {
    if (!isValid(request)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    var ip = http.remoteAddress().hostAddress();
    if (!rateLimiter.allow("ip:" + ip, perIpPerMinute)
        || !rateLimiter.allow("token:" + request.token(), perTokenPerMinute)) {
      return Response.status(Response.Status.TOO_MANY_REQUESTS).build();
    }
    return switch (fcmClient.send(request.token(), request.data())) {
      case SENT -> Response.ok().build();
      case UNREGISTERED -> Response.status(Response.Status.NOT_FOUND).build();
      case FAILED -> Response.status(Response.Status.BAD_GATEWAY).build();
    };
  }

  private static boolean isValid(PushRequest request) {
    if (request == null
        || request.token() == null
        || request.token().isBlank()
        || request.token().length() > MAX_TOKEN_LENGTH
        || request.data() == null
        || request.data().isEmpty()
        || request.data().size() > MAX_DATA_ENTRIES) {
      return false;
    }
    var chars = 0;
    for (var entry : request.data().entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        return false;
      }
      chars += entry.getKey().length() + entry.getValue().length();
    }
    return chars <= MAX_DATA_CHARS;
  }
}
