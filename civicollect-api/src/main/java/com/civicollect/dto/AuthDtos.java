package com.civicollect.dto;

import com.civicollect.domain.RequesterType;
import com.civicollect.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public static final String PHONE_REGEX = "^\\+?[0-9]{8,15}$";

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 150) String email,
            @Pattern(regexp = PHONE_REGEX, message = "Numéro de téléphone invalide") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères") String password,
            @NotNull RequesterType requesterType,
            @Size(max = 150) String organizationName,
            @AssertTrue(message = "Le consentement au traitement des données est obligatoire") boolean consent) {}

    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}

    public record UserResponse(Long id, String fullName, String email, String phone, Role role,
                               RequesterType requesterType, String organizationName,
                               Long municipalServiceId, boolean habilitated, boolean enabled) {}

    public record AuthResponse(String token, String refreshToken, String tokenType, long expiresInMinutes, UserResponse user) {
        public AuthResponse(String token, String tokenType, long expiresInMinutes, UserResponse user) {
            this(token, null, tokenType, expiresInMinutes, user);
        }
    }

    public record RefreshTokenRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(String refreshToken) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 150) String email,
            @Pattern(regexp = PHONE_REGEX, message = "Numéro de téléphone invalide") String phone) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record ForgotPasswordRequest(@NotBlank String identifier) {}

    public record ResetPasswordRequest(@NotBlank String token,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
