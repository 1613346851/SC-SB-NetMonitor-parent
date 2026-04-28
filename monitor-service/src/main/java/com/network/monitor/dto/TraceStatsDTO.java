package com.network.monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class TraceStatsDTO {

    private OverviewStats overview;
    private List<AttackTypeStats> attackTypeStats;
    private List<TrendStats> trendStats;
    private List<RiskLevelStats> riskLevelStats;
    private List<GeoStats> geoStats;
    private TraceDepthStats traceDepth;

    @Data
    public static class OverviewStats {
        private Double successRate;
        private Long avgTraceTime;
        private Integer highRiskIpCount;
        private Integer blacklistedIpCount;
        private Integer totalAttacks;
        private Integer tracedAttacks;
    }

    @Data
    public static class AttackTypeStats {
        private String attackType;
        private String attackTypeName;
        private Integer count;
        private Double percentage;
    }

    @Data
    public static class TrendStats {
        private String date;
        private Integer totalAttacks;
        private Integer tracedAttacks;
        private Integer highRiskAttacks;
    }

    @Data
    public static class RiskLevelStats {
        private String riskLevel;
        private String riskLevelName;
        private Integer count;
        private Double percentage;
    }

    @Data
    public static class GeoStats {
        private String location;
        private Integer count;
        private Double percentage;
    }

    @Data
    public static class TraceDepthStats {
        private Integer ipInfoCompleteness;
        private Integer attackChainCompleteness;
        private Integer correlationDepth;
        private Integer historyAnalysisDepth;
        private Integer riskAssessmentAccuracy;
    }
}
