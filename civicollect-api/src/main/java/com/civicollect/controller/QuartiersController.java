package com.civicollect.controller;

import com.civicollect.dto.AdminDtos.ReferenceRequest;
import com.civicollect.dto.AdminDtos.ZoneResponse;
import com.civicollect.service.ReferenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des quartiers – /api/quartiers (RF33).
 * Lecture publique ; écriture réservée aux ADMIN.
 */
@Tag(name = "Quartiers")
@RestController
@RequestMapping("/api/quartiers")
@RequiredArgsConstructor
public class QuartiersController {

    private final ReferenceService referenceService;

    @GetMapping
    public List<ZoneResponse> list() {
        return referenceService.listQuartiers();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneResponse> create(@Valid @RequestBody ReferenceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createQuartier(r));
    }

    @GetMapping("/{id}")
    public ZoneResponse get(@PathVariable Long id) {
        return referenceService.getQuartier(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneResponse update(@PathVariable Long id, @Valid @RequestBody ReferenceRequest r) {
        return referenceService.updateQuartier(id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        referenceService.deleteQuartier(id);
    }
}
