package org.backendbrilliance.streammetrics.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsQueryController {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get latest aggregate for a service
     * GET /api/metrics/latest?service=payment-service&metric=api.request.duration
     *
     * @param service
     * @param metric
     * @return
     */
    @GetMapping("/latest")
    public Map<String, Object> getLatest(@RequestParam String service, @RequestParam String metric){

        log.info("Getting latest metric for service: {}, metric: {}", service, metric);

        //Find all keys for this service+metric
        String pattern = String.format("agg:1m:%s:%s:*", service, metric);
        Set<String> keys = redisTemplate.keys(pattern);
        log.info("getLatest: keys: {}", keys);

        if(Objects.isNull(keys) || keys.isEmpty()){
            return Map.of("error", "keys not found");
        }

        //Get the most recent key (highest timestamp)
        String latestKey = keys.stream()
                .max(Comparator.comparing(this::extractTimestamp))
                .orElse(null);

        if(Objects.isNull(latestKey)){
            return Map.of("error", "No data found!");
        }

        //Fetch all fields
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(latestKey);
        log.info("getLatest: entries: {}", entries);
        return Map.of(
                "service", service,
                "metric", metric,
                "keys", keys,
                "entries", entries
        );
    }

    private Long extractTimestamp(String key) {
        //Format: agg:1m:service:metric:timestamp
        String[] parts = key.split(":");
        return Long.parseLong(parts[parts.length-1]);
    }

    /**
     * Get time-series data (last N minutes)
     * GET /api/metrics/timeseries?service=payment-service&metric=api.request.duration&minutes=10
     *
     * @param service
     * @param metric
     * @param minutes
     * @return
     */
    @GetMapping("/timeseries")
    public Map<String, Object> getTimeSeries(
            @RequestParam String service,
            @RequestParam String metric,
            @RequestParam(defaultValue = "10") String minutes){

        log.info("Getting time series for service: {}, metric: {}, minutes: {}", service, metric, minutes);

        String pattern = String.format("agg:1m:%s:%s:*", service, metric);
        Set<String> keys = redisTemplate.keys(pattern);
        log.info("getTimeSeries: keys: {}", keys);

        if(Objects.isNull(keys) || keys.isEmpty()){
            return Map.of("error", "keys not found");
        }

        int min = 0;
        try{
            min = Integer.parseInt(minutes);
        } catch(NumberFormatException e){
            log.error("Invalid minutes: {}", minutes, e);
        }

        //Get last N keys
        List<String> lastNKeys = keys.stream()
                .sorted(Comparator.comparing(this::extractTimestamp).reversed())
                .limit(min)
                .toList();
        log.info("getTimeSeries: lastNKeys: {}", lastNKeys);

        //Fetch data for each window
        List<Map<String, Object>> timeSeries = new ArrayList<>();
        lastNKeys.forEach(key -> {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", extractTimestamp(key));
            map.put("avg", entries.get("avg"));
            map.put("min", entries.get("min"));
            map.put("max", entries.get("max"));
            map.put("count", entries.get("count"));
            timeSeries.add(map);
        });
        log.info("getTimeSeries: timeSeries: {}", timeSeries);

        return Map.of(
                "service", service,
                "metric", metric,
                "windows", minutes,
                "data", timeSeries);
    }

    /**
     * Compare all services (latest window)
     * GET /api/metrics/compare?metric=api.request.duration
     *
     * @param metric
     * @return
     */
    @GetMapping("/compare")
    public Map<String, Object> compareServices(@RequestParam String metric){

        log.info("Comparing services for service: {}", metric);

        String pattern = String.format("agg:1m:*:%s:*", metric);
        Set<String> keys = redisTemplate.keys(pattern);
        log.info("compareServices: keys: {}", keys);

        if(Objects.isNull(keys) || keys.isEmpty()){
            return Map.of("error", "No data found");
        }

        //Group by Services, get latest  for each
//      DataSet Example:
//        127.0.0.1:6379> KEYS agg:1m:*:api.request.duration:*
//        1) "agg:1m:order-service:api.request.duration:1771483200000"
//        2) "agg:1m:payment-service:api.request.duration:1771483200000"
//        3) "agg:1m:user-service:api.request.duration:1771483200000"
//        4) "agg:1m:user-service:api.request.duration:1771483620000"
//        5) "agg:1m:payment-service:api.request.duration:1771483080000"
//        6) "agg:1m:user-service:api.request.duration:1771483080000"
        Map<String, String> latestByService = keys.stream()
                .collect(Collectors.groupingBy(this::extractService,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(this::extractTimestamp)),
                                opt -> opt.orElse(null))));
        log.info("compareServices: latestByService: {}", latestByService);

        //Fetch data for each service
        Map<String, Map<Object, Object>> comparison = new HashMap<>();
        latestByService.forEach((service, key)-> {
            if(Objects.nonNull(key)){
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                comparison.put(service, entries);
            }
        });
        log.info("compareServices: comparison: {}", comparison);

        return Map.of(
                "metric", metric,
                "service", comparison
        );
    }

    private String extractService(String k){
        String[] parts = k.split(":");
        return parts[2];
    }

}
