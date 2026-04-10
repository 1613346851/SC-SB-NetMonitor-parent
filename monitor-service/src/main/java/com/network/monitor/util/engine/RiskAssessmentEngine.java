package com.network.monitor.util.engine;

import java.util.HashMap;
import java.util.Map;

public final class RiskAssessmentEngine {

    private RiskAssessmentEngine() {
    }

    public static RiskAssessment assess(String vulnType, String vulnPath, String payload, String responseSnippet) {
        RiskAssessment assessment = new RiskAssessment();
        
        assessment.setBaseLevel(getBaseRiskLevel(vulnType));
        
        assessment.adjustForEnvironment(getEnvironmentFactors(vulnPath));
        
        assessment.setExploitDifficulty(assessExploitDifficulty(vulnPath, payload));
        
        assessment.setImpactScope(assessImpactScope(vulnType));
        
        assessment.calculateFinalRisk();
        
        return assessment;
    }

    private static RiskLevel getBaseRiskLevel(String vulnType) {
        return switch (vulnType) {
            case "COMMAND_INJECTION" -> RiskLevel.CRITICAL;
            case "SQL_INJECTION", "XXE", "SSRF", "PATH_TRAVERSAL", "FILE_INCLUSION" -> RiskLevel.HIGH;
            case "XSS", "CSRF" -> RiskLevel.MEDIUM;
            case "INFO_DISCLOSURE" -> RiskLevel.LOW;
            default -> RiskLevel.MEDIUM;
        };
    }

    private static Map<String, Object> getEnvironmentFactors(String vulnPath) {
        Map<String, Object> factors = new HashMap<>();
        
        factors.put("requiresAuth", requiresAuthentication(vulnPath));
        factors.put("isPublicFacing", isPublicFacing(vulnPath));
        factors.put("hasSensitiveData", hasSensitiveData(vulnPath));
        
        return factors;
    }

    private static int assessExploitDifficulty(String vulnPath, String payload) {
        int difficulty = 0;
        
        if (requiresAuthentication(vulnPath)) {
            difficulty += 2;
        }
        
        if (requiresSpecialPrivilege(vulnPath)) {
            difficulty += 3;
        }
        
        if (payload != null && payload.length() > 100) {
            difficulty += 1;
        }
        
        if (payload != null && (payload.contains("UNION") || payload.contains(";"))) {
            difficulty += 1;
        }
        
        return difficulty;
    }

    private static String assessImpactScope(String vulnType) {
        return switch (vulnType) {
            case "COMMAND_INJECTION", "SQL_INJECTION", "XXE" -> "系统级影响";
            case "SSRF", "PATH_TRAVERSAL", "FILE_INCLUSION" -> "服务级影响";
            case "XSS", "CSRF" -> "用户级影响";
            default -> "应用级影响";
        };
    }

    private static boolean requiresAuthentication(String vulnPath) {
        return vulnPath.contains("/admin") || 
               vulnPath.contains("/user") || 
               vulnPath.contains("/profile") ||
               vulnPath.contains("/settings");
    }

    private static boolean requiresSpecialPrivilege(String vulnPath) {
        return vulnPath.contains("/admin") || 
               vulnPath.contains("/system") ||
               vulnPath.contains("/config");
    }

    private static boolean isPublicFacing(String vulnPath) {
        return !vulnPath.contains("/admin") && 
               !vulnPath.contains("/internal") &&
               !vulnPath.contains("/private");
    }

    private static boolean hasSensitiveData(String vulnPath) {
        return vulnPath.contains("/user") || 
               vulnPath.contains("/account") ||
               vulnPath.contains("/password") ||
               vulnPath.contains("/payment");
    }

    public enum RiskLevel {
        CRITICAL("严重", 4),
        HIGH("高危", 3),
        MEDIUM("中危", 2),
        LOW("低危", 1),
        INFO("信息", 0);

        private final String name;
        private final int score;

        RiskLevel(String name, int score) {
            this.name = name;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public int getScore() {
            return score;
        }
    }

    public static final class RiskAssessment {
        private RiskLevel baseLevel;
        private RiskLevel finalLevel;
        private Map<String, Object> environmentFactors;
        private int exploitDifficulty;
        private String impactScope;
        private int riskScore;
        private String recommendation;

        public RiskAssessment() {
            this.environmentFactors = new HashMap<>();
            this.exploitDifficulty = 0;
            this.riskScore = 0;
        }

        public void setBaseLevel(RiskLevel baseLevel) {
            this.baseLevel = baseLevel;
        }

        public void adjustForEnvironment(Map<String, Object> factors) {
            this.environmentFactors = factors;
        }

        public void setExploitDifficulty(int difficulty) {
            this.exploitDifficulty = difficulty;
        }

        public void setImpactScope(String impactScope) {
            this.impactScope = impactScope;
        }

        public void calculateFinalRisk() {
            int baseScore = baseLevel.getScore();
            
            int adjustment = 0;
            
            Boolean isPublicFacing = (Boolean) environmentFactors.get("isPublicFacing");
            if (isPublicFacing != null && isPublicFacing) {
                adjustment += 1;
            }
            
            Boolean hasSensitiveData = (Boolean) environmentFactors.get("hasSensitiveData");
            if (hasSensitiveData != null && hasSensitiveData) {
                adjustment += 1;
            }
            
            adjustment -= exploitDifficulty / 3;
            
            int finalScore = Math.max(0, Math.min(4, baseScore + adjustment));
            
            this.riskScore = finalScore;
            this.finalLevel = getRiskLevelByScore(finalScore);
            this.recommendation = generateRecommendation();
        }

        private RiskLevel getRiskLevelByScore(int score) {
            if (score >= 4) return RiskLevel.CRITICAL;
            if (score >= 3) return RiskLevel.HIGH;
            if (score >= 2) return RiskLevel.MEDIUM;
            if (score >= 1) return RiskLevel.LOW;
            return RiskLevel.INFO;
        }

        private String generateRecommendation() {
            return switch (finalLevel) {
                case CRITICAL -> "立即修复！该漏洞可能导致系统完全被控制。";
                case HIGH -> "尽快修复！该漏洞可能导致严重的安全问题。";
                case MEDIUM -> "建议修复！该漏洞可能被利用造成一定影响。";
                case LOW -> "建议关注！该漏洞影响较小但仍需处理。";
                case INFO -> "信息提示，暂无安全风险。";
            };
        }

        public RiskLevel getFinalLevel() {
            return finalLevel;
        }

        public String getFinalLevelName() {
            return finalLevel != null ? finalLevel.getName() : "未知";
        }

        public int getRiskScore() {
            return riskScore;
        }

        public String getImpactScope() {
            return impactScope;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public Map<String, Object> getEnvironmentFactors() {
            return environmentFactors;
        }

        public int getExploitDifficulty() {
            return exploitDifficulty;
        }
    }
}
