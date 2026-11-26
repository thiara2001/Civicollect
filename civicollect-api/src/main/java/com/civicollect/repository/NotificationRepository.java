package com.civicollect.repository;

import com.civicollect.domain.Notification;
import com.civicollect.domain.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(Long userId, NotificationChannel channel, Pageable pageable);

    long countByRecipientIdAndChannelAndReadFalse(Long userId, NotificationChannel channel);
}
