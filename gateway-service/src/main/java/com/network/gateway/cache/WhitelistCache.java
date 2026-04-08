package com.network.gateway.cache;

import com.network.gateway.dto.WhitelistDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 检测白名单缓存
 * 存储从监测服务同步的白名单规则
 * 支持路径和请求头白名单
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
public class WhitelistCache {

    private static final Logger logger = LoggerFactory.getLogger(WhitelistCache.class);

    public static final String TYPE_PATH = "PATH";
    public static final String TYPE_HEADER = "HEADER";
    public static final String TYPE_IP = "IP";

    private final List<WhitelistDTO> pathWhitelists = new ArrayList<>();

    private final Set<String> headerWhitelist = ConcurrentHashMap.newKeySet();

    private final Set<String> ipWhitelist = ConcurrentHashMap.newKeySet();

    private final Map<String, Pattern> pathPatterns = new ConcurrentHashMap<>();

    private volatile long lastSyncTime = 0;

    public synchronized void addWhitelist(WhitelistDTO whitelist) {
        if (whitelist == null || whitelist.getWhitelistType() == null || whitelist.getWhitelistValue() == null) {
            logger.warn("尝试添加空白名单或无效白名单");
            return;
        }

        if (whitelist.getEnabled() == null || whitelist.getEnabled() != 1) {
            logger.debug("白名单未启用，跳过: type={}, value={}", whitelist.getWhitelistType(), whitelist.getWhitelistValue());
            return;
        }

        String type = whitelist.getWhitelistType().toUpperCase();
        String value = whitelist.getWhitelistValue();

        switch (type) {
            case TYPE_PATH:
                addPathWhitelist(whitelist);
                break;
            case TYPE_HEADER:
                headerWhitelist.add(value.toLowerCase());
                logger.info("添加请求头白名单: {}", value);
                break;
            case TYPE_IP:
                ipWhitelist.add(value);
                logger.info("添加IP白名单: {}", value);
                break;
            default:
                logger.warn("未知的白名单类型: {}", type);
        }

        lastSyncTime = System.currentTimeMillis();
    }

    private void addPathWhitelist(WhitelistDTO whitelist) {
        String value = whitelist.getWhitelistValue();

        pathWhitelists.removeIf(w -> w.getWhitelistValue().equals(value));
        pathWhitelists.add(whitelist);
        pathWhitelists.sort(Comparator.comparingInt(w -> w.getPriority() != null ? w.getPriority() : 100));

        String regex = convertPathToRegex(value);
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            pathPatterns.put(value, pattern);
            logger.info("添加路径白名单: {} (regex: {})", value, regex);
        } catch (PatternSyntaxException e) {
            logger.error("路径白名单正则编译失败: value={}, error={}", value, e.getMessage());
        }
    }

    private String convertPathToRegex(String path) {
        String regex = path.replace(".", "\\.");
        regex = regex.replace("*", ".*");
        if (!regex.startsWith("^")) {
            regex = "^" + regex;
        }
        if (!regex.endsWith(".*") && !regex.endsWith("$")) {
            regex = regex + "$";
        }
        return regex;
    }

    public synchronized void syncWhitelists(List<WhitelistDTO> whitelists) {
        if (whitelists == null) {
            logger.warn("同步白名单列表为空");
            return;
        }

        clear();

        for (WhitelistDTO whitelist : whitelists) {
            addWhitelist(whitelist);
        }

        lastSyncTime = System.currentTimeMillis();
        logger.info("批量同步白名单完成，共{}条，路径白名单{}条，请求头白名单{}条，IP白名单{}条",
                whitelists.size(), pathWhitelists.size(), headerWhitelist.size(), ipWhitelist.size());
    }

    public void clear() {
        pathWhitelists.clear();
        headerWhitelist.clear();
        ipWhitelist.clear();
        pathPatterns.clear();
        logger.info("白名单缓存已清空");
    }

    @PreDestroy
    public void destroy() {
        clear();
        logger.info("白名单缓存已销毁");
    }

    public boolean isPathWhitelisted(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Pattern> entry : pathPatterns.entrySet()) {
            if (entry.getValue().matcher(path).find()) {
                logger.debug("路径匹配白名单: path={}, pattern={}", path, entry.getKey());
                return true;
            }
        }

        return false;
    }

    public boolean isHeaderWhitelisted(String headerName) {
        if (headerName == null || headerName.isEmpty()) {
            return false;
        }

        return headerWhitelist.contains(headerName.toLowerCase());
    }

    public boolean isIpWhitelisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        return ipWhitelist.contains(ip);
    }

    public List<WhitelistDTO> getPathWhitelists() {
        return new ArrayList<>(pathWhitelists);
    }

    public Set<String> getHeaderWhitelist() {
        return new HashSet<>(headerWhitelist);
    }

    public Set<String> getIpWhitelist() {
        return new HashSet<>(ipWhitelist);
    }

    public int size() {
        return pathWhitelists.size() + headerWhitelist.size() + ipWhitelist.size();
    }

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public String getStats() {
        return String.format("白名单缓存统计 - 路径白名单:%d, 请求头白名单:%d, IP白名单:%d, 最后同步时间:%d",
                pathWhitelists.size(), headerWhitelist.size(), ipWhitelist.size(), lastSyncTime);
    }
}
