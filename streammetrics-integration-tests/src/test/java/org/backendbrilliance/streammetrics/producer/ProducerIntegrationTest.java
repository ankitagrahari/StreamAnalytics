package org.backendbrilliance.streammetrics.producer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.backendbrilliance.streammetrics.IntegrationTestBase;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.backendbrilliance.streammetrics.serde.MetricEventDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, MetricEvent> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void shouldProduceMessageSuccessfully() throws ExecutionException, InterruptedException, TimeoutException {
        BlockingQueue<ConsumerRecord<String, MetricEvent>> records = new LinkedBlockingQueue<>();

        ContainerProperties containerProperties = new ContainerProperties("test-metrics-events");
        containerProperties.setMessageListener((MessageListener<String, MetricEvent>) records::add);

        KafkaMessageListenerContainer<String, MetricEvent> container = createTestContainer(containerProperties);
        container.start();

        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());

        MetricEvent metricEvent = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .serviceId("test-service")
                .instanceId("test-pod-1")
                .metricType(MetricType.HTTP_REQUEST)
                .metricName("api.request.duration")
                .value(123.45)
                .unit("milliseconds")
                .tags(Map.of("endpoint", "/api/test"))
                .build();

        CompletableFuture<SendResult<String, MetricEvent>> future = kafkaTemplate.send("test-metrics-events", metricEvent);
        SendResult<String, MetricEvent> result = future.get(10, TimeUnit.SECONDS);

        //Verify Send succeeded
        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().topic()).isEqualTo("test-metrics-events");

        ConsumerRecord<String, MetricEvent> received = records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.value().getEventId()).isEqualTo(metricEvent.getEventId());
        assertThat(received.value().getServiceId()).isEqualTo("test-service");
        assertThat(received.value().getValue()).isEqualTo(123.45);

        container.stop();
    }

    @Test
    void shouldHandleHighThroughput() throws Exception {
        // Given: 1000 events to send
        int eventCount = 1000;

        // When: Send all events
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < eventCount; i++) {
            MetricEvent event = MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .serviceId("load-test-service")
                    .instanceId("pod-" + (i % 5))
                    .metricType(MetricType.HTTP_REQUEST)
                    .metricName("api.request.duration")
                    .value(50.0 + (i % 100))
                    .unit("milliseconds")
                    .build();

            kafkaTemplate.send("test-metrics-events", event.getServiceId(), event);
        }

        // Flush to ensure all sent
        kafkaTemplate.flush();

        long duration = System.currentTimeMillis() - startTime;
        double throughput = (eventCount * 1000.0) / duration;

        // Then: Should process quickly
        assertThat(duration).isLessThan(5000); // Less than 5 seconds
        assertThat(throughput).isGreaterThan(200); // At least 200 events/sec

        System.out.printf("Sent %d events in %d ms (%.2f events/sec)%n",
                eventCount, duration, throughput);
    }


    private KafkaMessageListenerContainer<String, MetricEvent> createTestContainer(ContainerProperties containerProperties) {
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, MetricEventDeserializer.class
        );

        DefaultKafkaConsumerFactory<String, MetricEvent> kafkaConsumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        return new KafkaMessageListenerContainer<>(kafkaConsumerFactory, containerProperties);
    }
}
