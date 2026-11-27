package com.civicollect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Trace d'un ajustement manuel motivé du classement (RF26, RG5). */
@Entity
@Table(name = "decisions")
@Getter
@Setter
public class Decision extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Need need;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User decider;

    private double previousAdjustment;
    private double newAdjustment;

    @Column(nullable = false, length = 1000)
    private String reason;
}
