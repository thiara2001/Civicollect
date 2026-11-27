package com.civicollect.dto;

import com.civicollect.domain.NeedStatus;

import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record KeyCount(String key, long count) {}

    public record DashboardResponse(long total, List<KeyCount> byCategory, List<KeyCount> byZone,
                                    List<KeyCount> byStatus, List<KeyCount> byRequesterType,
                                    List<KeyCount> byMonth) {}

    public record GlobalStatsResponse(
            long totalContributions, long totalDrafts, long totalPending,
            long totalValidated, long totalRealized, long totalRejected,
            long totalSupports, double validationRate, double realizationRate,
            Double averagePriorityScore, List<KeyCount> byRequesterType) {}

    public record QuartierAnalysisResponse(
            Long quartierId, String quartierName, long totalContributions,
            long pendingContributions, long validatedContributions, long realizedContributions,
            long totalSupports, Double averagePriorityScore) {}

    public record CategoryAnalysisResponse(
            Long categoryId, String categoryName, long totalContributions,
            long validatedContributions, long realizedContributions,
            long totalSupports, Double averagePriorityScore) {}

    public record StatusAnalysisResponse(NeedStatus status, String label, long count, double percentage) {}

    public record QuartierComparisonResponse(
            Long quartierId, String quartierName, Integer population,
            long totalContributions, Double contributionsPer1000Inhabitants,
            long realizedContributions, double realizationRate,
            long totalSupports, Double averagePriorityScore) {}
}
