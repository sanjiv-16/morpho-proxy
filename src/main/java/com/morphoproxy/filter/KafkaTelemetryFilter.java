package com.morphoproxy.filter;

import com.morphoproxy.model.TrafficEvent;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

@Component
public class KafkaTelemetryFilter extends AbstractGatewayFilterFactory<Object> {

    private final KafkaTemplate<String, TrafficEvent> kafkaTemplate;

    public KafkaTelemetryFilter(KafkaTemplate<String, TrafficEvent> kafkaTemplate) {
        super(Object.class);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();

            return chain.filter(exchange).doFinally(signalType -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null ? 
                                 exchange.getResponse().getStatusCode().value() : 500;
                
                String path = exchange.getRequest().getURI().getPath();
                Object targetUriAttr = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
                String destination = targetUriAttr != null ? targetUriAttr.toString() : "unknown";

                TrafficEvent event = new TrafficEvent(
                        Instant.now().toString(), path, duration, statusCode, destination
                );

                kafkaTemplate.send("api-traffic-events", event);
            });
        };
    }
}