package org.backendbrilliance.streammetric.consumer;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.backendbrilliance.streammetric.processor.MetricProcessor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.backendbrilliance.streammetric.metrics.MetricCounter;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricConsumerService {

    private final Validator validator;
    private final KafkaTemplate<String, MetricEvent> deadLetterProducer;
    private final MetricProcessor metricProcessor;
    private final Optional<Tracer> tracer;
    private final MetricCounter metricCounter;

    @Value("${app.kafka.topic.metrics-events}")
    private String inputTopic;

    @Value("${app.kafka.topic.metrics-dead-letter}")
    private String deadLetterTopic;

    @KafkaListener(
            topics = "${app.kafka.topic.metrics-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMetric(
            @Payload ConsumerRecord<String, MetricEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        MetricEvent event = record.value();

        log.debug("Received metric: eventId={}, partition={}, offset={}",
                event.getEventId(), partition, offset);

        // Extract and continue trace
        Span span = null;
        if (event.getMetadata() != null && event.getMetadata().containsKey("traceId")) {
            String traceId = event.getMetadata().get("traceId");
            log.debug("Processing with traceId: {}", traceId);
        }

        try {
            // Validate event
            Set<ConstraintViolation<MetricEvent>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                String errorMsg = "Validation failed: " + violations;
                log.error("Invalid metric event: eventId={}, errors={}", event.getEventId(), errorMsg);
                sendToDeadLetterQueue(event, errorMsg, "VALIDATION_ERROR");
                acknowledgment.acknowledge();
                return;
            }

            // Process metric
            metricProcessor.processMetrics(event);
            metricCounter.incrementProcessed(event.getServiceId());
            // Manual commit after successful processing
            acknowledgment.acknowledge();

            log.debug("Successfully processed metric: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing metric: eventId={}", event.getEventId(), e);
            metricCounter.incrementFailed(event.getServiceId(), "processing_error");
            sendToDeadLetterQueue(event, e.getMessage(), "PROCESSING_ERROR");
            acknowledgment.acknowledge();  // Still commit to avoid reprocessing
        }
    }

    private void sendToDeadLetterQueue(MetricEvent event, String reason, String errorType) {
        log.warn("Sending to DLQ: eventId={}, reason={}, type={}",
                event.getEventId(), reason, errorType);

        // Add error metadata
        Map<String, String> metadata = event.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            event.setMetadata(metadata);
        }

        metadata.put("dlq_reason", reason);
        metadata.put("dlq_error_type", errorType);
        metadata.put("dlq_timestamp", String.valueOf(System.currentTimeMillis()));
        metadata.put("dlq_original_topic", inputTopic);

        try {
            deadLetterProducer.send(deadLetterTopic, event.getEventId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Sent to DLQ: eventId={}", event.getEventId());
                        } else {
                            log.error("Failed to send to DLQ: eventId={}", event.getEventId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Critical: Failed to send to DLQ: eventId={}", event.getEventId(), e);
        }
    }
}