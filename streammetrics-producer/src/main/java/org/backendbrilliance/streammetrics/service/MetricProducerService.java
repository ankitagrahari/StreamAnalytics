package org.backendbrilliance.streammetrics.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MetricProducerService {

    private final KafkaTemplate<String, MetricEvent> kafkaTemplate;

    @Value("${app.kafka.topic.metrics-events}")
    private String topic;

    @Autowired
    public MetricProducerService(KafkaTemplate<String, MetricEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a metric event to Kafka with retry and circuit breaker patterns
     *
     * @param event The metric event to send
     * @return CompletableFuture with send result
     */
    @Retry(name = "kafkaProducer", fallbackMethod = "sendToDeadLetterQueue")
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendToDeadLetterQueue")
    public CompletableFuture<SendResult<String, MetricEvent>> sendMetric(MetricEvent event) {

        // Use serviceId as partition key for locality
        String partitionKey = event.getServiceId();

        log.debug("Sending metric event: eventId={}, serviceId={}, metricName={}",
                event.getEventId(), event.getServiceId(), event.getMetricName());

        return kafkaTemplate
                .send(topic, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Metric sent successfully: eventId={}, partition={}, offset={}",
                                event.getEventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send metric: eventId={}", event.getEventId(), ex);
                    }
                });
    }

    /**
     * Fallback method when main send fails after all retries
     */
    private CompletableFuture<SendResult<String, MetricEvent>> sendToDeadLetterQueue(
            MetricEvent event, Exception ex) {

        log.error("All retry attempts exhausted for event: {}. Sending to DLQ.",
                event.getEventId(), ex);

        // Send to dead letter queue
        return kafkaTemplate.send("metrics-dead-letter", event.getEventId(), event);
    }

    /**
     * Synchronous send for critical metrics (blocks until ack)
     */
    public SendResult<String, MetricEvent> sendMetricSync(MetricEvent event)
            throws Exception {

        log.warn("Synchronous send used for event: {} - This blocks the thread!",
                event.getEventId());

        return kafkaTemplate.send(topic, event.getServiceId(), event).get();
    }
}