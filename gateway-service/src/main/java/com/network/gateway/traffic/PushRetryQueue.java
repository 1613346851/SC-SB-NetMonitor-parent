package com.network.gateway.traffic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PushRetryQueue {

    private static final Logger logger = LoggerFactory.getLogger(PushRetryQueue.class);

    private final PriorityBlockingQueue<RetryTask> retryQueue = new PriorityBlockingQueue<>();
    
    private int maxQueueSize = 10000;
    private int maxRetryCount = 3;
    private long[] retryDelays = {0, 1000, 3000};
    private long taskExpireMs = 300000;
    
    private final AtomicInteger totalRetryCount = new AtomicInteger(0);
    private final AtomicInteger successRetryCount = new AtomicInteger(0);
    private final AtomicInteger failedRetryCount = new AtomicInteger(0);
    private final AtomicInteger discardedCount = new AtomicInteger(0);

    public void addRetryTask(PushTask task, Exception error) {
        if (retryQueue.size() >= maxQueueSize) {
            discardOldestTask();
        }
        
        if (!task.shouldRetry()) {
            logger.warn("推送任务已达到最大重试次数，丢弃: taskId={}, ip={}", 
                task.getTaskId(), task.getIp());
            discardedCount.incrementAndGet();
            return;
        }
        
        RetryTask retryTask = new RetryTask(task, error);
        retryQueue.offer(retryTask);
        totalRetryCount.incrementAndGet();
        
        logger.info("推送任务已加入重试队列: taskId={}, ip={}, retryCount={}, queueSize={}", 
            task.getTaskId(), task.getIp(), task.getRetryCount(), retryQueue.size());
    }

    public RetryTask takeTask() throws InterruptedException {
        RetryTask task = retryQueue.take();
        
        while (task != null && !task.isReadyToRetry()) {
            if (task.isExpired(taskExpireMs)) {
                logger.warn("重试任务已过期，丢弃: taskId={}", task.getTask().getTaskId());
                discardedCount.incrementAndGet();
                task = retryQueue.poll();
                continue;
            }
            
            Thread.sleep(100);
            
            if (!task.isReadyToRetry()) {
                retryQueue.offer(task);
                task = retryQueue.poll();
            }
        }
        
        return task;
    }

    public RetryTask pollTask() {
        RetryTask task = retryQueue.poll();
        if (task != null && !task.isReadyToRetry()) {
            retryQueue.offer(task);
            return null;
        }
        return task;
    }

    public List<RetryTask> getReadyTasks(int maxCount) {
        List<RetryTask> readyTasks = new ArrayList<>();
        List<RetryTask> notReadyTasks = new ArrayList<>();
        
        RetryTask task;
        while ((task = retryQueue.poll()) != null && readyTasks.size() < maxCount) {
            if (task.isReadyToRetry() && !task.isExpired(taskExpireMs)) {
                readyTasks.add(task);
            } else if (!task.isExpired(taskExpireMs)) {
                notReadyTasks.add(task);
            } else {
                logger.warn("重试任务已过期，丢弃: taskId={}", task.getTask().getTaskId());
                discardedCount.incrementAndGet();
            }
        }
        
        for (RetryTask notReady : notReadyTasks) {
            retryQueue.offer(notReady);
        }
        
        return readyTasks;
    }

    private void discardOldestTask() {
        RetryTask oldest = retryQueue.poll();
        if (oldest != null) {
            logger.warn("重试队列已满，丢弃最旧任务: taskId={}, ip={}", 
                oldest.getTask().getTaskId(), oldest.getTask().getIp());
            discardedCount.incrementAndGet();
        }
    }

    public void recordSuccess(PushTask task) {
        successRetryCount.incrementAndGet();
        logger.debug("重试推送成功: taskId={}, ip={}", task.getTaskId(), task.getIp());
    }

    public void recordFailure(PushTask task, Exception error) {
        failedRetryCount.incrementAndGet();
        
        if (task.shouldRetry()) {
            addRetryTask(task, error);
        } else {
            logger.error("重试推送最终失败，丢弃: taskId={}, ip={}, error={}", 
                task.getTaskId(), task.getIp(), error.getMessage());
            discardedCount.incrementAndGet();
        }
    }

    public int getQueueSize() {
        return retryQueue.size();
    }

    public boolean isEmpty() {
        return retryQueue.isEmpty();
    }

    public boolean hasPendingTasks() {
        return !retryQueue.isEmpty();
    }

    public boolean hasUrgentTasks() {
        for (RetryTask task : retryQueue) {
            if (task.isReadyToRetry()) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        int size = retryQueue.size();
        retryQueue.clear();
        logger.info("重试队列已清空，丢弃{}个任务", size);
    }

    public RetryQueueStats getStats() {
        RetryQueueStats stats = new RetryQueueStats();
        stats.setQueueSize(retryQueue.size());
        stats.setTotalRetryCount(totalRetryCount.get());
        stats.setSuccessRetryCount(successRetryCount.get());
        stats.setFailedRetryCount(failedRetryCount.get());
        stats.setDiscardedCount(discardedCount.get());
        return stats;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public void setTaskExpireMs(long taskExpireMs) {
        this.taskExpireMs = taskExpireMs;
    }
}
