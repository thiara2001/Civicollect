package com.civicollect.service;

import com.civicollect.config.AppProperties;
import com.civicollect.domain.SystemSetting;
import com.civicollect.dto.AdminDtos.SystemSettingsResponse;
import com.civicollect.dto.AdminDtos.UpdateSystemSettingsRequest;
import com.civicollect.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion centralisée des paramètres système persistés (avec valeurs par défaut issues de {@link AppProperties}).
 */
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    public static final String KEY_SUPPORT_CAP = "scoring.supportCap";
    public static final String KEY_DUPLICATES_THRESHOLD = "duplicates.threshold";
    public static final String KEY_RATE_LIMIT = "rateLimit.perHour";
    public static final String KEY_MAX_FILE_SIZE = "storage.maxFileSizeBytes";

    private final SystemSettingRepository repository;
    private final AppProperties props;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public int getSupportCap() {
        return getInt(KEY_SUPPORT_CAP, props.scoring().supportCap());
    }

    @Transactional(readOnly = true)
    public double getDuplicatesThreshold() {
        return getDouble(KEY_DUPLICATES_THRESHOLD, props.duplicates().threshold());
    }

    @Transactional(readOnly = true)
    public int getRateLimitPerHour() {
        return getInt(KEY_RATE_LIMIT, props.rateLimit().perHour());
    }

    @Transactional(readOnly = true)
    public long getMaxFileSizeBytes() {
        return getLong(KEY_MAX_FILE_SIZE, props.storage().maxFileSizeBytes());
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse currentSettings() {
        return new SystemSettingsResponse(
                getSupportCap(),
                getDuplicatesThreshold(),
                getRateLimitPerHour(),
                getMaxFileSizeBytes(),
                props.jwt().expirationMinutes());
    }

    @Transactional
    public SystemSettingsResponse updateSettings(UpdateSystemSettingsRequest r) {
        if (r.scoringSupportCap() != null) {
            save(KEY_SUPPORT_CAP, String.valueOf(r.scoringSupportCap()), "Plafond de soutiens dans le calcul de priorité");
        }
        if (r.duplicatesThreshold() != null) {
            save(KEY_DUPLICATES_THRESHOLD, String.valueOf(r.duplicatesThreshold()), "Seuil minimal de similarité pour les doublons");
        }
        if (r.rateLimitPerHour() != null) {
            save(KEY_RATE_LIMIT, String.valueOf(r.rateLimitPerHour()), "Nombre maximal de requêtes publiques par heure");
        }
        if (r.maxFileSizeBytes() != null) {
            save(KEY_MAX_FILE_SIZE, String.valueOf(r.maxFileSizeBytes()), "Taille maximale des pièces jointes en octets");
        }
        audit.log("SETTINGS_UPDATED", "SystemSetting", null, "Paramètres système mis à jour");
        return currentSettings();
    }

    private void save(String key, String value, String description) {
        SystemSetting setting = repository.findBySettingKey(key).orElseGet(() -> {
            SystemSetting s = new SystemSetting();
            s.setSettingKey(key);
            return s;
        });
        setting.setSettingValue(value);
        setting.setDescription(description);
        repository.save(setting);
    }

    private int getInt(String key, int fallback) {
        return repository.findBySettingKey(key)
                .map(s -> Integer.parseInt(s.getSettingValue()))
                .orElse(fallback);
    }

    private long getLong(String key, long fallback) {
        return repository.findBySettingKey(key)
                .map(s -> Long.parseLong(s.getSettingValue()))
                .orElse(fallback);
    }

    private double getDouble(String key, double fallback) {
        return repository.findBySettingKey(key)
                .map(s -> Double.parseDouble(s.getSettingValue()))
                .orElse(fallback);
    }
}
