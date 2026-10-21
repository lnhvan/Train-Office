package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.dto.ProfileForm;
import com.hsf302.trainoffice.dto.RegisterRequest;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

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
                .orElseThrow(() -> new RuntimeException("User not found"));
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
        user.setPassword(password);
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

        User user = userRepository.findByEmailAndPassword(normalizedEmail, pwd);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return null;
        }

        return user;
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
        validateUser(user);

        if (user.getUserId() != null) {
            userRepository.findById(user.getUserId()).ifPresent(existing -> {
                user.setCreatedAt(existing.getCreatedAt());

                if (user.getPassword() == null || user.getPassword().isBlank()) {
                    user.setPassword(existing.getPassword());
                }
            });
        }

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
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Override
    public User updateProfile(Long userId, ProfileForm profileForm) {
        User user = getUserById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
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
            throw new RuntimeException("User not found");
        }

        user.setPassword(password);

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

        if (!user.getPassword().equals(currentPassword)) {
            return false;
        }

        user.setPassword(newPassword);

        userRepository.save(user);

        return true;
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }

        String email = normalizeEmail(user.getEmail());

        if (email == null) {
            throw new IllegalArgumentException("Email is required.");
        }

        boolean duplicate = user.getUserId() == null
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndUserIdNot(email, user.getUserId());

        if (duplicate) {
            throw new IllegalArgumentException("Email already exists.");
        }

        if (user.getUserId() == null && (user.getPassword() == null || user.getPassword().isBlank())) {
            throw new IllegalArgumentException("Password is required.");
        }

        if (user.getPassword() != null && user.getPassword().length() > 255) {
            throw new IllegalArgumentException("Password is too long.");
        }

        if (user.getRole() == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        if (user.getStatus() == null) {
            throw new IllegalArgumentException("Status is required.");
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
