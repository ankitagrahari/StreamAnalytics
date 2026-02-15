package org.backendbrilliance.streammetrics.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MetricEventDeserializer implements Deserializer<MetricEvent> {

    private final ObjectMapper objectMapper;

    public MetricEventDeserializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public MetricEvent deserialize(String topic, byte[] data) {
        if (data == null) return null;

        try {
            return objectMapper.readValue(data, MetricEvent.class);
        } catch (Exception e) {
            log.error("Error deserializing from bytes: {}", data, e);
            throw new SerializationException("Error serializing MetricEvent", e);
        }
    }
}