package com.network.gateway.trace;

import lombok.Data;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.Callable;

@Data
@Component
public class TraceContext implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String PARENT_SPAN_ID = "parentSpanId";
    private static final String IP = "ip";
    private static final String STATE = "state";
    private static final String EVENT_ID = "eventId";

    private ThreadLocal<String> traceIdHolder = new ThreadLocal<>();
    private ThreadLocal<String> spanIdHolder = new ThreadLocal<>();
    private ThreadLocal<String> parentSpanIdHolder = new ThreadLocal<>();
    private ThreadLocal<String> ipHolder = new ThreadLocal<>();
    private ThreadLocal<String> stateHolder = new ThreadLocal<>();
    private ThreadLocal<String> eventIdHolder = new ThreadLocal<>();

    public void startTrace(String ip) {
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        
        traceIdHolder.set(traceId);
        spanIdHolder.set(spanId);
        ipHolder.set(ip);
        
        MDC.put(TRACE_ID, traceId);
        MDC.put(SPAN_ID, spanId);
        MDC.put(IP, ip);
    }

    public void startTrace(String ip, String parentTraceId) {
        String spanId = generateSpanId();
        
        traceIdHolder.set(parentTraceId);
        spanIdHolder.set(spanId);
        ipHolder.set(ip);
        
        MDC.put(TRACE_ID, parentTraceId);
        MDC.put(SPAN_ID, spanId);
        MDC.put(IP, ip);
    }

    public void setState(String state) {
        stateHolder.set(state);
        MDC.put(STATE, state);
    }

    public void setEventId(String eventId) {
        eventIdHolder.set(eventId);
        MDC.put(EVENT_ID, eventId);
    }

    public void setParentSpanId(String parentSpanId) {
        parentSpanIdHolder.set(parentSpanId);
        MDC.put(PARENT_SPAN_ID, parentSpanId);
    }

    public String getTraceId() {
        return traceIdHolder.get();
    }

    public String getSpanId() {
        return spanIdHolder.get();
    }

    public String getParentSpanId() {
        return parentSpanIdHolder.get();
    }

    public String getIp() {
        return ipHolder.get();
    }

    public String getState() {
        return stateHolder.get();
    }

    public String getEventId() {
        return eventIdHolder.get();
    }

    public void endTrace() {
        traceIdHolder.remove();
        spanIdHolder.remove();
        parentSpanIdHolder.remove();
        ipHolder.remove();
        stateHolder.remove();
        eventIdHolder.remove();
        
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
        MDC.remove(PARENT_SPAN_ID);
        MDC.remove(IP);
        MDC.remove(STATE);
        MDC.remove(EVENT_ID);
    }

    public <T> Callable<T> wrapCallable(Callable<T> task) {
        String traceId = getTraceId();
        String spanId = getSpanId();
        String ip = getIp();
        String state = getState();
        String eventId = getEventId();
        
        return () -> {
            try {
                if (traceId != null) {
                    traceIdHolder.set(traceId);
                    MDC.put(TRACE_ID, traceId);
                }
                if (spanId != null) {
                    spanIdHolder.set(spanId);
                    MDC.put(SPAN_ID, spanId);
                }
                if (ip != null) {
                    ipHolder.set(ip);
                    MDC.put(IP, ip);
                }
                if (state != null) {
                    stateHolder.set(state);
                    MDC.put(STATE, state);
                }
                if (eventId != null) {
                    eventIdHolder.set(eventId);
                    MDC.put(EVENT_ID, eventId);
                }
                return task.call();
            } finally {
                endTrace();
            }
        };
    }

    public Runnable wrapRunnable(Runnable task) {
        String traceId = getTraceId();
        String spanId = getSpanId();
        String ip = getIp();
        String state = getState();
        String eventId = getEventId();
        
        return () -> {
            try {
                if (traceId != null) {
                    traceIdHolder.set(traceId);
                    MDC.put(TRACE_ID, traceId);
                }
                if (spanId != null) {
                    spanIdHolder.set(spanId);
                    MDC.put(SPAN_ID, spanId);
                }
                if (ip != null) {
                    ipHolder.set(ip);
                    MDC.put(IP, ip);
                }
                if (state != null) {
                    stateHolder.set(state);
                    MDC.put(STATE, state);
                }
                if (eventId != null) {
                    eventIdHolder.set(eventId);
                    MDC.put(EVENT_ID, eventId);
                }
                task.run();
            } finally {
                endTrace();
            }
        };
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateSpanId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public SpanInfo createSpanInfo(String operation) {
        SpanInfo info = new SpanInfo();
        info.setTraceId(getTraceId());
        info.setSpanId(getSpanId());
        info.setParentSpanId(getParentSpanId());
        info.setIp(getIp());
        info.setState(getState());
        info.setOperation(operation);
        info.setStartTime(System.currentTimeMillis());
        return info;
    }

    @Data
    public static class SpanInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String ip;
        private String state;
        private String operation;
        private long startTime;
        private long endTime;
        private long duration;
        private String status;
        private String errorMessage;

        public void end() {
            this.endTime = System.currentTimeMillis();
            this.duration = endTime - startTime;
            this.status = "SUCCESS";
        }

        public void endWithError(String errorMessage) {
            this.endTime = System.currentTimeMillis();
            this.duration = endTime - startTime;
            this.status = "ERROR";
            this.errorMessage = errorMessage;
        }
    }
}
