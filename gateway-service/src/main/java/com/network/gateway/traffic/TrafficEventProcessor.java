package com.network.gateway.traffic;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TrafficEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TrafficEventProcessor.class);

    private final ExecutorService pushExecutor = Executors.newFixedThreadPool(4,
        r -> {
            Thread t = new Thread(r, "traffic-push-worker");
            t.setDaemon(true);
            return t;
        }
    );
    
    private final ExecutorService retryExecutor = Executors.newSingleThreadExecutor(
        r -> {
            Thread t = new Thread(r, "traffic-retry-worker");
            t.setDaemon(true);
            return t;
        }
    );
    
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

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
    private IpAttackStateCache ipAttackStateCache;

    @Autowired
    private TrafficActivityService activityService;

    @Autowired
    private NetworkCongestionDetector congestionDetector;

    @PostConstruct
    public void init() {
        logger.info("流量事件处理器已初始化（实时推送模式 + 动态拥塞控制）");
        startRetryProcessor();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("流量事件处理器正在关闭...");
        isShutdown.set(true);
        
        flushAndPush();
        
        pushExecutor.shutdown();
        retryExecutor.shutdown();
        try {
            if (!pushExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pushExecutor.shutdownNow();
            }
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pushExecutor.shutdownNow();
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("流量事件处理器已关闭");
    }

    public void onTrafficReceived(String ip) {
        if (isShutdown.get()) {
            return;
        }
        
        activityService.onTrafficReceived(ip);
        
        tryImmediatePush(ip);
    }

    public void onStateTransition(String ip, int fromState, int toState) {
        if (isShutdown.get()) {
            return;
        }
        
        activityService.onStateTransition(ip, fromState, toState);
        
        if (fromState != toState) {
            logger.info("状态转换触发即时推送: ip={}, {} -> {}", 
                ip, IpAttackStateConstant.getStateNameZh(fromState), IpAttackStateConstant.getStateNameZh(toState));
            
            pushImmediately(ip);
        }
    }

    private void tryImmediatePush(String ip) {
        if (congestionDetector.canPushImmediately()) {
            pushImmediately(ip);
        } else {
            logger.debug("网络拥塞，流量进入队列等待: ip={}, inFlight={}, window={}", 
                ip, congestionDetector.getCurrentInflight(), congestionDetector.getCongestionWindow());
        }
    }

    private void pushImmediately(String ip) {
        if (isShutdown.get()) {
            return;
        }
        
        TrafficAggregateData data = queueManager.flushIpQueueImmediately(ip);
        if (data == null || data.getTotalRequests() == 0) {
            return;
        }
        
        congestionDetector.recordPushStart();
        
        pushExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                doPush(data);
                long rtt = System.currentTimeMillis() - startTime;
                congestionDetector.recordPushSuccess(rtt);
            } catch (Exception e) {
                long rtt = System.currentTimeMillis() - startTime;
                congestionDetector.recordPushFailure(rtt, e);
            }
        });
    }

    private void doPush(TrafficAggregateData data) {
        if (degradationHandler.isInDegradationMode()) {
            logger.warn("系统处于降级模式，跳过推送: ip={}, requests={}", 
                data.getIp(), data.getTotalRequests());
            degradationHandler.handlePushFailure(
                createPushTask(data), 
                new Exception("系统处于降级模式"));
            return;
        }
        
        try {
            TrafficAggregatePushDTO dto = new TrafficAggregatePushDTO(data);
            trafficClient.pushAggregateTraffic(dto);
            degradationHandler.handlePushSuccess();
            logger.info("推送流量数据成功: ip={}, state={}, requests={}, uriGroups={}", 
                data.getIp(), 
                IpAttackStateConstant.getStateNameZh(data.getState()),
                data.getTotalRequests(),
                data.getUriGroups() != null ? data.getUriGroups().size() : 0);
        } catch (Exception e) {
            logger.error("推送流量数据失败: ip={}, requests={}, error={}", 
                data.getIp(), data.getTotalRequests(), e.getMessage());
            
            degradationHandler.handlePushFailure(createPushTask(data), e);
            retryQueue.addRetryTask(createPushTask(data), e);
            
            throw new RuntimeException("推送失败", e);
        }
    }

    private void startRetryProcessor() {
        retryExecutor.submit(() -> {
            while (!isShutdown.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    processRetryTasks();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("重试处理器异常", e);
                }
            }
        });
    }

    private void processRetryTasks() {
        if (!congestionDetector.canPushImmediately()) {
            return;
        }
        
        List<RetryTask> readyTasks = retryQueue.getReadyTasks(5);
        
        for (RetryTask retryTask : readyTasks) {
            PushTask task = retryTask.getTask();
            congestionDetector.recordPushStart();
            
            long startTime = System.currentTimeMillis();
            try {
                TrafficAggregatePushDTO dto = new TrafficAggregatePushDTO(task.getData());
                trafficClient.pushAggregateTraffic(dto);
                retryQueue.recordSuccess(task);
                degradationHandler.handlePushSuccess();
                congestionDetector.recordPushSuccess(System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                congestionDetector.recordPushFailure(System.currentTimeMillis() - startTime, e);
                retryQueue.recordFailure(task, e);
            }
        }
    }

    public void flushAndPush() {
        try {
            logger.info("执行最终刷新，推送所有待处理数据");
            
            List<TrafficAggregateData> flushData = queueManager.flushAll();
            
            for (TrafficAggregateData data : flushData) {
                try {
                    doPush(data);
                } catch (Exception e) {
                    logger.error("最终刷新推送失败: ip={}", data.getIp(), e);
                }
            }
            
        } catch (Exception e) {
            logger.error("最终刷新失败", e);
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

    public boolean isActive() {
        return activityService.isActive();
    }

    public long getIdleTime() {
        return activityService.getIdleTime();
    }

    public String getStatus() {
        return String.format("TrafficEventProcessor{active=%s, idleTime=%dms, congestion=%s}", 
            activityService.isActive(), 
            activityService.getIdleTime(),
            congestionDetector.getStatistics());
    }
    
    public NetworkCongestionDetector getCongestionDetector() {
        return congestionDetector;
    }
}
