package com.network.gateway.traffic;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.constant.IpAttackStateConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TrafficQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(TrafficQueueManager.class);

    private final ConcurrentHashMap<String, IpTrafficQueue> ipQueues = new ConcurrentHashMap<>();
    
    private final ConcurrentHashMap<Long, PushTask> pushQueue = new ConcurrentHashMap<>();
    
    private final AtomicLong taskIdGenerator = new AtomicLong(0);
    
    private int maxSampleSize = 3;
    private long flushIntervalMs = 5000;
    private int maxQueueSize = 10000;

    @Autowired
    private GatewayConfigCache configCache;

    public TrafficQueueManager() {
    }

    public void addRequest(String ip, TrafficSample sample) {
        IpTrafficQueue queue = getOrCreateQueue(ip);
        queue.addRequest(sample);
        
        logger.debug("流量已加入队列: ip={}, uri={}, method={}", 
            ip, sample.getRequestUri(), sample.getHttpMethod());
    }

    public void recordStateTransition(String ip, int fromState, int toState, String reason, int confidence) {
        IpTrafficQueue queue = getOrCreateQueue(ip);
        
        if (fromState != toState) {
            TrafficAggregateData preTransitionData = queue.toAggregateData();
            preTransitionData.setTransition(new StateTransitionDTO());
            preTransitionData.getTransition().setFromState(fromState);
            preTransitionData.getTransition().setToState(toState);
            preTransitionData.getTransition().setTransitionTime(System.currentTimeMillis());
            preTransitionData.getTransition().setReason(reason);
            preTransitionData.getTransition().setConfidence(confidence);
            
            queue.transitionTo(toState, reason, confidence);
            
            PushTask task = createPushTask(PushTaskType.STATE_TRANSITION, ip, preTransitionData);
            addToPushQueue(task);
            
            logger.info("状态转换已记录: ip={}, {} -> {}, reason={}", 
                ip, IpAttackStateConstant.getStateNameZh(fromState), 
                IpAttackStateConstant.getStateNameZh(toState), reason);
        }
    }

    private IpTrafficQueue getOrCreateQueue(String ip) {
        return ipQueues.computeIfAbsent(ip, k -> new IpTrafficQueue(k, IpAttackStateConstant.NORMAL, maxSampleSize));
    }

    public IpTrafficQueue getQueue(String ip) {
        return ipQueues.get(ip);
    }

    public void removeQueue(String ip) {
        ipQueues.remove(ip);
    }

    public int getQueueCount() {
        return ipQueues.size();
    }

    public List<String> getActiveIps() {
        return new ArrayList<>(ipQueues.keySet());
    }

    public List<IpTrafficQueue> getAllQueues() {
        return new ArrayList<>(ipQueues.values());
    }

    private PushTask createPushTask(PushTaskType type, String ip, TrafficAggregateData data) {
        long taskId = taskIdGenerator.incrementAndGet();
        PushTask task = new PushTask();
        task.setTaskId(taskId);
        task.setType(type);
        task.setIp(ip);
        task.setData(data);
        task.setCreateTime(System.currentTimeMillis());
        task.setRetryCount(0);
        return task;
    }

    private void addToPushQueue(PushTask task) {
        if (pushQueue.size() >= maxQueueSize) {
            discardOldestTask();
        }
        pushQueue.put(task.getTaskId(), task);
        logger.debug("推送任务已加入队列: taskId={}, type={}, ip={}", 
            task.getTaskId(), task.getType(), task.getIp());
    }

    private void discardOldestTask() {
        long oldestId = pushQueue.keySet().stream().min(Long::compare).orElse(0L);
        if (oldestId > 0) {
            PushTask removed = pushQueue.remove(oldestId);
            logger.warn("推送队列已满，丢弃最旧任务: taskId={}, ip={}", oldestId, 
                removed != null ? removed.getIp() : "unknown");
        }
    }

    public List<PushTask> getPendingTasks() {
        List<PushTask> tasks = new ArrayList<>(pushQueue.values());
        tasks.sort((a, b) -> Long.compare(a.getCreateTime(), b.getCreateTime()));
        return tasks;
    }

    public void removeTask(long taskId) {
        pushQueue.remove(taskId);
    }

    public void requeueTask(PushTask task) {
        task.setRetryCount(task.getRetryCount() + 1);
        task.setLastRetryTime(System.currentTimeMillis());
        pushQueue.put(task.getTaskId(), task);
        logger.debug("推送任务已重新入队: taskId={}, retryCount={}", 
            task.getTaskId(), task.getRetryCount());
    }

    public int getPendingTaskCount() {
        return pushQueue.size();
    }

    public List<TrafficAggregateData> flushPeriodic() {
        List<TrafficAggregateData> flushData = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, IpTrafficQueue> entry : ipQueues.entrySet()) {
            String ip = entry.getKey();
            IpTrafficQueue queue = entry.getValue();
            
            if (queue.getTimeSinceLastFlush() >= flushIntervalMs && queue.getTotalRequestCount() > 0) {
                TrafficAggregateData data = queue.toAggregateData();
                flushData.add(data);
                
                queue.markFlushed();
                queue.clearFlushedBuckets();
                
                logger.debug("周期性刷新: ip={}, requests={}", ip, data.getTotalRequests());
            }
        }
        
        return flushData;
    }

    public TrafficAggregateData flushIpQueue(String ip) {
        IpTrafficQueue queue = ipQueues.get(ip);
        if (queue == null || queue.getTotalRequestCount() == 0) {
            return null;
        }
        
        TrafficAggregateData data = queue.toAggregateData();
        queue.markFlushed();
        queue.clearFlushedBuckets();
        
        logger.debug("IP队列刷新: ip={}, requests={}", ip, data.getTotalRequests());
        return data;
    }

    public void cleanupExpiredQueues(long expireMs) {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, IpTrafficQueue> entry : ipQueues.entrySet()) {
            IpTrafficQueue queue = entry.getValue();
            if (queue.getTimeSinceLastFlush() > expireMs && queue.getTotalRequestCount() == 0) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String ip : toRemove) {
            ipQueues.remove(ip);
            logger.debug("清理过期队列: ip={}", ip);
        }
    }

    public String getStatistics() {
        return String.format("TrafficQueueManager{ipQueues=%d, pendingTasks=%d, totalRequests=%d}", 
            ipQueues.size(), 
            pushQueue.size(),
            ipQueues.values().stream().mapToInt(IpTrafficQueue::getTotalRequestCount).sum());
    }

    public void setMaxSampleSize(int maxSampleSize) {
        this.maxSampleSize = maxSampleSize;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }
}
