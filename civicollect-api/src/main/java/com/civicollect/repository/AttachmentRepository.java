package com.civicollect.repository;

import com.civicollect.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByNeedId(Long needId);

    long countByNeedId(Long needId);

    void deleteByNeedId(Long needId);
}
