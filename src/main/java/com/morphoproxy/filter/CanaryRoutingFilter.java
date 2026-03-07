package com.morphoproxy.filter;

import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

@Component
public class CanaryRoutingFilter extends AbstractGatewayFilterFactory<CanaryRoutingFilter.Config> {

    public CanaryRoutingFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            int randomValue = ThreadLocalRandom.current().nextInt(100);
            
            // If within the canary percentage, re-route the request to the modern system
            if (randomValue < config.getPercentage()) {
                URI targetUri = URI.create(config.getTargetUri() + exchange.getRequest().getURI().getPath());
                exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, targetUri);
            }
            
            return chain.filter(exchange);
        };
    }

    @Data
    public static class Config {
        private int percentage;
        private String targetUri;
    }
}