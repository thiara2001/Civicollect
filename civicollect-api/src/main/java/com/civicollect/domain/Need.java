package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "needs", indexes = {
        @Index(name = "idx_need_status", columnList = "status"),
        @Index(name = "idx_need_category", columnList = "category_id"),
        @Index(name = "idx_need_zone", columnList = "zone_id"),
        @Index(name = "idx_need_requester", columnList = "requester_id")
})
@Getter
@Setter
public class Need extends BaseEntity {

    @Column(unique = true, length = 30)
    private String trackingNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    private Zone zone;

    private String address;
    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Urgency urgency = Urgency.MOYENNE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NeedStatus status = NeedStatus.BROUILLON;

    /** Demandeur d'origine (RG1) ; null pour une soumission anonyme, une saisie agent ou un besoin consolidé. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User requester;

    @Enumerated(EnumType.STRING)
    private RequesterType requesterType;

    private boolean anonymous;

    private String contactName;
    private String contactPhone;
    private String contactEmail;

    /** Agent ayant saisi le besoin pour une personne non connectée (RF9). */
    @ManyToOne(fetch = FetchType.LAZY)
    private User createdByAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    private MunicipalService assignedService;

    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedCost;
    private Integer estimatedBeneficiaries;
    private Integer estimatedDelayDays;

    /** Score mis en cache par la dernière recalculation (le classement officiel est recalculé à la volée). */
    private Double priorityScore;

    /** Ajustement manuel motivé du décideur, en points (RF26). */
    private double priorityAdjustment;

    private boolean consolidated;

    /** Besoin consolidé dans lequel celui-ci a été fusionné (RG6). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Need mergedInto;

    private boolean complementRequested;

    @Column(length = 1000)
    private String rejectionReason;

    private Instant submittedAt;
}
