package org.backendbrilliance.streammetrics.consumer;

import org.backendbrilliance.streammetrics.model.AggregatedMetric;
import org.backendbrilliance.streammetrics.service.AggregatedMetricStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatedMetricConsumer {

    private final AggregatedMetricStore store;

    @KafkaListener(
            topics = "${app.kafka.topics.output-1m}",
            groupId = "aggregated-metric-store"
    )
    public void consumeAggregated(AggregatedMetric metric) {
        log.info("Received aggregated metric: service={}, metric={}, avg={}, count={}",
                metric.getServiceId(),
                metric.getMetricName(),
                metric.getAvg(),
                metric.getCount());

        store.store(metric);
    }
}