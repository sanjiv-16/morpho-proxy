package com.morphoproxy.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ShadowTrafficFilter extends AbstractGatewayFilterFactory<ShadowTrafficFilter.Config> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShadowTrafficFilter.class);

    // Methods that carry a body
    private static final java.util.Set<HttpMethod> BODY_METHODS = java.util.Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH
    );

    private final WebClient webClient;

    public ShadowTrafficFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public java.util.List<String> shortcutFieldOrder() {
        return java.util.Arrays.asList("shadowUri");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getRawPath();
            String query = exchange.getRequest().getURI().getRawQuery();
            String method = exchange.getRequest().getMethod().name();
            String requestId = exchange.getRequest().getId();

            String base = config.getShadowUri().endsWith("/")
                    ? config.getShadowUri().substring(0, config.getShadowUri().length() - 1)
                    : config.getShadowUri();
            String shadowUrl = base + path + (query != null ? "?" + query : "");

            log.info("👻 SHADOW START: [RequestId:{}] {} {} -> Mirroring to {}",
                    requestId, method, path, shadowUrl);

            long startTime = System.currentTimeMillis();

            HttpMethod httpMethod = exchange.getRequest().getMethod();

            Mono<Void> shadowMono;

            if (BODY_METHODS.contains(httpMethod)) {
                shadowMono = DataBufferUtils.join(exchange.getRequest().getBody())
                        .flatMap(dataBuffer -> {
                            byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bodyBytes);
                            DataBufferUtils.release(dataBuffer);

                            return webClient.method(httpMethod)
                                    .uri(shadowUrl)
                                    .headers(headers -> {
                                        headers.addAll(exchange.getRequest().getHeaders());
                                        headers.remove(HttpHeaders.HOST);
                                        headers.remove(HttpHeaders.CONTENT_LENGTH);
                                    })
                                    .body(BodyInserters.fromValue(bodyBytes))
                                    .retrieve()
                                    .toBodilessEntity()
                                    .then();
                        });
            } else {
                shadowMono = webClient.method(httpMethod)
                        .uri(shadowUrl)
                        .headers(headers -> {
                            headers.addAll(exchange.getRequest().getHeaders());
                            headers.remove(HttpHeaders.HOST);
                            headers.remove(HttpHeaders.CONTENT_LENGTH);
                        })
                        .retrieve()
                        .toBodilessEntity()
                        .then();
            }

            shadowMono
                    .doOnSuccess(v -> log.info(
                            "👻 SHADOW SUCCESS: [RequestId:{}] {} {} -> Modern responded in {}ms",
                            requestId, method, path,
                            System.currentTimeMillis() - startTime))
                    .doOnError(error -> log.error(
                            "👻 SHADOW ERROR: [RequestId:{}] {} {} -> Modern failed after {}ms: {}",
                            requestId, method, path,
                            System.currentTimeMillis() - startTime,
                            error.getMessage()))
                    .onErrorResume(error -> Mono.empty())
                    .subscribe();

            return chain.filter(exchange);
        };
    }

    @lombok.Data
    public static class Config {
        private String shadowUri;
    }
}