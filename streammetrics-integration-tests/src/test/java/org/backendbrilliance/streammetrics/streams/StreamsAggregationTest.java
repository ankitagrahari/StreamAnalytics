package org.backendbrilliance.streammetrics.streams;

import org.backendbrilliance.streammetrics.model.AggregatedMetric;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.backendbrilliance.streammetrics.serde.AggregatedMetricDeserializer;
import org.backendbrilliance.streammetrics.serde.AggregatedMetricSerializer;
import org.backendbrilliance.streammetrics.serde.MetricEventDeserializer;
import org.backendbrilliance.streammetrics.serde.MetricEventSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StreamsAggregationTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, MetricEvent> inputTopic;
    private TestOutputTopic<String, AggregatedMetric> outputTopic;

    @BeforeEach
    void setup() {
        // Create topology (simplified version for testing)
        StreamsBuilder builder = new StreamsBuilder();

        // Build the same aggregation topology
        // (You'd extract this from MetricAggregationTopology into a testable method)

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-streams");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        Topology topology = builder.build();
        testDriver = new TopologyTestDriver(topology, config);

        // Create test topics
        inputTopic = testDriver.createInputTopic(
                "test-metrics-events",
                Serdes.String().serializer(),
                new MetricEventSerializer()
        );

        outputTopic = testDriver.createOutputTopic(
                "test-metrics-aggregated-1m",
                Serdes.String().deserializer(),
                new AggregatedMetricDeserializer()
        );
    }

    @AfterEach
    void cleanup() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void shouldAggregateEventsInWindow() {
        // Given: 3 events in same window
        Instant windowStart = Instant.parse("2024-12-21T10:00:00Z");

        MetricEvent event1 = createEvent("service-1", windowStart.plusSeconds(10), 50.0);
        MetricEvent event2 = createEvent("service-1", windowStart.plusSeconds(20), 60.0);
        MetricEvent event3 = createEvent("service-1", windowStart.plusSeconds(30), 70.0);

        // When: Pipe events
        inputTopic.pipeInput("key1", event1, windowStart.plusSeconds(10));
        inputTopic.pipeInput("key2", event2, windowStart.plusSeconds(20));
        inputTopic.pipeInput("key3", event3, windowStart.plusSeconds(30));

        // Advance time to close window
        testDriver.advanceWallClockTime(Duration.ofMinutes(1));

        // Then: Should produce aggregate
        if (!outputTopic.isEmpty()) {
            TestRecord<String, AggregatedMetric> output = outputTopic.readRecord();
            AggregatedMetric aggregate = output.value();

            assertThat(aggregate.getCount()).isEqualTo(3);
            assertThat(aggregate.getAvg()).isEqualTo(60.0); // (50+60+70)/3
            assertThat(aggregate.getMin()).isEqualTo(50.0);
            assertThat(aggregate.getMax()).isEqualTo(70.0);
            assertThat(aggregate.getSum()).isEqualTo(180.0);
        }
    }

    private MetricEvent createEvent(String serviceId, Instant timestamp, double value) {
        return MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(timestamp)
                .serviceId(serviceId)
                .instanceId("pod-1")
                .metricType(MetricType.HTTP_REQUEST)
                .metricName("api.request.duration")
                .value(value)
                .unit("milliseconds")
                .build();
    }
}