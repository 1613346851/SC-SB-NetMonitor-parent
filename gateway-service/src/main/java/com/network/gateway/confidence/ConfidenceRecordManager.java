package com.network.gateway.confidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConfidenceRecordManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceRecordManager.class);

    private final Map<String, Deque<ConfidenceRecord>> recordMap = new ConcurrentHashMap<>();
    private int maxRecordsPerIp = 100;
    private int maxTotalRecords = 10000;

    public void record(ConfidenceRecord record) {
        if (record == null || record.getIp() == null) {
            return;
        }

        String ip = record.getIp();
        Deque<ConfidenceRecord> records = recordMap.computeIfAbsent(ip, k -> new LinkedList<>());

        synchronized (records) {
            records.addLast(record);

            while (records.size() > maxRecordsPerIp) {
                records.removeFirst();
            }
        }

        cleanupIfNecessary();

        logger.debug("记录置信度变化: {}", record);
    }

    public void record(ConfidenceResult result, int state, String reason) {
        ConfidenceRecord record = ConfidenceRecord.fromResult(result, state, reason);
        record(record);
    }

    public List<ConfidenceRecord> getRecords(String ip) {
        Deque<ConfidenceRecord> records = recordMap.get(ip);
        if (records == null) {
            return Collections.emptyList();
        }
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public List<ConfidenceRecord> getRecentRecords(String ip, int count) {
        List<ConfidenceRecord> allRecords = getRecords(ip);
        if (allRecords.size() <= count) {
            return allRecords;
        }
        return allRecords.subList(allRecords.size() - count, allRecords.size());
    }

    public ConfidenceRecord getLatestRecord(String ip) {
        Deque<ConfidenceRecord> records = recordMap.get(ip);
        if (records == null || records.isEmpty()) {
            return null;
        }
        synchronized (records) {
            return records.peekLast();
        }
    }

    public void clearRecords(String ip) {
        Deque<ConfidenceRecord> removed = recordMap.remove(ip);
        if (removed != null) {
            logger.debug("清除IP置信度记录: ip={}, count={}", ip, removed.size());
        }
    }

    public void clearAllRecords() {
        int total = getTotalRecordCount();
        recordMap.clear();
        logger.info("清除所有置信度记录，共{}条", total);
    }

    public int getRecordCount(String ip) {
        Deque<ConfidenceRecord> records = recordMap.get(ip);
        if (records == null) {
            return 0;
        }
        synchronized (records) {
            return records.size();
        }
    }

    public int getTotalRecordCount() {
        int total = 0;
        for (Deque<ConfidenceRecord> records : recordMap.values()) {
            synchronized (records) {
                total += records.size();
            }
        }
        return total;
    }

    public int getIpCount() {
        return recordMap.size();
    }

    private void cleanupIfNecessary() {
        int total = getTotalRecordCount();
        if (total > maxTotalRecords) {
            int toRemove = total - maxTotalRecords;
            int removed = 0;

            for (Map.Entry<String, Deque<ConfidenceRecord>> entry : recordMap.entrySet()) {
                if (removed >= toRemove) {
                    break;
                }
                Deque<ConfidenceRecord> records = entry.getValue();
                synchronized (records) {
                    while (!records.isEmpty() && removed < toRemove) {
                        records.removeFirst();
                        removed++;
                    }
                }
            }

            logger.debug("清理置信度记录: removed={}, remaining={}", removed, getTotalRecordCount());
        }
    }

    public void setMaxRecordsPerIp(int maxRecordsPerIp) {
        this.maxRecordsPerIp = maxRecordsPerIp;
    }

    public void setMaxTotalRecords(int maxTotalRecords) {
        this.maxTotalRecords = maxTotalRecords;
    }

    public String getStats() {
        return String.format("置信度记录统计 - IP数:%d, 总记录数:%d, 单IP最大:%d", 
            getIpCount(), getTotalRecordCount(), maxRecordsPerIp);
    }
}
