package com.civicollect.service;

import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import com.civicollect.dto.NeedDtos.NeedFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Critères de recherche composables (pattern Specification) : filtres + recherche textuelle (RF21). */
public final class NeedSpecifications {
    private NeedSpecifications() {}

    public static Specification<Need> filter(NeedFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f != null) {
                if (f.q() != null && !f.q().isBlank()) {
                    String like = "%" + f.q().trim().toLowerCase()
                            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                    p.add(cb.or(
                            cb.like(cb.lower(root.get("title")), like, '\\'),
                            cb.like(cb.lower(root.get("description")), like, '\\'),
                            cb.like(cb.lower(root.get("trackingNumber")), like, '\\')));
                }
                if (f.categoryId() != null) p.add(cb.equal(root.get("category").get("id"), f.categoryId()));
                if (f.zoneId() != null) p.add(cb.equal(root.get("zone").get("id"), f.zoneId()));
                if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
                if (f.urgency() != null) p.add(cb.equal(root.get("urgency"), f.urgency()));
                if (f.requesterType() != null) p.add(cb.equal(root.get("requesterType"), f.requesterType()));
                if (f.from() != null) {
                    p.add(cb.greaterThanOrEqualTo(root.<Instant>get("submittedAt"),
                            f.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
                }
                if (f.to() != null) {
                    p.add(cb.lessThan(root.<Instant>get("submittedAt"),
                            f.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
                }
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** Vue du personnel : tout sauf les brouillons et les besoins fusionnés. */
    public static Specification<Need> staffVisible() {
        return (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), NeedStatus.BROUILLON),
                cb.isNull(root.get("mergedInto")));
    }

    public static Specification<Need> statusIn(Collection<NeedStatus> statuses) {
        return (root, query, cb) -> cb.and(root.get("status").in(statuses), cb.isNull(root.get("mergedInto")));
    }

    public static Specification<Need> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
    }

    public static Specification<Need> hasCoordinates() {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("latitude")), cb.isNotNull(root.get("longitude")));
    }
}
