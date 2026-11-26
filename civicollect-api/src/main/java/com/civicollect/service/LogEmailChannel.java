package com.civicollect.service;

import com.civicollect.domain.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Implémentation par défaut : journalise l'e-mail. À remplacer par JavaMailSender (SMTP) en production. */
@Slf4j
@Component
public class LogEmailChannel implements MessageChannel {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[EMAIL] -> {} | {}", mask(recipient), subject);
    }

    static String mask(String s) {
        if (s == null || s.length() < 4) return "***";
        return s.charAt(0) + "***" + s.substring(s.length() - 2);
    }
}
