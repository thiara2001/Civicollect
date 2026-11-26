package com.civicollect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Configuration typée de l'application (préfixe "app"). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Scoring scoring, Duplicates duplicates, Storage storage,
                            RateLimit rateLimit, Cors cors, Bootstrap bootstrap) {

    public record Jwt(String secret, long expirationMinutes) {}
    public record Scoring(int supportCap) {}
    public record Duplicates(double threshold) {}
    public record Storage(String uploadDir, long maxFileSizeBytes) {}
    public record RateLimit(int perHour) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Bootstrap(String adminEmail, String adminPassword) {}
}
