package org.backendbrilliance.streammetrics.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.backendbrilliance.streammetrics.model.AggregatedMetric;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AggregatedMetricSerializer implements Serializer<AggregatedMetric> {

    private final ObjectMapper objectMapper;

    public AggregatedMetricSerializer() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public byte[] serialize(String topic, AggregatedMetric data) {
        if (data == null) return null;

        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            log.error("Error serializing MetricEvent: {}", data, e);
            throw new SerializationException("Error serializing MetricEvent", e);
        }
    }
}
