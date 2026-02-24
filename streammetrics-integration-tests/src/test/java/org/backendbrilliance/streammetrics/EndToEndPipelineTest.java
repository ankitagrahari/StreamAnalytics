package org.backendbrilliance.streammetrics;

import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

class EndToEndPipelineTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, MetricEvent> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void cleanupRedis() {
        // Clean Redis before each test
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    void shouldProcessEventsThroughEntirePipeline() {
        // Given: Send 100 events for the same service
        String serviceId = "test-service";
        String metricName = "api.request.duration";
        int eventCount = 100;

        Instant windowStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        for (int i = 0; i < eventCount; i++) {
            MetricEvent event = MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(windowStart.plusSeconds(i % 60)) // Spread across 1 minute
                    .serviceId(serviceId)
                    .instanceId("pod-" + (i % 3))
                    .metricType(MetricType.HTTP_REQUEST)
                    .metricName(metricName)
                    .value(50.0 + (i % 50)) // Values between 50-100
                    .unit("milliseconds")
                    .tags(Map.of("endpoint", "/api/test"))
                    .build();

            kafkaTemplate.send("test-metrics-events", serviceId, event);
        }

        kafkaTemplate.flush();

        // When: Wait for aggregation to complete (window closes + processing)
        // Windows close at the minute boundary, so wait up to 70 seconds
        String redisKeyPattern = String.format("agg:1m:%s:%s:*", serviceId, metricName);

        await()
                .atMost(Duration.ofSeconds(70))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var keys = redisTemplate.keys(redisKeyPattern);
                    assertThat(keys).isNotEmpty();
                });

        // Then: Verify aggregate exists in Redis
        var keys = redisTemplate.keys(redisKeyPattern);
        assertThat(keys).hasSize(1);

        String aggregateKey = keys.iterator().next();

        // Then: Verify aggregate values
        Map<Object, Object> aggregate = redisTemplate.opsForHash().entries(aggregateKey);

        assertThat(aggregate).containsKeys("count", "sum", "avg", "min", "max");

        Long count = Long.parseLong(aggregate.get("count").toString());
        Double avg = Double.parseDouble(aggregate.get("avg").toString());
        Double min = Double.parseDouble(aggregate.get("min").toString());
        Double max = Double.parseDouble(aggregate.get("max").toString());

        assertThat(count).isEqualTo(eventCount);
        assertThat(avg).isBetween(50.0, 100.0);
        assertThat(min).isBetween(50.0, 60.0); // First few values
        assertThat(max).isBetween(90.0, 100.0); // Last few values

        System.out.printf("✓ Pipeline test passed: count=%d, avg=%.2f, min=%.2f, max=%.2f%n",
                count, avg, min, max);
    }

    @Test
    void shouldHandleMultipleServices() {
        // Given: Events from 3 different services
        String[] services = {"payment-service", "user-service", "order-service"};
        int eventsPerService = 50;

        Instant windowStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        for (String service : services) {
            for (int i = 0; i < eventsPerService; i++) {
                MetricEvent event = MetricEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .timestamp(windowStart.plusSeconds(i % 60))
                        .serviceId(service)
                        .instanceId("pod-1")
                        .metricType(MetricType.HTTP_REQUEST)
                        .metricName("api.request.duration")
                        .value(100.0 + (i % 50))
                        .unit("milliseconds")
                        .build();

                kafkaTemplate.send("test-metrics-events", service, event);
            }
        }

        kafkaTemplate.flush();

        // When: Wait for all aggregates
        await()
                .atMost(Duration.ofSeconds(70))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var keys = redisTemplate.keys("agg:1m:*:api.request.duration:*");
                    assertThat(keys).hasSizeGreaterThanOrEqualTo(3);
                });

        // Then: Verify each service has an aggregate
        for (String service : services) {
            String pattern = String.format("agg:1m:%s:api.request.duration:*", service);
            var keys = redisTemplate.keys(pattern);

            assertThat(keys)
                    .as("Aggregate for %s should exist", service)
                    .isNotEmpty();

            String key = keys.iterator().next();
            Map<Object, Object> aggregate = redisTemplate.opsForHash().entries(key);

            Long count = Long.parseLong(aggregate.get("count").toString());
            assertThat(count).isEqualTo(eventsPerService);

            System.out.printf("✓ Service %s: count=%d%n", service, count);
        }
    }

    @Test
    void shouldHandleInvalidEvents() {
        // Given: Invalid event (null serviceId)
        MetricEvent invalidEvent = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .serviceId(null) // Invalid!
                .instanceId("pod-1")
                .metricType(MetricType.HTTP_REQUEST)
                .metricName("test")
                .value(100.0)
                .unit("ms")
                .build();

        // When: Send invalid event
        kafkaTemplate.send("test-metrics-events", "null", invalidEvent);
        kafkaTemplate.flush();

        // Then: Should not crash the pipeline
        // Wait a bit to ensure processing happened
        await()
                .pollDelay(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(10))
                .until(() -> true);

        // Then: Invalid event should NOT create aggregate
        var keys = redisTemplate.keys("agg:1m:null:*");
        assertThat(keys).isEmpty();

        System.out.println("✓ Invalid event handled gracefully (no aggregate created)");
    }
}