package com.peppeosmio.lockate.relay;

import java.util.Map;

/**
 * A push for one phone. {@code data} is what the backend sealed for the app, which the relay can't
 * read: it is forwarded as the FCM data message.
 */
public record PushRequest(String token, Map<String, String> data) {}
