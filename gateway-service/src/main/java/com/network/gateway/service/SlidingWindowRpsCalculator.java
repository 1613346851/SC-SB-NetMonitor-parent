package com.network.gateway.service;

import com.network.gateway.cache.GatewayConfigCache;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SlidingWindowRpsCalculator {

    private final GatewayConfigCache configCache;

    public SlidingWindowRpsCalculator(GatewayConfigCache configCache) {
        this.configCache = configCache;
    }

    public SlidingWindowState createWindowState() {
        long windowMs = configCache.getStateNormalToSuspiciousWindowMs();
        long stepMs = configCache.getSlidingWindowStepMs();
        return new SlidingWindowState(windowMs, stepMs);
    }

    public static class SlidingWindowState {
        private final long windowMs;
        private final long stepMs;
        private final ConcurrentLinkedDeque<TimeSlot> slots;
        private final AtomicLong totalCount;
        private volatile long lastCleanupTime;

        public SlidingWindowState(long windowMs, long stepMs) {
            this.windowMs = windowMs;
            this.stepMs = stepMs;
            this.slots = new ConcurrentLinkedDeque<>();
            this.totalCount = new AtomicLong(0);
            this.lastCleanupTime = System.currentTimeMillis();
        }

        public void recordRequest() {
            long now = System.currentTimeMillis();
            long slotStart = (now / stepMs) * stepMs;
            
            cleanupOldSlots(now);
            
            TimeSlot currentSlot = findOrCreateSlot(slotStart);
            currentSlot.increment();
            totalCount.incrementAndGet();
        }

        public void recordRequest(int count) {
            long now = System.currentTimeMillis();
            long slotStart = (now / stepMs) * stepMs;
            
            cleanupOldSlots(now);
            
            TimeSlot currentSlot = findOrCreateSlot(slotStart);
            currentSlot.add(count);
            totalCount.addAndGet(count);
        }

        public double calculateRps() {
            long now = System.currentTimeMillis();
            cleanupOldSlots(now);
            
            long windowStart = now - windowMs;
            long countInWindow = 0;
            
            for (TimeSlot slot : slots) {
                if (slot.getStartTime() >= windowStart) {
                    countInWindow += slot.getCount();
                }
            }
            
            double windowSeconds = windowMs / 1000.0;
            return countInWindow / windowSeconds;
        }

        public long getCountInWindow() {
            long now = System.currentTimeMillis();
            cleanupOldSlots(now);
            
            long windowStart = now - windowMs;
            long countInWindow = 0;
            
            for (TimeSlot slot : slots) {
                if (slot.getStartTime() >= windowStart) {
                    countInWindow += slot.getCount();
                }
            }
            
            return countInWindow;
        }

        public double calculateRpsForWindow(long customWindowMs) {
            long now = System.currentTimeMillis();
            cleanupOldSlots(now);
            
            long windowStart = now - customWindowMs;
            long countInWindow = 0;
            
            for (TimeSlot slot : slots) {
                if (slot.getStartTime() >= windowStart) {
                    countInWindow += slot.getCount();
                }
            }
            
            double windowSeconds = customWindowMs / 1000.0;
            return countInWindow / windowSeconds;
        }

        private TimeSlot findOrCreateSlot(long slotStart) {
            for (TimeSlot slot : slots) {
                if (slot.getStartTime() == slotStart) {
                    return slot;
                }
            }
            
            TimeSlot newSlot = new TimeSlot(slotStart);
            slots.addLast(newSlot);
            return newSlot;
        }

        private void cleanupOldSlots(long now) {
            long windowStart = now - windowMs - stepMs;
            
            while (!slots.isEmpty()) {
                TimeSlot oldest = slots.peekFirst();
                if (oldest != null && oldest.getStartTime() < windowStart) {
                    slots.pollFirst();
                } else {
                    break;
                }
            }
            
            lastCleanupTime = now;
        }

        public void reset() {
            slots.clear();
            totalCount.set(0);
        }

        public int getSlotCount() {
            return slots.size();
        }

        public long getTotalCount() {
            return totalCount.get();
        }
    }

    private static class TimeSlot {
        private final long startTime;
        private final AtomicLong count;

        public TimeSlot(long startTime) {
            this.startTime = startTime;
            this.count = new AtomicLong(0);
        }

        public long getStartTime() {
            return startTime;
        }

        public long getCount() {
            return count.get();
        }

        public void increment() {
            count.incrementAndGet();
        }

        public void add(int delta) {
            count.addAndGet(delta);
        }
    }
}
