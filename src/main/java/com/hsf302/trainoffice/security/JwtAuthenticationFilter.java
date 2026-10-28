package com.hsf302.trainoffice.security;

import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Chạy đầu mỗi request: đọc JWT trong cookie, kiểm tra chữ ký + hạn dùng,
 * nạp lại User từ database rồi đặt vào request attribute "currentUser".
 *
 * Nạp lại từ DB mỗi request để thay đổi có hiệu lực ngay:
 *   - tài khoản bị khóa (status khác ACTIVE)      -> coi như chưa đăng nhập
 *   - đổi / đặt lại mật khẩu (tokenVersion tăng)  -> token cũ hết hiệu lực
 *   - đổi role                                     -> quyền mới áp dụng ngay
 *
 * Token đã dùng quá nửa thời hạn sẽ được cấp lại (người đang dùng không bị đăng xuất giữa chừng).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String[] STATIC_PREFIXES = {
            "/css/", "/js/", "/images/", "/img/", "/fonts/", "/webjars/", "/favicon"
    };

    private final AuthCookie authCookie;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(AuthCookie authCookie,
                                   JwtService jwtService,
                                   UserRepository userRepository) {
        this.authCookie = authCookie;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = authCookie.read(request);

        if (token != null && !token.isBlank()) {
            Optional<User> user = jwtService.verify(token)
                    .flatMap(claims -> loadUser(claims).map(found -> {
                        refreshIfOld(response, found, claims);
                        return found;
                    }));

            if (user.isPresent()) {
                CurrentUser.set(request, user.get());
            } else {
                authCookie.clear(response);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> loadUser(JwtService.TokenClaims claims) {
        return userRepository.findById(claims.userId())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.currentTokenVersion() == claims.version());
    }

    private void refreshIfOld(HttpServletResponse response, User user, JwtService.TokenClaims claims) {
        Duration used = Duration.between(claims.issuedAt(), Instant.now());
        if (used.compareTo(jwtService.getTtl().dividedBy(2)) > 0) {
            authCookie.signIn(response, user);
        }
    }
}
