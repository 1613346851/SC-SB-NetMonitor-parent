package com.network.gateway.trace;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LogQueryService {

    private static final String LOG_FILE_PATH = "logs/gateway-service.log";
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[(.+?)\\] (\\w+) (.+?) - (.+)"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public LogQueryResult queryByTraceId(String traceId, int limit) {
        LogQueryResult result = new LogQueryResult();
        result.setTraceId(traceId);
        result.setQueryTime(Instant.now());
        
        List<LogEntry> entries = new ArrayList<>();
        
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                result.setSuccess(false);
                result.setMessage("Log file not found: " + LOG_FILE_PATH);
                return result;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null && count < limit) {
                    LogEntry entry = parseLogLine(line);
                    if (entry != null && containsTraceId(entry, traceId)) {
                        entries.add(entry);
                        count++;
                    }
                }
            }
            
            result.setSuccess(true);
            result.setEntries(entries);
            result.setTotalCount(entries.size());
            
        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("Error reading log file: " + e.getMessage());
        }
        
        return result;
    }

    public LogQueryResult queryByIp(String ip, int limit) {
        LogQueryResult result = new LogQueryResult();
        result.setQueryTime(Instant.now());
        
        List<LogEntry> entries = new ArrayList<>();
        
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                result.setSuccess(false);
                result.setMessage("Log file not found: " + LOG_FILE_PATH);
                return result;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null && count < limit) {
                    LogEntry entry = parseLogLine(line);
                    if (entry != null && (ip.equals(entry.getIp()) || entry.getMessage().contains(ip))) {
                        entries.add(entry);
                        count++;
                    }
                }
            }
            
            result.setSuccess(true);
            result.setEntries(entries);
            result.setTotalCount(entries.size());
            
        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("Error reading log file: " + e.getMessage());
        }
        
        return result;
    }

    public LogQueryResult queryByTimeRange(Instant startTime, Instant endTime, int limit) {
        LogQueryResult result = new LogQueryResult();
        result.setQueryTime(Instant.now());
        
        List<LogEntry> entries = new ArrayList<>();
        
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                result.setSuccess(false);
                result.setMessage("Log file not found: " + LOG_FILE_PATH);
                return result;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null && count < limit) {
                    LogEntry entry = parseLogLine(line);
                    if (entry != null && isInTimeRange(entry, startTime, endTime)) {
                        entries.add(entry);
                        count++;
                    }
                }
            }
            
            result.setSuccess(true);
            result.setEntries(entries);
            result.setTotalCount(entries.size());
            
        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("Error reading log file: " + e.getMessage());
        }
        
        return result;
    }

    public LogQueryResult queryByLevel(String level, int limit) {
        LogQueryResult result = new LogQueryResult();
        result.setQueryTime(Instant.now());
        
        List<LogEntry> entries = new ArrayList<>();
        
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                result.setSuccess(false);
                result.setMessage("Log file not found: " + LOG_FILE_PATH);
                return result;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null && count < limit) {
                    LogEntry entry = parseLogLine(line);
                    if (entry != null && level.equalsIgnoreCase(entry.getLevel())) {
                        entries.add(entry);
                        count++;
                    }
                }
            }
            
            result.setSuccess(true);
            result.setEntries(entries);
            result.setTotalCount(entries.size());
            
        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("Error reading log file: " + e.getMessage());
        }
        
        return result;
    }

    public LogQueryResult queryByKeyword(String keyword, int limit) {
        LogQueryResult result = new LogQueryResult();
        result.setQueryTime(Instant.now());
        
        List<LogEntry> entries = new ArrayList<>();
        
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                result.setSuccess(false);
                result.setMessage("Log file not found: " + LOG_FILE_PATH);
                return result;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null && count < limit) {
                    LogEntry entry = parseLogLine(line);
                    if (entry != null && entry.getRawLine().toLowerCase().contains(keyword.toLowerCase())) {
                        entries.add(entry);
                        count++;
                    }
                }
            }
            
            result.setSuccess(true);
            result.setEntries(entries);
            result.setTotalCount(entries.size());
            
        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("Error reading log file: " + e.getMessage());
        }
        
        return result;
    }

    public LogStats getLogStats() {
        LogStats stats = new LogStats();
        
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                return stats;
            }
            
            long fileSize = Files.size(logPath);
            stats.setFileSizeBytes(fileSize);
            
            long lineCount = Files.lines(logPath).count();
            stats.setTotalLines(lineCount);
            
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LogEntry entry = parseLogLine(line);
                    if (entry != null) {
                        switch (entry.getLevel().toUpperCase()) {
                            case "ERROR" -> stats.incrementErrorCount();
                            case "WARN" -> stats.incrementWarnCount();
                            case "INFO" -> stats.incrementInfoCount();
                            case "DEBUG" -> stats.incrementDebugCount();
                        }
                    }
                }
            }
            
        } catch (IOException e) {
            log.error("Error getting log stats: {}", e.getMessage());
        }
        
        return stats;
    }

    private LogEntry parseLogLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        
        if (matcher.matches()) {
            LogEntry entry = new LogEntry();
            String timestampStr = matcher.group(1);
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, DATE_FORMATTER);
                entry.setTimestamp(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception e) {
                entry.setTimestamp(Instant.now());
            }
            
            entry.setThread(matcher.group(2));
            entry.setLevel(matcher.group(3));
            entry.setLogger(matcher.group(4));
            entry.setMessage(matcher.group(5));
            entry.setRawLine(line);
            
            return entry;
        }
        
        LogEntry entry = new LogEntry();
        entry.setRawLine(line);
        entry.setMessage(line);
        entry.setTimestamp(Instant.now());
        entry.setLevel("UNKNOWN");
        
        return entry;
    }

    private boolean containsTraceId(LogEntry entry, String traceId) {
        if (entry.getMessage() != null && entry.getMessage().contains(traceId)) {
            return true;
        }
        if (entry.getTraceId() != null && entry.getTraceId().equals(traceId)) {
            return true;
        }
        return false;
    }

    private boolean isInTimeRange(LogEntry entry, Instant startTime, Instant endTime) {
        if (entry.getTimestamp() == null) {
            return false;
        }
        return !entry.getTimestamp().isBefore(startTime) && !entry.getTimestamp().isAfter(endTime);
    }

    @Data
    public static class LogEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private Instant timestamp;
        private String thread;
        private String level;
        private String logger;
        private String message;
        private String rawLine;
        private String traceId;
        private String ip;
    }

    @Data
    public static class LogQueryResult implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private boolean success;
        private String message;
        private String traceId;
        private Instant queryTime;
        private List<LogEntry> entries = new ArrayList<>();
        private int totalCount;
    }

    @Data
    public static class LogStats implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private long fileSizeBytes;
        private long totalLines;
        private long errorCount;
        private long warnCount;
        private long infoCount;
        private long debugCount;
        
        public void incrementErrorCount() { errorCount++; }
        public void incrementWarnCount() { warnCount++; }
        public void incrementInfoCount() { infoCount++; }
        public void incrementDebugCount() { debugCount++; }
    }
}
