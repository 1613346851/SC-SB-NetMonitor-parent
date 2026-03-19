package com.network.monitor.service;

import java.util.List;

/**
 * 全局 AI 大模型预留服务
 */
public interface AiModelService {

    List<String> generatePayload(String vulnType, String targetPath, String currentPayloads);

    String analyzeVuln(String vulnType, String responseBody, String detectionContext);

    String analyzeAttack(String attackPayload, String responseBody);

    String generateFixSuggestion(String vulnType, String vulnPath);

    String summaryReport(String summaryContext);
}
