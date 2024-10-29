package com.example.demo.websocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;


import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 *  This file copy content from WebsocketRoutingFilter.java
 * <p>
 *  Please refer to the original file for more information
 * <p>
 *  Must retest if change version of library spring-cloud in file pom.xml
 * @see org.springframework.cloud.gateway.filter.WebsocketRoutingFilter
 */
@Component
public class CustomWebsocketRoutingFilter extends WebsocketRoutingFilter {

    private static final Log log = LogFactory.getLog(CustomWebsocketRoutingFilter.class);

    private final WebSocketClient webSocketClient;

    private final WebSocketService webSocketService;

    private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

    private volatile List<HttpHeadersFilter> headersFilters;

    public CustomWebsocketRoutingFilter(WebSocketClient webSocketClient, WebSocketService webSocketService,
                                  ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider) {
        super(webSocketClient, webSocketService, headersFiltersProvider);
        this.webSocketClient = webSocketClient;
        this.webSocketService = webSocketService;
        this.headersFiltersProvider = headersFiltersProvider;
    }

    static String convertHttpToWs(String scheme) {
        scheme = scheme.toLowerCase();
        return "http".equals(scheme) ? "ws" : "https".equals(scheme) ? "wss" : scheme;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        changeSchemeIfIsWebSocketUpgrade(exchange);

        URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
        String scheme = requestUrl.getScheme();

        if (isAlreadyRouted(exchange) || (!"ws".equals(scheme) && !"wss".equals(scheme))) {
            return chain.filter(exchange);
        }
        setAlreadyRouted(exchange);

        HttpHeaders headers = exchange.getRequest().getHeaders();
        HttpHeaders filtered = filterRequest(getHeadersFilters(), exchange);

        List<String> protocols = getProtocols(headers);

        return this.webSocketService.handleRequest(exchange,
                new ProxyWebSocketHandler(requestUrl, this.webSocketClient, filtered, protocols));
    }

    List<String> getProtocols(HttpHeaders headers) {
        List<String> protocols = headers.get(SEC_WEBSOCKET_PROTOCOL);
        if (protocols != null) {
            ArrayList<String> updatedProtocols = new ArrayList<>();
            for (int i = 0; i < protocols.size(); i++) {
                String protocol = protocols.get(i);
                updatedProtocols.addAll(Arrays.asList(StringUtils.tokenizeToStringArray(protocol, ",")));
            }
            protocols = updatedProtocols;
        }
        return protocols;
    }

    List<HttpHeadersFilter> getHeadersFilters() {
        if (this.headersFilters == null) {
            this.headersFilters = this.headersFiltersProvider.getIfAvailable(ArrayList::new);

            headersFilters.add((headers, exchange) -> {
                HttpHeaders filtered = new HttpHeaders();
                filtered.addAll(headers);
                filtered.remove(HttpHeaders.HOST);
                boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);
                if (preserveHost) {
                    String host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
                    filtered.add(HttpHeaders.HOST, host);
                }
                return filtered;
            });

            headersFilters.add((headers, exchange) -> {
                HttpHeaders filtered = new HttpHeaders();
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (!entry.getKey().toLowerCase().startsWith("sec-websocket")) {
                        filtered.addAll(entry.getKey(), entry.getValue());
                    }
                }
                return filtered;
            });
        }

        return this.headersFilters;
    }

    static void changeSchemeIfIsWebSocketUpgrade(ServerWebExchange exchange) {
        URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
        String scheme = requestUrl.getScheme().toLowerCase();
        String upgrade = exchange.getRequest().getHeaders().getUpgrade();
        if ("WebSocket".equalsIgnoreCase(upgrade) && ("http".equals(scheme) || "https".equals(scheme))) {
            String wsScheme = convertHttpToWs(scheme);
            boolean encoded = containsEncodedParts(requestUrl);
            URI wsRequestUrl = UriComponentsBuilder.fromUri(requestUrl).scheme(wsScheme).build(encoded).toUri();
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, wsRequestUrl);
            if (log.isTraceEnabled()) {
                log.trace("changeSchemeTo:[" + wsRequestUrl + "]");
            }
        }
    }
}