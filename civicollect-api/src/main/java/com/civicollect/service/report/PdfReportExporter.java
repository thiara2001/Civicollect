package com.civicollect.service.report;

import com.civicollect.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PdfReportExporter implements ReportExporter {

    private final ExportService exportService;

    @Override
    public boolean supports(String format) {
        return "pdf".equalsIgnoreCase(format);
    }

    @Override
    public ReportPayload export(String title, List<String> notes, List<String> headers, List<List<String>> rows) {
        return new ReportPayload(exportService.pdf(title, notes, headers, rows), "application/pdf", "pdf");
    }
}
