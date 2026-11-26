package com.civicollect.service;

import com.civicollect.domain.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Passerelle SMS simulée (RF29). À remplacer par l'API d'un opérateur / agrégateur SMS. */
@Slf4j
@Component
public class LogSmsChannel implements MessageChannel {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[SMS] -> {} | {}", LogEmailChannel.mask(recipient), subject);
    }
}
