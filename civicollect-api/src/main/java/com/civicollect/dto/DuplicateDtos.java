package com.civicollect.dto;

import com.civicollect.domain.DuplicateStatus;
import com.civicollect.domain.NeedStatus;

import java.time.Instant;

public final class DuplicateDtos {
    private DuplicateDtos() {}

    public record DuplicateProposalResponse(
            Long id,
            Long sourceNeedId,
            String sourceTrackingNumber,
            String sourceTitle,
            NeedStatus sourceStatus,
            Long targetNeedId,
            String targetTrackingNumber,
            String targetTitle,
            NeedStatus targetStatus,
            double similarity,
            DuplicateStatus status,
            String reviewedBy,
            Instant reviewedAt,
            Instant createdAt) {}
}
