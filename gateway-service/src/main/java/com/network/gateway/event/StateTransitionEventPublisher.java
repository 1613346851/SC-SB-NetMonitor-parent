package com.network.gateway.event;

import com.network.gateway.cache.IpAttackStateEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class StateTransitionEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(StateTransitionEventPublisher.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void publishStateTransition(String ip, int previousState, int newState, 
                                        String reason, String eventId, int confidence) {
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip, previousState, newState, reason, eventId, confidence);
        
        publishEvent(event);
    }

    public void publishStateTransition(String ip, IpAttackStateEntry entry, 
                                        int previousState, String reason) {
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip, previousState, entry.getState(), 
                reason, entry.getEventId(), entry.getConfidence());
        
        publishEvent(event);
    }

    public void publishManualIntervention(String ip, int previousState, int newState,
                                           String operator, String reason) {
        String fullReason = String.format("[人工干预] %s - 操作人: %s", reason, operator);
        
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip, previousState, newState, fullReason, null, 100);
        
        publishEvent(event);
    }

    public void publishAttackStart(String ip, int confidence) {
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip, 
                com.network.gateway.constant.IpAttackStateConstant.NORMAL,
                com.network.gateway.constant.IpAttackStateConstant.SUSPICIOUS,
                com.network.gateway.constant.IpAttackStateConstant.TRANSITION_REASON_FREQUENCY_ABNORMAL,
                null, confidence);
        
        publishEvent(event);
    }

    public void publishDefenseExecuted(String ip, String eventId, int confidence) {
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip,
                com.network.gateway.constant.IpAttackStateConstant.ATTACKING,
                com.network.gateway.constant.IpAttackStateConstant.DEFENDED,
                com.network.gateway.constant.IpAttackStateConstant.TRANSITION_REASON_DEFENSE_EXECUTED,
                eventId, confidence);
        
        publishEvent(event);
    }

    public void publishCooldownStart(String ip, String eventId) {
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip,
                com.network.gateway.constant.IpAttackStateConstant.DEFENDED,
                com.network.gateway.constant.IpAttackStateConstant.COOLDOWN,
                com.network.gateway.constant.IpAttackStateConstant.TRANSITION_REASON_ATTACK_STOPPED,
                eventId, 0);
        
        publishEvent(event);
    }

    public void publishRecovery(String ip, int previousState, String reason) {
        StateTransitionEvent event = new StateTransitionEvent(
                this, ip, previousState,
                com.network.gateway.constant.IpAttackStateConstant.NORMAL,
                reason, null, 0);
        
        publishEvent(event);
    }

    private void publishEvent(StateTransitionEvent event) {
        try {
            eventPublisher.publishEvent(event);
            logger.info("状态转换事件已发布: {}", event);
        } catch (Exception e) {
            logger.error("发布状态转换事件失败: {}", event, e);
        }
    }
}
