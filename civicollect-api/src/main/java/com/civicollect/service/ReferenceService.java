package com.civicollect.service;

import com.civicollect.domain.Category;
import com.civicollect.domain.MunicipalService;
import com.civicollect.domain.Zone;
import com.civicollect.dto.AdminDtos.CategoryResponse;
import com.civicollect.dto.AdminDtos.ReferenceRequest;
import com.civicollect.dto.AdminDtos.ZoneResponse;
import com.civicollect.dto.NeedDtos.NamedRef;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.CategoryRepository;
import com.civicollect.repository.MunicipalServiceRepository;
import com.civicollect.repository.NeedRepository;
import com.civicollect.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des référentiels métier (SRP) : catégories, quartiers (zones) et services municipaux.
 */
@Service
@RequiredArgsConstructor
public class ReferenceService {

    private final CategoryRepository categories;
    private final ZoneRepository zones;
    private final MunicipalServiceRepository municipalServices;
    private final NeedRepository needs;
    private final AuditService audit;

    // ---------------- Catégories ----------------

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAll(Sort.by("name")).stream().map(AdminService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        return AdminService.toResponse(c);
    }

    @Transactional
    public CategoryResponse createCategory(ReferenceRequest r) {
        Category c = new Category();
        c.setName(r.name().trim());
        c.setDescription(r.description());
        categories.save(c);
        audit.log("CATEGORY_CREATED", "Category", c.getId(), c.getName());
        return AdminService.toResponse(c);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, ReferenceRequest r) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        c.setName(r.name().trim());
        c.setDescription(r.description());
        audit.log("CATEGORY_UPDATED", "Category", id, c.getName());
        return AdminService.toResponse(c);
    }

    @Transactional
    public void deactivateCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        c.setActive(false);
        audit.log("CATEGORY_DEACTIVATED", "Category", id, c.getName());
    }

    /** Supprime physiquement si aucun besoin n'y est rattaché, sinon désactive logiquement. */
    @Transactional
    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        if (needs.existsByCategoryId(id)) {
            c.setActive(false);
            audit.log("CATEGORY_DEACTIVATED", "Category", id, c.getName());
        } else {
            categories.delete(c);
            audit.log("CATEGORY_DELETED", "Category", id, c.getName());
        }
    }

    // ---------------- Quartiers / Zones ----------------

    @Transactional(readOnly = true)
    public List<ZoneResponse> listQuartiers() {
        return zones.findAllByOrderByNameAsc().stream().map(AdminService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse getQuartier(Long id) {
        Zone z = zones.findById(id).orElseThrow(() -> new NotFoundException("Quartier introuvable"));
        return AdminService.toResponse(z);
    }

    @Transactional
    public ZoneResponse createQuartier(ReferenceRequest r) {
        Zone z = new Zone();
        applyZone(z, r);
        zones.save(z);
        audit.log("ZONE_CREATED", "Zone", z.getId(), z.getName());
        return AdminService.toResponse(z);
    }

    @Transactional
    public ZoneResponse updateQuartier(Long id, ReferenceRequest r) {
        Zone z = zones.findById(id).orElseThrow(() -> new NotFoundException("Quartier introuvable"));
        applyZone(z, r);
        audit.log("ZONE_UPDATED", "Zone", id, z.getName());
        return AdminService.toResponse(z);
    }

    @Transactional
    public void deleteQuartier(Long id) {
        Zone z = zones.findById(id).orElseThrow(() -> new NotFoundException("Quartier introuvable"));
        zones.delete(z);
        audit.log("ZONE_DELETED", "Zone", id, z.getName());
    }

    // ---------------- Services municipaux ----------------

    @Transactional(readOnly = true)
    public List<NamedRef> listServices() {
        return municipalServices.findAllByOrderByNameAsc().stream().map(NeedMapper::ref).toList();
    }

    @Transactional
    public NamedRef createService(ReferenceRequest r) {
        MunicipalService s = new MunicipalService();
        s.setName(r.name().trim());
        municipalServices.save(s);
        audit.log("SERVICE_CREATED", "MunicipalService", s.getId(), s.getName());
        return NeedMapper.ref(s);
    }

    @Transactional
    public NamedRef updateService(Long id, ReferenceRequest r) {
        MunicipalService s = municipalServices.findById(id)
                .orElseThrow(() -> new NotFoundException("Service municipal introuvable"));
        s.setName(r.name().trim());
        audit.log("SERVICE_UPDATED", "MunicipalService", id, s.getName());
        return NeedMapper.ref(s);
    }

    @Transactional
    public void deleteService(Long id) {
        MunicipalService s = municipalServices.findById(id)
                .orElseThrow(() -> new NotFoundException("Service municipal introuvable"));
        municipalServices.delete(s);
        audit.log("SERVICE_DELETED", "MunicipalService", id, s.getName());
    }

    private static void applyZone(Zone z, ReferenceRequest r) {
        z.setName(r.name().trim());
        z.setLatitude(r.latitude());
        z.setLongitude(r.longitude());
        z.setPopulation(r.population());
    }
}
