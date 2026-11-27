package com.civicollect.controller;

import com.civicollect.dto.AnalyticsDtos.*;
import com.civicollect.service.AnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Analyse et statistiques – /api/analyses (RF18-RF21).
 * Pattern Facade : AnalyticsService agrège plusieurs repos.
 */
@Tag(name = "Analyses")
@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class AnalysesController {

    private final AnalyticsService analyticsService;

    @GetMapping("/globales")
    public GlobalStatsResponse globales() {
        return analyticsService.globalStats();
    }

    @GetMapping("/quartiers")
    public List<QuartierAnalysisResponse> quartiers() {
        return analyticsService.analyzeByQuartier();
    }

    @GetMapping("/categories")
    public List<CategoryAnalysisResponse> categories() {
        return analyticsService.analyzeByCategory();
    }

    @GetMapping("/statuts")
    public List<StatusAnalysisResponse> statuts() {
        return analyticsService.analyzeByStatus();
    }

    @GetMapping("/evolution")
    public List<KeyCount> evolution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.evolution(from, to);
    }

    @GetMapping("/comparaison")
    public List<QuartierComparisonResponse> comparaison() {
        return analyticsService.compareQuartiers();
    }
}
