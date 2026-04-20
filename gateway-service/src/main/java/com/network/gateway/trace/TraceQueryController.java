package com.network.gateway.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inner/trace")
@RequiredArgsConstructor
public class TraceQueryController {

    private final TraceService traceService;

    @GetMapping("/{traceId}")
    public ResponseEntity<Map<String, Object>> getTraceById(@PathVariable String traceId) {
        TraceService.TraceRecord trace = traceService.getTrace(traceId);
        
        Map<String, Object> result = new HashMap<>();
        if (trace != null) {
            result.put("success", true);
            result.put("trace", trace);
        } else {
            result.put("success", false);
            result.put("message", "Trace not found: " + traceId);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ip/{ip}")
    public ResponseEntity<Map<String, Object>> getTracesByIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<TraceService.TraceRecord> traces = traceService.getTracesByIp(ip);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("ip", ip);
        result.put("count", traces.size());
        result.put("traces", traces.stream().limit(limit).toList());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentTraces(
            @RequestParam(defaultValue = "50") int limit) {
        
        List<TraceService.TraceRecord> traces = traceService.getRecentTraces(limit);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", traces.size());
        result.put("traces", traces);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> getErrorTraces(
            @RequestParam(defaultValue = "50") int limit) {
        
        List<TraceService.TraceRecord> traces = traceService.getErrorTraces(limit);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", traces.size());
        result.put("traces", traces);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/slow")
    public ResponseEntity<Map<String, Object>> getSlowTraces(
            @RequestParam(defaultValue = "1000") long thresholdMs,
            @RequestParam(defaultValue = "50") int limit) {
        
        List<TraceService.TraceRecord> traces = traceService.getSlowTraces(thresholdMs, limit);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("thresholdMs", thresholdMs);
        result.put("count", traces.size());
        result.put("traces", traces);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTraceStats() {
        TraceService.TraceStats stats = traceService.getTraceStats();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("stats", stats);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{traceId}/timeline")
    public ResponseEntity<Map<String, Object>> getTraceTimeline(@PathVariable String traceId) {
        TraceService.TraceRecord trace = traceService.getTrace(traceId);
        
        Map<String, Object> result = new HashMap<>();
        if (trace != null) {
            result.put("success", true);
            result.put("traceId", traceId);
            result.put("timeline", buildTimeline(trace));
        } else {
            result.put("success", false);
            result.put("message", "Trace not found: " + traceId);
        }
        
        return ResponseEntity.ok(result);
    }

    private List<Map<String, Object>> buildTimeline(TraceService.TraceRecord trace) {
        return trace.getSpans().stream()
            .map(span -> {
                Map<String, Object> event = new HashMap<>();
                event.put("spanId", span.getSpanId());
                event.put("parentSpanId", span.getParentSpanId());
                event.put("operation", span.getOperation());
                event.put("startTime", span.getStartTime());
                event.put("endTime", span.getEndTime());
                event.put("durationMs", span.getDurationMs());
                event.put("status", span.getStatus());
                if (span.getErrorMessage() != null) {
                    event.put("message", span.getErrorMessage());
                }
                if (span.getDetails() != null) {
                    event.put("details", span.getDetails());
                }
                return event;
            })
            .toList();
    }
}
