package com.civicollect.web;

import com.civicollect.domain.NeedStatus;
import com.civicollect.domain.RequesterType;
import com.civicollect.domain.Urgency;
import com.civicollect.dto.AdminDtos.CategoryResponse;
import com.civicollect.dto.AdminDtos.ZoneResponse;
import com.civicollect.repository.CategoryRepository;
import com.civicollect.repository.ZoneRepository;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Référentiels en lecture publique, utilisés pour alimenter les formulaires. */
@Tag(name = "Référentiels")
@Hidden
@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final CategoryRepository categories;
    private final ZoneRepository zones;

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive())).toList();
    }

    @GetMapping("/zones")
    @Transactional(readOnly = true)
    public List<ZoneResponse> zones() {
        return zones.findAllByOrderByNameAsc().stream()
                .map(z -> new ZoneResponse(z.getId(), z.getName(), z.getLatitude(), z.getLongitude(), z.getPopulation())).toList();
    }

    @GetMapping("/statuses")
    public List<Map<String, String>> statuses() {
        return Arrays.stream(NeedStatus.values()).map(s -> Map.of("code", s.name(), "label", s.getLabel())).toList();
    }

    @GetMapping("/urgencies")
    public List<String> urgencies() {
        return Arrays.stream(Urgency.values()).map(Enum::name).toList();
    }

    @GetMapping("/requester-types")
    public List<String> requesterTypes() {
        return Arrays.stream(RequesterType.values()).map(Enum::name).toList();
    }
}
