package com.civicollect.controller;

import com.civicollect.dto.AdminDtos.CategoryResponse;
import com.civicollect.dto.AdminDtos.ReferenceRequest;
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
 * Gestion des catégories – /api/categories (RF33).
 * Lecture publique ; écriture réservée aux ADMIN.
 */
@Tag(name = "Catégories")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoriesController {

    private final ReferenceService referenceService;

    @GetMapping
    public List<CategoryResponse> list() {
        return referenceService.listCategories();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody ReferenceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createCategory(r));
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) {
        return referenceService.getCategory(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody ReferenceRequest r) {
        return referenceService.updateCategory(id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        referenceService.deleteCategory(id);
    }
}
