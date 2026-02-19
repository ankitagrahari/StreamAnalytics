package org.backendbrilliance.streammetrics.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.backendbrilliance.streammetrics.service.MetricProducerService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestMetricProducer {

    private final MetricProducerService producerService;
    private final Random random = new Random();

    public void produceTestEvents(Long totalEvents, boolean sendInvalidEvents) throws Exception {
        log.info("Starting to send test metrics...");

        String[] services = {"payment-service", "user-service", "order-service"};
        String[] endpoints = {"/api/v1/payments", "/api/v1/users", "/api/v1/orders"};
        String[] methods = {"GET", "POST", "PUT", "DELETE"};

        if(sendInvalidEvents) {
            MetricEvent invalidEvent = MetricEvent.builder()
                    .eventId("invalid-test-123")
                    .timestamp(Instant.now())
                    .serviceId(null)
                    .instanceId("test")
                    .metricType(MetricType.HTTP_REQUEST)
                    .metricName("test")
                    .value(-100.0)
                    .unit("ms")
                    .build();

            log.warn("Sending INVALID event for testing...");
            producerService.sendMetric(invalidEvent);
            Thread.sleep(1000);
        }

        for (int i = 0; i < totalEvents; i++) {
            String serviceId = services[random.nextInt(services.length)];
            String endpoint = endpoints[random.nextInt(endpoints.length)];
            String method = methods[random.nextInt(methods.length)];

            MetricEvent event = MetricEvent.create(
                    serviceId,
                    serviceId + "-pod-" + random.nextInt(3),
                    MetricType.HTTP_REQUEST,
                    "api.request.duration",
                    50.0 + random.nextDouble() * 500,
                    "milliseconds"
            );

            event.setTags(Map.of(
                    "endpoint", endpoint,
                    "method", method,
                    "statusCode", String.valueOf(200 + random.nextInt(100))
            ));

            event.setMetadata(Map.of(
                    "traceId", UUID.randomUUID().toString(),
                    "spanId", UUID.randomUUID().toString()
            ));

            producerService.sendMetric(event);

            // Small delay to avoid overwhelming
            TimeUnit.MILLISECONDS.sleep(10);
        }

        log.info("Finished producing {} test metrics",  totalEvents);
    }
}