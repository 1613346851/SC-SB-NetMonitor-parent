package com.network.gateway.handler;

import com.network.gateway.enums.TrafficPushStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrafficPushStrategyManager {

    private static final Logger logger = LoggerFactory.getLogger(TrafficPushStrategyManager.class);

    private final Map<String, TrafficPushHandler> handlerMap = new HashMap<>();

    @Autowired
    public TrafficPushStrategyManager(List<TrafficPushHandler> handlers) {
        for (TrafficPushHandler handler : handlers) {
            handlerMap.put(handler.getStrategyName(), handler);
            logger.info("注册流量推送处理器: {}", handler.getStrategyName());
        }
    }

    public TrafficPushHandler getHandler(TrafficPushStrategy strategy) {
        return handlerMap.get(strategy.getCode());
    }

    public void flushAll() {
        logger.info("开始刷新所有推送处理器缓存");
        for (TrafficPushHandler handler : handlerMap.values()) {
            try {
                handler.flush();
            } catch (Exception e) {
                logger.error("刷新处理器缓存失败: {}", handler.getStrategyName(), e);
            }
        }
        logger.info("所有推送处理器缓存刷新完成");
    }

    public String getAllStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("流量推送策略统计:\n");
        for (Map.Entry<String, TrafficPushHandler> entry : handlerMap.entrySet()) {
            if (entry.getValue() instanceof DelayedBatchPushHandler) {
                sb.append("  延迟批量: ").append(((DelayedBatchPushHandler) entry.getValue()).getStats()).append("\n");
            } else if (entry.getValue() instanceof CounterOnlyPushHandler) {
                sb.append("  计数推送: ").append(((CounterOnlyPushHandler) entry.getValue()).getStats()).append("\n");
            } else if (entry.getValue() instanceof AggregatePushHandler) {
                sb.append("  聚合推送: ").append(((AggregatePushHandler) entry.getValue()).getStats()).append("\n");
            }
        }
        return sb.toString();
    }
}
