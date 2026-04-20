package com.network.gateway.service;

import com.network.gateway.cache.GatewayConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DistributedAttackDetector {

    private static final Logger logger = LoggerFactory.getLogger(DistributedAttackDetector.class);

    private final GatewayConfigCache configCache;
    private final Map<String, NetworkSegmentTracker> networkSegmentMap;
    private final Map<String, String> ipToNetworkMap;

    @Autowired
    public DistributedAttackDetector(GatewayConfigCache configCache) {
        this.configCache = configCache;
        this.networkSegmentMap = new ConcurrentHashMap<>();
        this.ipToNetworkMap = new ConcurrentHashMap<>();
    }

    public DistributedAttackResult detectDistributedAttack(String ip) {
        int networkMask = configCache.getStateGlobalAttackNetworkMask();
        int relatedIpThreshold = configCache.getStateGlobalAttackRelatedIpThreshold();
        
        String networkSegment = getNetworkSegment(ip, networkMask);
        
        NetworkSegmentTracker tracker = networkSegmentMap.computeIfAbsent(networkSegment,
            k -> new NetworkSegmentTracker(networkSegment, relatedIpThreshold));
        
        tracker.addIp(ip);
        ipToNetworkMap.put(ip, networkSegment);
        
        return tracker.checkDistributedAttack();
    }

    public void recordAttack(String ip) {
        int networkMask = configCache.getStateGlobalAttackNetworkMask();
        String networkSegment = getNetworkSegment(ip, networkMask);
        
        NetworkSegmentTracker tracker = networkSegmentMap.computeIfAbsent(networkSegment,
            k -> new NetworkSegmentTracker(networkSegment, 
                configCache.getStateGlobalAttackRelatedIpThreshold()));
        
        tracker.addIp(ip);
        tracker.recordAttack(ip);
        ipToNetworkMap.put(ip, networkSegment);
    }

    public void clearIp(String ip) {
        String networkSegment = ipToNetworkMap.remove(ip);
        if (networkSegment != null) {
            NetworkSegmentTracker tracker = networkSegmentMap.get(networkSegment);
            if (tracker != null) {
                tracker.removeIp(ip);
                if (tracker.getIpCount() == 0) {
                    networkSegmentMap.remove(networkSegment);
                }
            }
        }
    }

    public Set<String> getRelatedIps(String ip) {
        int networkMask = configCache.getStateGlobalAttackNetworkMask();
        String networkSegment = getNetworkSegment(ip, networkMask);
        
        NetworkSegmentTracker tracker = networkSegmentMap.get(networkSegment);
        if (tracker != null) {
            return new HashSet<>(tracker.getAttackIps());
        }
        return new HashSet<>();
    }

    public int getRelatedIpCount(String ip) {
        int networkMask = configCache.getStateGlobalAttackNetworkMask();
        String networkSegment = getNetworkSegment(ip, networkMask);
        
        NetworkSegmentTracker tracker = networkSegmentMap.get(networkSegment);
        return tracker != null ? tracker.getAttackIpCount() : 0;
    }

    private String getNetworkSegment(String ip, int maskBits) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return ip;
            }
            
            int[] octets = new int[4];
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
            }
            
            long ipValue = ((long) octets[0] << 24) | 
                          ((long) octets[1] << 16) | 
                          ((long) octets[2] << 8) | 
                          octets[3];
            
            long mask = 0xFFFFFFFFL << (32 - maskBits);
            long networkValue = ipValue & mask;
            
            int n1 = (int) ((networkValue >> 24) & 0xFF);
            int n2 = (int) ((networkValue >> 16) & 0xFF);
            int n3 = (int) ((networkValue >> 8) & 0xFF);
            int n4 = (int) (networkValue & 0xFF);
            
            return n1 + "." + n2 + "." + n3 + "." + n4 + "/" + maskBits;
        } catch (Exception e) {
            logger.warn("解析IP地址失败: {}", ip, e);
            return ip;
        }
    }

    public static class NetworkSegmentTracker {
        private final String networkSegment;
        private final int relatedIpThreshold;
        private final Set<String> allIps;
        private final Set<String> attackIps;
        private long firstAttackTime;
        private long lastAttackTime;

        public NetworkSegmentTracker(String networkSegment, int relatedIpThreshold) {
            this.networkSegment = networkSegment;
            this.relatedIpThreshold = relatedIpThreshold;
            this.allIps = ConcurrentHashMap.newKeySet();
            this.attackIps = ConcurrentHashMap.newKeySet();
            this.firstAttackTime = 0;
            this.lastAttackTime = 0;
        }

        public void addIp(String ip) {
            allIps.add(ip);
        }

        public void removeIp(String ip) {
            allIps.remove(ip);
            attackIps.remove(ip);
        }

        public void recordAttack(String ip) {
            long now = System.currentTimeMillis();
            if (firstAttackTime == 0) {
                firstAttackTime = now;
            }
            lastAttackTime = now;
            attackIps.add(ip);
        }

        public DistributedAttackResult checkDistributedAttack() {
            DistributedAttackResult result = new DistributedAttackResult();
            result.setNetworkSegment(networkSegment);
            result.setRelatedIpCount(attackIps.size());
            result.setThreshold(relatedIpThreshold);
            
            if (attackIps.size() >= relatedIpThreshold) {
                result.setDistributedAttack(true);
                result.setReason(String.format("检测到分布式攻击: 网段=%s, 攻击IP数=%d, 阈值=%d",
                    networkSegment, attackIps.size(), relatedIpThreshold));
            } else {
                result.setDistributedAttack(false);
                result.setReason("未达到分布式攻击阈值");
            }
            
            return result;
        }

        public int getIpCount() {
            return allIps.size();
        }

        public int getAttackIpCount() {
            return attackIps.size();
        }

        public Set<String> getAttackIps() {
            return new HashSet<>(attackIps);
        }

        public long getAttackDuration() {
            if (firstAttackTime == 0) {
                return 0;
            }
            return lastAttackTime - firstAttackTime;
        }
    }

    public static class DistributedAttackResult {
        private boolean isDistributedAttack;
        private String reason;
        private String networkSegment;
        private int relatedIpCount;
        private int threshold;

        public boolean isDistributedAttack() {
            return isDistributedAttack;
        }

        public void setDistributedAttack(boolean distributedAttack) {
            isDistributedAttack = distributedAttack;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getNetworkSegment() {
            return networkSegment;
        }

        public void setNetworkSegment(String networkSegment) {
            this.networkSegment = networkSegment;
        }

        public int getRelatedIpCount() {
            return relatedIpCount;
        }

        public void setRelatedIpCount(int relatedIpCount) {
            this.relatedIpCount = relatedIpCount;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }
    }
}
