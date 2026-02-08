package org.backendbrilliance.streammetrics.service;

import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.backendbrilliance.streammetrics.serde.MetricEventDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(
        topics = {"metrics-events"},
        partitions = 3,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9999",
                "port=9999"
        }
)
class MetricProducerServiceIntegrationTest {

    @Autowired
    private MetricProducerService producerService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void shouldSendMetricEventSuccessfully() throws Exception {
        // Given
        BlockingQueue<ConsumerRecord<String, MetricEvent>> records = new LinkedBlockingQueue<>();

        ContainerProperties containerProps = new ContainerProperties("metrics-events");
        containerProps.setMessageListener((MessageListener<String, MetricEvent>) records::add);

        KafkaMessageListenerContainer<String, MetricEvent> container =
                createContainer(containerProps);
        container.start();

        ContainerTestUtils.waitForAssignment(container,
                embeddedKafkaBroker.getPartitionsPerTopic());

        MetricEvent event = MetricEvent.create(
                "test-service",
                "test-pod-1",
                MetricType.HTTP_REQUEST,
                "api.request.duration",
                123.45,
                "milliseconds"
        );

        // When
        CompletableFuture<SendResult<String, MetricEvent>> future =
                producerService.sendMetric(event);

        SendResult<String, MetricEvent> result = future.get(10, TimeUnit.SECONDS);

        // Then
        assertNotNull(result);
        assertEquals("metrics-events", result.getRecordMetadata().topic());

        // Verify consumer received it
        ConsumerRecord<String, MetricEvent> received =
                records.poll(10, TimeUnit.SECONDS);

        assertNotNull(received);
        assertEquals(event.getEventId(), received.value().getEventId());
        assertEquals(event.getServiceId(), received.value().getServiceId());

        container.stop();
    }

    private KafkaMessageListenerContainer<String, MetricEvent> createContainer(
            ContainerProperties containerProps) {

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafkaBroker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                MetricEventDeserializer.class
        );

        DefaultKafkaConsumerFactory<String, MetricEvent> cf =
                new DefaultKafkaConsumerFactory<>(consumerProps);

        return new KafkaMessageListenerContainer<>(cf, containerProps);
    }
}