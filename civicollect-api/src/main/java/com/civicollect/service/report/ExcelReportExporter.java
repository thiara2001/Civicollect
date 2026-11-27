package com.civicollect.service.report;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExcelReportExporter implements ReportExporter {

    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public boolean supports(String format) {
        return "excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format);
    }

    @Override
    public ReportPayload export(String title, List<String> notes, List<String> headers, List<List<String>> rows) {
        byte[] content = new ExcelWorkbookBuilder()
                .addSheet("Rapport", headers, rows)
                .build();
        return new ReportPayload(content, XLSX_MEDIA_TYPE, "xlsx");
    }
}
