package org.backendbrilliance.streammetrics.service;

import org.backendbrilliance.streammetrics.model.AggregatedMetric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatedMetricStore {

    private final RedisTemplate<String, Object> redisTemplate;

    public void store(AggregatedMetric metric) {
        String key = buildKey(metric);

        log.debug("Storing aggregated metric: key={}, count={}, avg={}",
                key, metric.getCount(), metric.getAvg());

        // Store as hash
        redisTemplate.opsForHash().put(key, "count", metric.getCount());
        redisTemplate.opsForHash().put(key, "sum", metric.getSum());
        redisTemplate.opsForHash().put(key, "avg", metric.getAvg());
        redisTemplate.opsForHash().put(key, "min", metric.getMin());
        redisTemplate.opsForHash().put(key, "max", metric.getMax());
        redisTemplate.opsForHash().put(key, "windowStart", metric.getWindowStart().toString());
        redisTemplate.opsForHash().put(key, "windowEnd", metric.getWindowEnd().toString());

        // Expire after 24 hours
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    private String buildKey(AggregatedMetric metric) {
        return String.format("agg:1m:%s:%s:%d",
                metric.getServiceId(),
                metric.getMetricName(),
                metric.getWindowStart().toEpochMilli()
        );
    }
}