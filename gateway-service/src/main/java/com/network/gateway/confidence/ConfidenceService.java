package com.network.gateway.confidence;

import com.network.gateway.cache.GatewayConfigCache;
import com.network.gateway.constant.IpAttackStateConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceService {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceService.class);

    @Autowired
    private ConfidenceCalculator calculator;

    @Autowired
    private ConfidenceSmoother smoother;

    @Autowired
    private ConfidenceRecordManager recordManager;

    @Autowired
    private GatewayConfigCache configCache;

    public void setCalculator(ConfidenceCalculator calculator) {
        this.calculator = calculator;
    }

    public void setSmoother(ConfidenceSmoother smoother) {
        this.smoother = smoother;
    }

    public void setRecordManager(ConfidenceRecordManager recordManager) {
        this.recordManager = recordManager;
    }

    public void setConfigCache(GatewayConfigCache configCache) {
        this.configCache = configCache;
    }

    public int calculateConfidence(ConfidenceContext context) {
        if (context == null) {
            return 0;
        }

        int rawConfidence = calculator.calculate(context);
        int smoothedConfidence = smoother.smooth(context.getIp(), rawConfidence, context.getCurrentState());

        logger.debug("置信度计算完成: ip={}, raw={}, smoothed={}", 
            context.getIp(), rawConfidence, smoothedConfidence);

        return smoothedConfidence;
    }

    public ConfidenceResult calculateWithRecord(ConfidenceContext context, String reason) {
        if (context == null) {
            return null;
        }

        ConfidenceResult result = calculator.calculateWithBreakdown(context);
        int smoothedConfidence = smoother.smooth(context.getIp(), result.getRawConfidence(), context.getCurrentState());
        result.setSmoothedConfidence(smoothedConfidence);
        result.setReason(reason);

        recordManager.record(result, context.getCurrentState(), reason);

        logger.info("置信度计算并记录: ip={}, raw={}, smoothed={}, reason={}", 
            context.getIp(), result.getRawConfidence(), smoothedConfidence, reason);

        return result;
    }

    public ConfidenceResult calculateForStateTransition(String ip, int fromState, int toState, 
            ConfidenceContext context, String reason) {
        
        if (context == null) {
            context = new ConfidenceContext(ip);
            context.setCurrentState(toState);
        }

        ConfidenceResult result = calculator.calculateWithBreakdown(context);
        int smoothedConfidence = smoother.smooth(ip, result.getRawConfidence(), toState);
        result.setSmoothedConfidence(smoothedConfidence);
        result.setReason(reason);

        recordManager.record(result, toState, reason);

        logger.info("状态转换置信度计算: ip={}, {} -> {}, confidence={}, reason={}", 
            ip, IpAttackStateConstant.getStateNameZh(fromState), 
            IpAttackStateConstant.getStateNameZh(toState), smoothedConfidence, reason);

        return result;
    }

    public void resetConfidence(String ip) {
        smoother.reset(ip);
        recordManager.clearRecords(ip);
        logger.info("置信度已重置: ip={}", ip);
    }

    public void resetConfidenceOnCooldownEnd(String ip) {
        smoother.reset(ip);
        logger.info("冷却期结束，置信度已重置: ip={}", ip);
    }

    public Integer getLastConfidence(String ip) {
        return smoother.getLastConfidence(ip);
    }

    public ConfidenceRecord getLatestRecord(String ip) {
        return recordManager.getLatestRecord(ip);
    }

    public ScoreBreakdown getScoreBreakdown(ConfidenceContext context) {
        return calculator.calculateBreakdown(context);
    }

    public void setSmoothStrategy(String strategy) {
        smoother.setStrategy(strategy);
    }

    public void setSmoothAlpha(double alpha) {
        smoother.setAlpha(alpha);
    }

    public String getStats() {
        return String.format("置信度服务统计 - %s, %s", 
            smoother.getStats(), recordManager.getStats());
    }
}
