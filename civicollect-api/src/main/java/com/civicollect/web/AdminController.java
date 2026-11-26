package com.civicollect.web;

import com.civicollect.domain.NeedStatus;
import com.civicollect.dto.AdminDtos.*;
import com.civicollect.dto.AuthDtos.UserResponse;
import com.civicollect.dto.NeedDtos.NamedRef;
import com.civicollect.service.AdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Toutes les routes /api/admin/** exigent le rôle ADMIN (voir SecurityConfig). */
@Tag(name = "Administration")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService admin;

    // ---- utilisateurs
    @GetMapping("/users")
    public Page<UserResponse> users(Pageable pageable) { return admin.listUsers(pageable); }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createUser(r));
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        return admin.updateUser(id, r);
    }

    // ---- catégories
    @GetMapping("/categories")
    public List<CategoryResponse> categories() { return admin.listCategories(); }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody ReferenceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createCategory(r));
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody ReferenceRequest r) {
        return admin.updateCategory(id, r);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCategory(@PathVariable Long id) { admin.deactivateCategory(id); }

    // ---- zones / quartiers
    @GetMapping("/zones")
    public List<ZoneResponse> zones() { return admin.listZones(); }

    @PostMapping("/zones")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ReferenceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createZone(r));
    }

    @PutMapping("/zones/{id}")
    public ZoneResponse updateZone(@PathVariable Long id, @Valid @RequestBody ReferenceRequest r) {
        return admin.updateZone(id, r);
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZone(@PathVariable Long id) { admin.deleteZone(id); }

    // ---- services municipaux
    @GetMapping("/services")
    public List<NamedRef> services() { return admin.listServices(); }

    @PostMapping("/services")
    public ResponseEntity<NamedRef> createService(@Valid @RequestBody ReferenceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createService(r));
    }

    @PutMapping("/services/{id}")
    public NamedRef updateService(@PathVariable Long id, @Valid @RequestBody ReferenceRequest r) {
        return admin.updateService(id, r);
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long id) { admin.deleteService(id); }

    // ---- modèles de messages
    @GetMapping("/notification-templates")
    public List<TemplateResponse> templates() { return admin.listTemplates(); }

    @PutMapping("/notification-templates/{status}")
    public TemplateResponse upsertTemplate(@PathVariable NeedStatus status, @Valid @RequestBody TemplateRequest r) {
        return admin.upsertTemplate(status, r);
    }

    // ---- audit
    @GetMapping("/audit")
    public Page<AuditEntry> audit(Pageable pageable) { return admin.auditLog(pageable); }
}
