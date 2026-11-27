package com.civicollect.service.report;

import com.civicollect.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CsvReportExporter implements ReportExporter {

    private final ExportService exportService;

    @Override
    public boolean supports(String format) {
        return "csv".equalsIgnoreCase(format);
    }

    @Override
    public ReportPayload export(String title, List<String> notes, List<String> headers, List<List<String>> rows) {
        return new ReportPayload(exportService.csv(headers, rows), "text/csv; charset=UTF-8", "csv");
    }
}
