package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.dto.ProfileForm;
import com.hsf302.trainoffice.dto.RegisterRequest;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.UserService;
import com.hsf302.trainoffice.repository.PasswordResetCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    /** Mật khẩu đã băm BCrypt có dạng $2a$/$2b$/$2y$ + cost + 53 ký tự. */
    private static final java.util.regex.Pattern BCRYPT_HASH =
            java.util.regex.Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetCodeRepository passwordResetCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Băm giả để email không tồn tại vẫn tốn thời gian như email đúng (không dò được email qua thời gian phản hồi). */
    private volatile String dummyHash;

    @Override
    public boolean existsByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail == null) {
            return false;
        }

        return userRepository.existsByEmail(normalizedEmail);
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(I18n.t("err.userNotFound")));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public boolean register(RegisterRequest registerRequest) {
        if (registerRequest == null) {
            return false;
        }

        String fullName = clean(registerRequest.getFullName());
        String email = normalizeEmail(registerRequest.getEmail());
        String password = registerRequest.getPassword();
        String confirmPassword = registerRequest.getConfirmPassword();

        if (fullName == null || email == null || password == null || password.isBlank()) {
            return false;
        }

        if (confirmPassword == null || !confirmPassword.equals(password)) {
            return false;
        }

        if (userRepository.existsByEmail(email)) {
            return false;
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(encodePassword(password));
        user.setFullName(fullName);
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        try {
            userRepository.save(user);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    public User login(String email, String pwd) {
        String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail == null || pwd == null || pwd.isBlank()) {
            return null;
        }

        User user = userRepository.findByEmail(normalizedEmail);

        if (user == null) {
            passwordMatches(pwd, dummyHash());
            return null;
        }

        if (!passwordMatches(pwd, user.getPassword())) {
            return null;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            return null;
        }

        // Mật khẩu cũ còn lưu dạng chữ thường: đăng nhập đúng thì đổi sang BCrypt luôn.
        if (!isEncoded(user.getPassword())) {
            user.setPassword(encodePassword(pwd));
            user = userRepository.save(user);
        }

        return user;
    }

    /**
     * So mật khẩu người dùng nhập với giá trị trong DB.
     * DB có thể là BCrypt (tài khoản mới / đã nâng cấp) hoặc chữ thường (dữ liệu cũ).
     */
    private boolean passwordMatches(String raw, String stored) {
        if (raw == null || stored == null || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        if (isEncoded(stored)) {
            return passwordEncoder.matches(raw, stored);
        }
        return MessageDigest.isEqual(
                raw.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    /** BCrypt chỉ dùng 72 byte đầu (Spring báo lỗi nếu dài hơn) -> chặn trước với thông báo dễ hiểu. */
    private String encodePassword(String raw) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(I18n.t("err.passwordIsTooLong"));
        }
        return passwordEncoder.encode(raw);
    }

    private boolean isEncoded(String value) {
        return value != null && BCRYPT_HASH.matcher(value).matches();
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("railjet-dummy-password");
        }
        return dummyHash;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public List<User> searchUsers(String keyword, UserRole role, UserStatus status) {
        return userRepository.search(blankToNull(keyword), role, status);
    }

    @Override
    public Optional<User> getUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }

    @Override
    public User saveUser(User user) {
        // Sửa tài khoản: Admin chỉ có thể thay đổi vai trò (role) và trạng thái (status)
        if (user.getUserId() != null) {
            User existing = userRepository.findById(user.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.userNotFound")));

            boolean roleChanged = user.getRole() != null && existing.getRole() != user.getRole();
            boolean statusChanged = user.getStatus() != null && existing.getStatus() != user.getStatus();

            if (user.getRole() != null) {
                existing.setRole(user.getRole());
            }
            if (user.getStatus() != null) {
                existing.setStatus(user.getStatus());
            }

            // Nếu đổi vai trò hoặc khóa/hủy tài khoản, thu hồi token đăng nhập để buộc đăng nhập lại
            if (roleChanged || (statusChanged && existing.getStatus() != UserStatus.ACTIVE)) {
                existing.revokeTokens();
            }

            return userRepository.save(existing);
        }

        // Tạo mới tài khoản (userId == null)
        validateUser(user);
        user.setPassword(encodePassword(user.getPassword()));
        user.setEmail(normalizeEmail(user.getEmail()));
        user.setFullName(clean(user.getFullName()));

        if (user.getRole() == null) {
            user.setRole(UserRole.CUSTOMER);
        }

        if (user.getStatus() == null) {
            user.setStatus(UserStatus.ACTIVE);
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        passwordResetCodeRepository.deleteAllForUser(userId);
        userRepository.deleteById(userId);
        userRepository.flush();
    }

    @Override
    public User updateProfile(Long userId, ProfileForm profileForm) {
        User user = getUserById(userId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.userNotFound")));
        user.setFullName(profileForm.getFullName().trim());
        user.setIdentityNumber(blankToNull(profileForm.getIdentityNumber()));
        user.setDateOfBirth(profileForm.getDateOfBirth());
        user.setGender(profileForm.getGender());
        return userRepository.save(user);
    }

    @Override
    public void resetPassword(String email, String password) {
        User user = userRepository.findByEmail(email);
        if(user == null){
            throw new RuntimeException(I18n.t("err.userNotFound"));
        }

        user.setPassword(encodePassword(password));
        user.revokeTokens();

        userRepository.save(user);
    }

    @Override
    public boolean changePassword(Long userId,
                                  String currentPassword,
                                  String newPassword) {

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return false;
        }

        if (!passwordMatches(currentPassword, user.getPassword())) {
            return false;
        }

        user.setPassword(encodePassword(newPassword));
        user.revokeTokens();

        userRepository.save(user);

        return true;
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException(I18n.t("err.userIsRequired2"));
        }

        String email = normalizeEmail(user.getEmail());

        if (email == null) {
            throw new IllegalArgumentException(I18n.t("err.emailIsRequired"));
        }

        boolean duplicate = user.getUserId() == null
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndUserIdNot(email, user.getUserId());

        if (duplicate) {
            throw new IllegalArgumentException(I18n.t("err.emailAlreadyExists"));
        }

        if (user.getUserId() == null && (user.getPassword() == null || user.getPassword().isBlank())) {
            throw new IllegalArgumentException(I18n.t("err.passwordIsRequired"));
        }

        if (user.getPassword() != null && user.getPassword().length() > 255) {
            throw new IllegalArgumentException(I18n.t("err.passwordIsTooLong"));
        }

        if (user.getRole() == null) {
            throw new IllegalArgumentException(I18n.t("err.roleIsRequired"));
        }

        if (user.getStatus() == null) {
            throw new IllegalArgumentException(I18n.t("err.statusIsRequired"));
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim().toLowerCase();
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
