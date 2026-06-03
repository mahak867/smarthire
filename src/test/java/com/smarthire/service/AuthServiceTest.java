// ── SmartHire · src/test/java/com/smarthire/service/AuthServiceTest.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.request.*;
import com.smarthire.api.v1.dto.response.*;
import com.smarthire.domain.entities.*;
import com.smarthire.domain.enums.UserRole;
import com.smarthire.domain.repositories.*;
import com.smarthire.exception.*;
import com.smarthire.security.JwtTokenProvider;
import com.smarthire.util.AuditLogger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — registration, login, refresh, logout")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtTokenProvider tokenProvider;
    @Mock AuditLogger auditLogger;

    AuthService authService;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository,
            tokenProvider, passwordEncoder, auditLogger);
    }

    // ── Registration ────────────────────────────────────────────────────────

    @Test
    @DisplayName("register() with duplicate email throws 409 ConflictException")
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmailIgnoreCase("existing@test.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("existing@test.com", "Password1", "John", "Doe");

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "TestAgent"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("existing@test.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() with valid data creates user with hashed password")
    void register_validData_savesHashedPassword() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u = User.builder().id(UUID.randomUUID()).email(u.getEmail())
                .passwordHash(u.getPasswordHash()).firstName(u.getFirstName())
                .lastName(u.getLastName()).role(UserRole.CANDIDATE).build();
            return u;
        });

        RegisterRequest request = new RegisterRequest("new@test.com", "Password1", "Jane", "Doe");
        UserResponse response = authService.register(request, "127.0.0.1", "TestAgent");

        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.role()).isEqualTo("CANDIDATE");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("Password1");
        assertThat(passwordEncoder.matches("Password1", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("register() normalises email to lowercase")
    void register_emailNormalisedToLowercase() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(UUID.randomUUID()).email(u.getEmail())
                .passwordHash(u.getPasswordHash()).firstName("Test").lastName("User")
                .role(UserRole.CANDIDATE).build();
        });

        authService.register(new RegisterRequest("UPPER@TEST.COM", "Password1", "Test", "User"),
            "127.0.0.1", "ua");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("upper@test.com");
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login() with non-existent email throws 401 UnauthorisedException")
    void login_unknownEmail_throwsUnauthorised() {
        when(userRepository.findByEmailIgnoreCase("unknown@test.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknown@test.com", "anyPassword");

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "ua"))
            .isInstanceOf(UnauthorisedException.class)
            .hasMessage("Incorrect email or password.");
    }

    @Test
    @DisplayName("login() with wrong password throws 401 and writes to audit_log")
    void login_wrongPassword_throwsAndAudits() {
        User user = buildUser();
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest(user.getEmail(), "WrongPassword1");

        assertThatThrownBy(() -> authService.login(request, "10.0.0.1", "Mozilla/5.0"))
            .isInstanceOf(UnauthorisedException.class);

        verify(auditLogger).log(eq(user.getId()), eq("LOGIN_FAILED_BAD_PASSWORD"),
            anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("login() with correct credentials returns tokens and updates lastLoginAt")
    void login_correctCredentials_returnsTokens() {
        User user = buildUser();
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(tokenProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpiryMs()).thenReturn(900_000L);
        when(tokenProvider.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(
            new LoginRequest(user.getEmail(), "Password1"), "127.0.0.1", "ua");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo(user.getEmail());
        verify(userRepository).save(argThat(u -> u.getLastLoginAt() != null));
    }

    @Test
    @DisplayName("login() on deactivated account throws 401 with helpful message")
    void login_deactivatedAccount_throwsWithClearMessage() {
        User user = buildUser();
        user.setActive(false);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(user.getEmail(), "Password1"), "127.0.0.1", "ua"))
            .isInstanceOf(UnauthorisedException.class)
            .hasMessageContaining("deactivated");
    }

    // ── Refresh ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh() with revoked token triggers security lockout for all user tokens")
    void refresh_revokedToken_locksOutUser() {
        User user = buildUser();
        RefreshToken revokedToken = RefreshToken.builder()
            .id(UUID.randomUUID()).user(user)
            .tokenHash(AuthService.sha256("revoked-token"))
            .expiresAt(OffsetDateTime.now().plusDays(7))
            .revokedAt(OffsetDateTime.now().minusHours(1))  // already revoked
            .build();

        String rawToken = "revoked-token";
        when(tokenProvider.validateToken(rawToken)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256(rawToken)))
            .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(rawToken, "127.0.0.1", "ua"))
            .isInstanceOf(UnauthorisedException.class)
            .hasMessageContaining("suspicious");

        verify(refreshTokenRepository).revokeAllForUser(eq(user.getId()), any());
        verify(auditLogger).log(eq(user.getId()), eq("REFRESH_TOKEN_REUSE_DETECTED"),
            anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("refresh() with expired JWT returns 401 with session-expired message")
    void refresh_expiredJwt_returnsSessionExpiredMessage() {
        when(tokenProvider.validateToken("expired-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("expired-token", "127.0.0.1", "ua"))
            .isInstanceOf(UnauthorisedException.class)
            .hasMessageContaining("session has expired");
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout() revokes refresh token in DB")
    void logout_revokesToken() {
        UUID userId = UUID.randomUUID();
        String rawToken = "valid-refresh-token";
        RefreshToken rt = RefreshToken.builder()
            .id(UUID.randomUUID()).tokenHash(AuthService.sha256(rawToken))
            .expiresAt(OffsetDateTime.now().plusDays(1))
            .build();

        when(refreshTokenRepository.findByTokenHash(AuthService.sha256(rawToken)))
            .thenReturn(Optional.of(rt));
        when(refreshTokenRepository.save(any())).thenReturn(rt);

        authService.logout(userId, rawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private User buildUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("test@smarthire.com")
            .passwordHash(passwordEncoder.encode("Password1"))
            .firstName("Test")
            .lastName("User")
            .role(UserRole.CANDIDATE)
            .active(true)
            .emailVerified(false)
            .build();
    }
}
