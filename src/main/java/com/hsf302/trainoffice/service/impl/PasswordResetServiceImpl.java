package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.MailService;
import com.hsf302.trainoffice.service.PasswordResetService;
import com.hsf302.trainoffice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final UserService userService;
    private final StringRedisTemplate redis;
    private final String keyPrefix;

    @Autowired
    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    MailService mailService,
                                    UserService userService,
                                    StringRedisTemplate redis) {
        this(userRepository, passwordEncoder, mailService, userService, redis, "rj:");
    }

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    MailService mailService,
                                    UserService userService,
                                    StringRedisTemplate redis,
                                    String keyPrefix) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.userService = userService;
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public RequestResult requestCode(String email) {
        if (email == null || email.isBlank()) {
            return RequestResult.SENT;
        }

        String normalizedEmail = email.trim().toLowerCase();
        String cooldownKey = getCooldownKey(normalizedEmail);

        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            return RequestResult.TOO_SOON;
        }

        User user = findActiveUser(normalizedEmail);
        if (user == null) {
            // Đặt cooldown để tránh spam và không làm lộ việc email có tài khoản hay không
            redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
            return RequestResult.SENT;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);

        String resetKey = getResetKey(normalizedEmail);
        redis.delete(resetKey);
        redis.opsForHash().putAll(resetKey, Map.of(
                "code_hash", codeHash,
                "attempts", "0"
        ));
        redis.expire(resetKey, Duration.ofMinutes(CODE_TTL_MINUTES));

        redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));

        mailService.sendPasswordResetOtp(
                user.getEmail(),
                user.getFullName() != null ? user.getFullName() : user.getEmail(),
                code,
                CODE_TTL_MINUTES);

        return RequestResult.SENT;
    }

    @Override
    public ResetResult resetPassword(String email, String code, String newPassword) {
        if (email == null || email.isBlank()) {
            return ResetResult.INVALID_CODE;
        }

        String normalizedEmail = email.trim().toLowerCase();
        User user = findActiveUser(normalizedEmail);
        if (user == null) {
            return ResetResult.INVALID_CODE;
        }

        String resetKey = getResetKey(normalizedEmail);
        Object codeHashObj = redis.opsForHash().get(resetKey, "code_hash");
        if (codeHashObj == null) {
            return ResetResult.EXPIRED;
        }

        String codeHash = codeHashObj.toString();
        Object attemptsObj = redis.opsForHash().get(resetKey, "attempts");
        int attempts = 0;
        if (attemptsObj != null) {
            try {
                attempts = Integer.parseInt(attemptsObj.toString());
            } catch (NumberFormatException ignored) {
            }
        }

        if (code == null || !passwordEncoder.matches(code.trim(), codeHash)) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                redis.delete(resetKey);
                return ResetResult.TOO_MANY_ATTEMPTS;
            }
            redis.opsForHash().put(resetKey, "attempts", String.valueOf(attempts));
            return ResetResult.INVALID_CODE;
        }

        // OTP đúng: Xóa khóa khỏi Redis ngay để không tái sử dụng được
        redis.delete(List.of(resetKey, getCooldownKey(normalizedEmail)));

        // Băm BCrypt + tăng tokenVersion (đăng xuất mọi thiết bị đang đăng nhập).
        userService.resetPassword(user.getEmail(), newPassword);

        return ResetResult.OK;
    }

    @Override
    public long getRemainingTtlSeconds(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        String resetKey = getResetKey(email.trim().toLowerCase());
        Long ttl = redis.getExpire(resetKey, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    @Override
    public long getCooldownRemainingSeconds(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        String cooldownKey = getCooldownKey(email.trim().toLowerCase());
        Long ttl = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    private String getResetKey(String normalizedEmail) {
        return keyPrefix + "pwd_reset:" + normalizedEmail;
    }

    private String getCooldownKey(String normalizedEmail) {
        return keyPrefix + "pwd_reset_cd:" + normalizedEmail;
    }

    private User findActiveUser(String email) {
        User user = userRepository.findByEmail(email);
        return user != null && user.getStatus() == UserStatus.ACTIVE ? user : null;
    }
}
