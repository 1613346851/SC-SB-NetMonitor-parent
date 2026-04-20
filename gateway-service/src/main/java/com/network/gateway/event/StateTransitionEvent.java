package com.network.gateway.event;

import com.network.gateway.constant.IpAttackStateConstant;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class StateTransitionEvent extends ApplicationEvent {

    private final String ip;
    private final int previousState;
    private final int newState;
    private final String previousStateName;
    private final String newStateName;
    private final String reason;
    private final String eventId;
    private final int confidence;
    private final long transitionTime;
    private final LocalDateTime transitionDateTime;
    private final String transitionType;

    public StateTransitionEvent(Object source, String ip, int previousState, int newState, 
                                 String reason, String eventId, int confidence) {
        super(source);
        this.ip = ip;
        this.previousState = previousState;
        this.newState = newState;
        this.previousStateName = IpAttackStateConstant.getStateNameZh(previousState);
        this.newStateName = IpAttackStateConstant.getStateNameZh(newState);
        this.reason = reason;
        this.eventId = eventId;
        this.confidence = confidence;
        this.transitionTime = System.currentTimeMillis();
        this.transitionDateTime = LocalDateTime.now();
        this.transitionType = determineTransitionType(previousState, newState);
    }

    private String determineTransitionType(int from, int to) {
        if (to == IpAttackStateConstant.NORMAL) {
            return "RECOVERY";
        } else if (from == IpAttackStateConstant.NORMAL) {
            return "ATTACK_START";
        } else if (to == IpAttackStateConstant.DEFENDED) {
            return "DEFENSE";
        } else if (to == IpAttackStateConstant.COOLDOWN) {
            return "COOLDOWN";
        } else if (from == IpAttackStateConstant.COOLDOWN && to == IpAttackStateConstant.ATTACKING) {
            return "REATTACK";
        }
        return "TRANSITION";
    }

    public boolean isAttackStart() {
        return "ATTACK_START".equals(transitionType);
    }

    public boolean isRecovery() {
        return "RECOVERY".equals(transitionType);
    }

    public boolean isDefense() {
        return "DEFENSE".equals(transitionType);
    }

    public boolean isReattack() {
        return "REATTACK".equals(transitionType);
    }

    @Override
    public String toString() {
        return String.format("StateTransitionEvent{ip='%s', %s -> %s, reason='%s', confidence=%d}",
                ip, previousStateName, newStateName, reason, confidence);
    }
}
