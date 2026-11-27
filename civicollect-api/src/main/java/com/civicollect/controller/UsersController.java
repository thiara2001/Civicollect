package com.civicollect.controller;

import com.civicollect.dto.AdminDtos.*;
import com.civicollect.dto.AuthDtos.UserResponse;
import com.civicollect.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Gestion des utilisateurs – /api/users (RF32).
 * Pattern Facade : délègue au UserService (SRP).
 */
@Tag(name = "Utilisateurs")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsersController {

    private final UserService userService;

    @GetMapping
    public Page<UserResponse> list(Pageable pageable) {
        return userService.list(pageable);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(r));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PutMapping("/{id}")
    public UserResponse updateFull(@PathVariable Long id, @Valid @RequestBody UpdateUserFullRequest r) {
        return userService.updateFull(id, r);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest r) {
        return userService.updateStatus(id, r.enabled());
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest r) {
        return userService.updateRole(id, r.role(), r.habilitated());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
