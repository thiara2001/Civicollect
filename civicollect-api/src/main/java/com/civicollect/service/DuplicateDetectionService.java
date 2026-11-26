package com.civicollect.service;

import com.civicollect.config.AppProperties;
import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import com.civicollect.dto.NeedDtos.DuplicateCandidate;
import com.civicollect.repository.NeedRepository;
import com.civicollect.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Détection de doublons (RF14) : similarité de Jaccard sur les jetons titre + description, bonus même zone. */
@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private static final double SAME_ZONE_BONUS = 0.10;

    private final NeedRepository needRepository;
    private final AppProperties props;

    /** Similarité de Jaccard entre deux textes, dans [0, 1]. */
    public static double jaccard(String a, String b) {
        Set<String> ta = TextUtils.tokens(a);
        Set<String> tb = TextUtils.tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    public static double similarity(Need a, Need b) {
        double s = jaccard(a.getTitle() + " " + a.getDescription(), b.getTitle() + " " + b.getDescription());
        boolean sameZone = a.getZone() != null && b.getZone() != null && a.getZone().getId().equals(b.getZone().getId());
        return Math.min(1.0, s + (sameZone ? SAME_ZONE_BONUS : 0.0));
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidate> findCandidates(Need need) {
        if (need.getCategory() == null) return List.of();
        Set<NeedStatus> statuses = EnumSet.of(NeedStatus.RECU, NeedStatus.EN_ANALYSE, NeedStatus.RETENU,
                NeedStatus.PLANIFIE, NeedStatus.EN_COURS);
        double threshold = props.duplicates().threshold();
        return needRepository
                .findTop200ByCategoryIdAndIdNotAndMergedIntoIsNullAndStatusInOrderBySubmittedAtDesc(
                        need.getCategory().getId(), need.getId(), statuses)
                .stream()
                .map(o -> new DuplicateCandidate(o.getId(), o.getTrackingNumber(), o.getTitle(), o.getStatus(), similarity(need, o)))
                .filter(c -> c.similarity() >= threshold)
                .sorted(Comparator.comparingDouble(DuplicateCandidate::similarity).reversed())
                .limit(10)
                .toList();
    }
}
