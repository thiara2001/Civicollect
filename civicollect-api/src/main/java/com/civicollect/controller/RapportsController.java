package com.civicollect.controller;

import com.civicollect.dto.ReportDtos.ReportDescriptor;
import com.civicollect.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rapports – /api/rapports (RF27).
 * Pattern Facade (ReportService) + Strategy (ReportExporter : PDF / CSV / Excel).
 */
@Tag(name = "Rapports")
@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class RapportsController {

    private final ReportService reportService;

    @GetMapping
    public List<ReportDescriptor> availableReports() {
        return reportService.availableReports();
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> globalPdf() {
        ReportService.GeneratedReport r = reportService.globalPdf();
        return download(r);
    }

    @GetMapping("/quartier/{id}")
    public ResponseEntity<byte[]> byQuartier(@PathVariable Long id,
                                              @RequestParam(defaultValue = "pdf") String format) {
        ReportService.GeneratedReport r = reportService.byQuartier(id, format);
        return download(r);
    }

    @GetMapping("/categorie/{id}")
    public ResponseEntity<byte[]> byCategorie(@PathVariable Long id,
                                               @RequestParam(defaultValue = "pdf") String format) {
        ReportService.GeneratedReport r = reportService.byCategory(id, format);
        return download(r);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel() {
        ReportService.GeneratedReport r = reportService.exportExcelStatistics();
        return download(r);
    }

    private static ResponseEntity<byte[]> download(ReportService.GeneratedReport r) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, r.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + r.filename() + "\"")
                .body(r.content());
    }
}
