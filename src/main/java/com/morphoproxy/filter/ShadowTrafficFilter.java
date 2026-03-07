package com.morphoproxy.filter;

import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ShadowTrafficFilter extends AbstractGatewayFilterFactory<ShadowTrafficFilter.Config> {

    private final WebClient webClient;

    public ShadowTrafficFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Fire and forget: send a duplicate request to the modern system asynchronously
            String shadowUrl = config.getShadowUri() + exchange.getRequest().getURI().getPath();
            
            webClient.method(exchange.getRequest().getMethod())
                     .uri(shadowUrl)
                     .headers(headers -> headers.addAll(exchange.getRequest().getHeaders()))
                     .retrieve()
                     .bodyToMono(Void.class)
                     .subscribe(); // Subscribe in the background, do not block the main chain

            return chain.filter(exchange);
        };
    }

    @Data
    public static class Config {
        private String shadowUri;
    }
}