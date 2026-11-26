package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Journal d'audit des actions sensibles (RF34). */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog extends BaseEntity {

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 80)
    private String entityType;

    private Long entityId;

    @Column(length = 1000)
    private String details;
}
