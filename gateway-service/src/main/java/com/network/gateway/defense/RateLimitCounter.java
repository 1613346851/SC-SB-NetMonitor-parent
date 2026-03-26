package com.network.gateway.defense;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class RateLimitCounter implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String ip;
    private long windowStart;
    private long windowEnd;
    private AtomicInteger count = new AtomicInteger(0);
    private long firstLimitTime;
    private long lastLimitTime;

    public RateLimitCounter() {
    }

    public RateLimitCounter(String ip, long windowStart) {
        this.ip = ip;
        this.windowStart = windowStart;
        this.windowEnd = windowStart + 60000;
        this.firstLimitTime = System.currentTimeMillis();
        this.lastLimitTime = this.firstLimitTime;
    }

    public int increment() {
        this.lastLimitTime = System.currentTimeMillis();
        return count.incrementAndGet();
    }

    public int getCount() {
        return count.get();
    }

    public boolean isInWindow(long timestamp) {
        return timestamp >= windowStart && timestamp < windowEnd;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= windowEnd;
    }

    public String getTimeWindow() {
        LocalDateTime start = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(firstLimitTime), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(lastLimitTime), ZoneId.systemDefault());
        return start.format(FORMATTER) + " ~ " + end.format(FORMATTER);
    }

    public long getDuration() {
        return lastLimitTime - firstLimitTime;
    }

    public double getAverageRate() {
        long duration = getDuration();
        if (duration <= 0) {
            return count.get();
        }
        return count.get() * 1000.0 / duration;
    }

    @Override
    public String toString() {
        return String.format("RateLimitCounter{ip=%s, count=%d, window=%s}", 
            ip, count.get(), getTimeWindow());
    }
}
