package org.backendbrilliance.streammetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backendbrilliance.streammetrics.client.TestMetricProducer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@Slf4j
@RequiredArgsConstructor
public class MetricProducerController {

    private final TestMetricProducer testMetricProducer;

    @GetMapping("/produce")
    public void produceEvents(
            @RequestParam(name = "events", required = false) String events,
            @RequestParam(name = "invalid", required = false) boolean sendInvalid) throws Exception {
        try {
            Long total = Objects.nonNull(events) ? Long.parseLong(events) : 0L;
            testMetricProducer.produceTestEvents(total, sendInvalid);
        } catch(NumberFormatException e) {
            log.error("Invalid events received: {}", events, e);
        }
    }

}
