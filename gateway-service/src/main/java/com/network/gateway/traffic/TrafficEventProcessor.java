package com.network.gateway.traffic;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.task.PeriodPushTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TrafficEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TrafficEventProcessor.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "traffic-event-processor");
            t.setDaemon(true);
            return t;
        }
    );

    private ScheduledFuture<?> flushTask;
    
    private static final long IDLE_TIMEOUT_MS = 60000;

    @Autowired
    private TrafficQueueManager queueManager;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    @Autowired
    private PushRetryQueue retryQueue;

    @Autowired
    private PushDegradationHandler degradationHandler;

    @Autowired
    private GatewayConfigCache configCache;

    @Autowired
    private PeriodPushTask periodPushTask;

    @Autowired
    private IpAttackStateCache ipAttackStateCache;

    @Autowired
    private TrafficActivityService activityService;

    @PostConstruct
    public void init() {
        long flushIntervalMs = configCache.getTrafficPushIntervalMs();
        logger.info("流量事件处理器已初始化（事件驱动模式），刷新间隔={}ms", flushIntervalMs);
        
        flushTask = scheduler.scheduleWithFixedDelay(
            this::processFlush,
            flushIntervalMs,
            flushIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public void shutdown() {
        logger.info("流量事件处理器正在关闭...");
        
        flushAndPush();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("流量事件处理器已关闭");
    }

    private void processFlush() {
        try {
            if (!activityService.isActive()) {
                return;
            }
            
            long idleTime = activityService.getIdleTime();
            
            if (idleTime > IDLE_TIMEOUT_MS && !hasPendingWork()) {
                activityService.deactivate();
                return;
            }
            
            if (!queueManager.hasAnyTraffic() && !queueManager.hasPendingPushTasks()) {
                return;
            }
            
            logger.debug("开始处理流量数据刷新");
            
            periodPushTask.checkAndPushPeriodData();
            
            ipAttackStateCache.cleanExpiredEntries();
            
            List<TrafficAggregateData> flushData = queueManager.flushPeriodic();
            
            for (TrafficAggregateData data : flushData) {
                pushAggregateData(data);
            }
            
            queueManager.cleanupExpiredQueues(300000);
            
            processRetryTasks();
            
        } catch (Exception e) {
            logger.error("处理流量数据刷新失败", e);
        }
    }

    private boolean hasPendingWork() {
        return queueManager.hasAnyTraffic() || 
               queueManager.hasPendingPushTasks() ||
               retryQueue.hasPendingTasks();
    }

    public void flushAndPush() {
        try {
            logger.info("执行最终刷新，推送所有待处理数据");
            
            List<TrafficAggregateData> flushData = queueManager.flushAll();
            
            for (TrafficAggregateData data : flushData) {
                pushAggregateData(data);
            }
            
            processRetryTasks();
            
        } catch (Exception e) {
            logger.error("最终刷新失败", e);
        }
    }

    private void pushAggregateData(TrafficAggregateData data) {
        if (degradationHandler.isInDegradationMode()) {
            degradationHandler.handlePushFailure(
                createPushTask(data), 
                new Exception("系统处于降级模式"));
            return;
        }
        
        try {
            TrafficAggregatePushDTO dto = new TrafficAggregatePushDTO(data);
            trafficClient.pushAggregateTraffic(dto);
            degradationHandler.handlePushSuccess();
            logger.debug("推送聚合流量数据成功: ip={}, state={}, requests={}", 
                data.getIp(), 
                IpAttackStateConstant.getStateNameZh(data.getState()),
                data.getTotalRequests());
        } catch (Exception e) {
            logger.warn("推送聚合流量数据失败: ip={}, error={}", 
                data.getIp(), e.getMessage());
            
            degradationHandler.handlePushFailure(createPushTask(data), e);
            
            retryQueue.addRetryTask(createPushTask(data), e);
        }
    }

    private PushTask createPushTask(TrafficAggregateData data) {
        PushTask task = new PushTask();
        task.setTaskId(System.currentTimeMillis());
        task.setType(PushTaskType.PERIODIC_FLUSH);
        task.setIp(data.getIp());
        task.setData(data);
        task.setCreateTime(System.currentTimeMillis());
        return task;
    }

    private void processRetryTasks() {
        List<RetryTask> readyTasks = retryQueue.getReadyTasks(10);
        
        for (RetryTask retryTask : readyTasks) {
            PushTask task = retryTask.getTask();
            try {
                TrafficAggregatePushDTO dto = new TrafficAggregatePushDTO(task.getData());
                trafficClient.pushAggregateTraffic(dto);
                retryQueue.recordSuccess(task);
                degradationHandler.handlePushSuccess();
            } catch (Exception e) {
                retryQueue.recordFailure(task, e);
            }
        }
    }

    public boolean isActive() {
        return activityService.isActive();
    }

    public long getIdleTime() {
        return activityService.getIdleTime();
    }

    public String getStatus() {
        return String.format("TrafficEventProcessor{active=%s, idleTime=%dms, pendingWork=%s}", 
            activityService.isActive(), activityService.getIdleTime(), hasPendingWork());
    }
}
