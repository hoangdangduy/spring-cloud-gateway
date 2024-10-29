package com.example.demo.websocket;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;

/**
 *  This file copy content from ProxyWebSocketHandler in WebsocketRoutingFilter.java
 * <p>
 *  Please refer to the original file for more information
 * <p>
 *  Must retest if change version of library spring-cloud in file pom.xml
 * @see org.springframework.cloud.gateway.filter.WebsocketRoutingFilter
 */
public class ProxyWebSocketHandler implements WebSocketHandler {

    private static final Log log = LogFactory.getLog(ProxyWebSocketHandler.class);

    private final WebSocketClient client;

    private final URI url;

    private final HttpHeaders headers;

    private final List<String> subProtocols;

    ProxyWebSocketHandler(URI url, WebSocketClient client, HttpHeaders headers, List<String> protocols) {
        this.client = client;
        this.url = url;
        this.headers = headers;
        if (protocols != null) {
            this.subProtocols = protocols;
        }
        else {
            this.subProtocols = Collections.emptyList();
        }
    }

    @Override
    public List<String> getSubProtocols() {
        return this.subProtocols;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return client.execute(url, this.headers, new WebSocketHandler() {

            private CloseStatus adaptCloseStatus(CloseStatus closeStatus) {
                int code = closeStatus.getCode();
                if (code > 2999 && code < 5000) {
                    return closeStatus;
                }
                switch (code) {
                    case 1000:
                    case 1001:
                    case 1002:
                    case 1003:
                    case 1007:
                    case 1008:
                    case 1009:
                    case 1010:
                    case 1011:
                        return closeStatus;
                    case 1004:
                    case 1005:
                    case 1006:
                    case 1012:
                    case 1013:
                    case 1015:
                    default:
                        return CloseStatus.PROTOCOL_ERROR;
                }
            }

            @Override
            public Mono<Void> handle(WebSocketSession proxySession) {
                Mono<Void> serverClose = proxySession.closeStatus().filter(__ -> session.isOpen())
                        .map(this::adaptCloseStatus).flatMap(session::close);
                Mono<Void> proxyClose = session.closeStatus().filter(__ -> proxySession.isOpen())
                        .map(this::adaptCloseStatus).flatMap(proxySession::close);
                Mono<Void> proxySessionSend = proxySession
                        .send(session.receive().doOnNext(WebSocketMessage::retain).doOnNext(webSocketMessage -> {
                            if (log.isTraceEnabled()) {
                                log.trace("proxySession(send from client): " + proxySession.getId()
                                        + ", corresponding session:" + session.getId() + ", packet: "
                                        + webSocketMessage.getPayloadAsText());
                            }
                        }));
                Mono<Void> serverSessionSend = session.send(
                        proxySession.receive().doOnNext(WebSocketMessage::retain).map(webSocketMessage -> {
                            String modifiedPayload = webSocketMessage.getPayloadAsText() + "data modify";
                            return new WebSocketMessage(webSocketMessage.getType(), session.bufferFactory().wrap(modifiedPayload.getBytes()));
                        }).doOnNext(webSocketMessage -> {
                            if (log.isTraceEnabled()) {
                                log.trace("session(send from backend): " + session.getId()
                                        + ", corresponding proxySession:" + proxySession.getId() + " packet: "
                                        + webSocketMessage.getPayloadAsText());
                            }
                        })
                );
                Mono.when(serverClose, proxyClose).subscribe();
                return Mono.zip(proxySessionSend, serverSessionSend).then();
            }

            @Override
            public List<String> getSubProtocols() {
                return subProtocols;
            }
        });
    }
}
