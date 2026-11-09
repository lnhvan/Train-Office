package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.AdminWalletTransaction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ví quản trị: sổ theo dõi tiền hệ thống đang giữ.
 * Cộng khi khách thanh toán thành công, trừ khi admin xác nhận đã chuyển tiền hoàn cho khách.
 * Mọi biến động đều được ghi vào lịch sử giao dịch.
 */
public interface AdminWalletService {

    BigDecimal getBalance();

    /** Cộng tiền vào ví (amount > 0). */
    void credit(BigDecimal amount, String referenceCode, String description);

    /** Trừ tiền khỏi ví (amount > 0). Ném IllegalStateException nếu số dư không đủ. */
    void debit(BigDecimal amount, String referenceCode, String description);

    /** 10 giao dịch gần nhất, mới nhất trước. */
    List<AdminWalletTransaction> getRecentTransactions();
}
