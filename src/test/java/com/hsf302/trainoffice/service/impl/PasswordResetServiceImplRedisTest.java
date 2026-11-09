package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.MailService;
import com.hsf302.trainoffice.service.PasswordResetService;
import com.hsf302.trainoffice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplRedisTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private UserService userService;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordResetServiceImpl service;

    private User activeUser;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);

        service = new PasswordResetServiceImpl(
                userRepository,
                passwordEncoder,
                mailService,
                userService,
                redis,
                "test:rj:"
        );

        activeUser = new User();
        activeUser.setUserId(1L);
        activeUser.setEmail("traveler@railjet.com");
        activeUser.setFullName("Nguyen Van A");
        activeUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void requestCode_WhenCooldownActive_ReturnsTooSoon() {
        when(redis.hasKey("test:rj:pwd_reset_cd:traveler@railjet.com")).thenReturn(true);

        PasswordResetService.RequestResult result = service.requestCode("traveler@railjet.com");

        assertEquals(PasswordResetService.RequestResult.TOO_SOON, result);
        verify(mailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void requestCode_WhenActiveUser_SavesToRedisWith5MinTtlAndSendsMail() {
        when(redis.hasKey("test:rj:pwd_reset_cd:traveler@railjet.com")).thenReturn(false);
        when(userRepository.findByEmail("traveler@railjet.com")).thenReturn(activeUser);

        PasswordResetService.RequestResult result = service.requestCode("traveler@railjet.com");

        assertEquals(PasswordResetService.RequestResult.SENT, result);
        verify(redis).delete("test:rj:pwd_reset:traveler@railjet.com");
        verify(hashOps).putAll(eq("test:rj:pwd_reset:traveler@railjet.com"), anyMap());
        verify(redis).expire("test:rj:pwd_reset:traveler@railjet.com", Duration.ofMinutes(5));
        verify(valueOps).set(eq("test:rj:pwd_reset_cd:traveler@railjet.com"), eq("1"), eq(Duration.ofSeconds(60)));
        verify(mailService).sendPasswordResetOtp(eq("traveler@railjet.com"), anyString(), anyString(), eq(5));
    }

    @Test
    void resetPassword_WhenKeyExpired_ReturnsExpired() {
        when(userRepository.findByEmail("traveler@railjet.com")).thenReturn(activeUser);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "code_hash")).thenReturn(null);

        PasswordResetService.ResetResult result = service.resetPassword("traveler@railjet.com", "123456", "NewPass123@");

        assertEquals(PasswordResetService.ResetResult.EXPIRED, result);
        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void resetPassword_WhenCodeMatches_DeletesFromRedisAndResetsPassword() {
        String rawCode = "654321";
        String encoded = passwordEncoder.encode(rawCode);

        when(userRepository.findByEmail("traveler@railjet.com")).thenReturn(activeUser);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "code_hash")).thenReturn(encoded);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "attempts")).thenReturn("0");

        PasswordResetService.ResetResult result = service.resetPassword("traveler@railjet.com", rawCode, "NewPass123@");

        assertEquals(PasswordResetService.ResetResult.OK, result);
        verify(userService).resetPassword("traveler@railjet.com", "NewPass123@");
        verify(redis).delete(anyList());
    }

    @Test
    void resetPassword_WhenCodeInvalid_IncrementsAttempts() {
        String encoded = passwordEncoder.encode("654321");

        when(userRepository.findByEmail("traveler@railjet.com")).thenReturn(activeUser);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "code_hash")).thenReturn(encoded);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "attempts")).thenReturn("2");

        PasswordResetService.ResetResult result = service.resetPassword("traveler@railjet.com", "000000", "NewPass123@");

        assertEquals(PasswordResetService.ResetResult.INVALID_CODE, result);
        verify(hashOps).put("test:rj:pwd_reset:traveler@railjet.com", "attempts", "3");
        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void resetPassword_WhenMaxAttemptsReached_DeletesKeyAndReturnsTooManyAttempts() {
        String encoded = passwordEncoder.encode("654321");

        when(userRepository.findByEmail("traveler@railjet.com")).thenReturn(activeUser);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "code_hash")).thenReturn(encoded);
        when(hashOps.get("test:rj:pwd_reset:traveler@railjet.com", "attempts")).thenReturn("4");

        PasswordResetService.ResetResult result = service.resetPassword("traveler@railjet.com", "000000", "NewPass123@");

        assertEquals(PasswordResetService.ResetResult.TOO_MANY_ATTEMPTS, result);
        verify(redis).delete("test:rj:pwd_reset:traveler@railjet.com");
        verify(userService, never()).resetPassword(anyString(), anyString());
    }
}
