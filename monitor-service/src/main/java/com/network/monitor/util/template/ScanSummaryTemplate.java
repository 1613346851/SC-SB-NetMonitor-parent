package com.network.monitor.util.template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScanSummaryTemplate {

    private ScanSummaryTemplate() {
    }

    public static String generateDetailedSummary(
            String status,
            String scanType,
            String target,
            int completedInterfaces,
            int totalInterfaces,
            int discoveredCount,
            long durationSeconds,
            List<Map<String, Object>> findings
    ) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("📊 扫描统计\n");
        summary.append(String.format("• 扫描状态: %s\n", getStatusText(status)));
        summary.append(String.format("• 扫描类型: %s\n", getScanTypeText(scanType)));
        summary.append(String.format("• 目标服务: %s\n", target));
        summary.append(String.format("• 扫描进度: %d/%d 个接口\n", completedInterfaces, totalInterfaces));
        summary.append(String.format("• 扫描时长: %d 秒\n", durationSeconds));
        summary.append(String.format("• 发现漏洞: %d 项\n", discoveredCount));
        
        if (findings != null && !findings.isEmpty()) {
            summary.append("\n🔍 漏洞分布\n");
            Map<String, Integer> vulnDistribution = countVulnsByType(findings);
            vulnDistribution.forEach((type, count) -> {
                summary.append(String.format("• %s: %d 项\n", getVulnTypeName(type), count));
            });
            
            summary.append("\n⚠️ 风险等级\n");
            Map<String, Integer> riskDistribution = countVulnsByRisk(findings);
            summary.append(String.format("• 严重: %d 项\n", riskDistribution.getOrDefault("CRITICAL", 0)));
            summary.append(String.format("• 高危: %d 项\n", riskDistribution.getOrDefault("HIGH", 0)));
            summary.append(String.format("• 中危: %d 项\n", riskDistribution.getOrDefault("MEDIUM", 0)));
            summary.append(String.format("• 低危: %d 项\n", riskDistribution.getOrDefault("LOW", 0)));
            
            summary.append("\n🎯 关键发现\n");
            findings.stream()
                .filter(f -> {
                    String level = (String) f.get("vulnLevel");
                    return "CRITICAL".equals(level) || "HIGH".equals(level);
                })
                .limit(3)
                .forEach(f -> {
                    summary.append(String.format("• [%s] %s - %s\n", 
                        f.get("vulnLevel"), f.get("vulnName"), f.get("vulnPath")));
                });
        }
        
        summary.append("\n💡 建议措施\n");
        if (discoveredCount > 0) {
            summary.append("• 立即修复严重和高危漏洞\n");
            summary.append("• 对所有输入点进行严格校验\n");
            summary.append("• 实施最小权限原则\n");
            summary.append("• 定期进行安全审计\n");
        } else {
            summary.append("• 未发现明显漏洞，建议持续监控\n");
            summary.append("• 定期进行深度安全测试\n");
        }
        
        return summary.toString();
    }

    private static String getStatusText(String status) {
        return switch (status) {
            case "IDLE" -> "空闲";
            case "RUNNING" -> "运行中";
            case "PAUSED" -> "已暂停";
            case "COMPLETED" -> "已完成";
            case "TERMINATED" -> "已终止";
            case "FAILED" -> "失败";
            default -> status;
        };
    }

    private static String getScanTypeText(String scanType) {
        return switch (scanType.toUpperCase()) {
            case "QUICK" -> "快速扫描";
            case "FULL" -> "全面扫描";
            default -> scanType;
        };
    }

    private static String getVulnTypeName(String vulnType) {
        return switch (vulnType) {
            case "SQL_INJECTION" -> "SQL注入";
            case "XSS" -> "跨站脚本";
            case "COMMAND_INJECTION" -> "命令注入";
            case "PATH_TRAVERSAL" -> "路径遍历";
            case "FILE_INCLUSION" -> "文件包含";
            case "SSRF" -> "服务端请求伪造";
            case "XXE" -> "XML外部实体";
            case "CSRF" -> "跨站请求伪造";
            default -> vulnType;
        };
    }

    private static Map<String, Integer> countVulnsByType(List<Map<String, Object>> findings) {
        Map<String, Integer> distribution = new HashMap<>();
        for (Map<String, Object> finding : findings) {
            String vulnType = (String) finding.get("vulnType");
            distribution.put(vulnType, distribution.getOrDefault(vulnType, 0) + 1);
        }
        return distribution;
    }

    private static Map<String, Integer> countVulnsByRisk(List<Map<String, Object>> findings) {
        Map<String, Integer> distribution = new HashMap<>();
        for (Map<String, Object> finding : findings) {
            String vulnLevel = (String) finding.get("vulnLevel");
            distribution.put(vulnLevel, distribution.getOrDefault(vulnLevel, 0) + 1);
        }
        return distribution;
    }
}
