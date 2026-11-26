package com.civicollect.service;

import com.civicollect.domain.AuditLog;
import com.civicollect.repository.AuditLogRepository;
import com.civicollect.security.AuthenticatedUser;
import com.civicollect.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Journal d'audit des actions sensibles (RF34) : écrit dans la même transaction que l'action auditée. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUser;

    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(currentUser.find().map(AuthenticatedUser::getUsername).orElse("anonyme/système"));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details != null && details.length() > 1000 ? details.substring(0, 1000) : details);
        repository.save(entry);
    }
}
