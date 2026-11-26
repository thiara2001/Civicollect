package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Historique complet des actions sur un besoin (RF31). */
@Entity
@Table(name = "need_history", indexes = @Index(name = "idx_history_need", columnList = "need_id"))
@Getter
@Setter
public class NeedHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Need need;

    /** Null pour une action anonyme ou système. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User actor;

    @Column(nullable = false, length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    private NeedStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private NeedStatus toStatus;

    @Column(length = 1000)
    private String details;
}
