package com.civicollect.web;

import com.civicollect.dto.AdminDtos.CriterionRequest;
import com.civicollect.dto.PriorityDtos.*;
import com.civicollect.service.PriorityService;
import com.civicollect.service.scoring.ScoringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "Priorisation")
@RestController
@RequestMapping("/api/priorities")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class PriorityController {

    private final ScoringService scoringService;
    private final PriorityService priorityService;

    @GetMapping("/ranking")
    public List<RankedNeed> ranking(@RequestParam(required = false) Integer top) {
        List<RankedNeed> all = scoringService.ranking();
        return top != null && top > 0 && top < all.size() ? all.subList(0, top) : all;
    }

    /** RF25 : simulation de scénario avec des poids alternatifs (rien n'est enregistré). */
    @PostMapping("/simulate")
    public List<RankedNeed> simulate(@Valid @RequestBody SimulationRequest request) {
        List<RankedNeed> all = scoringService.simulate(request.weights());
        Integer top = request.top();
        return top != null && top > 0 && top < all.size() ? all.subList(0, top) : all;
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasAnyRole('ELU','ADMIN')")
    public Map<String, Integer> recalculate() {
        return Map.of("recalculated", scoringService.recalculate());
    }

    @GetMapping("/needs/{needId}/score")
    public RankedNeed explain(@PathVariable Long needId) {
        return scoringService.explain(needId)
                .orElseThrow(() -> new com.civicollect.exception.NotFoundException("Besoin non classé"));
    }

    // ---- critères et pondérations (RF24, RG4)

    @GetMapping("/criteria")
    public List<CriterionResponse> criteria() {
        return priorityService.listCriteria();
    }

    @PutMapping("/criteria/{id}/weight")
    @PreAuthorize("hasAnyRole('ELU','ADMIN')")
    public CriterionResponse updateWeight(@PathVariable Long id, @Valid @RequestBody WeightUpdateRequest request) {
        return priorityService.updateWeight(id, request.weight());
    }

    @PostMapping("/criteria")
    @PreAuthorize("hasRole('ADMIN')")
    public CriterionResponse createCriterion(@Valid @RequestBody CriterionRequest request) {
        return priorityService.createCriterion(request);
    }

    @PutMapping("/criteria/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CriterionResponse updateCriterion(@PathVariable Long id, @Valid @RequestBody CriterionRequest request) {
        return priorityService.updateCriterion(id, request);
    }

    // ---- évaluations manuelles et ajustements (RF26, RG5)

    @PutMapping("/needs/{needId}/evaluations")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public List<CriterionScore> evaluate(@PathVariable Long needId, @Valid @RequestBody EvaluationRequest request) {
        return priorityService.evaluate(needId, request);
    }

    @PostMapping("/needs/{needId}/adjustment")
    @PreAuthorize("hasAnyRole('ELU','ADMIN')")
    public RankedNeed adjust(@PathVariable Long needId, @Valid @RequestBody AdjustmentRequest request) {
        return priorityService.adjust(needId, request);
    }

    @GetMapping("/needs/{needId}/decisions")
    public List<DecisionResponse> decisions(@PathVariable Long needId) {
        return priorityService.decisions(needId);
    }

    // ---- rapport (RF27)

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ELU','ADMIN')")
    public ResponseEntity<byte[]> report(@RequestParam(defaultValue = "pdf") String format,
                                         @RequestParam(required = false) Integer top) {
        PriorityService.Report r = priorityService.report(format, top);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(r.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(r.filename(), StandardCharsets.UTF_8).build().toString())
                .body(r.content());
    }
}
