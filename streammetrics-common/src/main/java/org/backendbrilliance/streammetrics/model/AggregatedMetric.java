package org.backendbrilliance.streammetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents aggregated metrics over a time window
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedMetric {

    @JsonProperty("service_id")
    private String serviceId;

    @JsonProperty("metric_name")
    private String metricName;

    @JsonProperty("metric_type")
    private MetricType metricType;

    @JsonProperty("window_start")
    private Instant windowStart;

    @JsonProperty("window_end")
    private Instant windowEnd;

    @JsonProperty("count")
    private Long count;

    @JsonProperty("sum")
    private Double sum;

    @JsonProperty("avg")
    private Double avg;

    @JsonProperty("min")
    private Double min;

    @JsonProperty("max")
    private Double max;

    @JsonProperty("unit")
    private String unit;
}
