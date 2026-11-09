package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.AdminWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminWalletTransactionRepository extends JpaRepository<AdminWalletTransaction, Long> {

    List<AdminWalletTransaction> findTop10ByOrderByTransactionIdDesc();
}
