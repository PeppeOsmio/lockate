package com.peppeosmio.lockate.config;

import com.peppeosmio.lockate.anonymous_group.websocket.AGSendLocationHandshakeInterceptor;
import com.peppeosmio.lockate.anonymous_group.websocket.AGSendLocationWSHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AGSendLocationWSHandler agSendLocationWSHandler;
    private final AGSendLocationHandshakeInterceptor agSendLocationHandshakeInterceptor;

    public WebSocketConfig(
            AGSendLocationWSHandler agSendLocationWSHandler,
            AGSendLocationHandshakeInterceptor agSendLocationHandshakeInterceptor) {
        this.agSendLocationWSHandler = agSendLocationWSHandler;
        this.agSendLocationHandshakeInterceptor = agSendLocationHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                        agSendLocationWSHandler,
                        "/api/ws/anonymous-groups/{anonymousGroupId}/send-location")
                .addInterceptors(agSendLocationHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
