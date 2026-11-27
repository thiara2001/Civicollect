package com.civicollect.controller;

import com.civicollect.dto.NeedDtos.*;
import com.civicollect.service.NeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Gestion des contributions – /api/contributions (RF6-RF10, RF30).
 * Inclut la gestion des soutiens et des photos (RF10, RF6).
 *
 * <p>Pattern Facade : délègue au NeedService (SRP).
 * <p>Remarque : les anciens endpoints /api/needs restent opérationnels pour la compatibilité.
 */
@Tag(name = "Contributions")
@RestController
@RequestMapping("/api/contributions")
@RequiredArgsConstructor
public class ContributionsController {

    private final NeedService needService;

    // ============ CRUD de base ============

    @PostMapping
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public ResponseEntity<NeedResponse> create(@Valid @RequestBody ContributionRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.createContribution(r));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NeedResponse> list(NeedFilter filter, Pageable pageable) {
        return needService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NeedResponse get(@PathVariable Long id) {
        return needService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NeedResponse update(@PathVariable Long id, @Valid @RequestBody ContributionRequest r) {
        return needService.updateContribution(id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        needService.deleteContribution(id);
    }

    // ============ Vues spécialisées ============

    @GetMapping("/mes-contributions")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public Page<NeedResponse> mine(Pageable pageable) {
        return needService.mine(pageable);
    }

    @GetMapping("/publiques")
    public Page<NeedResponse> publicValidated(NeedFilter filter, Pageable pageable) {
        return needService.publicValidated(filter, pageable);
    }

    @GetMapping("/recherche")
    @PreAuthorize("isAuthenticated()")
    public Page<NeedResponse> search(NeedFilter filter, Pageable pageable) {
        return needService.search(filter, pageable);
    }

    // ============ Photos ============

    @PostMapping("/{id}/photos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttachmentResponse> addPhoto(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.addPhoto(id, file));
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("isAuthenticated()")
    public List<AttachmentResponse> photos(@PathVariable Long id) {
        return needService.photos(id);
    }

    // ============ Soutiens ============

    @PostMapping("/{id}/soutiens")
    @Operation(tags = "Soutiens")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public SupportResponse support(@PathVariable Long id) {
        return needService.support(id);
    }

    @DeleteMapping("/{id}/soutiens")
    @Operation(tags = "Soutiens")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsupport(@PathVariable Long id) {
        needService.unsupport(id);
    }

    @GetMapping("/{id}/soutiens")
    @Operation(tags = "Soutiens")
    @PreAuthorize("isAuthenticated()")
    public List<SupportEntryResponse> listSupports(@PathVariable Long id) {
        return needService.listSupports(id);
    }

    @GetMapping("/{id}/soutiens/count")
    @Operation(tags = "Soutiens")
    public SupportResponse supportCount(@PathVariable Long id) {
        return needService.supportCount(id);
    }
}
