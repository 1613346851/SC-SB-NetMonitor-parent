package com.network.gateway.traffic;

import com.network.gateway.constant.IpAttackStateConstant;
import lombok.Data;

import java.io.Serializable;

@Data
public class PushTask implements Serializable, Comparable<PushTask> {

    private static final long serialVersionUID = 1L;

    private long taskId;
    private PushTaskType type;
    private String ip;
    private TrafficAggregateData data;
    private long createTime;
    private int retryCount;
    private long lastRetryTime;
    private int maxRetryCount = 3;

    public PushTask() {
    }

    public boolean shouldRetry() {
        return retryCount < maxRetryCount;
    }

    public boolean isExpired(long expireMs) {
        return System.currentTimeMillis() - createTime > expireMs;
    }

    @Override
    public int compareTo(PushTask other) {
        int statePriority1 = getStatePriority(this.data != null ? this.data.getState() : IpAttackStateConstant.NORMAL);
        int statePriority2 = getStatePriority(other.data != null ? other.data.getState() : IpAttackStateConstant.NORMAL);
        
        if (statePriority1 != statePriority2) {
            return Integer.compare(statePriority1, statePriority2);
        }
        
        if (this.type != other.type) {
            return this.type == PushTaskType.STATE_TRANSITION ? -1 : 1;
        }
        
        return Long.compare(this.createTime, other.createTime);
    }

    private int getStatePriority(int state) {
        switch (state) {
            case IpAttackStateConstant.NORMAL:
                return 5;
            case IpAttackStateConstant.SUSPICIOUS:
                return 4;
            case IpAttackStateConstant.ATTACKING:
                return 3;
            case IpAttackStateConstant.DEFENDED:
                return 2;
            case IpAttackStateConstant.COOLDOWN:
                return 1;
            default:
                return 6;
        }
    }

    public long getAge() {
        return System.currentTimeMillis() - createTime;
    }
}
