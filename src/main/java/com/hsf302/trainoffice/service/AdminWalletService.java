package com.hsf302.trainoffice.service;

import java.math.BigDecimal;

public interface AdminWalletService {

    BigDecimal getBalance();

    void addToBalance(BigDecimal amount);

    void subtractFromBalance(BigDecimal amount);
}