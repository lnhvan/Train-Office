package com.hsf302.trainoffice.security;

import com.hsf302.trainoffice.entity.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Tạo và kiểm tra JWT đăng nhập (HS256, ký bằng khóa bí mật app.jwt.secret).
 *
 * Nội dung token:
 *   sub  = userId
 *   role = CUSTOMER / ADMIN
 *   ver  = User.tokenVersion (tăng khi đổi / đặt lại mật khẩu -> mọi token cũ hết hiệu lực)
 *   iat, exp, jti, iss
 */
@Service
public class JwtService {

    private static final String ISSUER = "railjet";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_VERSION = "ver";

    private final byte[] secret;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.secret = decodeSecret(secret);
        this.ttl = Duration.ofMinutes(Math.max(5, expirationMinutes));

        if (this.secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret phải dài ít nhất 32 byte (256 bit) cho HS256.");
        }
    }

    public Duration getTtl() {
        return ttl;
    }

    public String createToken(User user) {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_ROLE, user.getRole() != null ? user.getRole().name() : null)
                .claim(CLAIM_VERSION, user.currentTokenVersion())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .jwtID(UUID.randomUUID().toString())
                .build();

        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Không ký được JWT", ex);
        }
    }

    /**
     * Kiểm tra chữ ký, thuật toán, issuer và hạn dùng.
     * Trả về rỗng nếu token sai / hết hạn / bị sửa.
     */
    public Optional<TokenClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            SignedJWT jwt = SignedJWT.parse(token);

            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            if (!jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiresAt = claims.getExpirationTime();
            Date issuedAt = claims.getIssueTime();

            if (!ISSUER.equals(claims.getIssuer()) || expiresAt == null || issuedAt == null
                    || expiresAt.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }

            Long userId = Long.valueOf(claims.getSubject());
            Integer version = claims.getIntegerClaim(CLAIM_VERSION);

            return Optional.of(new TokenClaims(
                    userId,
                    claims.getStringClaim(CLAIM_ROLE),
                    version != null ? version : 0,
                    issuedAt.toInstant(),
                    expiresAt.toInstant()));
        } catch (ParseException | JOSEException | NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình app.jwt.secret.");
        }
        String value = secret.trim();
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException notBase64) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Thông tin đọc được từ một JWT hợp lệ. */
    public record TokenClaims(Long userId, String role, int version, Instant issuedAt, Instant expiresAt) {
    }
}
