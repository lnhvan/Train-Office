package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Mã 6 số để đặt lại mật khẩu, gửi qua email.
 * Chỉ lưu bản băm BCrypt của mã (lộ database cũng không đọc được mã).
 */
@Entity
@Table(
        name = "password_reset_codes",
        indexes = @Index(name = "idx_password_reset_codes_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reset_code_id")
    private Long resetCodeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_password_reset_codes_user"))
    private User user;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
