package com.morphoproxy.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        
        log.info("➡️ INCOMING: {} {}", method, path);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            var statusCode = exchange.getResponse().getStatusCode();
            
            log.info("⬅️ OUTGOING: {} {} | Status: {} | Time: {}ms", 
                    method, 
                    path, 
                    (statusCode != null ? statusCode.value() : "UNKNOWN"), 
                    duration);
        }));
    }

    @Override
    public int getOrder() {
       return -1; 
    }
}