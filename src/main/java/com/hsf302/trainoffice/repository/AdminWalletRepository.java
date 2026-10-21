package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.AdminWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminWalletRepository extends JpaRepository<AdminWallet, Long> {

    Optional<AdminWallet> findFirstByOrderByWalletIdAsc();
}