package org.backendbrilliance.streammetric.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricCounter {

    private final MeterRegistry meterRegistry;

    public void incrementProcessed(String serviceId) {
        Counter.builder("metrics.processed")
                .tag("service", serviceId)
                .register(meterRegistry)
                .increment();
    }

    public void incrementFailed(String serviceId, String reason) {
        Counter.builder("metrics.failed")
                .tag("service", serviceId)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
}
