package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.AdminWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminWalletRepository extends JpaRepository<AdminWallet, Long> {

    Optional<AdminWallet> findFirstByOrderByWalletIdAsc();

    /** Đọc ví và khóa dòng tới hết transaction, để hai giao dịch cộng/trừ đồng thời không ghi đè nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from AdminWallet w where w.walletId = :walletId")
    Optional<AdminWallet> findByIdForUpdate(@Param("walletId") Long walletId);
}
