package com.jobtracker.service;

import com.jobtracker.dto.*;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "passwordResetTokenExpiryMinutes", 30);
    }

    // --- register ---

    @Test
    void register_withNewEmail_savesUserAndReturnsToken() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setName("Test User");
        request.setPassword("password123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(tokenProvider.generateTokenFromEmail(request.getEmail())).thenReturn("jwt-token");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getName()).isEqualTo("Test User");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_withExistingEmail_throwsBadRequestException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("taken@example.com");
        request.setName("Test User");
        request.setPassword("password123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_encodesPasswordBeforeSaving() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setName("User");
        request.setPassword("plaintext");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(tokenProvider.generateTokenFromEmail(any())).thenReturn("token");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertThat(saved.getPassword()).isEqualTo("hashed");
            return saved;
        });

        authService.register(request);

        verify(passwordEncoder).encode("plaintext");
    }

    // --- login ---

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("User")
                .build();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void login_withUnknownEmail_throwsResourceNotFoundException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("token");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- forgotPassword ---

    @Test
    void forgotPassword_withExistingUser_savesTokenAndSendsEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("User")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        authService.forgotPassword(request);

        assertThat(user.getPasswordResetToken()).isNotNull();
        assertThat(user.getPasswordResetTokenExpiry()).isAfter(LocalDateTime.now());
        verify(userRepository).save(user);
        verify(emailService).sendPasswordResetEmail(eq("user@example.com"), anyString());
    }

    @Test
    void forgotPassword_withNonExistingUser_doesNothing() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("nobody@example.com");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    // --- resetPassword ---

    @Test
    void resetPassword_withValidToken_updatesPasswordAndClearsToken() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-reset-token");
        request.setNewPassword("newPassword123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("User")
                .passwordResetToken("valid-reset-token")
                .passwordResetTokenExpiry(LocalDateTime.now().plusMinutes(10))
                .build();

        when(userRepository.findByPasswordResetToken("valid-reset-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hashed");
        when(userRepository.save(any())).thenReturn(user);

        authService.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("new-hashed");
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetTokenExpiry()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_withInvalidToken_throwsBadRequestException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("bad-token");
        request.setNewPassword("newPassword123");

        when(userRepository.findByPasswordResetToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid or expired reset token");
    }

    @Test
    void resetPassword_withExpiredToken_throwsBadRequestException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("expired-token");
        request.setNewPassword("newPassword123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("User")
                .passwordResetToken("expired-token")
                .passwordResetTokenExpiry(LocalDateTime.now().minusMinutes(5))
                .build();

        when(userRepository.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Reset token has expired");

        verify(userRepository, never()).save(any());
    }
}
