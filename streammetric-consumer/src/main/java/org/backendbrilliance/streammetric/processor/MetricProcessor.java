package org.backendbrilliance.streammetric.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricProcessor {

    private final RedisTemplate<String, Object> redisTemplate;

    public void processMetrics(MetricEvent event) {
        log.debug("Processing metric: eventId={}, serviceId={}, metricName={}",
                event.getEventId(), event.getServiceId(), event.getMetricName());

        // Store raw event in Redis (temporary - 1 hour TTL)
        String rawKey = String.format("metrics:%s:%s:raw",
                event.getServiceId(),
                event.getMetricName());

        redisTemplate.opsForList().rightPush(rawKey, event);
        redisTemplate.expire(rawKey, 1, TimeUnit.HOURS);

        // Update counter
        String counterKey = String.format("metrics:%s:%s:count",
                event.getServiceId(),
                event.getMetricName());
        redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.expire(counterKey, 1, TimeUnit.HOURS);

        // Store latest value
        String latestKey = String.format("metrics:%s:%s:latest",
                event.getServiceId(),
                event.getMetricName());
        redisTemplate.opsForValue().set(latestKey, event.getValue());
        redisTemplate.expire(latestKey, 1, TimeUnit.HOURS);

        log.debug("Stored metric in Redis: key={}", rawKey);
    }
}
