package com.civicollect.web;

import com.civicollect.dto.NeedDtos.*;
import com.civicollect.service.NeedWorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Modération et qualification")
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
public class ModerationController {

    private final NeedWorkflowService workflow;

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
}
