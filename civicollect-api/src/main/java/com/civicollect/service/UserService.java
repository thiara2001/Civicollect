package com.civicollect.service;

import com.civicollect.domain.MunicipalService;
import com.civicollect.domain.Role;
import com.civicollect.domain.User;
import com.civicollect.dto.AdminDtos.*;
import com.civicollect.dto.AuthDtos.UserResponse;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.ConflictException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.*;
import com.civicollect.security.CurrentUserProvider;
import com.civicollect.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des comptes utilisateurs (SRP) : création, consultation, modification, statut, rôle et suppression.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final MunicipalServiceRepository municipalServices;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final NotificationRepository notifications;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), 100)),
                Sort.by("id"));
        return users.findAll(p).map(AuthService::toUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return AuthService.toUserResponse(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest r) {
        String email = TextUtils.blankToNull(r.email());
        String phone = TextUtils.blankToNull(r.phone());
        if (email == null && phone == null) {
            throw new BusinessRuleException("Un e-mail ou un téléphone est obligatoire");
        }
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail déjà utilisé");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw new ConflictException("Téléphone déjà utilisé");
        }
        User u = new User();
        u.setFullName(r.fullName().trim());
        u.setEmail(email != null ? email.toLowerCase() : null);
        u.setPhone(phone);
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u.setRole(r.role());
        u.setHabilitated(r.habilitated() && r.role() == Role.ELU);
        if (r.municipalServiceId() != null) {
            u.setMunicipalService(findService(r.municipalServiceId()));
        }
        users.save(u);
        audit.log("USER_CREATED", "User", u.getId(), "role=" + u.getRole());
        return AuthService.toUserResponse(u);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest r) {
        User u = find(id);
        if (r.role() != null) u.setRole(r.role());
        if (r.municipalServiceId() != null) u.setMunicipalService(findService(r.municipalServiceId()));
        if (r.enabled() != null) u.setEnabled(r.enabled());
        if (r.habilitated() != null) u.setHabilitated(r.habilitated() && u.getRole() == Role.ELU);
        audit.log("USER_UPDATED", "User", id, "role=" + u.getRole() + ", enabled=" + u.isEnabled()
                + ", habilitated=" + u.isHabilitated());
        return AuthService.toUserResponse(u);
    }

    @Transactional
    public UserResponse updateFull(Long id, UpdateUserFullRequest r) {
        User u = find(id);
        if (r.fullName() != null && !r.fullName().isBlank()) {
            u.setFullName(r.fullName().trim());
        }
        if (r.email() != null) {
            String email = TextUtils.blankToNull(r.email());
            if (email != null) {
                users.findByEmailIgnoreCase(email).ifPresent(other -> {
                    if (!other.getId().equals(id)) throw new ConflictException("E-mail déjà utilisé");
                });
                u.setEmail(email.toLowerCase());
            }
        }
        if (r.phone() != null) {
            String phone = TextUtils.blankToNull(r.phone());
            if (phone != null) {
                users.findByPhone(phone).ifPresent(other -> {
                    if (!other.getId().equals(id)) throw new ConflictException("Téléphone déjà utilisé");
                });
                u.setPhone(phone);
            }
        }
        if (r.role() != null) u.setRole(r.role());
        if (r.municipalServiceId() != null) u.setMunicipalService(findService(r.municipalServiceId()));
        if (r.enabled() != null) u.setEnabled(r.enabled());
        if (r.habilitated() != null) u.setHabilitated(r.habilitated() && u.getRole() == Role.ELU);
        audit.log("USER_UPDATED", "User", id, "role=" + u.getRole() + ", enabled=" + u.isEnabled());
        return AuthService.toUserResponse(u);
    }

    @Transactional
    public UserResponse updateStatus(Long id, boolean enabled) {
        User u = find(id);
        u.setEnabled(enabled);
        if (!enabled) {
            refreshTokens.findByUserIdAndRevokedFalse(id).forEach(t -> t.setRevoked(true));
        }
        audit.log("USER_STATUS_CHANGED", "User", id, "enabled=" + enabled);
        return AuthService.toUserResponse(u);
    }

    @Transactional
    public UserResponse updateRole(Long id, Role role, Boolean habilitated) {
        User u = find(id);
        u.setRole(role);
        if (habilitated != null) {
            u.setHabilitated(habilitated && role == Role.ELU);
        } else if (role != Role.ELU) {
            u.setHabilitated(false);
        }
        audit.log("USER_ROLE_CHANGED", "User", id, "role=" + role + ", habilitated=" + u.isHabilitated());
        return AuthService.toUserResponse(u);
    }

    @Transactional
    public void delete(Long id) {
        Long actorId = currentUser.find().map(a -> a.getId()).orElse(null);
        if (id.equals(actorId)) {
            throw new BusinessRuleException("Impossible de supprimer votre propre compte");
        }
        User u = find(id);
        refreshTokens.deleteByUserId(id);
        resetTokens.deleteByUserId(id);
        notifications.deleteByRecipientId(id);
        users.delete(u);
        users.flush();
        audit.log("USER_DELETED", "User", id, "deleted");
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
    }

    private MunicipalService findService(Long id) {
        return municipalServices.findById(id).orElseThrow(() -> new NotFoundException("Service municipal introuvable"));
    }
}
