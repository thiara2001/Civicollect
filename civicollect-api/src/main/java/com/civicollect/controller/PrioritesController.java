package com.civicollect.controller;

import com.civicollect.dto.PriorityDtos.*;
import com.civicollect.service.PriorityService;
import com.civicollect.service.scoring.ScoringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Priorisation des contributions – /api/priorites (RF23-RF27).
 * Pattern Strategy : ScoringService délègue aux ScoringStrategy.
 */
@Tag(name = "Priorisation")
@RestController
@RequestMapping("/api/priorites")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class PrioritesController {

    private final ScoringService scoringService;
    private final PriorityService priorityService;

    @GetMapping
    public List<RankedNeed> ranking() {
        return scoringService.ranking();
    }

    @GetMapping("/{id}")
    public RankedNeed explain(@PathVariable Long id) {
        return scoringService.explain(id)
                .orElseThrow(() -> new com.civicollect.exception.NotFoundException("Contribution introuvable dans le classement"));
    }

    @PostMapping("/calculer")
    @PreAuthorize("hasAnyRole('ELU','ADMIN')")
    public Map<String, Object> calculer() {
        int count = scoringService.recalculate();
        return Map.of("recalculated", count, "message", count + " contribution(s) recalculée(s)");
    }

    @GetMapping("/criteres")
    public List<CriterionResponse> criteres() {
        return priorityService.listCriteria();
    }

    @PutMapping("/criteres")
    @PreAuthorize("hasAnyRole('ELU','ADMIN')")
    public List<CriterionResponse> updateCriteres(@Valid @RequestBody BatchWeightUpdateRequest r) {
        return priorityService.updateWeightsBatch(r);
    }
}
