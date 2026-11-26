package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.AuthDtos.*;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.ConflictException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.OrganizationRepository;
import com.civicollect.repository.PasswordResetTokenRepository;
import com.civicollect.repository.UserRepository;
import com.civicollect.security.CurrentUserProvider;
import com.civicollect.security.JwtService;
import com.civicollect.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/** Inscription, connexion, profil et mot de passe (RF1, RF2, RF5). */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUser;
    private final NotificationService notificationService;
    private final AuditService audit;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        String email = TextUtils.blankToNull(r.email());
        String phone = TextUtils.blankToNull(r.phone());
        if (email == null && phone == null) {
            throw new BusinessRuleException("Un e-mail ou un numéro de téléphone est obligatoire");
        }
        assertAvailable(email, phone, null);

        User u = new User();
        u.setFullName(r.fullName().trim());
        u.setEmail(email != null ? email.toLowerCase() : null);
        u.setPhone(phone);
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u.setRequesterType(r.requesterType());
        // Seuls les comptes citoyens s'auto-inscrivent ; agents/élus/admins sont créés par un administrateur (RF2, RF3).
        u.setRole(r.requesterType() == RequesterType.HABITANT ? Role.HABITANT : Role.ASSOCIATION);
        u.setConsentGivenAt(Instant.now());
        if (r.requesterType() != RequesterType.HABITANT) {
            if (r.organizationName() == null || r.organizationName().isBlank()) {
                throw new BusinessRuleException("Le nom de l'organisation est obligatoire");
            }
            Organization org = new Organization();
            org.setName(r.organizationName().trim());
            org.setType(r.requesterType());
            org.setResponsibleName(u.getFullName());
            u.setOrganization(organizations.save(org));
        }
        users.save(u);
        audit.log("USER_REGISTERED", "User", u.getId(), "role=" + u.getRole());
        return toAuthResponse(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(r.identifier().trim(), r.password()));
        User u = users.findByEmailIgnoreCaseOrPhone(r.identifier().trim(), r.identifier().trim())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        return toAuthResponse(u);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return toUserResponse(users.findById(currentUser.require().getId()).orElseThrow());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest r) {
        User u = users.findById(currentUser.require().getId()).orElseThrow();
        String email = TextUtils.blankToNull(r.email());
        String phone = TextUtils.blankToNull(r.phone());
        if (email == null && phone == null) {
            throw new BusinessRuleException("Un e-mail ou un numéro de téléphone est obligatoire");
        }
        assertAvailable(email, phone, u.getId());
        u.setFullName(r.fullName().trim());
        u.setEmail(email != null ? email.toLowerCase() : null);
        u.setPhone(phone);
        return toUserResponse(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest r) {
        User u = users.findById(currentUser.require().getId()).orElseThrow();
        if (!passwordEncoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw new BusinessRuleException("Mot de passe actuel incorrect");
        }
        u.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        audit.log("PASSWORD_CHANGED", "User", u.getId(), null);
    }

    /** Ne révèle jamais si le compte existe (réponse identique). Le jeton n'est stocké que sous forme hachée. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest r) {
        String id = r.identifier().trim();
        users.findByEmailIgnoreCaseOrPhone(id, id).ifPresent(u -> {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = HexFormat.of().formatHex(raw);
            PasswordResetToken t = new PasswordResetToken();
            t.setUser(u);
            t.setTokenHash(sha256(token));
            t.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
            resetTokens.save(t);
            notificationService.sendDirect(u, "Réinitialisation de votre mot de passe CiviCollect",
                    "Code de réinitialisation (valable 30 minutes) : " + token);
            audit.log("PASSWORD_RESET_REQUESTED", "User", u.getId(), null);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest r) {
        PasswordResetToken t = resetTokens.findByTokenHash(sha256(r.token()))
                .orElseThrow(() -> new BusinessRuleException("Jeton invalide ou expiré"));
        if (t.isUsed() || t.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Jeton invalide ou expiré");
        }
        t.setUsed(true);
        t.getUser().setPasswordHash(passwordEncoder.encode(r.newPassword()));
        audit.log("PASSWORD_RESET_DONE", "User", t.getUser().getId(), null);
    }

    // ---------------------------------------------------------------

    private void assertAvailable(String email, String phone, Long selfId) {
        if (email != null) {
            users.findByEmailIgnoreCase(email).ifPresent(o -> {
                if (!o.getId().equals(selfId)) throw new ConflictException("Cette adresse e-mail est déjà utilisée");
            });
        }
        if (phone != null) {
            users.findByPhone(phone).ifPresent(o -> {
                if (!o.getId().equals(selfId)) throw new ConflictException("Ce numéro de téléphone est déjà utilisé");
            });
        }
    }

    private AuthResponse toAuthResponse(User u) {
        return new AuthResponse(jwtService.generate(u), "Bearer", jwtService.getExpirationMinutes(), toUserResponse(u));
    }

    public static UserResponse toUserResponse(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.getRequesterType(), u.getOrganization() != null ? u.getOrganization().getName() : null,
                u.getMunicipalService() != null ? u.getMunicipalService().getId() : null,
                u.isHabilitated(), u.isEnabled());
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
