package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.User;

import java.util.Map;

public interface GoogleOAuthService {

    /** Kiểm tra xem Client ID và Client Secret của Google đã được cấu hình hay chưa. */
    boolean isConfigured();

    /** Tạo URL chuyển hướng sang Google OAuth 2.0 kèm CSRF state. */
    String buildAuthorizationUrl(String state);

    /** Gửi authorization code lên Google để đổi lấy thông tin người dùng (UserInfo). */
    Map<String, Object> exchangeCodeForUserInfo(String code);

    /** Tìm hoặc tự động tạo tài khoản User từ thông tin Google trả về. */
    User processGoogleUser(Map<String, Object> userInfo);
}
