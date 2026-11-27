package com.civicollect.controller;

import com.civicollect.dto.NeedDtos.*;
import com.civicollect.service.NeedService;
import com.civicollect.service.NeedWorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Modération des contributions – /api/moderation (RF12-RF17).
 * Conserve les anciens endpoints pour la compatibilité ascendante (CiviCollectFlowTest).
 * Ajoute les nouveaux endpoints en français.
 *
 * <p>Pattern State : le workflow des statuts est géré par NeedWorkflowService.
 */
@Tag(name = "Modération et qualification")
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class ModerationController {

    private final NeedWorkflowService workflow;
    private final NeedService needService;

    // ============ Nouveaux endpoints en français ============

    @GetMapping("/en-attente")
    public Page<NeedResponse> enAttente(Pageable pageable) {
        return workflow.moderationQueue(pageable);
    }

    @GetMapping("/{id}")
    public NeedResponse getForModeration(@PathVariable Long id) {
        return workflow.getForModeration(id);
    }

    @PutMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public NeedResponse valider(@PathVariable Long id) {
        return workflow.validate(id);
    }

    @PutMapping("/{id}/rejeter")
    @PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
    public NeedResponse rejeter(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return workflow.reject(id, request.reason());
    }

    @GetMapping("/{id}/historique")
    public List<HistoryEntry> historique(@PathVariable Long id) {
        return needService.history(id);
    }

    // ============ Anciens endpoints (compatibilité ascendante – CiviCollectFlowTest) ============

    @GetMapping("/queue")
    public Page<NeedResponse> queue(Pageable pageable) {
        return workflow.moderationQueue(pageable);
    }

    @PostMapping("/needs/{id}/validate")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public NeedResponse validate(@PathVariable Long id) {
        return workflow.validate(id);
    }

    @PostMapping("/needs/{id}/reject")
    @PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
    public NeedResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return workflow.reject(id, request.reason());
    }

    @PostMapping("/needs/{id}/request-complement")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public NeedResponse requestComplement(@PathVariable Long id, @Valid @RequestBody ComplementRequest request) {
        return workflow.requestComplement(id, request.message());
    }

    @PostMapping("/needs/{id}/status")
    public NeedResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return workflow.changeStatus(id, request);
    }

    @PutMapping("/needs/{id}/assignment")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public NeedResponse assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return workflow.assign(id, request);
    }

    @PutMapping("/needs/{id}/estimate")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public NeedResponse estimate(@PathVariable Long id, @Valid @RequestBody EstimateRequest request) {
        return workflow.estimate(id, request);
    }

    @PostMapping("/merge")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public NeedResponse merge(@Valid @RequestBody MergeRequest request) {
        return workflow.merge(request);
    }

    @DeleteMapping("/needs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        needService.deleteContribution(id);
    }
}
