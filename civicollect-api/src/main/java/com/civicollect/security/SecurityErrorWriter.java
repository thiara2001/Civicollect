package com.civicollect.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Écrit une erreur JSON (ProblemDetail minimal) pour les rejets émis par les filtres de sécurité. */
final class SecurityErrorWriter {
    private SecurityErrorWriter() {}

    static void write(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String safe = detail.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + safe + "\"}");
    }
}
