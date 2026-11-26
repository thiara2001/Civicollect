package com.civicollect.web;

import com.civicollect.dto.AnalyticsDtos.DashboardResponse;
import com.civicollect.dto.NeedDtos.MapPoint;
import com.civicollect.dto.NeedDtos.NeedFilter;
import com.civicollect.service.AnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Analyse")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analytics;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.dashboard(from, to);
    }

    @GetMapping("/map")
    public List<MapPoint> map(NeedFilter filter) {
        return analytics.map(filter);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format, NeedFilter filter) {
        AnalyticsService.Export e = analytics.export(format, filter);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(e.filename(), StandardCharsets.UTF_8).build().toString())
                .body(e.content());
    }
}
