package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "attachments")
@Getter
@Setter
public class Attachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Need need;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String contentType;

    @Column(name = "size_bytes")
    private long sizeBytes;

    /** Chemin relatif au répertoire de stockage (jamais fourni par le client). */
    @Column(nullable = false)
    private String storagePath;
}
