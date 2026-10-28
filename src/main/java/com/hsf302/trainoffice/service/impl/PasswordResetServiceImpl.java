package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.PasswordResetCode;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.PasswordResetCodeRepository;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.MailService;
import com.hsf302.trainoffice.service.PasswordResetService;
import com.hsf302.trainoffice.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final UserService userService;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetCodeRepository codeRepository,
                                    PasswordEncoder passwordEncoder,
                                    MailService mailService,
                                    UserService userService) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public RequestResult requestCode(String email) {
        User user = findActiveUser(email);
        if (user == null) {
            return RequestResult.SENT;
        }

        Optional<PasswordResetCode> latest = codeRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
        if (latest.isPresent()
                && latest.get().getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return RequestResult.TOO_SOON;
        }

        // Mã cũ chưa dùng hết hiệu lực khi có mã mới.
        codeRepository.invalidateAllForUser(user.getUserId());

        String code = String.format("%06d", random.nextInt(1_000_000));

        PasswordResetCode resetCode = new PasswordResetCode();
        resetCode.setUser(user);
        resetCode.setCodeHash(passwordEncoder.encode(code));
        resetCode.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        codeRepository.save(resetCode);

        mailService.send(
                user.getEmail(),
                I18n.t("mail.reset.subject"),
                I18n.t("mail.reset.body",
                        user.getFullName() != null ? user.getFullName() : user.getEmail(),
                        code,
                        CODE_TTL_MINUTES));

        return RequestResult.SENT;
    }

    @Override
    @Transactional
    public ResetResult resetPassword(String email, String code, String newPassword) {
        User user = findActiveUser(email);
        if (user == null) {
            return ResetResult.INVALID_CODE;
        }

        PasswordResetCode resetCode = codeRepository
                .findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId())
                .orElse(null);

        if (resetCode == null) {
            return ResetResult.INVALID_CODE;
        }

        if (resetCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            resetCode.setUsed(true);
            codeRepository.save(resetCode);
            return ResetResult.EXPIRED;
        }

        if (code == null || !passwordEncoder.matches(code.trim(), resetCode.getCodeHash())) {
            resetCode.setAttempts(resetCode.getAttempts() + 1);
            if (resetCode.getAttempts() >= MAX_ATTEMPTS) {
                resetCode.setUsed(true);
                codeRepository.save(resetCode);
                return ResetResult.TOO_MANY_ATTEMPTS;
            }
            codeRepository.save(resetCode);
            return ResetResult.INVALID_CODE;
        }

        resetCode.setUsed(true);
        codeRepository.save(resetCode);

        // Băm BCrypt + tăng tokenVersion (đăng xuất mọi thiết bị đang đăng nhập).
        userService.resetPassword(user.getEmail(), newPassword);

        return ResetResult.OK;
    }

    private User findActiveUser(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase());
        return user != null && user.getStatus() == UserStatus.ACTIVE ? user : null;
    }
}
