package com.civicollect.service;

import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import com.civicollect.dto.NeedDtos.*;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.NeedRepository;
import com.civicollect.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Page publique de transparence et suivi par numéro (RF11, RF30). Aucune donnée personnelle n'est exposée. */
@Service
@RequiredArgsConstructor
public class PublicService {

    private final NeedRepository needs;
    private final NeedService needService;

    @Transactional(readOnly = true)
    public Page<PublicNeedResponse> publicNeeds(NeedFilter filter, Pageable pageable) {
        Pageable p = PageUtils.sanitize(pageable, PageUtils.NEED_SORTS, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Specification<Need> spec = Specification.where(NeedSpecifications.statusIn(NeedStatus.publicStatuses()))
                .and(NeedSpecifications.filter(filter));
        Page<Need> page = needs.findAll(spec, p);
        Map<Long, Long> counts = needService.supportCounts(page.getContent().stream().map(Need::getId).toList());
        return page.map(n -> NeedMapper.toPublic(n, counts.getOrDefault(n.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public List<MapPoint> publicMap(NeedFilter filter) {
        Specification<Need> spec = Specification.where(NeedSpecifications.statusIn(NeedStatus.publicStatuses()))
                .and(NeedSpecifications.hasCoordinates()).and(NeedSpecifications.filter(filter));
        return needs.findAll(spec).stream()
                .map(n -> new MapPoint(n.getId(), n.getTitle(), n.getLatitude(), n.getLongitude(),
                        n.getCategory() != null ? n.getCategory().getName() : null, n.getStatus(), n.getUrgency(), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public TrackingResponse track(String trackingNumber) {
        Need n = needs.findByTrackingNumber(trackingNumber.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("Numéro de suivi inconnu"));
        return NeedService.toTracking(n);
    }
}
