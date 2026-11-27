package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Proposition persistée de doublon entre deux contributions, permettant confirmation ou rejet par un agent. */
@Entity
@Table(name = "duplicate_proposals", uniqueConstraints = @UniqueConstraint(
        name = "uk_duplicate_pair", columnNames = {"source_need_id", "target_need_id"}))
@Getter
@Setter
public class DuplicateProposal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_need_id", nullable = false)
    private Need sourceNeed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_need_id", nullable = false)
    private Need targetNeed;

    @Column(nullable = false)
    private double similarity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DuplicateStatus status = DuplicateStatus.POTENTIAL;

    @ManyToOne(fetch = FetchType.LAZY)
    private User reviewedBy;

    private Instant reviewedAt;
}
