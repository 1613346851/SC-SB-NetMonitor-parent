package com.network.gateway.event;

import com.network.gateway.traffic.TrafficQueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StateTransitionEventListener {

    private static final Logger logger = LoggerFactory.getLogger(StateTransitionEventListener.class);

    @Autowired
    private TrafficQueueManager queueManager;

    @EventListener
    public void handleStateTransitionEvent(StateTransitionEvent event) {
        try {
            logger.info("接收到状态转换事件: ip={}, {} -> {}, reason={}, confidence={}", 
                event.getIp(),
                com.network.gateway.constant.IpAttackStateConstant.getStateNameZh(event.getPreviousState()),
                com.network.gateway.constant.IpAttackStateConstant.getStateNameZh(event.getNewState()),
                event.getReason(),
                event.getConfidence());
            
            queueManager.recordStateTransition(
                event.getIp(),
                event.getPreviousState(),
                event.getNewState(),
                event.getReason(),
                event.getConfidence()
            );
            
            logger.info("状态转换事件处理完成: ip={}", event.getIp());
            
        } catch (Exception e) {
            logger.error("处理状态转换事件失败: ip={}, error={}", 
                event.getIp(), e.getMessage(), e);
        }
    }
}
