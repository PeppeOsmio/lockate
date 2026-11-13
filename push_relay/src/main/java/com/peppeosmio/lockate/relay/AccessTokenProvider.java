package com.peppeosmio.lockate.relay;

import java.io.IOException;

/** An OAuth access token of our Firebase project's service account, for the FCM API. */
public interface AccessTokenProvider {
  String accessToken() throws IOException, InterruptedException;
}
