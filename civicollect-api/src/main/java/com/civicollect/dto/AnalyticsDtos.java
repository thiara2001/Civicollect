package com.civicollect.dto;

import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record KeyCount(String key, long count) {}

    public record DashboardResponse(long total, List<KeyCount> byCategory, List<KeyCount> byZone,
                                    List<KeyCount> byStatus, List<KeyCount> byRequesterType,
                                    List<KeyCount> byMonth) {}
}
