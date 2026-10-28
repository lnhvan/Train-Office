package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** Mã mới nhất của tài khoản (dù đã dùng hay chưa) - dùng để chặn gửi lại quá nhanh. */
    Optional<PasswordResetCode> findFirstByUser_UserIdOrderByCreatedAtDesc(Long userId);

    /** Mã mới nhất còn dùng được. */
    Optional<PasswordResetCode> findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("update PasswordResetCode c set c.used = true where c.user.userId = :userId and c.used = false")
    int invalidateAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PasswordResetCode c where c.user.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
