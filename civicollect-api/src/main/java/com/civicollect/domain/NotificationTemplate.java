package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Modèle de message par statut (RF35). Variables : {trackingNumber}, {title}, {status}, {reason}. */
@Entity
@Table(name = "notification_templates")
@Getter
@Setter
public class NotificationTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private NeedStatus status;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;
}
