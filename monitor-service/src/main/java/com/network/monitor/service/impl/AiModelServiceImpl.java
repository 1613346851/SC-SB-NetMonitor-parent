package com.network.monitor.service.impl;

import com.network.monitor.entity.SysConfigEntity;
import com.network.monitor.service.AiModelService;
import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

/**
 * 全局 AI 大模型预留实现
 * 当前阶段仅负责配置占位与统一调用入口预留，不执行真实 AI 调用。
 */
@Slf4j
@Service
public class AiModelServiceImpl implements AiModelService {

    private static final String AI_MODEL_URL = "ai.model.url";
    private static final String AI_MODEL_API_KEY = "ai.model.apiKey";

    @Autowired
    private SysConfigService sysConfigService;

    @PostConstruct
    public void initDefaultConfigs() {
        ensureConfig(AI_MODEL_URL, "", "全局AI大模型接口地址");
        ensureConfig(AI_MODEL_API_KEY, "", "全局AI大模型API密钥");
    }

    @Override
    public List<String> generatePayload(String vulnType, String targetPath, String currentPayloads) {
        log.debug("AI Payload 生成预留调用：vulnType={}, targetPath={}", vulnType, targetPath);
        return Collections.emptyList();
    }

    @Override
    public String analyzeVuln(String vulnType, String responseBody, String detectionContext) {
        log.debug("AI 漏洞研判预留调用：vulnType={}, context={}", vulnType, detectionContext);
        return null;
    }

    @Override
    public String analyzeAttack(String attackPayload, String responseBody) {
        log.debug("AI 攻击分析预留调用");
        return null;
    }

    @Override
    public String generateFixSuggestion(String vulnType, String vulnPath) {
        log.debug("AI 修复建议预留调用：vulnType={}, vulnPath={}", vulnType, vulnPath);
        return null;
    }

    @Override
    public String summaryReport(String summaryContext) {
        log.debug("AI 报告总结预留调用");
        return null;
    }

    private void ensureConfig(String key, String value, String description) {
        try {
            if (sysConfigService.getConfigByKey(key) != null) {
                return;
            }
            SysConfigEntity config = new SysConfigEntity();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            sysConfigService.addConfig(config);
            log.info("已自动补充AI配置占位：{}", key);
        } catch (Exception e) {
            log.warn("初始化AI配置占位失败：key={}, msg={}", key, e.getMessage());
        }
    }
}
