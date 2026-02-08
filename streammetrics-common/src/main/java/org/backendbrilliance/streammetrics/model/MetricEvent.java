package org.backendbrilliance.streammetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single metric event in the system.
 * This is the core data model sent through Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricEvent {

    @NotBlank(message = "Event ID cannot be blank")
    @JsonProperty("event_id")
    private String eventId;

    @NotNull(message = "Timestamp cannot be null")
    @JsonProperty("timestamp")
    private Instant timestamp;

    @NotBlank(message = "Service ID cannot be blank")
    @JsonProperty("service_id")
    private String serviceId;

    @NotBlank(message = "Instance ID cannot be blank")
    @JsonProperty("instance_id")
    private String instanceId;

    @NotNull(message = "Metric type cannot be null")
    @JsonProperty("metric_type")
    private MetricType metricType;

    @NotBlank(message = "Metric name cannot be blank")
    @JsonProperty("metric_name")
    private String metricName;

    @NotNull(message = "Value cannot be null")
    @Positive(message = "Value must be positive")
    @JsonProperty("value")
    private Double value;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("tags")
    private Map<String, String> tags;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Creates a new MetricEvent with auto-generated ID and current timestamp
     */
    public static MetricEvent create(String serviceId, String instanceId,
                                     MetricType metricType, String metricName,
                                     Double value, String unit) {
        return MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .serviceId(serviceId)
                .instanceId(instanceId)
                .metricType(metricType)
                .metricName(metricName)
                .value(value)
                .unit(unit)
                .build();
    }
}
