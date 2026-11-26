package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.AdminDtos.*;
import com.civicollect.dto.AuthDtos.UserResponse;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.ConflictException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.*;
import com.civicollect.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Administration : utilisateurs, rôles, services, référentiels, modèles de messages, audit (RF32-RF35). */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository users;
    private final MunicipalServiceRepository municipalServices;
    private final CategoryRepository categories;
    private final ZoneRepository zones;
    private final NotificationTemplateRepository templates;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    // ---------------- utilisateurs (RF32) ----------------

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), 100)),
                Sort.by("id"));
        return users.findAll(p).map(com.civicollect.service.AuthService::toUserResponse);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest r) {
        String email = TextUtils.blankToNull(r.email());
        String phone = TextUtils.blankToNull(r.phone());
        if (email == null && phone == null) throw new BusinessRuleException("Un e-mail ou un téléphone est obligatoire");
        if (email != null && users.existsByEmailIgnoreCase(email)) throw new ConflictException("E-mail déjà utilisé");
        if (phone != null && users.existsByPhone(phone)) throw new ConflictException("Téléphone déjà utilisé");
        User u = new User();
        u.setFullName(r.fullName().trim());
        u.setEmail(email != null ? email.toLowerCase() : null);
        u.setPhone(phone);
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u.setRole(r.role());
        u.setHabilitated(r.habilitated() && r.role() == Role.ELU);
        if (r.municipalServiceId() != null) u.setMunicipalService(findService(r.municipalServiceId()));
        users.save(u);
        audit.log("USER_CREATED", "User", u.getId(), "role=" + u.getRole());
        return AuthService.toUserResponse(u);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest r) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        if (r.role() != null) u.setRole(r.role());
        if (r.municipalServiceId() != null) u.setMunicipalService(findService(r.municipalServiceId()));
        if (r.enabled() != null) u.setEnabled(r.enabled());
        if (r.habilitated() != null) u.setHabilitated(r.habilitated() && u.getRole() == Role.ELU);
        audit.log("USER_UPDATED", "User", id, "role=" + u.getRole() + ", enabled=" + u.isEnabled()
                + ", habilitated=" + u.isHabilitated());
        return AuthService.toUserResponse(u);
    }

    // ---------------- référentiels (RF33) ----------------

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAll(Sort.by("name")).stream().map(AdminService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(ReferenceRequest r) {
        Category c = new Category();
        c.setName(r.name().trim());
        c.setDescription(r.description());
        categories.save(c);
        audit.log("CATEGORY_CREATED", "Category", c.getId(), c.getName());
        return toResponse(c);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, ReferenceRequest r) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        c.setName(r.name().trim());
        c.setDescription(r.description());
        audit.log("CATEGORY_UPDATED", "Category", id, c.getName());
        return toResponse(c);
    }

    /** Désactivation logique : les besoins existants conservent leur catégorie. */
    @Transactional
    public void deactivateCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        c.setActive(false);
        audit.log("CATEGORY_DEACTIVATED", "Category", id, c.getName());
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> listZones() {
        return zones.findAllByOrderByNameAsc().stream().map(AdminService::toResponse).toList();
    }

    @Transactional
    public ZoneResponse createZone(ReferenceRequest r) {
        Zone z = new Zone();
        applyZone(z, r);
        zones.save(z);
        audit.log("ZONE_CREATED", "Zone", z.getId(), z.getName());
        return toResponse(z);
    }

    @Transactional
    public ZoneResponse updateZone(Long id, ReferenceRequest r) {
        Zone z = zones.findById(id).orElseThrow(() -> new NotFoundException("Zone introuvable"));
        applyZone(z, r);
        audit.log("ZONE_UPDATED", "Zone", id, z.getName());
        return toResponse(z);
    }

    @Transactional
    public void deleteZone(Long id) {
        Zone z = zones.findById(id).orElseThrow(() -> new NotFoundException("Zone introuvable"));
        zones.delete(z);                    // refusé (409) si des besoins y sont rattachés
        audit.log("ZONE_DELETED", "Zone", id, z.getName());
    }

    @Transactional(readOnly = true)
    public List<com.civicollect.dto.NeedDtos.NamedRef> listServices() {
        return municipalServices.findAllByOrderByNameAsc().stream().map(NeedMapper::ref).toList();
    }

    @Transactional
    public com.civicollect.dto.NeedDtos.NamedRef createService(ReferenceRequest r) {
        MunicipalService s = new MunicipalService();
        s.setName(r.name().trim());
        municipalServices.save(s);
        audit.log("SERVICE_CREATED", "MunicipalService", s.getId(), s.getName());
        return NeedMapper.ref(s);
    }

    @Transactional
    public com.civicollect.dto.NeedDtos.NamedRef updateService(Long id, ReferenceRequest r) {
        MunicipalService s = findService(id);
        s.setName(r.name().trim());
        audit.log("SERVICE_UPDATED", "MunicipalService", id, s.getName());
        return NeedMapper.ref(s);
    }

    @Transactional
    public void deleteService(Long id) {
        MunicipalService s = findService(id);
        municipalServices.delete(s);
        audit.log("SERVICE_DELETED", "MunicipalService", id, s.getName());
    }

    // ---------------- modèles de notification (RF35) ----------------

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates() {
        return Arrays.stream(NeedStatus.values())
                .map(st -> templates.findByStatus(st).map(t -> new TemplateResponse(st, t.getSubject(), t.getBody())).orElse(null))
                .filter(t -> t != null)
                .toList();
    }

    @Transactional
    public TemplateResponse upsertTemplate(NeedStatus status, TemplateRequest r) {
        NotificationTemplate t = templates.findByStatus(status).orElseGet(() -> {
            NotificationTemplate n = new NotificationTemplate();
            n.setStatus(status);
            return n;
        });
        t.setSubject(r.subject());
        t.setBody(r.body());
        templates.save(t);
        audit.log("TEMPLATE_UPDATED", "NotificationTemplate", t.getId(), status.name());
        return new TemplateResponse(status, t.getSubject(), t.getBody());
    }

    // ---------------- audit (RF34) ----------------

    @Transactional(readOnly = true)
    public Page<AuditEntry> auditLog(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), 200)),
                Sort.by(Sort.Direction.DESC, "id"));
        return auditLogs.findAll(p).map(a -> new AuditEntry(a.getId(), a.getActor(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getCreatedAt()));
    }

    // ---------------------------------------------------------------

    private MunicipalService findService(Long id) {
        return municipalServices.findById(id).orElseThrow(() -> new NotFoundException("Service municipal introuvable"));
    }

    private static void applyZone(Zone z, ReferenceRequest r) {
        z.setName(r.name().trim());
        z.setLatitude(r.latitude());
        z.setLongitude(r.longitude());
        z.setPopulation(r.population());
    }

    static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }

    static ZoneResponse toResponse(Zone z) {
        return new ZoneResponse(z.getId(), z.getName(), z.getLatitude(), z.getLongitude(), z.getPopulation());
    }
}
