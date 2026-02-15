package org.backendbrilliance.streammetrics.serde;

import org.backendbrilliance.streammetrics.model.MetricEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MetricEventSerializer implements Serializer<MetricEvent> {

    private final ObjectMapper objectMapper;

    public MetricEventSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public byte[] serialize(String topic, MetricEvent data) {
        if (data == null) return null;

        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            log.error("Error serializing MetricEvent: {}", data, e);
            throw new SerializationException("Error serializing MetricEvent", e);
        }
    }
}