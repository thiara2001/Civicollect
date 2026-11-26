package com.civicollect.repository;

import com.civicollect.domain.NeedStatus;
import com.civicollect.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByStatus(NeedStatus status);
}
