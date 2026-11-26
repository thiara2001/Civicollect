package com.civicollect.service;

import com.civicollect.domain.Named;
import com.civicollect.domain.Need;
import com.civicollect.dto.NeedDtos.NamedRef;
import com.civicollect.dto.NeedDtos.NeedResponse;
import com.civicollect.dto.NeedDtos.PublicNeedResponse;

/** Conversion entité -> DTO (aucune entité JPA n'est exposée par l'API). À appeler dans une transaction. */
public final class NeedMapper {
    private NeedMapper() {}

    public static NamedRef ref(Named n) {
        return n == null ? null : new NamedRef(n.getId(), n.getName());
    }

    public static NeedResponse toResponse(Need n, long supportCount, boolean supportedByMe) {
        return new NeedResponse(
                n.getId(), n.getTrackingNumber(), n.getTitle(), n.getDescription(),
                ref(n.getCategory()), ref(n.getZone()), n.getAddress(), n.getLatitude(), n.getLongitude(),
                n.getUrgency(), n.getStatus(), n.getRequesterType(), n.isAnonymous(),
                ref(n.getAssignedService()), n.getEstimatedCost(), n.getEstimatedBeneficiaries(),
                n.getEstimatedDelayDays(), n.getPriorityScore(), n.getPriorityAdjustment(),
                n.isConsolidated(), n.getMergedInto() != null ? n.getMergedInto().getId() : null,
                n.isComplementRequested(), n.getRejectionReason(),
                supportCount, supportedByMe, n.getSubmittedAt(), n.getCreatedAt());
    }

    public static PublicNeedResponse toPublic(Need n, long supportCount) {
        return new PublicNeedResponse(
                n.getId(), n.getTrackingNumber(), n.getTitle(), n.getDescription(),
                n.getCategory() != null ? n.getCategory().getName() : null,
                n.getZone() != null ? n.getZone().getName() : null,
                n.getStatus(), n.getStatus().getLabel(), supportCount,
                n.getLatitude(), n.getLongitude(), n.getSubmittedAt());
    }
}
