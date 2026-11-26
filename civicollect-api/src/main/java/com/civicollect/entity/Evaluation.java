package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Valeur (0-10) d'un critère manuel pour un besoin (gravité, impact social, impact sanitaire...). */
@Entity
@Table(name = "evaluations", uniqueConstraints = @UniqueConstraint(name = "uk_eval_need_criterion", columnNames = {"need_id", "criterion_id"}))
@Getter
@Setter
public class Evaluation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Need need;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Criterion criterion;

    @Column(name = "score_value", nullable = false)
    private double scoreValue;

    @ManyToOne(fetch = FetchType.LAZY)
    private User evaluatedBy;
}
