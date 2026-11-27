package com.civicollect.dto;

import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record ReportDescriptor(
            String code,
            String title,
            String description,
            String endpoint,
            List<String> formats) {}
}
