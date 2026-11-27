package com.civicollect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Paramètre applicatif configurable par l'administrateur et persisté en base. */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(nullable = false, length = 500)
    private String settingValue;

    @Column(length = 255)
    private String description;
}
