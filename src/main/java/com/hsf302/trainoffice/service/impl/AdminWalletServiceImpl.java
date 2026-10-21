package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.AdminWallet;
import com.hsf302.trainoffice.repository.AdminWalletRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AdminWalletServiceImpl implements AdminWalletService {

    private final AdminWalletRepository adminWalletRepository;

    private static final Object walletLock = new Object();

    public AdminWalletServiceImpl(AdminWalletRepository adminWalletRepository) {
        this.adminWalletRepository = adminWalletRepository;
    }

    @Override
    @Transactional
    public BigDecimal getBalance() {
        return getOrCreateWallet().getBalance();
    }

    @Override
    @Transactional
    public void addToBalance(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        synchronized (walletLock) {
            AdminWallet wallet = getOrCreateWallet();
            wallet.setBalance(wallet.getBalance().add(amount));
            adminWalletRepository.save(wallet);
        }
    }

    @Override
    @Transactional
    public void subtractFromBalance(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        synchronized (walletLock) {
            AdminWallet wallet = getOrCreateWallet();
            wallet.setBalance(wallet.getBalance().subtract(amount));
            adminWalletRepository.save(wallet);
        }
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