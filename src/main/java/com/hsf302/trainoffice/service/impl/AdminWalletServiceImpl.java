package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.WalletTransactionType;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.AdminWallet;
import com.hsf302.trainoffice.entity.AdminWalletTransaction;
import com.hsf302.trainoffice.repository.AdminWalletRepository;
import com.hsf302.trainoffice.repository.AdminWalletTransactionRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminWalletServiceImpl implements AdminWalletService {

    private final AdminWalletRepository adminWalletRepository;
    private final AdminWalletTransactionRepository transactionRepository;

    public AdminWalletServiceImpl(AdminWalletRepository adminWalletRepository,
                                  AdminWalletTransactionRepository transactionRepository) {
        this.adminWalletRepository = adminWalletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public BigDecimal getBalance() {
        return getOrCreateWallet().getBalance();
    }

    @Override
    @Transactional
    public void credit(BigDecimal amount, String referenceCode, String description) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }

        AdminWallet wallet = lockWallet();
        wallet.setBalance(wallet.getBalance().add(amount));
        adminWalletRepository.save(wallet);

        record(WalletTransactionType.CREDIT, amount, wallet.getBalance(), referenceCode, description);
    }

    @Override
    @Transactional
    public void debit(BigDecimal amount, String referenceCode, String description) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }

        AdminWallet wallet = lockWallet();

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(I18n.t(
                    "err.walletInsufficientBalance", formatVnd(wallet.getBalance()), formatVnd(amount)));
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        adminWalletRepository.save(wallet);

        record(WalletTransactionType.DEBIT, amount, wallet.getBalance(), referenceCode, description);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminWalletTransaction> getRecentTransactions() {
        return transactionRepository.findTop10ByOrderByTransactionIdDesc();
    }

    private static String formatVnd(BigDecimal amount) {
        return String.format("%,d", amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue());
    }

    private void record(WalletTransactionType type,
                        BigDecimal amount,
                        BigDecimal balanceAfter,
                        String referenceCode,
                        String description) {
        transactionRepository.save(AdminWalletTransaction.builder()
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceCode(referenceCode)
                .description(description)
                .build());
    }

    /** Ví (tạo nếu chưa có), khóa dòng tới hết transaction. */
    private AdminWallet lockWallet() {
        Long walletId = getOrCreateWallet().getWalletId();

        return adminWalletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalStateException("Admin wallet not found"));
    }

    private AdminWallet getOrCreateWallet() {
        return adminWalletRepository.findFirstByOrderByWalletIdAsc()
                .orElseGet(() -> {
                    AdminWallet wallet = new AdminWallet();
                    wallet.setBalance(BigDecimal.ZERO);
                    return adminWalletRepository.save(wallet);
                });
    }
}
