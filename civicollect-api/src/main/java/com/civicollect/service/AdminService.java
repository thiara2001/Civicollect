package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.AdminDtos.*;
import com.civicollect.dto.AuthDtos.UserResponse;
import com.civicollect.dto.NeedDtos.NamedRef;
import com.civicollect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Administration : délégation utilisateurs/référentiels, modèles de messages, audit, indicateurs et paramètres (RF32-RF35). */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final ReferenceService referenceService;
    private final SystemSettingService settingService;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ZoneRepository zones;
    private final MunicipalServiceRepository municipalServices;
    private final NeedRepository needs;
    private final DuplicateProposalRepository duplicateProposals;
    private final NotificationTemplateRepository templates;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;

    // ---------------- utilisateurs (RF32) ----------------

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userService.list(pageable);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest r) {
        return userService.create(r);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest r) {
        return userService.update(id, r);
    }

    // ---------------- référentiels (RF33) ----------------

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return referenceService.listCategories();
    }

    @Transactional
    public CategoryResponse createCategory(ReferenceRequest r) {
        return referenceService.createCategory(r);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, ReferenceRequest r) {
        return referenceService.updateCategory(id, r);
    }

    @Transactional
    public void deactivateCategory(Long id) {
        referenceService.deactivateCategory(id);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> listZones() {
        return referenceService.listQuartiers();
    }

    @Transactional
    public ZoneResponse createZone(ReferenceRequest r) {
        return referenceService.createQuartier(r);
    }

    @Transactional
    public ZoneResponse updateZone(Long id, ReferenceRequest r) {
        return referenceService.updateQuartier(id, r);
    }

    @Transactional
    public void deleteZone(Long id) {
        referenceService.deleteQuartier(id);
    }

    @Transactional(readOnly = true)
    public List<NamedRef> listServices() {
        return referenceService.listServices();
    }

    @Transactional
    public NamedRef createService(ReferenceRequest r) {
        return referenceService.createService(r);
    }

    @Transactional
    public NamedRef updateService(Long id, ReferenceRequest r) {
        return referenceService.updateService(id, r);
    }

    @Transactional
    public void deleteService(Long id) {
        referenceService.deleteService(id);
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

    // ---------------- audit & indicateurs (RF34) ----------------

    @Transactional(readOnly = true)
    public Page<AuditEntry> auditLog(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), 200)),
                Sort.by(Sort.Direction.DESC, "id"));
        return auditLogs.findAll(p).map(AdminService::toAuditEntry);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role r : Role.values()) {
            byRole.put(r.name(), 0L);
        }
        for (Object[] row : users.countByRoleGroup()) {
            if (row[0] != null) {
                byRole.put(((Role) row[0]).name(), ((Number) row[1]).longValue());
            }
        }
        List<AuditEntry> recent = auditLogs.findTop10ByOrderByIdDesc().stream()
                .map(AdminService::toAuditEntry)
                .toList();

        return new AdminDashboardResponse(
                users.count(),
                users.countByEnabledTrue(),
                byRole,
                categories.count(),
                categories.findByActiveTrueOrderByNameAsc().size(),
                zones.count(),
                municipalServices.count(),
                needs.countByMergedIntoIsNull(),
                needs.countByStatusAndMergedIntoIsNull(NeedStatus.RECU),
                duplicateProposals.countByStatus(DuplicateStatus.POTENTIAL),
                auditLogs.count(),
                recent
        );
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse getSettings() {
        return settingService.currentSettings();
    }

    @Transactional
    public SystemSettingsResponse updateSettings(UpdateSystemSettingsRequest r) {
        return settingService.updateSettings(r);
    }

    // ---------------------------------------------------------------

    private static AuditEntry toAuditEntry(AuditLog a) {
        return new AuditEntry(a.getId(), a.getActor(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getCreatedAt());
    }

    static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }

    static ZoneResponse toResponse(Zone z) {
        return new ZoneResponse(z.getId(), z.getName(), z.getLatitude(), z.getLongitude(), z.getPopulation());
    }
}
