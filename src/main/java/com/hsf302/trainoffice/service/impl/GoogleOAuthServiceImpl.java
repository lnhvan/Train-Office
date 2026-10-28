package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.GoogleOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleOAuthServiceImpl implements GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthServiceImpl.class);

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    @Value("${app.google.client-id:}")
    private String clientId;

    @Value("${app.google.client-secret:}")
    private String clientSecret;

    @Value("${app.google.redirect-uri:http://localhost:8081/google-login}")
    private String redirectUri;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    public GoogleOAuthServiceImpl(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", "openid email profile");
        params.put("state", state != null ? state : "");
        params.put("prompt", "select_account");

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return GOOGLE_AUTH_URL + "?" + query;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeCodeForUserInfo(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
        tokenRequest.add("code", code);
        tokenRequest.add("client_id", clientId);
        tokenRequest.add("client_secret", clientSecret);
        tokenRequest.add("redirect_uri", redirectUri);
        tokenRequest.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(tokenRequest, headers);

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restTemplate.postForObject(GOOGLE_TOKEN_URL, requestEntity, Map.class);
        } catch (HttpStatusCodeException ex) {
            log.error("Google token exchange error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException(I18n.t("msg.auth.googleError", ex.getResponseBodyAsString()));
        }

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException(I18n.t("msg.auth.missingAccessToken"));
        }

        String accessToken = (String) tokenResponse.get("access_token");

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);

        ResponseEntity<Map> userResponse;
        try {
            userResponse = restTemplate.exchange(
                    GOOGLE_USERINFO_URL, HttpMethod.GET, userEntity, Map.class);
        } catch (HttpStatusCodeException ex) {
            log.error("Google userinfo fetch error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException(I18n.t("msg.auth.missingGoogleProfile"));
        }

        Map<String, Object> userInfo = userResponse.getBody();
        if (userInfo == null || !userInfo.containsKey("email")) {
            throw new IllegalStateException(I18n.t("msg.auth.missingGoogleProfile"));
        }

        return userInfo;
    }

    @Override
    @Transactional
    public User processGoogleUser(Map<String, Object> userInfo) {
        String rawEmail = (String) userInfo.get("email");
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException(I18n.t("msg.auth.missingGoogleProfile"));
        }
        String email = rawEmail.trim().toLowerCase();

        User existingUser = userRepository.findByEmail(email);
        if (existingUser != null) {
            if (existingUser.getStatus() != UserStatus.ACTIVE) {
                throw new IllegalStateException(I18n.t("msg.auth.accountBlocked"));
            }
            return existingUser;
        }

        // Tự động tạo tài khoản mới nếu chưa tồn tại
        String rawName = (String) userInfo.get("name");
        String sanitizedName = cleanFullName(rawName, email);

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setFullName(sanitizedName);
        // Tạo mật khẩu ngẫu nhiên an toàn (đã băm BCrypt)
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setRole(UserRole.CUSTOMER);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setTokenVersion(0);

        return userRepository.save(newUser);
    }

    /**
     * Làm sạch tên theo regex chuẩn của User entity: chỉ chứa chữ Unicode và khoảng trắng.
     */
    private String cleanFullName(String rawName, String email) {
        if (rawName != null && !rawName.isBlank()) {
            String cleaned = rawName.replaceAll("[^\\p{L}\\s]", " ").trim().replaceAll("\\s+", " ");
            if (!cleaned.isBlank()) {
                return truncate(cleaned, 100);
            }
        }
        if (email != null && !email.isBlank()) {
            String prefix = email.split("@")[0].replaceAll("[^\\p{L}\\s]", " ").trim().replaceAll("\\s+", " ");
            if (!prefix.isBlank()) {
                return truncate(prefix, 100);
            }
        }
        return "Google User";
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength).trim() : text;
    }
}
