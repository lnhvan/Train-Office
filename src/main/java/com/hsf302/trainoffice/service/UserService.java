package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.RegisterRequest;
import com.hsf302.trainoffice.dto.ProfileForm;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    boolean existsByEmail(String email);

    User findById(Long id);

    User findByEmail(String email);

    boolean register(RegisterRequest registerRequest);

    User login(String email, String pwd);

    List<User> getAllUsers();

    List<User> searchUsers(String keyword, UserRole role, UserStatus status);

    Optional<User> getUserById(Long userId);

    User saveUser(User user);

    void deleteUser(Long userId);

    User updateProfile(Long userId, ProfileForm profileForm);

    void resetPassword(String email, String password);

    boolean changePassword(Long userId, String currentPassword, String newPassword);
}
