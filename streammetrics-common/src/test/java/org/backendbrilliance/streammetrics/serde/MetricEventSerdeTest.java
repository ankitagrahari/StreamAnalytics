package org.backendbrilliance.streammetrics.serde;

import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricEventSerdeTest {

    private final MetricEventSerializer serializer = new MetricEventSerializer();
    private final MetricEventDeserializer deserializer = new MetricEventDeserializer();

    @Test
    void shouldSerializeAndDeserializeMetricEvent() {
        // Given
        MetricEvent original = MetricEvent.builder()
                .eventId("test-123")
                .timestamp(Instant.now())
                .serviceId("payment-service")
                .instanceId("payment-pod-1")
                .metricType(MetricType.HTTP_REQUEST)
                .metricName("api.request.duration")
                .value(245.5)
                .unit("milliseconds")
                .tags(Map.of("endpoint", "/api/payments", "method", "POST"))
                .metadata(Map.of("traceId", "abc123"))
                .build();

        // When
        byte[] serialized = serializer.serialize("test-topic", original);
        MetricEvent deserialized = deserializer.deserialize("test-topic", serialized);

        // Then
        assertNotNull(serialized);
        assertNotNull(deserialized);
        assertEquals(original.getEventId(), deserialized.getEventId());
        assertEquals(original.getServiceId(), deserialized.getServiceId());
        assertEquals(original.getValue(), deserialized.getValue());
        assertEquals(original.getTags(), deserialized.getTags());
    }

    @Test
    void shouldHandleNullGracefully() {
        // When/Then
        assertNull(serializer.serialize("test-topic", null));
        assertNull(deserializer.deserialize("test-topic", null));
    }
}