package com.network.gateway.trace;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final TraceContext traceContext;
    
    private final Map<String, TraceRecord> traceRecords = new ConcurrentHashMap<>();
    private final Map<String, List<SpanRecord>> spanRecords = new ConcurrentHashMap<>();
    private final AtomicInteger traceCounter = new AtomicInteger(0);
    
    private static final int MAX_TRACE_RECORDS = 10000;

    public String startTrace(String ip, String uri, String method) {
        String traceId = traceContext.getTraceId();
        if (traceId == null) {
            traceId = generateTraceId();
        }
        
        TraceRecord record = new TraceRecord();
        record.setTraceId(traceId);
        record.setIp(ip);
        record.setUri(uri);
        record.setMethod(method);
        record.setStartTime(Instant.now());
        record.setStatus("STARTED");
        
        traceRecords.put(traceId, record);
        spanRecords.put(traceId, new ArrayList<>());
        
        traceCounter.incrementAndGet();
        
        if (traceRecords.size() > MAX_TRACE_RECORDS) {
            cleanupOldRecords();
        }
        
        log.debug("Trace started: traceId={}, ip={}, uri={}", traceId, ip, uri);
        return traceId;
    }

    public void addSpan(String traceId, String spanId, String operation, String parentSpanId) {
        List<SpanRecord> spans = spanRecords.get(traceId);
        if (spans == null) {
            log.warn("No trace found for span: traceId={}", traceId);
            return;
        }
        
        SpanRecord span = new SpanRecord();
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setOperation(operation);
        span.setStartTime(Instant.now());
        span.setStatus("STARTED");
        
        spans.add(span);
        log.debug("Span added: traceId={}, spanId={}, operation={}", traceId, spanId, operation);
    }

    public void endSpan(String traceId, String spanId, String status, String errorMessage) {
        List<SpanRecord> spans = spanRecords.get(traceId);
        if (spans == null) {
            return;
        }
        
        for (SpanRecord span : spans) {
            if (span.getSpanId().equals(spanId)) {
                span.setEndTime(Instant.now());
                span.setDurationMs(calculateDuration(span.getStartTime(), span.getEndTime()));
                span.setStatus(status);
                if (errorMessage != null) {
                    span.setErrorMessage(errorMessage);
                }
                log.debug("Span ended: traceId={}, spanId={}, status={}, duration={}ms", 
                    traceId, spanId, status, span.getDurationMs());
                break;
            }
        }
    }

    public void endTrace(String traceId, String status, int statusCode, long processingTime) {
        TraceRecord record = traceRecords.get(traceId);
        if (record == null) {
            log.warn("No trace found to end: traceId={}", traceId);
            return;
        }
        
        record.setEndTime(Instant.now());
        record.setDurationMs(processingTime);
        record.setStatus(status);
        record.setStatusCode(statusCode);
        
        log.debug("Trace ended: traceId={}, status={}, duration={}ms", traceId, status, processingTime);
    }

    public void addEvent(String traceId, String eventType, String description, Map<String, Object> details) {
        List<SpanRecord> spans = spanRecords.get(traceId);
        if (spans == null) {
            return;
        }
        
        SpanRecord eventSpan = new SpanRecord();
        eventSpan.setSpanId(generateSpanId());
        eventSpan.setOperation(eventType);
        eventSpan.setStartTime(Instant.now());
        eventSpan.setEndTime(Instant.now());
        eventSpan.setDurationMs(0L);
        eventSpan.setStatus("EVENT");
        eventSpan.setErrorMessage(description);
        if (details != null) {
            eventSpan.setDetails(details);
        }
        
        spans.add(eventSpan);
        log.debug("Event added: traceId={}, type={}, description={}", traceId, eventType, description);
    }

    public TraceRecord getTrace(String traceId) {
        TraceRecord record = traceRecords.get(traceId);
        if (record != null) {
            List<SpanRecord> spans = spanRecords.get(traceId);
            record.setSpans(spans != null ? new ArrayList<>(spans) : new ArrayList<>());
        }
        return record;
    }

    public List<TraceRecord> getTracesByIp(String ip) {
        return traceRecords.values().stream()
            .filter(r -> ip.equals(r.getIp()))
            .toList();
    }

    public List<TraceRecord> getRecentTraces(int limit) {
        return traceRecords.values().stream()
            .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
            .limit(limit)
            .toList();
    }

    public List<TraceRecord> getErrorTraces(int limit) {
        return traceRecords.values().stream()
            .filter(r -> "ERROR".equals(r.getStatus()) || r.getStatusCode() >= 400)
            .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
            .limit(limit)
            .toList();
    }

    public List<TraceRecord> getSlowTraces(long thresholdMs, int limit) {
        return traceRecords.values().stream()
            .filter(r -> r.getDurationMs() != null && r.getDurationMs() > thresholdMs)
            .sorted((a, b) -> Long.compare(b.getDurationMs(), a.getDurationMs()))
            .limit(limit)
            .toList();
    }

    public TraceStats getTraceStats() {
        TraceStats stats = new TraceStats();
        stats.setTotalTraces(traceRecords.size());
        
        long successCount = traceRecords.values().stream()
            .filter(r -> "SUCCESS".equals(r.getStatus()))
            .count();
        long errorCount = traceRecords.values().stream()
            .filter(r -> "ERROR".equals(r.getStatus()))
            .count();
        
        stats.setSuccessCount(successCount);
        stats.setErrorCount(errorCount);
        
        double avgDuration = traceRecords.values().stream()
            .filter(r -> r.getDurationMs() != null)
            .mapToLong(TraceRecord::getDurationMs)
            .average()
            .orElse(0);
        stats.setAvgDurationMs(avgDuration);
        
        long maxDuration = traceRecords.values().stream()
            .filter(r -> r.getDurationMs() != null)
            .mapToLong(TraceRecord::getDurationMs)
            .max()
            .orElse(0);
        stats.setMaxDurationMs(maxDuration);
        
        return stats;
    }

    public void deleteTrace(String traceId) {
        traceRecords.remove(traceId);
        spanRecords.remove(traceId);
        log.debug("Trace deleted: traceId={}", traceId);
    }

    public void cleanupOldRecords() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        
        traceRecords.entrySet().removeIf(entry -> {
            if (entry.getValue().getEndTime() != null && 
                entry.getValue().getEndTime().isBefore(cutoff)) {
                spanRecords.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        log.debug("Cleaned up old trace records, current count: {}", traceRecords.size());
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateSpanId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private long calculateDuration(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0;
        }
        return java.time.Duration.between(start, end).toMillis();
    }

    @Data
    public static class TraceRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String traceId;
        private String ip;
        private String uri;
        private String method;
        private Instant startTime;
        private Instant endTime;
        private Long durationMs;
        private String status;
        private Integer statusCode;
        private List<SpanRecord> spans = new ArrayList<>();
    }

    @Data
    public static class SpanRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String spanId;
        private String parentSpanId;
        private String operation;
        private Instant startTime;
        private Instant endTime;
        private Long durationMs;
        private String status;
        private String errorMessage;
        private Map<String, Object> details;
    }

    @Data
    public static class TraceStats implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int totalTraces;
        private long successCount;
        private long errorCount;
        private double avgDurationMs;
        private long maxDurationMs;
    }
}
