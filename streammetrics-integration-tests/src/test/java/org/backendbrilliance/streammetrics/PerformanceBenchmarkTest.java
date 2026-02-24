package org.backendbrilliance.streammetrics;

import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceBenchmarkTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, MetricEvent> kafkaTemplate;

    @Test
    void shouldSustain1000EventsPerSecond() throws Exception {
        // Given: 5000 events (5 seconds at 1000 events/sec)
        int totalEvents = 5000;
        int targetDurationMs = 5000;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: Send events as fast as possible
        long startTime = System.currentTimeMillis();

        CompletableFuture<?>[] futures = new CompletableFuture[totalEvents];

        for (int i = 0; i < totalEvents; i++) {
            MetricEvent event = MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .serviceId("benchmark-service")
                    .instanceId("pod-1")
                    .metricType(MetricType.HTTP_REQUEST)
                    .metricName("api.request.duration")
                    .value(50.0 + (i % 100))
                    .unit("milliseconds")
                    .build();

            futures[i] = kafkaTemplate.send("test-metrics-events", event.getServiceId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    });
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).get();

        long duration = System.currentTimeMillis() - startTime;
        double throughput = (totalEvents * 1000.0) / duration;

        // Then: Verify performance
        assertThat(successCount.get()).isEqualTo(totalEvents);
        assertThat(failureCount.get()).isZero();
        assertThat(throughput).isGreaterThan(1000.0);

        System.out.printf("Performance: %d events in %d ms = %.2f events/sec%n",
                totalEvents, duration, throughput);
    }
}
