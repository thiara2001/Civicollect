package com.civicollect.controller;

import com.civicollect.dto.DuplicateDtos.DuplicateProposalResponse;
import com.civicollect.service.DuplicateDetectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Détection et gestion des doublons – /api/doublons (RF14, RG6).
 * Pattern Strategy : DuplicateDetectionService injecte la SimilarityStrategy.
 */
@Tag(name = "Doublons")
@RestController
@RequestMapping("/api/doublons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class DoublonsController {

    private final DuplicateDetectionService duplicateService;

    @GetMapping("/contribution/{id}")
    public List<DuplicateProposalResponse> forContribution(@PathVariable Long id) {
        return duplicateService.findProposalsForContribution(id);
    }

    @GetMapping
    public List<DuplicateProposalResponse> listPotential() {
        return duplicateService.listPotentialProposals();
    }

    @PostMapping("/{id}/confirmer")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public DuplicateProposalResponse confirmer(@PathVariable Long id) {
        return duplicateService.confirmProposal(id);
    }

    @PostMapping("/{id}/ignorer")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public DuplicateProposalResponse ignorer(@PathVariable Long id) {
        return duplicateService.ignoreProposal(id);
    }
}
