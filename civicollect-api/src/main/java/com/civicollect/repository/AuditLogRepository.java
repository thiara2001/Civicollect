package com.civicollect.repository;

import com.civicollect.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop10ByOrderByIdDesc();
}
