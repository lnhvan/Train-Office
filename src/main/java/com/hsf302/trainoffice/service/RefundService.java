package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Refund;
import com.hsf302.trainoffice.entity.User;

import java.util.List;
import java.util.Optional;
public interface RefundService {

    Refund createRefundRequest(Long ticketId, User user, com.hsf302.trainoffice.dto.RefundRequestForm form);

    List<Refund> getPendingRefunds();

    void approveRefund(Long refundId, User adminUser);

    void rejectRefund(Long refundId, User adminUser);
    Optional<Refund> getRefundById(Long refundId);
}