package com.civicollect.repository;

import com.civicollect.domain.Notification;
import com.civicollect.domain.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(Long userId, NotificationChannel channel, Pageable pageable);

    Page<Notification> findByRecipientIdAndChannelAndReadFalseOrderByCreatedAtDesc(Long userId, NotificationChannel channel, Pageable pageable);

    long countByRecipientIdAndChannelAndReadFalse(Long userId, NotificationChannel channel);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipient.id = :userId and n.channel = :channel and n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("channel") NotificationChannel channel);

    void deleteByNeedId(Long needId);

    void deleteByRecipientId(Long userId);
}
