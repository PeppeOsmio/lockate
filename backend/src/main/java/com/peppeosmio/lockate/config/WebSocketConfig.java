package com.peppeosmio.lockate.config;

import com.peppeosmio.lockate.anonymous_group.websocket.AGWSHandshakeInterceptor;
import com.peppeosmio.lockate.anonymous_group.websocket.AGWSHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final AGWSHandler agWSHandler;
  private final AGWSHandshakeInterceptor agWSHandshakeInterceptor;

  public WebSocketConfig(
      AGWSHandler agWSHandler,
      AGWSHandshakeInterceptor agWSHandshakeInterceptor) {
    this.agWSHandler = agWSHandler;
    this.agWSHandshakeInterceptor = agWSHandshakeInterceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(
            agWSHandler, "/api/ws/anonymous-groups/{anonymousGroupId}")
        .addInterceptors(agWSHandshakeInterceptor)
        .setAllowedOriginPatterns("*");
  }
}
