package com.jobtracker.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "mySecretKeyForTestingPurposesOnlyAtLeast32Chars!!");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
    }

    @Test
    void generateTokenFromEmail_returnsNonBlankToken() {
        String token = jwtTokenProvider.generateTokenFromEmail("test@example.com");
        assertThat(token).isNotBlank();
    }

    @Test
    void getEmailFromToken_returnsCorrectEmail() {
        String token = jwtTokenProvider.generateTokenFromEmail("test@example.com");
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("test@example.com");
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = jwtTokenProvider.generateTokenFromEmail("test@example.com");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForInvalidToken() {
        assertThat(jwtTokenProvider.validateToken("not.a.valid.token")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForEmptyString() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }

    @Test
    void generateToken_withAuthentication_producesTokenContainingEmail() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("auth@example.com");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        String token = jwtTokenProvider.generateToken(authentication);

        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("auth@example.com");
    }
}
